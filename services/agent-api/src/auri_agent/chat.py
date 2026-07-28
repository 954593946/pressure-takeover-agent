"""SSE adapter that wraps the LangChain AuriAgent for mobile ChatRepository streaming.

Emits ChatStreamEvent-aligned SSE events: text_delta, tool_call, tool_result,
confirmation_required, done.
"""

from __future__ import annotations

import asyncio
import logging
from typing import AsyncGenerator
from uuid import uuid4

from .runtime import AgentRuntime

logger = logging.getLogger(__name__)


class ChatAgent:
    """Thin SSE wrapper around the runtime's AuriAgent (LangChain)."""

    def __init__(self, runtime: AgentRuntime) -> None:
        self.runtime = runtime

    async def chat_stream(
        self, message: str, session_id: str, input_mode: str = "text",
    ) -> AsyncGenerator[dict[str, object], None]:
        """Run the LangChain agent and yield SSE event dicts."""
        agent = self.runtime.conversation_agent
        event_id = f"evt_chat_{uuid4().hex[:12]}"

        # Get a working copy of the current state
        working_state = await self.runtime.get_state()
        if session_id and working_state.session_id != session_id:
            working_state.session_id = session_id

        # Run the LangChain agent
        try:
            result = await agent.handle(
                text=message,
                state=working_state,
                source="mobile",
                event_id=event_id,
            )
        except Exception as exc:
            logger.warning("Agent handle failed: %s", type(exc).__name__)
            yield {"type": "text_delta", "content": "抱歉，处理你的请求时出了点问题。能再说一次吗？"}
            yield {"type": "done", "sessionId": working_state.session_id, "revision": 0}
            return

        # Commit the state changes back to the runtime
        try:
            async with self.runtime._lock:
                if self.runtime._state.session_id == working_state.session_id or not self.runtime._state.session_id.startswith("demo_"):
                    self.runtime._state = result.state
                    self.runtime.llm_last_mode = result.mode
                    self.runtime._event_ids.add(event_id)
                    self.runtime._touch(f"chat:{event_id}")
                    committed = self.runtime._state.model_copy(deep=True)
            await self.runtime._broadcast(committed)
        except Exception as exc:
            logger.warning("State commit failed: %s", type(exc).__name__)

        # ── Emit SSE events ────────────────────────────────────────────────

        # 1) Text delta — chunk the reply for typing effect
        reply = result.reply or ""
        chunk_size = max(1, min(4, len(reply) // 6)) if reply else 1
        for i in range(0, len(reply), chunk_size):
            yield {"type": "text_delta", "content": reply[i:i + chunk_size]}
            await asyncio.sleep(0)  # yield to event loop

        # 2) Tool calls
        for tool_name in result.called_tools:
            tc_id = f"tc_{tool_name}_{uuid4().hex[:6]}"
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
        if tool_name == "prepare_assistance":
            actions = len(state.actions)
            return f"已准备 {actions} 项方案"
        if tool_name == "confirm_current_actions":
            return "已确认执行"
        return "完成"
