from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Annotated, Literal

from langchain.tools import ToolRuntime, tool
from pydantic import BaseModel, Field

from .engine import ActionPlanner, DomainError, RiskEngine, consume_confirmation
from .models import ConfirmationRequest, Scene, Stage, Surface, Task, WearableState, WorldState
from .prompts import compact_state


class TaskDraft(BaseModel):
    title: str = Field(min_length=1, max_length=80)
    scheduled_at: datetime | None = None
    location: str | None = Field(default=None, max_length=120)
    task_type: Literal["rigid", "flexible"]
    priority: Literal["low", "medium", "high"] = "medium"
    adjustable: bool = True
    waiting_party: list[str] = Field(default_factory=list, max_length=10)
    capability_tags: list[str] = Field(default_factory=list, max_length=10)


@dataclass
class AgentToolContext:
    toolbox: "AgentToolbox"


class AgentToolbox:
    """Mutates only an isolated WorldState copy for one agent run."""

    def __init__(self, state: WorldState, *, event_id: str, source: str, original_text: str):
        self.state = state
        self.event_id = event_id
        self.source = source
        self.original_text = original_text.strip()
        self.called_tools: list[str] = []

    def _record(self, name: str) -> None:
        self.called_tools.append(name)
        self.state.action_ledger.append(f"agent_tool:{name}:{self.event_id}")

    def create_tasks(self, drafts: list[TaskDraft], *, replace_existing: bool) -> dict[str, object]:
        if not drafts:
            return {"ok": False, "error": "至少需要一项任务"}
        new_tasks: list[Task] = []
        id_prefix = self.event_id.removeprefix("evt_")[-8:] or "agent"
        for index, draft in enumerate(drafts):
            title = draft.title.strip()
            tags = list(dict.fromkeys(draft.capability_tags))
            task_type = draft.task_type
            priority = draft.priority
            adjustable = draft.adjustable
            if any(word in title for word in ("超市", "采购", "买菜")):
                task_type = "flexible"
                adjustable = True
                if "grocery_delivery" not in tags:
                    tags.append("grocery_delivery")
            if "孩子" in title:
                task_type = "rigid"
                priority = "high"
                adjustable = False
            new_tasks.append(
                Task(
                    task_id=f"task_{id_prefix}_{index + 1}",
                    title=title,
                    scheduled_at=draft.scheduled_at,
                    location=draft.location,
                    task_type=task_type,
                    priority=priority,
                    adjustable=adjustable,
                    waiting_party=list(dict.fromkeys(draft.waiting_party)),
                    capability_tags=tags,
                )
            )

        existing = [] if replace_existing else list(self.state.tasks)
        seen = {(task.title, task.scheduled_at) for task in existing}
        created = [task for task in new_tasks if (task.title, task.scheduled_at) not in seen]
        self.state.tasks = existing + created
        if self.state.scene == Scene.OFF_VEHICLE:
            self.state.stage = Stage.OFF_VEHICLE_IDLE
            self.state.primary_surface = Surface.MOBILE
        self._record("create_tasks")
        return {
            "ok": True,
            "created_count": len(created),
            "tasks": [
                {
                    "task_id": task.task_id,
                    "title": task.title,
                    "scheduled_at": task.scheduled_at.isoformat() if task.scheduled_at else None,
                    "task_type": task.task_type,
                    "priority": task.priority,
                }
                for task in created
            ],
        }

    def get_status(self) -> dict[str, object]:
        self._record("get_status")
        return {"ok": True, "state": compact_state(self.state)}

    def report_meeting_delay(self, delay_minutes: int) -> dict[str, object]:
        delay_minutes = max(0, min(int(delay_minutes), 240))
        reasons = [reason for reason in self.state.risk.reason_codes if reason != "MEETING_OVERRUN"]
        self.state.risk.reason_codes = [*reasons, "MEETING_OVERRUN"]
        RiskEngine.recompute(self.state)
        if self.state.scene == Scene.OFF_VEHICLE:
            self.state.stage = Stage.PRE_DEPARTURE_WARNING
            self.state.wearable = WearableState(
                connected=self.state.wearable.connected,
                mode="warning",
                text="出发时间临近",
                color="yellow",
                haptic="double_short",
            )
        self._record("report_meeting_delay")
        return {
            "ok": True,
            "delay_minutes": delay_minutes,
            "pressure_level": self.state.risk.pressure_level,
            "stage": self.state.stage,
        }

    def reschedule_task(self, task_reference: str, scheduled_at: datetime) -> dict[str, object]:
        reference = task_reference.strip().lower()
        task = next(
            (
                item
                for item in self.state.tasks
                if item.task_id.lower() == reference or reference in item.title.lower()
            ),
            None,
        )
        if task is None:
            return {"ok": False, "error": "未找到对应任务"}
        if task.task_type != "flexible" or not task.adjustable:
            return {"ok": False, "error": "该任务是刚性任务，不能由 Agent 自动改期"}
        task.scheduled_at = scheduled_at
        task.status = "rescheduled"
        self._record("reschedule_task")
        return {
            "ok": True,
            "task_id": task.task_id,
            "title": task.title,
            "scheduled_at": scheduled_at.isoformat(),
            "status": task.status,
        }

    def prepare_assistance(self, *, include_messages: bool, include_grocery: bool) -> dict[str, object]:
        if self.state.confirmation is not None and self.state.confirmation.status == "pending":
            self._record("prepare_assistance")
            return {
                "ok": True,
                "reused_pending_plan": True,
                "prepared_actions": [
                    {
                        "action_id": action.action_id,
                        "type": action.type,
                        "target": action.target,
                        "status": action.status,
                        "summary": action.summary,
                    }
                    for action in self.state.actions
                    if action.action_id in self.state.confirmation.action_ids
                ],
                "confirmation_id": self.state.confirmation.confirmation_id,
                "requires_confirmation": True,
            }
        self.state.stage = Stage.PLANNING
        RiskEngine.recompute(self.state, assistance_requested=True)
        actions = ActionPlanner.prepare(
            self.state,
            include_messages=include_messages,
            include_grocery=include_grocery,
        )
        self._record("prepare_assistance")
        return {
            "ok": True,
            "prepared_actions": [
                {
                    "action_id": action.action_id,
                    "type": action.type,
                    "target": action.target,
                    "status": action.status,
                    "summary": action.summary,
                }
                for action in actions
            ],
            "confirmation_id": self.state.confirmation.confirmation_id if self.state.confirmation else None,
            "requires_confirmation": self.state.confirmation is not None,
        }

    def confirm_current_actions(self, decision: Literal["accept", "reject"]) -> dict[str, object]:
        if not self._decision_is_explicit(decision):
            return {
                "ok": False,
                "error": "用户没有给出足够明确的确认或拒绝表达，未执行任何动作",
            }
        if self.source not in {"mobile", "vehicle_hmi"}:
            return {"ok": False, "error": "当前输入来源没有确认权限"}
        confirmation = self.state.confirmation
        if confirmation is None:
            return {"ok": False, "error": "当前没有待确认方案"}
        request = ConfirmationRequest(
            confirmation_id=confirmation.confirmation_id,
            decision=decision,
            confirmed_by=self.source,
            input_mode="voice",
        )
        try:
            consume_confirmation(self.state, request)
        except DomainError as exc:
            return {"ok": False, "error_code": exc.code, "error": str(exc)}
        self._record("confirm_current_actions")
        return {
            "ok": True,
            "decision": decision,
            "confirmation_id": confirmation.confirmation_id,
            "confirmation_status": confirmation.status,
            "completed_actions": [
                action.action_id for action in self.state.actions if action.status == "completed"
            ],
        }

    def _decision_is_explicit(self, decision: Literal["accept", "reject"]) -> bool:
        text = self.original_text.replace(" ", "")
        reject_markers = ("拒绝", "取消", "不要", "别执行", "算了")
        accept_markers = ("确认", "同意", "执行吧", "开始执行", "按这个办", "就这样处理")
        if decision == "reject":
            return any(marker in text for marker in reject_markers)
        return any(marker in text for marker in accept_markers) and not any(
            marker in text for marker in reject_markers
        )


@tool
def create_tasks(
    tasks: Annotated[list[TaskDraft], "从用户原话提取的一项或多项任务，不得补充用户没有提到的任务"],
    runtime: ToolRuntime[AgentToolContext],
    replace_existing: Annotated[bool, "仅当用户明确要求替换全部任务时为 true，否则为 false"] = False,
) -> dict[str, object]:
    """创建结构化任务；适用于新增、记录、安排或提醒事项。"""
    return runtime.context.toolbox.create_tasks(tasks, replace_existing=replace_existing)


@tool
def get_status(runtime: ToolRuntime[AgentToolContext]) -> dict[str, object]:
    """读取当前任务、风险、场景、动作和待确认方案；不会修改业务状态。"""
    return runtime.context.toolbox.get_status()


@tool
def report_meeting_delay(
    delay_minutes: Annotated[int, "用户明确报告的会议延迟分钟数，范围 0 到 240"],
    runtime: ToolRuntime[AgentToolContext],
) -> dict[str, object]:
    """记录用户明确报告的会议延迟，并触发确定性的出发前风险重算。"""
    return runtime.context.toolbox.report_meeting_delay(delay_minutes)


@tool
def reschedule_task(
    task_reference: Annotated[str, "任务 ID 或足以唯一识别任务的标题"],
    scheduled_at: Annotated[datetime, "新的 ISO 8601 时间，必须包含明确日期和时区"],
    runtime: ToolRuntime[AgentToolContext],
) -> dict[str, object]:
    """调整已有弹性任务时间；刚性任务会被确定性规则拒绝。"""
    return runtime.context.toolbox.reschedule_task(task_reference, scheduled_at)


@tool
def prepare_assistance(
    runtime: ToolRuntime[AgentToolContext],
    include_messages: Annotated[bool, "是否为已有刚性任务的等待方准备模拟消息"] = True,
    include_grocery: Annotated[bool, "是否为已有采购任务准备模拟订单预览"] = True,
) -> dict[str, object]:
    """根据已有任务准备协助方案；只生成待确认动作，不会直接发消息或下单。"""
    return runtime.context.toolbox.prepare_assistance(
        include_messages=include_messages,
        include_grocery=include_grocery,
    )


@tool
def confirm_current_actions(
    decision: Annotated[Literal["accept", "reject"], "接受或拒绝当前待确认方案"],
    runtime: ToolRuntime[AgentToolContext],
) -> dict[str, object]:
    """处理当前方案；只有用户原话明确确认/拒绝且输入端拥有确认权时才会执行。"""
    return runtime.context.toolbox.confirm_current_actions(decision)


AURI_TOOLS = [
    create_tasks,
    get_status,
    report_meeting_delay,
    reschedule_task,
    prepare_assistance,
    confirm_current_actions,
]
