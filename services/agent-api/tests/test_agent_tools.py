from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from auri_agent.agent import AuriAgent
from auri_agent.app import create_app
from auri_agent.config import Settings
from auri_agent.models import initial_state
from auri_agent.tools import AURI_TOOLS, AgentToolbox, TaskDraft


TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


def task(title: str, task_type: str, **kwargs) -> TaskDraft:
    return TaskDraft(title=title, task_type=task_type, **kwargs)


def test_public_tool_schemas_hide_runtime_context() -> None:
    schemas = {item.name: item.tool_call_schema.model_json_schema() for item in AURI_TOOLS}

    assert set(schemas) == {
        "create_tasks",
        "get_status",
        "report_meeting_delay",
        "reschedule_task",
        "prepare_assistance",
        "confirm_current_actions",
    }
    assert all("runtime" not in schema.get("properties", {}) for schema in schemas.values())
    assert schemas["get_status"]["properties"] == {}


def test_assistance_is_grounded_in_existing_tasks() -> None:
    state = initial_state("demo_grounded")
    toolbox = AgentToolbox(state, event_id="evt_tasks", source="mobile", original_text="记下这些任务")
    toolbox.create_tasks(
        [
            task(
                "18:10去学校接孩子",
                "rigid",
                priority="high",
                adjustable=False,
                waiting_party=["老师", "家人"],
            ),
            task("之后去超市采购", "flexible", capability_tags=["grocery_delivery"]),
        ],
        replace_existing=False,
    )

    result = toolbox.prepare_assistance(include_messages=True, include_grocery=True)

    assert result["requires_confirmation"] is True
    assert {action.type for action in state.actions} == {"message", "service_order"}
    assert {action.target for action in state.actions if action.type == "message"} == {"老师", "家人"}
    assert len(state.service_orders) == 1
    assert state.confirmation is not None


def test_assistance_does_not_invent_grocery_or_child_contacts() -> None:
    state = initial_state("demo_airport")
    toolbox = AgentToolbox(state, event_id="evt_airport", source="mobile", original_text="去机场接同事")
    toolbox.create_tasks(
        [task("20:00去机场接同事", "rigid", adjustable=False, waiting_party=["同事"])],
        replace_existing=False,
    )

    toolbox.prepare_assistance(include_messages=True, include_grocery=True)

    assert [action.target for action in state.actions] == ["同事"]
    assert state.service_orders == []
    assert all("老师" not in action.target and "家人" not in action.target for action in state.actions)


def test_confirmation_requires_explicit_words_and_owner_surface() -> None:
    state = initial_state("demo_confirm")
    setup = AgentToolbox(state, event_id="evt_setup", source="mobile", original_text="帮我处理")
    setup.create_tasks([task("去超市采购", "flexible")], replace_existing=False)
    setup.prepare_assistance(include_messages=True, include_grocery=True)
    assert state.confirmation is not None

    vague = AgentToolbox(state, event_id="evt_vague", source="mobile", original_text="好的")
    assert vague.confirm_current_actions("accept")["ok"] is False
    assert state.confirmation.status == "pending"

    wrong_surface = AgentToolbox(
        state,
        event_id="evt_wrong_surface",
        source="demo_console",
        original_text="确认执行吧",
    )
    assert wrong_surface.confirm_current_actions("accept")["ok"] is False
    assert state.confirmation.status == "pending"

    owner = AgentToolbox(state, event_id="evt_owner", source="mobile", original_text="确认执行吧")
    assert owner.confirm_current_actions("accept")["ok"] is True
    assert state.confirmation.status == "accepted"
    assert state.service_orders[0].status == "submitted"


@pytest.mark.asyncio
async def test_completed_tool_state_survives_final_model_timeout() -> None:
    class ToolThenTimeoutGraph:
        async def ainvoke(self, _input: dict, *, context, config: dict) -> dict:
            context.toolbox.create_tasks(
                [task("20:00去机场接同事", "rigid", adjustable=False, waiting_party=["同事"])],
                replace_existing=False,
            )
            raise TimeoutError("final response timed out")

    agent = AuriAgent(Settings(llm_enabled=False, openai_api_key=""))
    agent.graph = ToolThenTimeoutGraph()

    result = await agent.handle(
        "请创建20:00去机场接同事的任务",
        initial_state("demo_partial"),
        source="mobile",
        event_id="evt_partial",
    )

    assert result.mode == "langchain_agent_fallback_reply"
    assert result.called_tools == ["create_tasks"]
    assert [item.title for item in result.state.tasks] == ["20:00去机场接同事"]
    assert "机场" in result.reply


def test_user_utterance_fallback_changes_state_only_when_needed() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    client = TestClient(app)
    session_id = client.get("/v1/state").json()["session_id"]

    def utterance(event_id: str, text: str) -> dict:
        response = client.post(
            "/v1/event",
            json={
                "schema_version": "0.2.0",
                "event_id": event_id,
                "session_id": session_id,
                "type": "user.utterance",
                "source": "mobile",
                "timestamp": datetime.now(TZ).isoformat(),
                "payload": {"text": text},
            },
        )
        assert response.status_code == 202
        return response.json()

    greeting = utterance("evt_hello", "你好，今天辛苦了")
    assert greeting["state"]["tasks"] == []
    assert greeting["state"]["actions"] == []

    created = utterance("evt_create", "请记一个今天晚上去超市采购的任务")
    assert len(created["state"]["tasks"]) == 1
    assert "超市" in created["state"]["tasks"][0]["title"]
    assert created["state"]["output"]["conclusion"] != greeting["state"]["output"]["conclusion"]

    status = utterance("evt_status", "现在有什么任务？")
    assert status["state"]["actions"] == []
    assert "1项待办" in status["state"]["output"]["conclusion"]

    assistance = utterance("evt_help", "帮我处理这些事情，先准备方案给我确认")
    assert assistance["state"]["confirmation"]["status"] == "pending"
    assert len(assistance["state"]["actions"]) == 1

    duplicate = utterance("evt_status", "这条文本不会被重复执行")
    assert duplicate["duplicate"] is True
    assert duplicate["revision"] == assistance["revision"]
