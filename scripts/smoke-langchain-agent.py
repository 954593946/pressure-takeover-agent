"""Run a safe in-process smoke test against the configured LangChain model."""

from __future__ import annotations

import asyncio
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "services" / "agent-api" / "src"))

from auri_agent.config import Settings  # noqa: E402
from auri_agent.models import Event, now  # noqa: E402
from auri_agent.runtime import AgentRuntime  # noqa: E402


async def send(runtime: AgentRuntime, session_id: str, event_id: str, text: str) -> None:
    result = await runtime.submit_event(
        Event(
            event_id=event_id,
            session_id=session_id,
            type="user.utterance",
            source="mobile",
            timestamp=now(),
            payload={"text": text},
        )
    )
    output = result.state.output.conclusion if result.state.output else ""
    print(f"{event_id}: mode={runtime.llm_last_mode}; tools={runtime.conversation_agent.last_tools}")
    print(f"reply: {output}")


async def main() -> int:
    settings = Settings()
    if not settings.llm_configured:
        print("SKIP: OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL 未完整配置。")
        return 2

    runtime = AgentRuntime(settings)
    state = await runtime.get_state()
    session_id = state.session_id
    print(f"provider configured: model={settings.openai_model}; session={session_id}")

    await send(
        runtime,
        session_id,
        "evt_smoke_tasks",
        "请创建两个任务：今天18:10去阳光小学接孩子，老师和家人在等，不能改期；之后去超市采购，可以调整。",
    )
    await send(runtime, session_id, "evt_smoke_delay", "会议还要延迟20分钟，帮我记一下。")
    await send(runtime, session_id, "evt_smoke_help", "帮我处理这些事情，先准备方案给我确认。")
    await send(runtime, session_id, "evt_smoke_confirm", "我明确确认执行这个方案。")

    final = await runtime.get_state()
    expected_tools = {
        "create_tasks",
        "report_meeting_delay",
        "prepare_assistance",
        "confirm_current_actions",
    }
    ledger_tools = {
        entry.split(":")[1]
        for entry in final.action_ledger
        if entry.startswith("agent_tool:")
    }
    checks = {
        "last_request_used_model": runtime.llm_last_mode.startswith("langchain_agent"),
        "expected_tools_called": expected_tools.issubset(ledger_tools),
        "two_tasks_created": len(final.tasks) == 2,
        "confirmation_accepted": final.confirmation is not None and final.confirmation.status == "accepted",
        "actions_completed": bool(final.actions) and all(
            action.status in {"completed", "blocked"} for action in final.actions
        ),
    }
    print(f"checks: {checks}")
    if not all(checks.values()):
        return 1
    print("PASS: LangChain 已完成自然语言理解、工具调用、状态更新和确认执行。")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
