"""Verify Console, mobile chat and the official HMI share one Agent World State.

Run against a dedicated local Agent only. The test resets the active session and
must never be pointed at the shared public deployment.
"""

import json
import os
import uuid
from pathlib import Path
from urllib import error, request

from playwright.sync_api import Page, sync_playwright


AGENT = os.getenv("AURI_AGENT_URL", "http://127.0.0.1:8795").rstrip("/")
WEB_ROOT = os.getenv("AURI_WEB_ROOT", "http://127.0.0.1:5174").rstrip("/")
CONSOLE = f"{WEB_ROOT}/apps/demo-console/"
HMI = f"{WEB_ROOT}/apps/vehicle-hmi/"
TOKEN = os.getenv("AURI_AGENT_TOKEN", "")
CHROME = os.getenv(
    "PLAYWRIGHT_CHROMIUM_EXECUTABLE",
    str(Path.home() / ".cache/ms-playwright/chromium-1228/chrome-linux64/chrome"),
)
SCREENSHOT_DIR = Path(os.getenv("AURI_E2E_SCREENSHOT_DIR", "/tmp/auri-hmi-multisurface"))


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


def mobile_chat(message: str, session_id: str, input_mode: str = "voice") -> list[dict]:
    payload = json.dumps(
        {
            "message": message,
            "inputMode": input_mode,
            "sessionId": session_id,
            "clientEventId": f"evt_chat_{uuid.uuid4()}",
        },
        ensure_ascii=False,
    ).encode("utf-8")
    headers = {"Accept": "text/event-stream", "Content-Type": "application/json"}
    if TOKEN:
        headers["X-Agent-Token"] = TOKEN
    req = request.Request(f"{AGENT}/v1/chat", method="POST", data=payload, headers=headers)
    with request.urlopen(req, timeout=30) as response:
        raw = response.read().decode("utf-8")
    events = []
    for frame in raw.replace("\r\n", "\n").split("\n\n"):
        data = "\n".join(
            line[5:].lstrip() for line in frame.splitlines() if line.startswith("data:")
        )
        if data:
            events.append(json.loads(data))
    return events


def wait_console_stage(page: Page, stage: str) -> None:
    page.wait_for_function(
        "stage => document.querySelector('#stage')?.textContent === stage",
        arg=stage,
        timeout=15000,
    )


def wait_same_revision(console: Page, hmi: Page, expected: int) -> None:
    console.wait_for_function(
        "revision => document.querySelector('#revision')?.textContent === `revision ${revision}`",
        arg=expected,
        timeout=15000,
    )
    hmi.wait_for_function(
        "revision => window.AURI_HMI_NEXT?.getState().viewModel.meta.revision === revision",
        arg=expected,
        timeout=15000,
    )


def click_director_step(page: Page, expected_stage: str) -> dict:
    page.locator("#runCurrentStep").wait_for(state="visible")
    page.wait_for_function(
        "() => !document.querySelector('#runCurrentStep')?.disabled",
        timeout=15000,
    )
    page.locator("#runCurrentStep").click()
    wait_console_stage(page, expected_stage)
    return api("/v1/state")


def main() -> None:
    if "onrender.com" in AGENT:
        raise SystemExit("Refusing to reset a shared public Agent; use a dedicated local Agent URL.")

    initial = api("/v1/session/reset", "POST", {"scenario_id": "hmi-multisurface-e2e"})
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=CHROME, headless=True)
        context = browser.new_context(viewport={"width": 1920, "height": 720})
        console = context.new_page()
        hmi = context.new_page()
        errors: dict[str, list[str]] = {"console": [], "hmi": []}
        console.on("pageerror", lambda item: errors["console"].append(str(item)))
        hmi.on("pageerror", lambda item: errors["hmi"].append(str(item)))

        console_config = json.dumps({"apiBase": AGENT, "token": TOKEN, "stream": True})
        hmi_config = json.dumps({"apiBase": AGENT, "token": TOKEN, "stream": True})
        console.add_init_script(
            f"window.AURI_CONFIG={console_config};"
            "try{localStorage.removeItem('auri-demo-console-config')}catch(_e){}"
        )
        hmi.add_init_script(
            f"window.AURI_HMI_CONFIG={hmi_config};"
            "try{localStorage.removeItem('auri-hmi-next-config')}catch(_e){};"
            "window.__auriSpoken=[];"
            "try{window.speechSynthesis.speak=(item)=>window.__auriSpoken.push(item.text)}catch(_e){}"
        )

        console.goto(CONSOLE, wait_until="domcontentloaded", timeout=30000)
        hmi.goto(HMI, wait_until="domcontentloaded", timeout=30000)
        console.wait_for_function("document.querySelector('#syncMode')?.textContent === 'SSE 实时'", timeout=20000)
        hmi.wait_for_function("window.AURI_HMI_NEXT?.getState().syncMode === 'streaming'", timeout=20000)
        console.locator("#preflightBtn").click()
        console.wait_for_function(
            "Array.from(document.querySelectorAll('#eventLog .log-row')).some(row => row.textContent.includes('preflight') && row.textContent.includes('SSE connected'))",
            timeout=20000,
        )
        wait_same_revision(console, hmi, initial["revision"])
        assert console.locator("#sessionId").inner_text() == initial["session_id"]
        assert hmi.evaluate("window.AURI_HMI_NEXT.getState().viewModel.meta.sessionId") == initial["session_id"]
        assert hmi.evaluate("window.AURI_HMI_NEXT.getState().viewModel.tasks.total") == 0
        assert console.locator("#runCurrentStep").is_disabled()
        assert "等待手机" in console.locator("#nextStepHint").inner_text()

        # The real task enters through the same mobile /v1/chat API used by the
        # Android client. Console must detect the first task revision itself;
        # no manual "sync task" acknowledgement is allowed in this path.
        chat_events = mobile_chat("今天18:10接孩子，之后去超市", initial["session_id"])
        assert any(event.get("type") == "done" for event in chat_events)
        task_state = api("/v1/state")
        assert len(task_state["tasks"]) == 2
        wait_same_revision(console, hmi, task_state["revision"])
        assert "接孩子" in console.locator("#tasks").inner_text()
        assert "超市" in hmi.locator("#auri-responsibility-strip").inner_text()
        console.wait_for_function(
            "document.querySelector('#nextStepHint')?.textContent.includes('会议延迟')"
            " && !document.querySelector('#runCurrentStep')?.disabled",
            timeout=15000,
        )

        state = click_director_step(console, "pre_departure_warning")
        wait_same_revision(console, hmi, state["revision"])
        assert console.locator('button[data-action="vehicle"]').is_disabled()
        assert console.locator("#riskReasons").inner_text().strip()
        state = click_director_step(console, "handover_to_vehicle")
        wait_same_revision(console, hmi, state["revision"])
        state = click_director_step(console, "vehicle_observation")
        wait_same_revision(console, hmi, state["revision"])
        assert state["primary_surface"] == "vehicle_hmi"
        state = click_director_step(console, "takeover_L2")
        wait_same_revision(console, hmi, state["revision"])
        assert state["risk"]["late_minutes"] == 18
        assert "压力辅助信号" in console.locator("#nextStepHint").inner_text()
        assert console.locator('button[data-action="utterance"]').is_disabled()
        state = click_director_step(console, "takeover_L2")
        wait_same_revision(console, hmi, state["revision"])
        assert state["wearable"]["heart_rate"] == 120
        assert "手机语音求助" in console.locator("#nextStepHint").inner_text()
        assert console.locator('button[data-action="utterance"]').is_enabled()

        help_events = mobile_chat("我还来得及吗？帮我处理", state["session_id"])
        assert any(event.get("type") == "done" for event in help_events)
        wait_console_stage(console, "waiting_confirmation")
        state = api("/v1/state")
        wait_same_revision(console, hmi, state["revision"])
        assert "预计晚到 18 分钟" in hmi.locator("#auri-takeover-risk").inner_text()
        assert "我还来得及吗" in hmi.locator("#auri-driver-utterance").inner_text()
        assert hmi.locator("#auri-takeover-confirm").is_enabled()
        assert state["confirmation"]["confirmation_id"] in console.locator("#confirmationDetails").inner_text()
        assert "186" in console.locator("#serviceOrders").inner_text()
        assert "9 件" in console.locator("#serviceOrders").inner_text()
        if hmi.evaluate("window.AURI_HMI_NEXT.getState().map.status") == "online":
            hmi.wait_for_function(
                "Array.from(document.querySelectorAll('#auri-amap-canvas img'))"
                ".filter(image => image.complete && image.naturalWidth > 0).length >= 10",
                timeout=20000,
            )
        hmi.screenshot(path=SCREENSHOT_DIR / "waiting-confirmation-1920x720.png")

        hmi.locator("#auri-takeover-confirm").click()
        wait_console_stage(console, "action_completed")
        completed = api("/v1/state")
        wait_same_revision(console, hmi, completed["revision"])
        assert all(action["status"] == "completed" for action in completed["actions"])
        assert hmi.evaluate("window.__auriSpoken") == ["已处理，你按当前速度安全驾驶即可。"]

        # A standalone mobile AC instruction updates the shared vehicle state;
        # neither the Console nor HMI is allowed to keep a local copy.
        ac_events = mobile_chat("空调调到26度制冷大风", completed["session_id"])
        assert any(
            event.get("type") == "tool_call" and event.get("function", {}).get("name") == "control_ac"
            for event in ac_events
        )
        ac_state = api("/v1/state")
        assert ac_state["vehicle_state"] == {
            "ac_on": True,
            "ac_target_temp": 26.0,
            "ac_mode": "cool",
            "fan_speed": "high",
        }
        wait_same_revision(console, hmi, ac_state["revision"])
        assert "26°C" in console.locator("#vehicleState").inner_text()
        assert hmi.locator("#bbl").inner_text() == "26°C"
        assert hmi.locator("#bbr").inner_text() == "26°C"

        # Stop only the HMI client, advance the Agent from the Console, then
        # reconnect. The HMI must fetch the latest snapshot before streaming.
        hmi.evaluate("window.AURI_HMI_NEXT.disconnect()")
        hmi.wait_for_function("window.AURI_HMI_NEXT.getState().syncMode === 'stopped'")
        disconnected_revision = hmi.evaluate("window.AURI_HMI_NEXT.getState().viewModel.meta.revision")
        cooldown = click_director_step(console, "cooldown")
        console.wait_for_function(
            "revision => document.querySelector('#revision')?.textContent === `revision ${revision}`",
            arg=cooldown["revision"],
        )
        assert hmi.evaluate("window.AURI_HMI_NEXT.getState().viewModel.meta.revision") == disconnected_revision
        hmi.evaluate("window.AURI_HMI_NEXT.connect()")
        hmi.wait_for_function("window.AURI_HMI_NEXT.getState().syncMode === 'streaming'", timeout=20000)
        wait_same_revision(console, hmi, cooldown["revision"])

        parked = click_director_step(console, "parked_review")
        wait_same_revision(console, hmi, parked["revision"])
        assert parked["primary_surface"] == "mobile"
        assert not errors["console"], errors["console"]
        assert not errors["hmi"], errors["hmi"]

        print(json.dumps({
            "session_id": parked["session_id"],
            "initial_revision": initial["revision"],
            "final_revision": parked["revision"],
            "final_stage": parked["stage"],
            "tasks": len(parked["tasks"]),
            "actions": len(parked["actions"]),
            "vehicle_state": parked["vehicle_state"],
            "console_sync": console.locator("#syncMode").inner_text(),
            "hmi_sync": hmi.evaluate("window.AURI_HMI_NEXT.getState().syncMode"),
            "javascript_errors": errors,
            "screenshot": str(SCREENSHOT_DIR / "waiting-confirmation-1920x720.png"),
        }, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
