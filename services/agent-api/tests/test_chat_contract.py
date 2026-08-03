import asyncio
import json
import os
from datetime import timedelta

import pytest
from httpx import ASGITransport, AsyncClient

from auri_agent.agent import AgentRunResult
from auri_agent.app import create_app
from auri_agent.config import Settings
from auri_agent.models import Event, now
from auri_agent.runtime import AgentRuntime


def chat_body(session_id: str, event_id: str, message: str = "打开空调") -> dict:
    return {
        "message": message,
        "inputMode": "voice",
        "sessionId": session_id,
        "clientEventId": event_id,
    }


def sse_events(text: str) -> list[dict]:
    events = []
    for frame in text.replace("\r\n", "\n").split("\n\n"):
        data = "\n".join(line[5:].lstrip() for line in frame.splitlines() if line.startswith("data:"))
        if data:
            events.append(json.loads(data))
    return events


def environment_auth_headers() -> dict[str, str]:
    token = os.getenv("AGENT_SHARED_TOKEN", "")
    return {"X-Agent-Token": token} if token else {}


async def submit(
    client: AsyncClient,
    event_id: str,
    event_type: str,
    payload: dict,
    *,
    source: str = "demo_console",
) -> dict:
    state = (await client.get("/v1/state")).json()
    response = await client.post(
        "/v1/event",
        json={
            "schema_version": "0.2.0",
            "event_id": event_id,
            "session_id": state["session_id"],
            "type": event_type,
            "source": source,
            "timestamp": now().isoformat(),
            "payload": payload,
        },
    )
    assert response.status_code == 202, response.text
    return response.json()["state"]


@pytest.mark.asyncio
async def test_chat_requires_auth_client_event_id_and_nonempty_message() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token="test-token"))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        session_id = (
            await client.get("/v1/state", headers={"X-Agent-Token": "test-token"})
        ).json()["session_id"]
        body = chat_body(session_id, "evt_chat_auth")

        assert (await client.post("/v1/chat", json=body)).status_code == 401
        missing_id = await client.post(
            "/v1/chat",
            json={"message": "打开空调", "inputMode": "voice", "sessionId": session_id},
            headers={"X-Agent-Token": "test-token"},
        )
        empty = await client.post(
            "/v1/chat",
            json={**body, "message": "   "},
            headers={"X-Agent-Token": "test-token"},
        )

    assert missing_id.status_code == 422
    assert empty.status_code == 400
    assert empty.json()["detail"]["code"] == "EMPTY_MESSAGE"


@pytest.mark.asyncio
async def test_chat_session_mismatch_is_409_and_does_not_commit() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test", headers=environment_auth_headers()
    ) as client:
        before = (await client.get("/v1/state")).json()
        response = await client.post("/v1/chat", json=chat_body("wrong-session", "evt_chat_wrong_session"))
        after = (await client.get("/v1/state")).json()

    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "SESSION_MISMATCH"
    assert after["session_id"] == before["session_id"]
    assert after["revision"] == before["revision"]


@pytest.mark.asyncio
async def test_chat_retry_and_sync_fallback_reuse_one_runtime_commit() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test", headers=environment_auth_headers()
    ) as client:
        initial = (await client.get("/v1/state")).json()
        body = chat_body(initial["session_id"], "evt_chat_idempotent")
        streamed = await client.post("/v1/chat", json=body)
        first_state = (await client.get("/v1/state")).json()
        fallback = await client.post("/v1/chat/sync", json=body)
        final_state = (await client.get("/v1/state")).json()

    events = sse_events(streamed.text)
    assert streamed.status_code == 200
    assert events[-1] == {
        "type": "done",
        "sessionId": initial["session_id"],
        "revision": first_state["revision"],
    }
    assert fallback.status_code == 200
    assert fallback.json()["duplicate"] is True
    assert fallback.json()["revision"] == first_state["revision"]
    assert final_state["revision"] == first_state["revision"]
    assert final_state["action_ledger"].count("event:evt_chat_idempotent") == 1
    assert final_state["vehicle_state"]["ac_on"] is True


@pytest.mark.asyncio
async def test_chat_rejects_same_idempotency_key_with_different_payload() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test", headers=environment_auth_headers()
    ) as client:
        initial = (await client.get("/v1/state")).json()
        body = chat_body(initial["session_id"], "evt_chat_payload_collision", "打开空调")
        first = await client.post("/v1/chat/sync", json=body)
        collision = await client.post("/v1/chat/sync", json={**body, "message": "关闭空调"})
        final_state = (await client.get("/v1/state")).json()

    assert first.status_code == 200
    assert collision.status_code == 409
    assert collision.json()["detail"]["code"] == "IDEMPOTENCY_KEY_REUSED"
    assert final_state["revision"] == first.json()["revision"]
    assert final_state["vehicle_state"]["ac_on"] is True


@pytest.mark.asyncio
async def test_concurrent_console_event_and_chat_preserve_both_revisions() -> None:
    runtime = AgentRuntime(Settings(llm_enabled=False, openai_api_key=""))
    initial = await runtime.get_state()
    await runtime.submit_event(
        Event(
            event_id="evt_seed_task",
            session_id=initial.session_id,
            type="task.created",
            source="mobile",
            timestamp=now(),
            payload={"text": "今天18:10接孩子"},
        )
    )
    original = runtime.conversation_agent
    entered = asyncio.Event()
    release = asyncio.Event()

    class PausingAgent:
        calls = 0

        async def handle(self, *args, **kwargs) -> AgentRunResult:
            self.calls += 1
            if self.calls == 1:
                entered.set()
                await release.wait()
            return await original.handle(*args, **kwargs)

        async def compose_confirmation_reply(self, *args, **kwargs) -> str:
            return await original.compose_confirmation_reply(*args, **kwargs)

    runtime.conversation_agent = PausingAgent()
    chat_task = asyncio.create_task(
        runtime.submit_chat(
            message="打开空调",
            session_id=initial.session_id,
            input_mode="voice",
            client_event_id="evt_chat_concurrent",
        )
    )
    await asyncio.wait_for(entered.wait(), timeout=1)
    await runtime.submit_event(
        Event(
            event_id="evt_concurrent_meeting",
            session_id=initial.session_id,
            type="meeting.overrun",
            source="demo_console",
            timestamp=now(),
            payload={"delay_minutes": 20},
        )
    )
    release.set()
    result, duplicate = await asyncio.wait_for(chat_task, timeout=3)
    state = await runtime.get_state()

    assert duplicate is False
    assert runtime.conversation_agent.calls == 2
    assert state.revision == 3
    assert state.vehicle_state.ac_on is True
    assert "event:evt_concurrent_meeting" in state.action_ledger
    assert "event:evt_chat_concurrent" in state.action_ledger
    assert result.state.revision == state.revision


@pytest.mark.asyncio
async def test_agent_failure_returns_503_without_done_or_state_commit() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))

    class FailingAgent:
        async def handle(self, *_args, **_kwargs):
            raise RuntimeError("provider unavailable")

    app.state.runtime.conversation_agent = FailingAgent()
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test", headers=environment_auth_headers()
    ) as client:
        initial = (await client.get("/v1/state")).json()
        response = await client.post(
            "/v1/chat",
            json=chat_body(initial["session_id"], "evt_chat_provider_failure"),
        )
        after = (await client.get("/v1/state")).json()

    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "AGENT_EXECUTION_FAILED"
    assert "done" not in response.text
    assert after["revision"] == initial["revision"]


@pytest.mark.asyncio
async def test_chat_confirm_preserves_session_owner_expiry_and_duplicate_errors(monkeypatch) -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test", headers=environment_auth_headers()
    ) as client:
        await submit(client, "evt_confirm_task", "task.created", {"text": "今天18:10接孩子，之后去超市"}, source="mobile")
        await submit(client, "evt_confirm_vehicle", "scene.vehicle_entered", {})
        prepared = await submit(
            client,
            "evt_confirm_help",
            "user.utterance",
            {"text": "我还来得及吗？帮我处理", "input_mode": "voice"},
            source="mobile",
        )
        confirmation = prepared["confirmation"]
        assert confirmation["owner_surface"] == "vehicle_hmi"
        wrong_session = await client.post(
            "/v1/chat/confirm",
            json={"sessionId": "wrong", "confirmationId": confirmation["confirmation_id"], "decision": "accept"},
        )
        wrong_owner = await client.post(
            "/v1/chat/confirm",
            json={
                "sessionId": prepared["session_id"],
                "confirmationId": confirmation["confirmation_id"],
                "decision": "accept",
            },
        )

        assert wrong_session.status_code == 409
        assert wrong_session.json()["detail"]["code"] == "SESSION_MISMATCH"
        assert wrong_owner.status_code == 409
        assert wrong_owner.json()["detail"]["code"] == "WRONG_SURFACE"

        # Reset and prepare a mobile-owned confirmation for expiry and duplicate checks.
        reset = await client.post("/v1/session/reset", json={"scenario_id": "chat-confirm"})
        session_id = reset.json()["session_id"]
        await submit(client, "evt_mobile_task", "task.created", {"text": "之后去超市"}, source="mobile")
        mobile_prepared = await submit(
            client,
            "evt_mobile_help",
            "user.utterance",
            {"text": "帮我处理这些事情，先准备方案给我确认", "input_mode": "voice"},
            source="mobile",
        )
        mobile_confirmation = mobile_prepared["confirmation"]
        assert mobile_confirmation["owner_surface"] == "mobile"

        import auri_agent.engine as engine

        real_now = engine.now
        revision_before_expiry = mobile_prepared["revision"]
        monkeypatch.setattr(engine, "now", lambda: real_now() + timedelta(hours=1))
        expired = await client.post(
            "/v1/chat/confirm",
            json={
                "sessionId": session_id,
                "confirmationId": mobile_confirmation["confirmation_id"],
                "decision": "accept",
            },
        )
        assert expired.status_code == 409
        assert expired.json()["detail"]["code"] == "EXPIRED"
        expired_state = (await client.get("/v1/state")).json()
        assert expired_state["confirmation"]["status"] == "expired"
        assert expired_state["revision"] == revision_before_expiry + 1
        assert expired_state["action_ledger"].count(
            f"confirm_expired:{mobile_confirmation['confirmation_id']}"
        ) == 1

        monkeypatch.setattr(engine, "now", real_now)
        await client.post("/v1/session/reset", json={"scenario_id": "chat-confirm-duplicate"})
        await submit(client, "evt_duplicate_task", "task.created", {"text": "之后去超市"}, source="mobile")
        duplicate_prepared = await submit(
            client,
            "evt_duplicate_help",
            "user.utterance",
            {"text": "帮我处理这些事情，先准备方案给我确认", "input_mode": "voice"},
            source="mobile",
        )
        confirm_body = {
            "sessionId": duplicate_prepared["session_id"],
            "confirmationId": duplicate_prepared["confirmation"]["confirmation_id"],
            "decision": "accepted",
        }
        responses = [await client.post("/v1/chat/confirm", json=confirm_body) for _ in range(10)]
        final_state = (await client.get("/v1/state")).json()

    assert all(response.status_code == 200 for response in responses)
    assert responses[0].json()["duplicate"] is False
    assert all(response.json()["duplicate"] is True for response in responses[1:])
    assert len({response.json()["revision"] for response in responses}) == 1
    assert final_state["confirmation"]["status"] == "accepted"
    assert final_state["action_ledger"].count(
        f"confirm:{confirm_body['confirmationId']}:button"
    ) == 1
