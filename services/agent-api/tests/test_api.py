import asyncio
from datetime import datetime, timedelta, timezone
from pathlib import Path

import json
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from jsonschema import Draft202012Validator

from auri_agent.app import create_app, world_state_event_stream
from auri_agent.config import Settings
from auri_agent.llm import ExtractedTask, TaskExtraction, TaskParser
from auri_agent.models import ConfirmationRequest, Event, GeoPoint, initial_state, now
from auri_agent.observability import classify_provider_error
from auri_agent.prompts import TASK_RIGIDITY_POLICY, build_agent_prompt
from auri_agent.runtime import AgentRuntime


TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")
REPO_ROOT = Path(__file__).resolve().parents[3]


@pytest_asyncio.fixture
async def client():
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as test_client:
        yield test_client


async def event(client: AsyncClient, event_id: str, event_type: str, payload: dict, source: str = "demo_console") -> dict:
    session_id = (await client.get("/v1/state")).json()["session_id"]
    return {
        "schema_version": "0.2.0",
        "event_id": event_id,
        "session_id": session_id,
        "type": event_type,
        "source": source,
        "timestamp": datetime.now(TZ).isoformat(),
        "payload": payload,
    }


async def prepare_confirmation(client: AsyncClient) -> dict:
    await client.post(
        "/v1/event",
        json=await event(client, "evt_task", "task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile"),
    )
    await client.post("/v1/event", json=await event(client, "evt_meeting", "meeting.overrun", {"delay_minutes": 20}))
    await client.post("/v1/event", json=await event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    await client.post(
        "/v1/event",
        json=await event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    response = await client.post(
        "/v1/event",
        json=await event(
            client,
            "evt_help",
            "user.utterance",
            {"text": "我还来得及吗？帮我处理", "input_mode": "voice"},
            "mobile",
        ),
    )
    assert response.status_code == 202
    state = response.json()["state"]
    assert state["last_utterance"]["text"] == "我还来得及吗？帮我处理"
    assert state["last_utterance"]["source"] == "mobile"
    assert state["last_utterance"]["input_mode"] == "voice"
    return state


@pytest.mark.asyncio
async def test_health_never_exposes_key(client: AsyncClient) -> None:
    response = await client.get("/health")
    body = response.json()
    assert body["service_name"] == "auri-agent-api"
    assert body["build_sha"]
    assert body["started_at"].endswith("+00:00")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["llm_framework"] == "langchain"
    assert response.json()["agent_tools_enabled"] is False
    assert response.json()["agent_last_tools"] == []
    assert response.json()["llm_last_success_at"] is None
    assert response.json()["llm_last_fallback_reason"] == "not_configured"
    assert response.json()["llm_last_error_code"] == "LLM_NOT_CONFIGURED"
    assert "api_key" not in response.text.lower()


def test_provider_error_classification_is_stable_and_non_sensitive() -> None:
    class Response:
        def __init__(self, status_code: int):
            self.status_code = status_code

    class ProviderError(RuntimeError):
        def __init__(self, status_code: int, secret_message: str):
            super().__init__(secret_message)
            self.response = Response(status_code)

    assert classify_provider_error(TimeoutError("secret timeout payload")) == (
        "UPSTREAM_TIMEOUT",
        "timeout",
    )
    assert classify_provider_error(ProviderError(401, "private credential")) == (
        "UPSTREAM_AUTH",
        "http_401",
    )
    assert classify_provider_error(ProviderError(429, "private quota")) == (
        "UPSTREAM_RATE_LIMIT",
        "http_429",
    )
    assert classify_provider_error(ProviderError(503, "private upstream body")) == (
        "UPSTREAM_5XX",
        "http_5xx",
    )


def test_task_rigidity_policy_is_shared_by_both_real_agent_paths() -> None:
    parser = TaskParser(Settings(llm_enabled=False, openai_api_key=""))
    conversation_prompt = build_agent_prompt(initial_state("test_prompt"))

    assert TASK_RIGIDITY_POLICY in parser._system_prompt()
    assert TASK_RIGIDITY_POLICY in conversation_prompt
    assert "仅仅出现“9 点”“今晚”“周六”等明确时间，不能作为刚性依据" in TASK_RIGIDITY_POLICY
    assert "今晚 9 点去打游戏”是 `flexible`" in TASK_RIGIDITY_POLICY
    assert "缺席会导致全队弃权”是 `rigid`" in TASK_RIGIDITY_POLICY


@pytest.mark.asyncio
async def test_mobile_voice_transcript_is_written_to_world_state() -> None:
    runtime = AgentRuntime(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    state = await runtime.get_state()
    accepted = await asyncio.wait_for(
        runtime.submit_event(
            Event(
                event_id="evt_mobile_voice",
                session_id=state.session_id,
                type="user.utterance",
                source="mobile",
                timestamp=now(),
                payload={"text": "我还来得及吗？帮我处理", "input_mode": "voice"},
            )
        ),
        timeout=5,
    )

    assert accepted.state.last_utterance is not None
    assert accepted.state.last_utterance.text == "我还来得及吗？帮我处理"
    assert accepted.state.last_utterance.source == "mobile"
    assert accepted.state.last_utterance.input_mode == "voice"


def test_geo_point_rejects_invalid_coordinate_ranges() -> None:
    with pytest.raises(ValueError):
        GeoPoint(name="invalid longitude", longitude=181, latitude=31)
    with pytest.raises(ValueError):
        GeoPoint(name="invalid latitude", longitude=120, latitude=-91)


@pytest.mark.asyncio
async def test_agent_publishes_demo_navigation_contract_for_known_location(client: AsyncClient) -> None:
    created = await client.post(
        "/v1/event",
        json=await event(client, "evt_route_task", "task.created", {"text": "今天18:10接孩子"}, "mobile"),
    )
    state = created.json()["state"]

    assert state["navigation"]["route_id"] == "route_demo_task_pickup_child"
    assert state["navigation"]["task_id"] == "task_pickup_child"
    assert state["navigation"]["origin"]["address"] == "江苏省苏州工业园区星龙街455号"
    assert state["navigation"]["origin"]["longitude"] == pytest.approx(120.791879)
    assert state["navigation"]["destination"]["name"] == "阳光小学"
    assert state["navigation"]["destination"]["latitude"] == pytest.approx(31.3048)
    assert state["navigation"]["source"] == "demo_fixture"
    assert state["navigation"]["is_simulated"] is True
    assert state["navigation"]["progress"] == pytest.approx(0.03)
    assert state["navigation"]["updated_at"] == state["updated_at"]

    entered = await client.post(
        "/v1/event",
        json=await event(client, "evt_route_vehicle", "scene.vehicle_entered", {}),
    )
    driving_state = entered.json()["state"]
    assert driving_state["navigation"]["route_id"] == state["navigation"]["route_id"]
    assert driving_state["navigation"]["progress"] == pytest.approx(0.32)
    assert driving_state["navigation"]["updated_at"] == driving_state["updated_at"]


@pytest.mark.asyncio
async def test_agent_does_not_invent_coordinates_for_unknown_location(client: AsyncClient) -> None:
    response = await client.post(
        "/v1/event",
        json=await event(
            client,
            "evt_unknown_route",
            "task.created",
            {
                "tasks": [
                    {
                        "task_id": "task_private_location",
                        "title": "拜访客户",
                        "scheduled_at": None,
                        "location": "客户临时地址",
                        "task_type": "rigid",
                        "priority": "high",
                        "adjustable": False,
                        "status": "pending",
                        "waiting_party": ["客户"],
                        "capability_tags": [],
                    }
                ]
            },
            "mobile",
        ),
    )
    assert response.status_code == 202
    assert response.json()["state"]["navigation"] is None


@pytest.mark.asyncio
async def test_fallback_never_invents_child_for_unrelated_pickup() -> None:
    parser = TaskParser(Settings(llm_enabled=False, openai_api_key=""))
    tasks = await parser.parse("今晚二十点去机场接从北京回来的同事")

    assert parser.last_mode == "fallback"
    assert len(tasks) == 1
    assert tasks[0].title == "今晚二十点去机场接从北京回来的同事"
    assert all("孩子" not in task.title for task in tasks)


@pytest.mark.asyncio
async def test_langchain_agent_output_is_normalised_to_public_task_contract() -> None:
    class FakeAgent:
        async def ainvoke(self, _input: dict) -> dict:
            return {
                "structured_response": TaskExtraction(
                    tasks=[
                        ExtractedTask(
                            title="去机场接同事",
                            scheduled_at="2026-07-21T20:00:00+08:00",
                            location="机场",
                            task_type="rigid",
                            priority="high",
                            adjustable=False,
                            waiting_party=["同事"],
                        )
                    ]
                )
            }

    parser = TaskParser(Settings(llm_enabled=False, openai_api_key=""))
    parser.agent = FakeAgent()
    tasks = await parser.parse("今晚二十点去机场接从北京回来的同事")

    assert parser.last_mode == "langchain_agent"
    assert parser.last_success_at is not None
    assert parser.last_fallback_reason is None
    assert parser.last_error_code is None
    assert [task.title for task in tasks] == ["去机场接同事"]
    assert tasks[0].task_id == "task_agent_1"
    assert tasks[0].status == "pending"
    assert all("孩子" not in task.title for task in tasks)


@pytest.mark.asyncio
async def test_shared_backend_requires_team_token() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token="team-test-token"))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as secured_client:
        health = await secured_client.get("/health")
        missing = await secured_client.get("/v1/state")
        wrong = await secured_client.get("/v1/state", headers={"X-Agent-Token": "wrong"})
        allowed = await secured_client.get("/v1/state", headers={"X-Agent-Token": "team-test-token"})
    assert health.status_code == 200
    assert health.json()["shared_access_enabled"] is True
    assert "team-test-token" not in health.text
    assert missing.status_code == 401
    assert wrong.status_code == 401
    assert allowed.status_code == 200


@pytest.mark.asyncio
async def test_world_state_stream_starts_with_snapshot_and_keeps_connection_alive() -> None:
    runtime = AgentRuntime(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))

    class ConnectedRequest:
        async def is_disconnected(self) -> bool:
            return False

    stream = world_state_event_stream(ConnectedRequest(), runtime, heartbeat_seconds=0.01)
    initial = await asyncio.wait_for(anext(stream), timeout=0.2)
    heartbeat = await asyncio.wait_for(anext(stream), timeout=0.2)
    await stream.aclose()

    assert initial.startswith("event: state.updated\ndata: ")
    assert '"schema_version":"0.2.0"' in initial
    assert heartbeat == ": heartbeat\n\n"
    assert not runtime._subscribers


@pytest.mark.asyncio
async def test_happy_path_and_duplicate_confirmation(client: AsyncClient) -> None:
    state = await prepare_confirmation(client)
    assert state["stage"] == "waiting_confirmation"
    assert state["primary_surface"] == "vehicle_hmi"
    assert state["risk"]["pressure_level"] == "L2"
    assert state["service_orders"][0]["total"] == 186
    message_action = next(action for action in state["actions"] if action["type"] == "message")
    order_action = next(action for action in state["actions"] if action["type"] == "service_order")
    assert "消息草稿" in message_action["summary"]
    assert "预计18:28到" in message_action["summary"]
    assert "牛奶×2" in order_action["summary"]
    assert "鸡蛋×1" in order_action["summary"]
    assert "模拟商超配送" in order_action["summary"]
    confirmation_id = state["confirmation"]["confirmation_id"]

    body = {"confirmation_id": confirmation_id, "decision": "accept", "confirmed_by": "vehicle_hmi", "input_mode": "button"}
    first = await client.post("/v1/confirm", json=body)
    second = await client.post("/v1/confirm", json={**body, "input_mode": "voice"})
    assert first.status_code == 200
    assert second.status_code == 200
    first_state = first.json()
    second_state = second.json()
    assert first_state["stage"] == "action_completed"
    assert first_state["service_orders"][0]["order_id"] == second_state["service_orders"][0]["order_id"]
    assert first_state["revision"] == second_state["revision"]
    completed_order = next(action for action in first_state["actions"] if action["type"] == "service_order")
    assert first_state["service_orders"][0]["order_id"] in completed_order["summary"]
    assert "牛奶×2" in first_state["output"]["conclusion"]
    assert "鸡蛋×1" in first_state["output"]["conclusion"]
    assert "186元" in first_state["output"]["conclusion"]
    assert "20:00-21:00" in first_state["output"]["conclusion"]


@pytest.mark.asyncio
async def test_confirmation_rejects_non_owner_surface(client: AsyncClient) -> None:
    state = await prepare_confirmation(client)
    response = await client.post(
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


@pytest.mark.asyncio
async def test_duplicate_event_is_idempotent(client: AsyncClient) -> None:
    payload = await event(client, "evt_same", "meeting.overrun", {"delay_minutes": 20})
    first = (await client.post("/v1/event", json=payload)).json()
    second = (await client.post("/v1/event", json=payload)).json()
    assert first["duplicate"] is False
    assert second["duplicate"] is True
    assert first["revision"] == second["revision"]


@pytest.mark.asyncio
async def test_l3_requires_two_auxiliary_signal_classes(client: AsyncClient) -> None:
    await client.post("/v1/event", json=await event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    await client.post(
        "/v1/event",
        json=await event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    one = (await client.post(
        "/v1/event",
        json=await event(client, "evt_hr", "wearable.signal", {"heart_rate": 120, "confidence": 0.9}, "wearable"),
    )).json()["state"]
    assert one["risk"]["pressure_level"] == "L2"
    assert one["wearable"]["text"] == "压力信号升高"
    assert one["wearable"]["color"] == "yellow"
    assert one["wearable"]["haptic"] == "double_short"
    two = (await client.post(
        "/v1/event",
        json=await event(client, "evt_brake", "driving.signal", {"harsh_brake": True}),
    )).json()["state"]
    assert two["risk"]["pressure_level"] == "L3"
    assert two["stage"] == "takeover_L3"
    assert two["wearable"]["text"] == "高负荷保护"
    assert two["wearable"]["color"] == "red"
    assert two["wearable"]["haptic"] == "error_once"


@pytest.mark.asyncio
async def test_over_budget_order_is_not_confirmable(client: AsyncClient) -> None:
    await client.post(
        "/v1/event",
        json=await event(client, "evt_task", "task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile"),
    )
    await client.post("/v1/event", json=await event(client, "evt_vehicle", "scene.vehicle_entered", {}))
    await client.post("/v1/event", json=await event(client, "evt_mock", "service.mock.config", {"mode": "over_budget"}))
    await client.post(
        "/v1/event",
        json=await event(client, "evt_traffic", "traffic.updated", {"eta": "2026-07-15T18:28:00+08:00", "late_minutes": 18}),
    )
    state = (await client.post(
        "/v1/event",
        json=await event(client, "evt_help", "user.utterance", {"text": "帮我处理"}, "vehicle_hmi"),
    )).json()["state"]
    order_action = next(action for action in state["actions"] if action["type"] == "service_order")
    assert order_action["status"] == "blocked"
    assert state["confirmation"] is None or order_action["action_id"] not in state["confirmation"]["action_ids"]
    assert state["service_orders"][0]["error_code"] == "OVER_BUDGET"


@pytest.mark.asyncio
async def test_out_of_stock_order_is_not_confirmable_or_reported_as_executed(client: AsyncClient) -> None:
    await client.post(
        "/v1/event",
        json=await event(client, "evt_stock_task", "task.created", {"text": "之后去超市采购"}, "mobile"),
    )
    await client.post(
        "/v1/event",
        json=await event(client, "evt_stock_mock", "service.mock.config", {"mode": "out_of_stock"}),
    )
    state = (
        await client.post(
            "/v1/event",
            json=await event(
                client,
                "evt_stock_help",
                "user.utterance",
                {"text": "帮我处理，先准备方案"},
                "mobile",
            ),
        )
    ).json()["state"]

    order_action = next(action for action in state["actions"] if action["type"] == "service_order")
    assert order_action["status"] == "blocked"
    assert state["confirmation"] is None or order_action["action_id"] not in state["confirmation"]["action_ids"]
    assert state["service_orders"][0]["error_code"] == "OUT_OF_STOCK"
    assert state["service_orders"][0]["status"] == "blocked"
    assert "已下单" not in state["output"]["conclusion"]


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

    await submit("evt_task", "task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile")
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
