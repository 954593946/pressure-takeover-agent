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
                message_count = sum(action.type == "message" for action in completed)
                order_done = any(action.type == "service_order" for action in completed)
                parts = []
                if message_count:
                    parts.append(f"{message_count}条消息已在 Demo 中模拟发送")
                if order_done:
                    parts.append("采购订单已在 Demo 中模拟提交")
                return "都处理好了：" + "，".join(parts) + "。你先安心处理眼前的事。"
            return "当前没有可执行的待确认方案，我没有进行任何操作。"
        if "prepare_assistance" in called_tools:
            actions = [action for action in state.actions if action.status == "awaiting_confirmation"]
            if not actions:
                return "我看过当前任务了，还没有找到可以安全代办的事项。你可以再告诉我希望处理哪一件。"
            message_count = sum(action.type == "message" for action in actions)
            has_order = any(action.type == "service_order" for action in actions)
            parts = []
            if message_count:
                parts.append(f"{message_count}条模拟消息")
            if has_order:
                parts.append("采购订单预览")
            return "别着急，我已经为你准备好" + "和".join(parts) + "，确认后才会执行。你先专心处理眼前的事。"
        if "create_tasks" in called_tools:
            titles = [task.title for task in state.tasks[-3:]]
            return "记好了：" + "、".join(titles) + "。我会按时间和任务刚性帮你持续留意。"
        if "reschedule_task" in called_tools:
            changed = [task for task in state.tasks if task.status == "rescheduled"]
            if changed:
                return f"已把“{changed[-1].title}”调整到新的时间，我会按更新后的安排继续跟进。"
        if "report_meeting_delay" in called_tools:
            return f"会议延迟已经记下，当前压力等级为{state.risk.pressure_level.value}。我会继续结合任务和路况判断是否需要介入。"
        if "get_status" in called_tools:
            pending = len([task for task in state.tasks if task.status == "pending"])
            confirmation = "，有一项方案等待确认" if state.confirmation and state.confirmation.status == "pending" else ""
            return f"目前有{pending}项待办，压力等级为{state.risk.pressure_level.value}{confirmation}。"
        return "我在。你可以直接告诉我要新增什么任务、调整哪项安排，或者让我查看当前进展。"

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
