"""SSE adapter that wraps the LangChain AuriAgent for mobile ChatRepository streaming.

Emits ChatStreamEvent-aligned SSE events: text_delta, tool_call, tool_result,
confirmation_required, done.
"""

from __future__ import annotations

import asyncio
from typing import AsyncGenerator
from uuid import uuid4

from .agent import AgentRunResult
from .engine import build_execution_receipt, build_preparation_receipt
from .runtime import AgentRuntime

class ChatAgent:
    """Thin SSE wrapper around the runtime's AuriAgent (LangChain)."""

    def __init__(self, runtime: AgentRuntime) -> None:
        self.runtime = runtime

    async def chat_stream(
        self,
        message: str,
        session_id: str | None,
        input_mode: str = "text",
        client_event_id: str | None = None,
    ) -> AsyncGenerator[dict[str, object], None]:
        """Compatibility helper that submits through the public Runtime path."""
        event_id = client_event_id or f"evt_chat_{uuid4().hex[:12]}"
        result, _duplicate = await self.runtime.submit_chat(
            message=message,
            session_id=session_id,
            input_mode=input_mode,
            client_event_id=event_id,
        )
        async for event in self.stream_result(result, event_id):
            yield event

    async def stream_result(
        self,
        result: AgentRunResult,
        client_event_id: str,
    ) -> AsyncGenerator[dict[str, object], None]:
        """Format an already committed Runtime result as the frozen Chat SSE contract."""

        # 1) Text delta — chunk the reply for typing effect
        reply = result.reply or ""
        chunk_size = max(1, min(4, len(reply) // 6)) if reply else 1
        for i in range(0, len(reply), chunk_size):
            yield {"type": "text_delta", "content": reply[i:i + chunk_size]}
            await asyncio.sleep(0)  # yield to event loop

        # 2) Tool calls
        for index, tool_name in enumerate(result.called_tools, start=1):
            tc_id = f"tc_{client_event_id[-12:]}_{index}"
            yield {
                "type": "tool_call",
                "toolCallId": tc_id,
                "function": {"name": tool_name, "arguments": "{}"},
            }
            yield {
                "type": "tool_result",
                "toolCallId": tc_id,
                "success": True,
                "summary": self._tool_summary(tool_name, result.state),
            }

        # 3) Confirmation required
        confirmation = result.state.confirmation
        if confirmation and confirmation.status == "pending":
            yield {
                "type": "confirmation_required",
                "confirmationId": confirmation.confirmation_id,
                "prompt": reply or "方案已准备好，是否确认执行？",
                "actionIds": confirmation.action_ids,
            }

        # 4) Done
        yield {"type": "done", "sessionId": result.state.session_id, "revision": result.state.revision}

    @staticmethod
    def _tool_summary(tool_name: str, state) -> str:
        if tool_name == "create_tasks":
            return f"已创建 {len(state.tasks)} 项任务"
        if tool_name == "get_status":
            return "已读取当前状态"
        if tool_name == "report_meeting_delay":
            return f"延迟已记录，压力等级 {state.risk.pressure_level.value}"
        if tool_name == "reschedule_task":
            return "任务已调整"
        if tool_name == "control_ac":
            vehicle = state.vehicle_state
            return f"空调已{'打开' if vehicle.ac_on else '关闭'}，目标温度 {vehicle.ac_target_temp:g}℃"
        if tool_name == "prepare_assistance":
            return build_preparation_receipt(state)
        if tool_name == "confirm_current_actions":
            return build_execution_receipt(state)
        return "完成"
