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


def submit(event_type: str, payload: dict, source: str = "demo_console") -> dict:
    state = api("/v1/state")
    return api("/v1/event", "POST", {
        "schema_version": "0.2.0",
        "event_id": f"live_amap_{event_type.replace('.', '_')}_{uuid4().hex[:8]}",
        "session_id": state["session_id"],
        "type": event_type,
        "source": source,
        "timestamp": datetime.now(TZ).isoformat(),
        "payload": payload,
    })["state"]


def follow_metrics(page) -> dict:
    return page.evaluate("""() => {
      const canvasNode=document.querySelector('#auri-amap-canvas');
      const mapsNode=canvasNode?.querySelector('.amap-maps');
      const fixedNode=document.querySelector('.auri-amap-fixed-vehicle');
      const fixedRing=fixedNode?.querySelector('span');
      const actualVehicle=document.querySelector('.auri-amap-vehicle');
      const originNode=document.querySelector('.auri-amap-origin');
      const destinationNode=document.querySelector('.auri-amap-destination');
      const canvas=canvasNode?.getBoundingClientRect();
      const maps=mapsNode?.getBoundingClientRect();
      const fixed=fixedNode?.getBoundingClientRect();
      const actual=actualVehicle?.getBoundingClientRect();
      const origin=originNode?.getBoundingClientRect();
      const destination=destinationNode?.getBoundingClientRect();
      const chevrons=[...document.querySelectorAll('.auri-amap-chevron')]
        .map(node => {
          const rect=node.getBoundingClientRect();
          const style=getComputedStyle(node);
          return {x:rect.x,y:rect.y,width:rect.width,height:rect.height,display:style.display,opacity:Number(style.opacity)};
        })
        .filter(item => item.display !== 'none' && item.width > 0 && item.height > 0 && item.opacity > 0);
      const fixedStyle=fixedNode ? getComputedStyle(fixedNode) : null;
      const ringStyle=fixedRing ? getComputedStyle(fixedRing) : null;
      return {
        canvas:{x:canvas?.x,y:canvas?.y,width:canvas?.width,height:canvas?.height},
        maps:{x:maps?.x,y:maps?.y,width:maps?.width,height:maps?.height},
        transform:mapsNode ? getComputedStyle(mapsNode).transform : 'none',
        fixed:{x:fixed?.x,y:fixed?.y,width:fixed?.width,height:fixed?.height},
        fixedDisplay:fixedStyle?.display || 'none',
        fixedCenterRatio:{
          x:canvas && fixed ? (fixed.x + fixed.width / 2 - canvas.x) / canvas.width : null,
          y:canvas && fixed ? (fixed.y + fixed.height / 2 - canvas.y) / canvas.height : null
        },
        fixedRingAnimation:ringStyle?.animationName || 'none',
        actualVehicle:{
          width:actual?.width || 0,
          height:actual?.height || 0,
          display:actualVehicle ? getComputedStyle(actualVehicle).display : 'none',
          opacity:actualVehicle ? Number(getComputedStyle(actualVehicle).opacity) : 0
        },
        overviewMarkers:{
          origin:origin ? {x:origin.x,y:origin.y,width:origin.width,height:origin.height} : null,
          destination:destination ? {x:destination.x,y:destination.y,width:destination.width,height:destination.height} : null
        },
        vehicleMotion:document.querySelector('.right-panel')?.dataset.vehicleMotion || '',
        chevrons
      };
    }""")


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
        page.wait_for_function("document.querySelector('.right-panel')?.dataset.vehicleMotion === 'moving'", timeout=5000)
        moving_before = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")
        moving_metrics = follow_metrics(page)
        page.screenshot(path=str(OUTPUT / "moving-follow.png"))
        page.wait_for_timeout(900)
        moving_after = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")
        timing = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback")

        state = api("/v1/state")
        rigid = next(task for task in state["tasks"] if task.get("task_type") == "rigid")
        scheduled = datetime.fromisoformat(rigid["scheduled_at"])
        submit("traffic.updated", {
            "eta": (scheduled + timedelta(minutes=18)).isoformat(),
            "late_minutes": 18,
        })
        page.wait_for_function("window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'takeover_L2'", timeout=15000)
        page.wait_for_timeout(900)
        stopped_before = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")
        page.wait_for_timeout(1300)
        stopped_after = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")
        stopped_metrics = follow_metrics(page)
        page.screenshot(path=str(OUTPUT / "stopped-follow.png"))

        submit("user.utterance", {"text": "我还来得及吗？帮我处理", "input_mode": "voice"}, "mobile")
        page.wait_for_function("window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'waiting_confirmation'", timeout=15000)
        page.locator("#auri-takeover-confirm").click()
        page.wait_for_function("window.AURI_HMI_NEXT.getState().viewModel.lifecycle.stage === 'action_completed'", timeout=15000)
        page.wait_for_function("window.AURI_HMI_NEXT.getState().drivePlayback.speedKph >= 20", timeout=5000)
        page.wait_for_function("document.querySelector('.right-panel')?.dataset.vehicleMotion === 'moving'", timeout=5000)
        resumed_before = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")
        resumed_metrics = follow_metrics(page)
        page.screenshot(path=str(OUTPUT / "resumed-follow.png"))
        page.wait_for_timeout(900)
        resumed_after = page.evaluate("window.AURI_HMI_NEXT.getState().drivePlayback.progress")

        authoritative_progress = float(api("/v1/state")["navigation"]["progress"])
        page.locator('[data-map-control="overview"]').click()
        page.wait_for_timeout(900)
        overview = page.evaluate("window.AURI_HMI_NEXT.getState().map")
        overview_metrics = follow_metrics(page)
        overview_transform = overview_metrics["transform"]
        page.screenshot(path=str(OUTPUT / "overview.png"))
        assert follow["cameraMode"] == "follow", follow
        assert overview["cameraMode"] == "overview", overview
        assert follow["motionMethod"] == "moveAlong", follow
        assert follow_label == ("3D 跟车" if follow["rendering3d"] == "native" else "跟车视角"), follow_label
        assert moving_after > moving_before, (moving_before, moving_after)
        assert abs(stopped_after - stopped_before) < 0.001, (stopped_before, stopped_after)
        assert resumed_before < 0.55, resumed_before
        assert resumed_after > resumed_before, (resumed_before, resumed_after)
        assert timing["mapMotionDurationMs"] < timing["tickIntervalMs"], timing
        assert follow["motion"]["plannedDurationMs"] <= timing["mapMotionDurationMs"], (follow, timing)
        assert follow["motion"]["overlapCount"] == 0, follow
        assert follow["motion"]["completedCount"] > 0, follow
        assert overview["motion"]["overlapCount"] == 0, overview
        assert abs(overview["motion"]["markerProgress"] - authoritative_progress) < 0.001, (overview, authoritative_progress)
        if follow["rendering3d"] == "native":
            assert follow["cameraPitch"] >= 50 and overview["cameraPitch"] <= 20, (follow, overview)
        else:
            ratio = moving_metrics["fixedCenterRatio"]
            assert follow["cameraPitch"] >= 35, follow
            assert moving_metrics["transform"] != "none", moving_metrics
            assert moving_metrics["transform"] != overview_transform, (moving_metrics, overview_transform)
            assert moving_metrics["fixedDisplay"] == "grid", moving_metrics
            assert 0.45 <= ratio["x"] <= 0.55 and 0.72 <= ratio["y"] <= 0.86, moving_metrics
            assert moving_metrics["fixedRingAnimation"] != "none", moving_metrics
            assert stopped_metrics["fixedRingAnimation"] == "none", stopped_metrics
            assert resumed_metrics["fixedRingAnimation"] != "none", resumed_metrics
            assert stopped_metrics["vehicleMotion"] == "stopped", stopped_metrics
            assert overview_metrics["fixedDisplay"] == "none", overview_metrics
            assert overview_metrics["actualVehicle"]["width"] > 0 and overview_metrics["actualVehicle"]["opacity"] > 0, overview_metrics
            for marker in overview_metrics["overviewMarkers"].values():
                assert marker and marker["width"] > 0 and marker["height"] > 0, overview_metrics
                marker_center_x = marker["x"] + marker["width"] / 2
                marker_center_y = marker["y"] + marker["height"] / 2
                canvas = overview_metrics["canvas"]
                assert canvas["x"] <= marker_center_x <= canvas["x"] + canvas["width"], overview_metrics
                assert canvas["y"] <= marker_center_y <= canvas["y"] + canvas["height"], overview_metrics
            fixed_y = moving_metrics["fixed"]["y"] + moving_metrics["fixed"]["height"] / 2
            assert sum(1 for item in moving_metrics["chevrons"] if item["y"] < fixed_y) >= 2, moving_metrics
            resumed_fixed_y = resumed_metrics["fixed"]["y"] + resumed_metrics["fixed"]["height"] / 2
            assert sum(1 for item in resumed_metrics["chevrons"] if item["y"] < resumed_fixed_y) >= 2, resumed_metrics
        assert not errors, errors
        print(json.dumps({
            "follow": follow,
            "overview": overview,
            "authoritativeOverviewProgress": authoritative_progress,
            "motionProgress": {
                "moving": [moving_before, moving_after],
                "stopped": [stopped_before, stopped_after],
                "resumed": [resumed_before, resumed_after],
            },
            "movingMetrics": moving_metrics,
            "stoppedMetrics": stopped_metrics,
            "resumedMetrics": resumed_metrics,
            "overviewMetrics": overview_metrics,
            "followLabel": follow_label,
            "overviewTransform": overview_transform,
            "timing": timing,
            "screenshots": [
                str(OUTPUT / "moving-follow.png"),
                str(OUTPUT / "stopped-follow.png"),
                str(OUTPUT / "resumed-follow.png"),
                str(OUTPUT / "overview.png"),
            ],
            "javascriptErrors": errors,
        }, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
