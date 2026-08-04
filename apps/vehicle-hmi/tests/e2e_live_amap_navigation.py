"""Validate live AMap follow/overview cameras without committing credentials."""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone, timedelta
from pathlib import Path
from urllib import request
from uuid import uuid4

from playwright.sync_api import sync_playwright


AGENT = os.getenv("AURI_AGENT_URL", "http://127.0.0.1:8795").rstrip("/")
TOKEN = os.getenv("AURI_AGENT_TOKEN", "")
HMI = os.getenv("AURI_HMI_URL", "http://127.0.0.1:5174/apps/vehicle-hmi/")
AMAP_KEY = os.environ["AURI_AMAP_KEY"]
AMAP_SECURITY = os.environ["AURI_AMAP_SECURITY"]
CHROME = os.getenv(
    "PLAYWRIGHT_CHROMIUM_EXECUTABLE",
    str(Path.home() / ".cache/ms-playwright/chromium-1228/chrome-linux64/chrome"),
)
OUTPUT = Path(os.getenv("AURI_AMAP_VISUAL_DIR", "/tmp/auri-live-amap-navigation"))
TZ = timezone(timedelta(hours=8))


def api(path: str, method: str = "GET", payload: dict | None = None) -> dict:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode()
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if TOKEN:
        headers["X-Agent-Token"] = TOKEN
    req = request.Request(f"{AGENT}{path}", method=method, data=body, headers=headers)
    with request.urlopen(req, timeout=20) as response:
        return json.loads(response.read().decode())


def submit(event_type: str, payload: dict, source: str = "demo_console") -> None:
    state = api("/v1/state")
    api("/v1/event", "POST", {
        "schema_version": "0.2.0",
        "event_id": f"live_amap_{event_type.replace('.', '_')}_{uuid4().hex[:8]}",
        "session_id": state["session_id"],
        "type": event_type,
        "source": source,
        "timestamp": datetime.now(TZ).isoformat(),
        "payload": payload,
    })


def main() -> None:
    if AGENT not in {"http://127.0.0.1:8795", "http://localhost:8795"}:
        raise SystemExit("Live AMap test only resets the isolated local Agent on port 8795.")
    api("/v1/session/reset", "POST", {"scenario_id": "live-amap-navigation"})
    submit("task.created", {"text": "今天18:10接孩子，之后去超市"}, "mobile")
    submit("meeting.overrun", {"delay_minutes": 20})
    submit("scene.vehicle_entered", {})
    OUTPUT.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=CHROME, headless=True)
        page = browser.new_page(viewport={"width": 1366, "height": 768})
        errors: list[str] = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        config = json.dumps({
            "apiBase": AGENT,
            "token": TOKEN,
            "stream": True,
            "mapProvider": "amap",
            "amapKey": AMAP_KEY,
            "amapSecurityJsCode": AMAP_SECURITY,
        })
        page.add_init_script(
            f"window.AURI_HMI_CONFIG={config};"
            "try{localStorage.removeItem('auri-hmi-next-config')}catch(_e){};"
        )
        page.goto(HMI, wait_until="domcontentloaded", timeout=30000)
        try:
            page.wait_for_function("window.AURI_HMI_NEXT?.getState().map.status === 'online'", timeout=30000)
        except Exception:
            diagnostic = page.evaluate("window.AURI_HMI_NEXT?.getState() || null")
            page.screenshot(path=str(OUTPUT / "connection-failure.png"))
            raise AssertionError(f"AMap did not become online: {json.dumps(diagnostic, ensure_ascii=False)}")
        page.wait_for_function("window.AURI_HMI_NEXT?.getState().viewModel.lifecycle.stage === 'vehicle_observation'", timeout=30000)
        page.wait_for_timeout(2400)

        page.locator('[data-map-control="follow"]').click()
        page.wait_for_timeout(900)
        follow = page.evaluate("window.AURI_HMI_NEXT.getState().map")
        follow_label = page.locator('[data-map-control="follow"] span').inner_text().strip()
        follow_boxes = page.evaluate("""() => {
          const canvas=document.querySelector('#auri-amap-canvas').getBoundingClientRect();
          const maps=document.querySelector('#auri-amap-canvas .amap-maps').getBoundingClientRect();
          return {
            canvas:{x:canvas.x,y:canvas.y,width:canvas.width,height:canvas.height},
            maps:{x:maps.x,y:maps.y,width:maps.width,height:maps.height},
            transform:getComputedStyle(document.querySelector('#auri-amap-canvas .amap-maps')).transform
          };
        }""")
        timing = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback")
        page.screenshot(path=str(OUTPUT / "follow.png"))

        page.locator('[data-map-control="overview"]').click()
        page.wait_for_timeout(900)
        overview = page.evaluate("window.AURI_HMI_NEXT.getState().map")
        overview_transform = page.evaluate("getComputedStyle(document.querySelector('#auri-amap-canvas .amap-maps')).transform")
        page.screenshot(path=str(OUTPUT / "overview.png"))
        assert follow["cameraMode"] == "follow", follow
        assert overview["cameraMode"] == "overview", overview
        assert follow["motionMethod"] == "moveAlong", follow
        assert follow_label == ("3D 跟车" if follow["rendering3d"] == "native" else "跟车视角"), follow_label
        assert timing["mapMotionDurationMs"] < timing["tickIntervalMs"], timing
        assert follow["motion"]["plannedDurationMs"] <= timing["mapMotionDurationMs"], (follow, timing)
        assert follow["motion"]["overlapCount"] == 0, follow
        assert follow["motion"]["completedCount"] > 0, follow
        if follow["rendering3d"] == "native":
            assert follow["cameraPitch"] >= 50 and overview["cameraPitch"] <= 20, (follow, overview)
        else:
            assert follow_boxes["transform"] != overview_transform, (follow_boxes, overview_transform)
        assert not errors, errors
        print(json.dumps({
            "follow": follow,
            "overview": overview,
            "followBoxes": follow_boxes,
            "followLabel": follow_label,
            "overviewTransform": overview_transform,
            "timing": timing,
            "screenshots": [str(OUTPUT / "follow.png"), str(OUTPUT / "overview.png")],
            "javascriptErrors": errors,
        }, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
