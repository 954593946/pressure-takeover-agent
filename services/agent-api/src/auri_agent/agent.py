from __future__ import annotations

import asyncio
import logging
import re
from dataclasses import dataclass
from typing import Any

from langchain.agents import create_agent
from langchain.agents.middleware import ModelRequest, dynamic_prompt
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import InMemorySaver

from .config import Settings
from .engine import build_execution_receipt, build_preparation_receipt
from .llm import fallback_tasks
from .models import InteractionOutput, Scene, Surface, WorldState, output_expiry
from .prompts import build_agent_prompt, build_completion_prompt
from .tools import AURI_TOOLS, AgentToolContext, AgentToolbox, TaskDraft


logger = logging.getLogger(__name__)


@dynamic_prompt
def auri_dynamic_prompt(request: ModelRequest) -> str:
    context = request.runtime.context
    if isinstance(context, AgentToolContext):
        return build_agent_prompt(context.toolbox.state)
    return "你是 AURI。只能依据当前事实和工具结果回答，不得虚构执行结果。"


@dataclass
class AgentRunResult:
    state: WorldState
    reply: str
    mode: str
    called_tools: list[str]


class AuriAgent:
    """LangChain orchestration layer over an isolated deterministic WorldState copy."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.model: ChatOpenAI | None = None
        self.graph = None
        self.last_mode = "fallback"
        self.last_tools: list[str] = []
        self._model_lock = asyncio.Lock()
        if settings.llm_configured:
            self.model = ChatOpenAI(
                model=settings.openai_model,
                api_key=settings.openai_api_key,
                base_url=settings.openai_base_url.rstrip("/"),
                timeout=settings.openai_timeout_seconds,
                max_retries=0,
                temperature=0.2,
            )
            self.graph = create_agent(
                model=self.model,
                tools=AURI_TOOLS,
                middleware=[auri_dynamic_prompt],
                context_schema=AgentToolContext,
                checkpointer=InMemorySaver(),
                name="auri_orchestrator",
            )

    @property
    def configured(self) -> bool:
        return self.graph is not None

    async def handle(
        self,
        text: str,
        state: WorldState,
        *,
        source: str,
        event_id: str,
    ) -> AgentRunResult:
        original = state.model_copy(deep=True)
        explicit_ac = self._parse_explicit_ac_command(text)
        if explicit_ac is not None:
            toolbox = AgentToolbox(
                original.model_copy(deep=True),
                event_id=event_id,
                source=source,
                original_text=text,
            )
            toolbox.control_ac(**explicit_ac)
            reply = self._state_reply(toolbox.state, toolbox.called_tools)
            reply = self._ground_reply(reply, toolbox.state, toolbox.called_tools)
            self._apply_reply(toolbox.state, reply)
            self.last_mode = "deterministic_tool"
            self.last_tools = list(toolbox.called_tools)
            return AgentRunResult(
                state=toolbox.state,
                reply=reply,
                mode=self.last_mode,
                called_tools=list(toolbox.called_tools),
            )

        if self.graph is not None:
            toolbox = AgentToolbox(
                original.model_copy(deep=True),
                event_id=event_id,
                source=source,
                original_text=text,
            )
            try:
                async with self._model_lock:
                    result = await self.graph.ainvoke(
                        {"messages": [{"role": "user", "content": text}]},
                        context=AgentToolContext(toolbox=toolbox),
                        config={
                            "configurable": {"thread_id": original.session_id},
                            "recursion_limit": 12,
                        },
                    )
                reply = self._extract_last_ai_text(result.get("messages", []))
                reply = self._ground_reply(reply, toolbox.state, toolbox.called_tools)
                self._apply_reply(toolbox.state, reply)
                self.last_mode = "langchain_agent"
                self.last_tools = list(toolbox.called_tools)
                return AgentRunResult(
                    state=toolbox.state,
                    reply=reply,
                    mode=self.last_mode,
                    called_tools=list(toolbox.called_tools),
                )
            except Exception as exc:  # provider/tool failures must preserve the demo path
                logger.warning("AURI agent fell back after %s", type(exc).__name__)
                if toolbox.called_tools:
                    reply = self._state_reply(toolbox.state, toolbox.called_tools)
                    reply = self._ground_reply(reply, toolbox.state, toolbox.called_tools)
                    self._apply_reply(toolbox.state, reply)
                    self.last_mode = "langchain_agent_fallback_reply"
                    self.last_tools = list(toolbox.called_tools)
                    return AgentRunResult(
                        state=toolbox.state,
                        reply=reply,
                        mode=self.last_mode,
                        called_tools=list(toolbox.called_tools),
                    )

        fallback = self._fallback(text, original, source=source, event_id=event_id)
        self.last_mode = fallback.mode
        self.last_tools = list(fallback.called_tools)
        return fallback

    @staticmethod
    def _parse_explicit_ac_command(text: str) -> dict[str, Any] | None:
        """Recognise a standalone, unambiguous AC command without consulting chat history."""
        compact = re.sub(r"[\s，。！？!?、,.；;：:]", "", text).lower()
        if "空调" not in compact:
            return None
        if compact.endswith("吗") or any(marker in compact for marker in ("是否", "是不是", "有没有", "空调状态")):
            return None

        # Compound requests still go through the agent so another intent is not
        # silently discarded by the deterministic fast path.
        other_intent_markers = (
            "任务",
            "提醒",
            "日程",
            "会议",
            "路况",
            "消息",
            "下单",
            "采购",
            "超市",
            "接孩子",
        )
        if any(marker in compact for marker in other_intent_markers):
            return None

        off_markers = (
            "关空调",
            "关闭空调",
            "把空调关",
            "空调关掉",
            "空调关闭",
            "不要开空调",
            "别开空调",
            "不用开空调",
        )
        on_markers = (
            "开空调",
            "打开空调",
            "开启空调",
            "空调打开",
            "空调开启",
            "空调开一下",
        )
        turn_off = any(marker in compact for marker in off_markers)
        turn_on = any(marker in compact for marker in on_markers)
        temperature_match = re.search(
            r"(?:调到|设为|设置为|调成|开到|到)(\d{1,2})(?:度|℃)",
            compact,
        )

        if turn_off:
            return {"ac_on": False}
        if not turn_on and temperature_match is None:
            return None

        command: dict[str, Any] = {"ac_on": True}
        if temperature_match is not None:
            command["target_temp"] = float(temperature_match.group(1))
        return command

    async def compose_confirmation_reply(self, state: WorldState, *, decision: str) -> str:
        if self.model is not None:
            try:
                async with self._model_lock:
                    message = await self.model.ainvoke(
                        [
                            SystemMessage(content=build_completion_prompt(state, decision)),
                            HumanMessage(content="请直接给用户最终结果，不要输出 JSON 或分析过程。"),
                        ]
                    )
                reply = self._ground_reply(self._message_text(message), state, ["confirm_current_actions"])
                if reply:
                    self.last_mode = "langchain_agent"
                    self.last_tools = ["confirm_current_actions"]
                    return reply
            except Exception as exc:
                logger.warning("AURI completion reply fell back after %s", type(exc).__name__)
        self.last_mode = "fallback_reply"
        self.last_tools = ["confirm_current_actions"]
        return self._state_reply(state, self.last_tools)

    def _fallback(
        self,
        text: str,
        state: WorldState,
        *,
        source: str,
        event_id: str,
    ) -> AgentRunResult:
        toolbox = AgentToolbox(state, event_id=event_id, source=source, original_text=text)
        compact = text.replace(" ", "")
        has_pending_confirmation = state.confirmation is not None and state.confirmation.status == "pending"
        if has_pending_confirmation and any(
            marker in compact for marker in ("拒绝", "取消", "不要执行", "确认", "同意", "执行吧")
        ):
            decision = "reject" if any(marker in compact for marker in ("拒绝", "取消", "不要执行")) else "accept"
            toolbox.confirm_current_actions(decision)
        elif any(marker in compact for marker in ("现在什么状态", "现在怎么样", "当前状态", "进展", "有哪些任务", "有什么任务")):
            toolbox.get_status()
        elif "会议" in compact and any(marker in compact for marker in ("延迟", "超时", "晚了", "拖堂")):
            match = re.search(r"(\d{1,3})\s*分钟", text)
            toolbox.report_meeting_delay(int(match.group(1)) if match else 15)
        elif any(marker in compact for marker in ("帮我处理", "替我处理", "帮忙", "怎么办", "替我安排")):
            toolbox.prepare_assistance(include_messages=True, include_grocery=True)
        elif any(
            marker in compact
            for marker in (
                "创建任务",
                "新增任务",
                "提醒我",
                "记一下",
                "记下",
                "请记",
                "安排一个",
                "接孩子",
                "去超市",
                "买菜",
                "采购",
            )
        ):
            drafts = [
                TaskDraft(
                    title=task.title,
                    scheduled_at=task.scheduled_at,
                    location=task.location,
                    task_type=task.task_type,
                    priority=task.priority,
                    adjustable=task.adjustable,
                    waiting_party=task.waiting_party,
                    capability_tags=task.capability_tags,
                )
                for task in fallback_tasks(text)
            ]
            toolbox.create_tasks(drafts, replace_existing=False)

        reply = self._state_reply(toolbox.state, toolbox.called_tools)
        reply = self._ground_reply(reply, toolbox.state, toolbox.called_tools)
        self._apply_reply(toolbox.state, reply)
        return AgentRunResult(
            state=toolbox.state,
            reply=reply,
            mode="fallback",
            called_tools=list(toolbox.called_tools),
        )

    def _ground_reply(self, reply: str, state: WorldState, called_tools: list[str]) -> str:
        reply = " ".join((reply or "").strip().split())
        pending = state.confirmation is not None and state.confirmation.status == "pending"
        completion_claims = ("已发送", "已经发送", "已下单", "已经下单", "已提交", "都处理好了", "全部完成")
        if pending and any(claim in reply for claim in completion_claims):
            reply = self._state_reply(state, called_tools)
        if (
            "confirm_current_actions" in called_tools
            and any(action.status == "completed" for action in state.actions)
            and not self._has_execution_details(reply, state)
        ):
            reply = self._state_reply(state, called_tools)
        if not reply:
            reply = self._state_reply(state, called_tools)
        max_chars = 90 if state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING} else 240
        if len(reply) > max_chars:
            reply = reply[: max_chars - 1].rstrip("，。；; ") + "。"
        return reply

    def _state_reply(self, state: WorldState, called_tools: list[str]) -> str:
        if "confirm_current_actions" in called_tools:
            if state.confirmation and state.confirmation.status == "rejected":
                return "已按你的要求取消，本次没有执行消息或订单。你可以随时让我重新整理方案。"
            completed = [action for action in state.actions if action.status == "completed"]
            if completed:
                return build_execution_receipt(
                    state,
                    include_order_id=state.scene not in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING},
                )
            return "当前没有可执行的待确认方案，我没有进行任何操作。"
        if "prepare_assistance" in called_tools:
            actions = [action for action in state.actions if action.status == "awaiting_confirmation"]
            if not actions:
                return "我看过当前任务了，还没有找到可以安全代办的事项。你可以再告诉我希望处理哪一件。"
            return build_preparation_receipt(state)
        if "create_tasks" in called_tools:
            titles = [task.title for task in state.tasks[-3:]]
            return "记好了：" + "、".join(titles) + "。我会按时间和任务刚性帮你持续留意。"
        if "reschedule_task" in called_tools:
            changed = [task for task in state.tasks if task.status == "rescheduled"]
            if changed:
                return f"已把“{changed[-1].title}”调整到新的时间，我会按更新后的安排继续跟进。"
        if "control_ac" in called_tools:
            vehicle = state.vehicle_state
            if not vehicle.ac_on:
                return "空调已关闭，车机和手机状态已经同步。"
            temperature = int(vehicle.ac_target_temp) if vehicle.ac_target_temp.is_integer() else vehicle.ac_target_temp
            return f"空调已打开，当前设为{temperature}℃，车机和手机状态已经同步。"
        if "report_meeting_delay" in called_tools:
            return f"会议延迟已经记下，当前压力等级为{state.risk.pressure_level.value}。我会继续结合任务和路况判断是否需要介入。"
        if "get_status" in called_tools:
            pending = len([task for task in state.tasks if task.status == "pending"])
            confirmation = "，有一项方案等待确认" if state.confirmation and state.confirmation.status == "pending" else ""
            return f"目前有{pending}项待办，压力等级为{state.risk.pressure_level.value}{confirmation}。"
        return "我在。你可以直接告诉我要新增什么任务、调整哪项安排，或者让我查看当前进展。"

    @staticmethod
    def _has_execution_details(reply: str, state: WorldState) -> bool:
        completed_messages = [
            action for action in state.actions if action.type == "message" and action.status == "completed"
        ]
        if completed_messages and not all(action.target in reply for action in completed_messages):
            return False
        if completed_messages:
            timing_detail = (
                state.eta.strftime("%H:%M")
                if state.eta is not None
                else f"{state.risk.late_minutes}分钟" if state.risk.late_minutes > 0 else "到达时间"
            )
            if timing_detail not in reply:
                return False
        submitted_orders = [order for order in state.service_orders if order.status == "submitted"]
        for order in submitted_orders:
            visible_items = order.items[:2]
            if not all(item.name in reply for item in visible_items):
                return False
            if f"{order.total:.0f}" not in reply or order.delivery_window not in reply:
                return False
            if "配送" not in reply and "商超" not in reply:
                return False
        has_receipt = bool(completed_messages or submitted_orders)
        return has_receipt and ("Demo" in reply or "模拟" in reply)

    def _apply_reply(self, state: WorldState, reply: str) -> None:
        requires_confirmation = state.confirmation is not None and state.confirmation.status == "pending"
        if state.output is not None:
            state.output.conclusion = reply
            state.output.requires_confirmation = requires_confirmation
            return
        suppressed = (
            ["mobile", "wearable"]
            if state.primary_surface == Surface.VEHICLE_HMI
            else ["vehicle_hmi", "wearable"]
        )
        state.output = InteractionOutput(
            message_id=f"msg_agent_{abs(hash((state.session_id, state.revision, reply))) % 10**12:012d}",
            priority="high" if state.risk.pressure_level.value in {"L2", "L3"} else "normal",
            owner_surface=state.primary_surface,
            suppressed_surfaces=suppressed,
            expires_at=output_expiry(1 if state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING} else 5),
            requires_confirmation=requires_confirmation,
            conclusion=reply,
        )

    def _extract_last_ai_text(self, messages: list[Any]) -> str:
        for message in reversed(messages):
            if isinstance(message, AIMessage):
                text = self._message_text(message)
                if text:
                    return text
        return ""

    @staticmethod
    def _message_text(message: Any) -> str:
        content = getattr(message, "content", "")
        if isinstance(content, str):
            return content.strip()
        if isinstance(content, list):
            parts: list[str] = []
            for block in content:
                if isinstance(block, str):
                    parts.append(block)
                elif isinstance(block, dict) and isinstance(block.get("text"), str):
                    parts.append(block["text"])
            return " ".join(parts).strip()
        return str(content).strip() if content else ""
