"""Verify dynamic Agent actions are rendered from the real World State.

This test owns two isolated sessions on the dedicated local Agent and drives
the real HMI page through the local static server. It intentionally does not
use the Console fixture, because the purpose is to prove that arbitrary
recipients and an empty recipient list survive the Agent/HMI contract.

Run only against the dedicated local services:

    AURI_AGENT_URL=http://127.0.0.1:8795 \
    AURI_WEB_ROOT=http://127.0.0.1:5174 \
    python apps/vehicle-hmi/tests/e2e_dynamic_world_state_actions.py
"""

from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone, timedelta
from pathlib import Path
from urllib import error, request
from uuid import uuid4

from playwright.sync_api import Page, sync_playwright


AGENT = os.getenv("AURI_AGENT_URL", "http://127.0.0.1:8795").rstrip("/")
WEB_ROOT = os.getenv("AURI_WEB_ROOT", "http://127.0.0.1:5174").rstrip("/")
HMI = f"{WEB_ROOT}/apps/vehicle-hmi/"
TOKEN = os.getenv("AURI_AGENT_TOKEN", "")
CHROME = os.getenv(
    "PLAYWRIGHT_CHROMIUM_EXECUTABLE",
    str(Path.home() / ".cache/ms-playwright/chromium-1228/chrome-linux64/chrome"),
)
TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


def api(path: str, method: str = "GET", payload: dict | None = None) -> dict:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if TOKEN:
        headers["X-Agent-Token"] = TOKEN
    req = request.Request(f"{AGENT}{path}", method=method, data=body, headers=headers)
    try:
        with request.urlopen(req, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8")
        raise AssertionError(f"{method} {path} failed: {exc.code} {detail}") from exc
    except OSError as exc:
        raise AssertionError(f"Cannot reach {AGENT}: {exc}") from exc


def event(event_type: str, payload: dict, source: str = "mobile") -> dict:
    state = api("/v1/state")
    return api(
        "/v1/event",
        "POST",
        {
            "schema_version": "0.2.0",
            "event_id": f"dynamic_actions_{event_type}_{uuid4().hex[:12]}",
            "session_id": state["session_id"],
            "type": event_type,
            "source": source,
            "timestamp": datetime.now(TZ).isoformat(),
            "payload": payload,
        },
    )["state"]


def reset(scenario_id: str) -> dict:
    return api("/v1/session/reset", "POST", {"scenario_id": scenario_id})


def task(
    task_id: str,
    title: str,
    *,
    task_type: str,
    adjustable: bool,
    waiting_party: list[str],
    capability_tags: list[str] | None = None,
) -> dict:
    return {
        "task_id": task_id,
        "title": title,
        "scheduled_at": "2026-08-05T18:10:00+08:00",
        "location": "阳光小学" if "孩子" in title else "苏州工业园区",
        "task_type": task_type,
        "priority": "high" if task_type == "rigid" else "low",
        "adjustable": adjustable,
        "waiting_party": waiting_party,
        "capability_tags": capability_tags or [],
    }


def connect_hmi(page: Page, session_id: str) -> None:
    page.wait_for_function(
        "sessionId => window.AURI_HMI_NEXT?.getState().viewModel.meta.sessionId === sessionId",
        arg=session_id,
        timeout=20000,
    )
    page.wait_for_function(
        "() => ['streaming', 'polling_fallback'].includes(window.AURI_HMI_NEXT?.getState().syncMode)",
        timeout=20000,
    )


def open_hmi(browser_context, session_id: str) -> Page:
    page = browser_context.new_page()
    config = json.dumps({"apiBase": AGENT, "token": TOKEN, "stream": True})
    page.add_init_script(
        f"window.AURI_HMI_CONFIG={config};"
        "try{localStorage.removeItem('auri-hmi-next-config')}catch(_e){};"
        "window.SAFEDRIVER_CONFIG={systemSpeechFallback:false};"
        "window.AURI_HMI_SPEECH_ADAPTER={cancel(){},speak(){return true}};"
    )
    page.goto(HMI, wait_until="domcontentloaded", timeout=30000)
    connect_hmi(page, session_id)
    return page


def progress_to_help(task_list: list[dict]) -> dict:
    task_state = event("task.created", {"text": "手机语音创建任务", "tasks": task_list})
    event("meeting.overrun", {"delay_minutes": 20}, "demo_console")
    event("scene.vehicle_entered", {}, "demo_console")
    event(
        "traffic.updated",
        {"eta": "2026-08-05T18:28:00+08:00", "late_minutes": 18},
        "demo_console",
    )
    return event(
        "user.utterance",
        {"text": "我还来得及吗？帮我处理", "input_mode": "voice"},
        "mobile",
    )


def message_body(summary: str) -> str:
    body = re.sub(r"^给.+?的消息草稿[：:]\s*", "", summary)
    return body.split("（Demo 模拟消息", 1)[0].split("(Demo 模拟消息", 1)[0].strip()


def assert_hmi_action_details(page: Page, state: dict) -> None:
    actions = state["actions"]
    buttons = page.locator('#auri-takeover-actions [data-panel-target^="action:"]')
    assert buttons.count() == len(actions), (buttons.count(), len(actions))

    for action in actions:
        selector = f'[data-panel-target="action:{action["action_id"]}"]'
        button = page.locator(selector)
        assert button.count() == 1, action
        assert action["target"] in button.inner_text(), action

        button.click()
        page.wait_for_function(
            "() => document.querySelector('#auri-driver-detail')?.hidden === false",
            timeout=5000,
        )
        detail = page.locator("#auri-detail-body").inner_text()
        assert action["target"] in detail, (action, detail)
        if action["type"] == "message":
            assert message_body(action["summary"]) in detail, (action, detail)
        else:
            assert action["summary"] in detail or action["target"] in detail, (action, detail)
        page.locator("#auri-driver-back").click()
        page.wait_for_function(
            "() => document.querySelector('#auri-driver-detail')?.hidden === true",
            timeout=5000,
        )


def scenario_a(browser_context) -> None:
    state = reset("dynamic-world-state-five-contacts")
    contacts = ["陈老师", "孩子妈妈", "孩子爷爷", "邻居李叔", "托管中心"]
    page = open_hmi(browser_context, state["session_id"])
    try:
        final = progress_to_help(
            [
                task(
                    "task_dynamic_pickup",
                    "接孩子",
                    task_type="rigid",
                    adjustable=False,
                    waiting_party=contacts,
                ),
                task(
                    "task_dynamic_grocery",
                    "超市配送",
                    task_type="flexible",
                    adjustable=True,
                    waiting_party=[],
                    capability_tags=["grocery_delivery"],
                ),
            ]
        )
        assert final["stage"] == "waiting_confirmation", final
        assert final["primary_surface"] == "vehicle_hmi", final
        messages = [action for action in final["actions"] if action["type"] == "message"]
        orders = [action for action in final["actions"] if action["type"] == "service_order"]
        assert [action["target"] for action in messages] == contacts, final["actions"]
        assert len(messages) == 5
        assert len(orders) == 1
        assert len(final["actions"]) == 6
        assert all(message_body(action["summary"]) for action in messages)

        page.wait_for_function(
            "count => document.querySelectorAll('#auri-takeover-actions [data-panel-target^=\\\"action:\\\"]') .length === count",
            arg=6,
            timeout=15000,
        )
        assert_hmi_action_details(page, final)
    finally:
        page.close()


def scenario_b(browser_context) -> None:
    state = reset("dynamic-world-state-no-contacts")
    page = open_hmi(browser_context, state["session_id"])
    try:
        final = progress_to_help(
            [
                task(
                    "task_dynamic_no_contact",
                    "接孩子",
                    task_type="rigid",
                    adjustable=False,
                    waiting_party=[],
                )
            ]
        )
        assert final["actions"] == [], final
        assert final["service_orders"] == [], final
        assert final["confirmation"] is None, final
        assert final["stage"] == "planning", final

        page.wait_for_function(
            "() => document.querySelector('#auri-takeover-actions')?.textContent.includes('等待 Agent 生成处理方案')",
            timeout=15000,
        )
        page_text = page.locator("body").inner_text()
        assert "王老师" not in page_text
        assert "孩子妈妈" not in page_text
        assert page.locator('#auri-takeover-actions [data-panel-target^="action:"]').count() == 0
    finally:
        page.close()


def main() -> None:
    if AGENT not in {"http://127.0.0.1:8795", "http://localhost:8795"}:
        raise SystemExit(f"Refusing to reset non-dedicated Agent URL: {AGENT}")

    health = api("/health")
    assert health.get("status") == "ok", health
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=CHROME, headless=True)
        context = browser.new_context(viewport={"width": 1920, "height": 720})
        try:
            scenario_a(context)
            scenario_b(context)
        finally:
            context.close()
            browser.close()
    print("PASS: scenario A rendered 5 World State messages plus 1 service_order; scenario B rendered zero message actions without invented contacts")


if __name__ == "__main__":
    main()
