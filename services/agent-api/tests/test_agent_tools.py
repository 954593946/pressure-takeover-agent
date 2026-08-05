from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from httpx import ASGITransport, AsyncClient

from auri_agent.agent import AuriAgent
from auri_agent.app import create_app
from auri_agent.chat import ChatAgent
from auri_agent.config import Settings
from auri_agent.models import initial_state
from auri_agent.runtime import AgentRuntime
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
        "delete_task",
        "control_ac",
        "prepare_assistance",
        "confirm_current_actions",
    }
    assert all("runtime" not in schema.get("properties", {}) for schema in schemas.values())
    assert schemas["get_status"]["properties"] == {}


def test_assistance_is_grounded_in_existing_tasks() -> None:
    state = initial_state("demo_grounded")
    toolbox = AgentToolbox(
        state,
        event_id="evt_tasks",
        source="mobile",
        original_text="18:10接孩子，老师和家人在等；之后去超市",
    )
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
    family_action = next(action for action in state.actions if action.target == "家人")
    assert family_action.action_id == "action_message_2"
    assert "我会安全驾驶并继续同步进度" in family_action.summary
    assert "你先安心等我" not in family_action.summary
    assert len(state.service_orders) == 1
    assert state.confirmation is not None


def test_assistance_does_not_invent_grocery_or_child_contacts() -> None:
    state = initial_state("demo_airport")
    toolbox = AgentToolbox(
        state,
        event_id="evt_airport",
        source="mobile",
        original_text="去机场接同事，请通知同事",
    )
    toolbox.create_tasks(
        [task("20:00去机场接同事", "rigid", adjustable=False, waiting_party=["同事"])],
        replace_existing=False,
    )

    toolbox.prepare_assistance(include_messages=True, include_grocery=True)

    assert [action.target for action in state.actions] == ["同事"]
    assert state.service_orders == []
    assert all("老师" not in action.target and "家人" not in action.target for action in state.actions)


def test_assistance_preserves_specific_family_contact() -> None:
    state = initial_state("demo_grandparent")
    toolbox = AgentToolbox(
        state,
        event_id="evt_grandparent",
        source="mobile",
        original_text="18:10接孩子，请通知王老师和孩子奶奶",
    )
    toolbox.create_tasks(
        [task("18:10去学校接孩子", "rigid", adjustable=False, waiting_party=["王老师", "孩子奶奶"])],
        replace_existing=False,
    )

    toolbox.prepare_assistance(include_messages=True, include_grocery=False)

    assert {action.target for action in state.actions} == {"王老师", "孩子奶奶"}


@pytest.mark.parametrize(
    ("contacts", "expected_count"),
    [
        ([], 0),
        (["陈老师"], 1),
        (["陈老师", "孩子爷爷", "孩子妈妈"], 3),
        (["陈老师", "孩子爷爷", "孩子妈妈", "爸爸", "社区联系人"], 5),
    ],
)
def test_assistance_uses_every_existing_waiting_party_without_invention(
    contacts: list[str], expected_count: int
) -> None:
    state = initial_state(f"demo_contacts_{expected_count}")
    state.eta = datetime(2026, 8, 5, 18, 28, tzinfo=TZ)
    state.risk.late_minutes = 18
    original_text = "帮我处理接孩子"
    if contacts:
        original_text += "，需要通知" + "、".join(contacts)
    toolbox = AgentToolbox(
        state,
        event_id=f"evt_contacts_{expected_count}",
        source="mobile",
        original_text=original_text,
    )
    toolbox.create_tasks(
        [task("18:10接孩子", "rigid", priority="high", adjustable=False, waiting_party=contacts)],
        replace_existing=False,
    )

    result = toolbox.prepare_assistance(include_messages=True, include_grocery=False)
    messages = [action for action in state.actions if action.type == "message"]

    assert [action.target for action in messages] == contacts
    assert len(messages) == expected_count
    assert len({action.action_id for action in messages}) == expected_count
    assert all(action.action_id == f"action_message_{index + 1}" for index, action in enumerate(messages))
    assert all("预计18:28到" in action.summary for action in messages)
    assert all("王老师" not in action.summary and "孩子妈妈" not in action.summary for action in messages if action.target not in {"王老师", "孩子妈妈"})
    if expected_count == 0:
        assert result["requires_confirmation"] is False
        assert state.confirmation is None
    else:
        assert result["requires_confirmation"] is True
        assert state.confirmation is not None


def test_task_creation_drops_recipients_not_named_by_user() -> None:
    state = initial_state("demo_no_invented_contacts")
    toolbox = AgentToolbox(
        state,
        event_id="evt_no_invented_contacts",
        source="mobile",
        original_text="今天18:10接孩子，之后去超市",
    )

    toolbox.create_tasks(
        [
            task(
                "18:10接孩子",
                "rigid",
                priority="high",
                adjustable=False,
                waiting_party=["孩子", "王老师", "孩子妈妈"],
            )
        ],
        replace_existing=False,
    )

    assert state.tasks[0].waiting_party == []


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


def test_demo_receipts_include_message_body_and_purchase_details() -> None:
    state = initial_state("demo_receipts")
    state.eta = datetime(2026, 7, 29, 18, 28, tzinfo=TZ)
    state.risk.late_minutes = 18
    setup = AgentToolbox(
        state,
        event_id="evt_receipts",
        source="mobile",
        original_text="帮我处理，并通知孩子",
    )
    setup.create_tasks(
        [
            task(
                "18:10去学校接孩子",
                "rigid",
                priority="high",
                adjustable=False,
                waiting_party=["孩子"],
            ),
            task("之后去超市采购", "flexible", capability_tags=["grocery_delivery"]),
        ],
        replace_existing=False,
    )

    prepared = setup.prepare_assistance(include_messages=True, include_grocery=True)
    message = next(action for action in state.actions if action.type == "message")
    order_action = next(action for action in state.actions if action.type == "service_order")

    assert "给孩子的消息草稿" in message.summary
    assert "预计18:28到" in message.summary
    assert "你先安心等我" in message.summary
    assert "未连接真实通讯服务" in message.summary
    assert "牛奶×2" in order_action.summary
    assert "鸡蛋×1" in order_action.summary
    assert "共9件（8种）" in order_action.summary
    assert "模拟商超配送" in order_action.summary
    assert "未发生真实支付" in order_action.summary
    assert "牛奶×2" in state.output.conclusion
    assert prepared["requires_confirmation"] is True

    confirmation = AgentToolbox(
        state,
        event_id="evt_receipts_confirm",
        source="mobile",
        original_text="确认执行吧",
    ).confirm_current_actions("accept")

    assert confirmation["ok"] is True
    assert confirmation["execution_receipts"]
    assert message.summary.startswith("已模拟发送给孩子：")
    assert order_action.details_ref == state.service_orders[0].order_id
    assert state.service_orders[0].order_id in order_action.summary
    assert "孩子" in state.output.conclusion
    assert "牛奶×2" in state.output.conclusion
    assert "186元" in state.output.conclusion
    assert "20:00-21:00" in state.output.conclusion

    grounded = AuriAgent(Settings(llm_enabled=False, openai_api_key=""))._ground_reply(
        "都处理好了。",
        state,
        ["confirm_current_actions"],
    )
    assert "孩子" in grounded
    assert "牛奶×2" in grounded
    assert "鸡蛋×1" in grounded
    assert "20:00-21:00" in grounded


def test_demo_receipts_render_utc_eta_in_shanghai_time() -> None:
    state = initial_state("demo_receipts_utc")
    state.eta = datetime(2026, 7, 29, 10, 28, tzinfo=timezone.utc)
    state.risk.late_minutes = 18
    setup = AgentToolbox(
        state,
        event_id="evt_receipts_utc",
        source="mobile",
        original_text="帮我处理，并通知孩子",
    )
    setup.create_tasks(
        [task("18:10去学校接孩子", "rigid", priority="high", adjustable=False, waiting_party=["孩子"])],
        replace_existing=False,
    )

    setup.prepare_assistance(include_messages=True, include_grocery=False)
    message = next(action for action in state.actions if action.type == "message")

    assert "预计18:28到" in message.summary
    assert "预计10:28到" not in message.summary


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


@pytest.mark.asyncio
async def test_standalone_ac_command_skips_model_and_preserves_existing_tasks() -> None:
    class MustNotRunGraph:
        async def ainvoke(self, *_args, **_kwargs) -> dict:
            raise AssertionError("standalone AC command must not be routed through prior chat history")

    state = initial_state("demo_ac_after_task")
    setup = AgentToolbox(state, event_id="evt_setup_task", source="mobile", original_text="创建任务")
    setup.create_tasks([task("今晚9点打游戏", "flexible")], replace_existing=False)

    agent = AuriAgent(Settings(llm_enabled=False, openai_api_key=""))
    agent.graph = MustNotRunGraph()
    result = await agent.handle(
        "然后请帮我打开空调",
        state,
        source="mobile",
        event_id="evt_open_ac",
    )

    assert result.mode == "deterministic_tool"
    assert result.called_tools == ["control_ac"]
    assert result.state.vehicle_state.ac_on is True
    assert [item.title for item in result.state.tasks] == ["今晚9点打游戏"]
    assert "空调已打开" in result.reply


def test_ac_status_question_is_not_mistaken_for_a_control_command() -> None:
    assert AuriAgent._parse_explicit_ac_command("空调打开了吗？") is None
    assert AuriAgent._parse_explicit_ac_command("现在空调是不是打开的？") is None


@pytest.mark.asyncio
async def test_ac_temperature_and_close_commands_are_deterministic() -> None:
    agent = AuriAgent(Settings(llm_enabled=False, openai_api_key=""))
    opened = await agent.handle(
        "把空调开到22度",
        initial_state("demo_ac_temperature"),
        source="mobile",
        event_id="evt_ac_temperature",
    )
    closed = await agent.handle(
        "关闭空调",
        opened.state,
        source="mobile",
        event_id="evt_ac_close",
    )

    assert opened.called_tools == ["control_ac"]
    assert opened.state.vehicle_state.ac_on is True
    assert opened.state.vehicle_state.ac_target_temp == 22
    assert closed.called_tools == ["control_ac"]
    assert closed.state.vehicle_state.ac_on is False
    assert "空调已关闭" in closed.reply


@pytest.mark.asyncio
async def test_ac_mode_and_fan_commands_are_written_to_world_state() -> None:
    agent = AuriAgent(Settings(llm_enabled=False, openai_api_key=""))

    result = await agent.handle(
        "空调调到26度制冷大风",
        initial_state("demo_ac_mode_fan"),
        source="mobile",
        event_id="evt_ac_mode_fan",
    )

    assert result.called_tools == ["control_ac"]
    assert result.state.vehicle_state.ac_on is True
    assert result.state.vehicle_state.ac_target_temp == 26
    assert result.state.vehicle_state.ac_mode == "cool"
    assert result.state.vehicle_state.fan_speed == "high"
    assert "26℃" in result.state.output.conclusion
    assert "已经同步" in result.state.output.conclusion


@pytest.mark.asyncio
async def test_chat_stream_opens_ac_on_first_request_after_task_creation() -> None:
    runtime = AgentRuntime(Settings(llm_enabled=False, openai_api_key=""))
    chat = ChatAgent(runtime)
    initial = await runtime.get_state()

    async for _event in chat.chat_stream(
        "请记一个今天晚上9点打游戏的任务",
        initial.session_id,
    ):
        pass
    events = [
        event
        async for event in chat.chat_stream(
            "打开空调",
            initial.session_id,
        )
    ]
    state = await runtime.get_state()

    tool_names = [
        event["function"]["name"]
        for event in events
        if event.get("type") == "tool_call"
    ]
    assert tool_names == ["control_ac"]
    assert state.vehicle_state.ac_on is True
    assert len(state.tasks) == 1


@pytest.mark.asyncio
async def test_user_utterance_fallback_changes_state_only_when_needed() -> None:
    app = create_app(Settings(llm_enabled=False, openai_api_key="", agent_shared_token=""))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        session_id = (await client.get("/v1/state")).json()["session_id"]

        async def utterance(event_id: str, text: str) -> dict:
            response = await client.post(
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

        greeting = await utterance("evt_hello", "你好，今天辛苦了")
        assert greeting["state"]["tasks"] == []
        assert greeting["state"]["actions"] == []

        created = await utterance("evt_create", "请记一个今天晚上去超市采购的任务")
        assert len(created["state"]["tasks"]) == 1
        assert "超市" in created["state"]["tasks"][0]["title"]
        assert created["state"]["output"]["conclusion"] != greeting["state"]["output"]["conclusion"]

        ac = await utterance("evt_ac", "打开空调")
        assert ac["state"]["vehicle_state"]["ac_on"] is True
        assert len(ac["state"]["tasks"]) == 1
        assert "空调已打开" in ac["state"]["output"]["conclusion"]

        status = await utterance("evt_status", "现在有什么任务？")
        assert status["state"]["actions"] == []
        assert "1项待办" in status["state"]["output"]["conclusion"]

        assistance = await utterance("evt_help", "帮我处理这些事情，先准备方案给我确认")
        assert assistance["state"]["confirmation"]["status"] == "pending"
        assert len(assistance["state"]["actions"]) == 1

        duplicate = await utterance("evt_status", "这条文本不会被重复执行")
        assert duplicate["duplicate"] is True
        assert duplicate["revision"] == assistance["revision"]
