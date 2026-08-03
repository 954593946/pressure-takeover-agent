"""Run the real HMI happy path against a dedicated local Agent instance.

This test resets the configured Agent session. Do not point it at the shared
public Agent.
"""

import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib import error, request
from uuid import uuid4

from playwright.sync_api import sync_playwright


AGENT = os.getenv("AURI_AGENT_URL", "http://127.0.0.1:8795").rstrip("/")
HMI = os.getenv("AURI_HMI_URL", "http://127.0.0.1:5174/apps/vehicle-hmi/")
TOKEN = os.getenv("AURI_AGENT_TOKEN", "")
CHROME = os.getenv(
    "PLAYWRIGHT_CHROMIUM_EXECUTABLE",
    str(Path.home() / ".cache/ms-playwright/chromium-1228/chrome-linux64/chrome"),
)
SCREENSHOT_DIR = Path(os.getenv("AURI_E2E_SCREENSHOT_DIR", "/tmp"))
TZ = timezone(timedelta(hours=8))


def api(path, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if TOKEN:
        headers["X-Agent-Token"] = TOKEN
    req = request.Request(f"{AGENT}{path}", method=method, data=body, headers=headers)
    try:
        with request.urlopen(req, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8")
        raise AssertionError(f"{method} {path} failed: {exc.code} {detail}") from exc


def submit(event_type, payload, source="demo_console"):
    state = api("/v1/state")
    envelope = {
        "schema_version": "0.2.0",
        "event_id": f"hmi_e2e_{event_type}_{uuid4().hex[:10]}",
        "session_id": state["session_id"],
        "type": event_type,
        "source": source,
        "timestamp": datetime.now(TZ).isoformat(),
        "payload": payload,
    }
    return api("/v1/event", "POST", envelope)["state"]


def main():
    if "onrender.com" in AGENT:
        raise SystemExit("Refusing to reset a shared public Agent; use a dedicated local Agent URL.")
    api("/v1/session/reset", "POST", {"scenario_id": "hmi-local-e2e"})
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=CHROME, headless=True)
        page = browser.new_page(viewport={"width": 1920, "height": 1080})
        page_errors = []
        page.on("pageerror", lambda item: page_errors.append(str(item)))
        config = json.dumps({"apiBase": AGENT, "token": TOKEN, "stream": True})
        page.add_init_script(
            f"window.AURI_HMI_CONFIG={config};"
            "try{localStorage.removeItem('auri-hmi-next-config')}catch(_e){};"
            "window.__auriSpoken=[];"
            "try{window.speechSynthesis.speak=(item)=>window.__auriSpoken.push(item.text)}catch(_e){}"
        )
        page.goto(HMI, wait_until="domcontentloaded", timeout=30000)
        page.wait_for_function(
            "window.AURI_HMI_NEXT?.getState().viewModel.lifecycle.stage === 'off_vehicle_idle'"
        )
        page.wait_for_function("window.AURI_HMI_NEXT?.getState().syncMode === 'streaming'")
        initial = page.evaluate("window.AURI_HMI_NEXT.getState()")
        assert initial["viewModel"]["tasks"]["total"] == 0
        assert page.locator("#auri-responsibility-strip").is_hidden()

        task_state = submit(
            "task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile"
        )
        assert task_state["navigation"]["task_id"] == "task_pickup_child"
        assert task_state["navigation"]["source"] == "demo_fixture"
        assert task_state["navigation"]["is_simulated"] is True
        page.wait_for_function("window.AURI_HMI_NEXT.getState().viewModel.tasks.total >= 2")
        assert page.evaluate(
            "window.AURI_HMI_NEXT.getState().viewModel.meta.revision"
        ) == task_state["revision"]
        displayed_route = page.evaluate("window.AURI_HMI_NEXT.getState().viewModel.navigation.route")
        assert displayed_route["id"] == task_state["navigation"]["route_id"]
        assert displayed_route["destination"]["coordinates"] == [120.7359, 31.3048]
        assert page.locator(".auri-responsibility-item").count() == 2
        responsibility_text = page.locator("#auri-responsibility-strip").inner_text()
        assert "接孩子" in responsibility_text
        assert "超市" in responsibility_text

        page.locator("#vd-nav-card").click(position={"x": 400, "y": 18})
        assert "行程详情" in page.locator("#hdr-a").inner_text()
        assert "阳光小学" in page.locator("#body-a").inner_text()
        page.locator(".auri-panel-close").click()

        warning_state = submit("meeting.overrun", {"delay_minutes": 20})
        page.wait_for_function(
            "revision => window.AURI_HMI_NEXT.getState().viewModel.meta.revision >= revision",
            arg=warning_state["revision"],
        )
        assert warning_state["stage"] == "pre_departure_warning"
        assert page.locator("#auri-stage-notice").is_hidden()
        assert page.locator("#auri-device-notice").is_hidden()

        vehicle_state = submit("scene.vehicle_entered", {})
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.lifecycle.primarySurface === 'vehicle_hmi'"
        )
        assert vehicle_state["scene"] == "driving"
        page.wait_for_function("document.querySelector('#auri-stage-notice')?.classList.contains('is-visible')")
        stage_notice_text = page.locator("#auri-stage-notice").inner_text()
        assert any(text in stage_notice_text for text in ["路线正在同步到车机", "正在前往"])
        page.wait_for_function("document.querySelector('#auri-device-notice')?.classList.contains('is-visible')")
        assert "腕上" in page.locator("#auri-device-notice").inner_text()

        page.locator('[data-auri-section="auri"]').click()
        page.locator('[data-panel-target="sync"]').click()
        assert "设备同步" in page.locator("#hdr-a").inner_text()
        assert all(label in page.locator("#body-a").inner_text() for label in ["手机", "腕表", "车机"])
        page.locator(".auri-panel-close").click()

        rigid = next(
            (task for task in vehicle_state["tasks"] if task.get("task_type") == "rigid"),
            None,
        )
        scheduled = (
            datetime.fromisoformat(rigid["scheduled_at"])
            if rigid and rigid.get("scheduled_at")
            else datetime.now(TZ)
        )
        traffic_state = submit(
            "traffic.updated",
            {"eta": (scheduled + timedelta(minutes=18)).isoformat(), "late_minutes": 18},
        )
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.risk.lateMinutes === 18"
        )
        assert traffic_state["risk"]["pressure_level"] == "L2"

        prepared = submit(
            "user.utterance",
            {"text": "我还来得及吗？帮我处理", "input_mode": "voice"},
            "mobile",
        )
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'waiting_confirmation'"
        )
        assert prepared["confirmation"]["owner_surface"] == "vehicle_hmi"
        assert page.locator("#auri-takeover-confirm").is_enabled()
        assert "我还来得及吗" in page.locator("#auri-takeover-risk").inner_text()
        assert page.locator(".auri-takeover-action").count() == len(prepared["actions"][:3])
        page.wait_for_timeout(400)
        page.screenshot(path=SCREENSHOT_DIR / "auri-hmi-e2e-waiting-confirmation.png")

        page.locator("#auri-takeover-confirm").click()
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'action_completed'",
            timeout=15000,
        )
        completed = api("/v1/state")
        shown = page.evaluate("window.AURI_HMI_NEXT.getState().viewModel")
        assert shown["meta"]["revision"] == completed["revision"]
        assert all(action["status"] == "completed" for action in completed["actions"])
        page.wait_for_timeout(200)
        assert page.evaluate("window.__auriSpoken") == ["已处理，你按当前速度安全驾驶即可。"]

        submit("cooldown.elapsed", {})
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'cooldown'"
        )
        assert page.locator("#vd-nav-card").is_visible()
        assert page.locator("#auri-stage-notice").is_visible()
        assert "AURI 已降低打扰" in page.locator("#auri-stage-notice").inner_text()

        parked = submit("scene.parked", {})
        page.wait_for_function(
            "window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'parked_review'"
        )
        assert parked["primary_surface"] == "mobile"
        assert page.locator("#auri-takeover-card").is_visible()
        assert page.locator("#vd-nav-card").is_hidden()
        assert "手机继续处理" in page.locator("#auri-takeover-stage").inner_text()
        page.wait_for_timeout(400)
        page.screenshot(path=SCREENSHOT_DIR / "auri-hmi-e2e-parked-review.png")

        final_state = page.evaluate("window.AURI_HMI_NEXT.getState()")
        assert final_state["syncMode"] == "streaming"
        assert not page_errors, page_errors
        print(json.dumps({
            "session_id": parked["session_id"],
            "initial_revision": initial["viewModel"]["meta"]["revision"],
            "final_revision": parked["revision"],
            "tasks": len(parked["tasks"]),
            "actions": len(parked["actions"]),
            "final_stage": parked["stage"],
            "sync_mode": final_state["syncMode"],
            "javascript_errors": len(page_errors),
        }, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
