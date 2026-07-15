import asyncio
from datetime import datetime, timedelta, timezone
from pathlib import Path

import json
import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

from auri_agent.app import create_app
from auri_agent.config import Settings
from auri_agent.models import ConfirmationRequest, Event


TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")
REPO_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def client() -> TestClient:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    return TestClient(app)


def event(client: TestClient, event_id: str, event_type: str, payload: dict, source: str = "demo_console") -> dict:
    session_id = client.get("/v1/state").json()["session_id"]
    return {
        "schema_version": "0.2.0",
        "event_id": event_id,
        "session_id": session_id,
        "type": event_type,
        "source": source,
        "timestamp": datetime.now(TZ).isoformat(),
        "payload": payload,
    }


def prepare_confirmation(client: TestClient) -> dict:
    client.post("/v1/event", json=event(client, "evt_task", "task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile"))
    client.post("/v1/event", json=event(client, "evt_meeting", "meeting.overrun", {"delay_minutes": 20}))
    client.post("/v1/event", json=event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    client.post(
        "/v1/event",
        json=event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    response = client.post(
        "/v1/event",
        json=event(client, "evt_help", "user.utterance", {"text": "我还来得及吗？帮我处理"}, "vehicle_hmi"),
    )
    assert response.status_code == 202
    return response.json()["state"]


def test_health_never_exposes_key(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "api_key" not in response.text.lower()


def test_shared_backend_requires_team_token() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token="team-test-token"))
    secured_client = TestClient(app)
    health = secured_client.get("/health")
    assert health.status_code == 200
    assert health.json()["shared_access_enabled"] is True
    assert "team-test-token" not in health.text

    missing = secured_client.get("/v1/state")
    wrong = secured_client.get("/v1/state", headers={"X-Agent-Token": "wrong"})
    allowed = secured_client.get("/v1/state", headers={"X-Agent-Token": "team-test-token"})
    assert missing.status_code == 401
    assert wrong.status_code == 401
    assert allowed.status_code == 200


def test_happy_path_and_duplicate_confirmation(client: TestClient) -> None:
    state = prepare_confirmation(client)
    assert state["stage"] == "waiting_confirmation"
    assert state["primary_surface"] == "vehicle_hmi"
    assert state["risk"]["pressure_level"] == "L2"
    assert state["service_orders"][0]["total"] == 186
    confirmation_id = state["confirmation"]["confirmation_id"]

    body = {"confirmation_id": confirmation_id, "decision": "accept", "confirmed_by": "vehicle_hmi", "input_mode": "button"}
    first = client.post("/v1/confirm", json=body)
    second = client.post("/v1/confirm", json={**body, "input_mode": "voice"})
    assert first.status_code == 200
    assert second.status_code == 200
    first_state = first.json()
    second_state = second.json()
    assert first_state["stage"] == "action_completed"
    assert first_state["service_orders"][0]["order_id"] == second_state["service_orders"][0]["order_id"]
    assert first_state["revision"] == second_state["revision"]


def test_confirmation_rejects_non_owner_surface(client: TestClient) -> None:
    state = prepare_confirmation(client)
    response = client.post(
        "/v1/confirm",
        json={
            "confirmation_id": state["confirmation"]["confirmation_id"],
            "decision": "accept",
            "confirmed_by": "mobile",
            "input_mode": "button",
        },
    )
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "WRONG_SURFACE"


def test_duplicate_event_is_idempotent(client: TestClient) -> None:
    payload = event(client, "evt_same", "meeting.overrun", {"delay_minutes": 20})
    first = client.post("/v1/event", json=payload).json()
    second = client.post("/v1/event", json=payload).json()
    assert first["duplicate"] is False
    assert second["duplicate"] is True
    assert first["revision"] == second["revision"]


def test_l3_requires_two_auxiliary_signal_classes(client: TestClient) -> None:
    client.post("/v1/event", json=event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    client.post(
        "/v1/event",
        json=event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    one = client.post(
        "/v1/event",
        json=event(client, "evt_hr", "wearable.signal", {"heart_rate": 120, "confidence": 0.9}, "wearable"),
    ).json()["state"]
    assert one["risk"]["pressure_level"] == "L2"
    two = client.post(
        "/v1/event",
        json=event(client, "evt_brake", "driving.signal", {"harsh_brake": True}),
    ).json()["state"]
    assert two["risk"]["pressure_level"] == "L3"
    assert two["stage"] == "takeover_L3"


def test_over_budget_order_is_not_confirmable(client: TestClient) -> None:
    client.post("/v1/event", json=event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    client.post("/v1/event", json=event(client, "evt_mock", "service.mock.config", {"mode": "over_budget"}))
    client.post(
        "/v1/event",
        json=event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    state = client.post(
        "/v1/event",
        json=event(client, "evt_help", "user.utterance", {"text": "帮我处理"}, "vehicle_hmi"),
    ).json()["state"]
    order_action = next(action for action in state["actions"] if action["type"] == "service_order")
    assert order_action["status"] == "blocked"
    assert order_action["action_id"] not in state["confirmation"]["action_ids"]
    assert state["service_orders"][0]["error_code"] == "OVER_BUDGET"


@pytest.mark.asyncio
async def test_concurrent_confirmation_executes_order_once() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key=""))
    runtime = app.state.runtime
    session_id = (await runtime.get_state()).session_id

    async def submit(event_id: str, event_type: str, payload: dict, source: str = "demo_console"):
        await runtime.submit_event(
            Event(
                event_id=event_id,
                session_id=session_id,
                type=event_type,
                source=source,
                timestamp=datetime.now(TZ),
                payload=payload,
            )
        )

    await submit("evt_vehicle", "scene.vehicle_entered", {})
    await submit("evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18})
    await submit("evt_help", "user.utterance", {"text": "帮我处理"}, "vehicle_hmi")
    state = await runtime.get_state()
    request = ConfirmationRequest(
        confirmation_id=state.confirmation.confirmation_id,
        decision="accept",
        confirmed_by="vehicle_hmi",
        input_mode="button",
    )
    first, second = await asyncio.gather(runtime.confirm(request), runtime.confirm(request))
    first_order = first[0].service_orders[0].order_id
    second_order = second[0].service_orders[0].order_id
    assert first_order == second_order
    assert first[0].revision == second[0].revision


def test_contract_examples_validate() -> None:
    world_schema = json.loads((REPO_ROOT / "contracts" / "world-state.schema.json").read_text(encoding="utf-8"))
    event_schema = json.loads((REPO_ROOT / "contracts" / "event.schema.json").read_text(encoding="utf-8"))
    world_example = json.loads((REPO_ROOT / "contracts" / "examples" / "world-state.json").read_text(encoding="utf-8"))
    event_example = json.loads((REPO_ROOT / "contracts" / "examples" / "confirmation-event.json").read_text(encoding="utf-8"))
    Draft202012Validator(world_schema, format_checker=Draft202012Validator.FORMAT_CHECKER).validate(world_example)
    Draft202012Validator(event_schema, format_checker=Draft202012Validator.FORMAT_CHECKER).validate(event_example)
