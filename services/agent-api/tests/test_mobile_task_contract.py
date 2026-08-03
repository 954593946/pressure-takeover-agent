from datetime import datetime, timedelta, timezone

import httpx
import pytest

from auri_agent.app import create_app
from auri_agent.config import Settings


TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


@pytest.mark.asyncio
async def test_mobile_text_payload_is_agent_classified_and_retry_is_idempotent() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        session_id = (await client.get("/v1/state")).json()["session_id"]
        event = {
            "schema_version": "0.2.0",
            "event_id": "evt_mobile_quick_task_001",
            "session_id": session_id,
            "type": "task.created",
            "source": "mobile",
            "timestamp": datetime.now(TZ).isoformat(),
            "payload": {
                "text": "今天18:10接孩子，计划时间 2026-08-03T18:10:00+08:00",
            },
        }

        first = await client.post("/v1/event", json=event)
        retry = await client.post("/v1/event", json=event)
        state = (await client.get("/v1/state")).json()

    assert first.status_code == 202
    assert retry.status_code == 202
    assert first.json()["duplicate"] is False
    assert retry.json()["duplicate"] is True
    assert retry.json()["revision"] == first.json()["revision"]
    assert len(state["tasks"]) == 1
    assert "接孩子" in state["tasks"][0]["title"]
    assert state["tasks"][0]["task_type"] == "rigid"
    assert state["tasks"][0]["adjustable"] is False
