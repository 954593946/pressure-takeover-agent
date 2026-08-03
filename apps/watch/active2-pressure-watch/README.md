# AURI Active 2 Watch

A Zepp OS app for Amazfit Active 2 NFC (Round). The watch is a low-interruption wrist terminal for AURI: it displays compact takeover states, plays haptic feedback, reads a small health snapshot, and exchanges ACK/SENSOR/PONG messages with the phone Side Service.

## Target

- Device: Amazfit Active 2 NFC (Round)
- Zepp OS: 5.0
- API level: 4.2
- Screen: 466 x 466 round display
- Device sources: `8913152`, `8913153`, `10092800`, `10092801`

## Current Status

- AURI UI has six modes: `idle`, `warning`, `handover`, `processing`, `completed`, `error`.
- `idle`, `handover`, and `processing` are visually distinct: steady blue, cyan handover halo, and purple-blue breathing processing.
- The watch page has no local debug button. Demo state changes must come from the phone through `watch.setState`.
- The bottom debug text has been removed from the watch UI.
- The watch icon matches the mobile AURI launcher icon.
- Demo keep-awake is enabled while the page is open: page bright time is extended, drop-wrist/palm screen-off is paused, and wake-up relaunch is enabled.
- Haptic patterns use count-based bounded pulse sequences and explicitly stop.
- `completed` shows the green completion state, then returns to `idle` locally after about 5 seconds without a new ACK or vibration.
- Health snapshots are still available through remote `watch.sensorRequest`.
- Duplicate command handling is preserved for repeated remote `command_id` messages.
- Side Service uses Zepp ZML as the P0 Bluetooth bridge. The experimental raw BLE bridge remains in `utils/raw-bridge.js`, but it is not started on the P0 path.
- Final demo state changes are driven by Agent `WorldState` updates through the Android Wearable Gateway.

## Protocol

Formal state changes come from the phone-side gateway via Zepp app-side:

```text
Android Wearable Gateway / Agent
  -> Zepp app-side
  -> watch.setState
  -> AURI Watch render + haptic
  -> watch.ack
```

`SET_STATE` from phone/Side Service to watch:

```json
{
  "command_id": "cmd-001",
  "mode": "warning",
  "icon": "!",
  "title": "Risk alert",
  "text": "Departure window tightening",
  "color": 15116032,
  "dimColor": 5061387,
  "haptic": "double_short",
  "duration_ms": 3000
}
```

`ACK` from watch to phone/Side Service. ACK is only emitted for remote `watch.setState` commands:

```json
{
  "type": "ACK",
  "command_id": "cmd-001",
  "result": "ok",
  "reason": "",
  "timestamp": 1720000000000
}
```

`SENSOR` from watch to phone/Side Service:

```json
{
  "type": "SENSOR",
  "heart_rate": 92,
  "spo2": 97,
  "sleep_minutes_yesterday": 420,
  "sleep_score": 82,
  "deep_sleep_minutes": 96,
  "worn": true,
  "battery": 81,
  "confidence": "device",
  "timestamp": 1720000000,
  "result": "ok"
}
```

`PING/PONG` are used for heartbeat. If the watch does not receive a Side Service message for about 45 seconds, it shows `请看手机 / 连接已中断` without repeated vibration.

## Haptic Mapping

- `idle`: no vibration
- `completed` / `soft_short` / `gentle_short`: 1 pulse
- `handover` / `single_pulse` / `single_short`: 2 pulses
- `warning` / `double_short`: 3 pulses
- `processing` / `three_beat` / `triple`: 4 pulses
- `error` / `error_once` / `error_combo`: 5 pulses

## Final Demo Behavior

- `pre_departure_warning / L1`: yellow `warning`, 3-pulse vibration.
- `handover_to_vehicle`, `vehicle_observation`: cyan `handover` visual state, no Agent-driven vibration.
- `takeover_L2`, `planning`, `waiting_confirmation`: purple-blue breathing `processing` visual state, no Agent-driven vibration.
- `action_completed`: green `completed`, 1-pulse vibration, then local auto-return to `idle` after about 5 seconds.
- `cooldown` and `parked_review`: low-interruption `idle`, no vibration.
- The watch never owns confirmation in the demo; confirmation remains on `vehicle_hmi`.

## Commands

Run commands from this directory:

```sh
zeus build -t "Amazfit Active 2 NFC (Round)"
zeus dev -t "Amazfit Active 2 NFC (Round)"
zeus preview -t "Amazfit Active 2 NFC (Round)"
```

## Real Device Preview

1. Open Zepp App and bind the Active 2 watch.
2. Enable Developer Mode in Zepp App.
3. Run `zeus preview -t "Amazfit Active 2 NFC (Round)"`.
4. Scan the QR code in Zepp App Developer Mode.
5. Keep the phone and watch connected while the package downloads and installs.

## Demo Checklist

- Keep the watch page open during the demo; it should remain bright beyond the normal 30s timeout.
- Use the Android app "Wearable device" debug controls for manual state/haptic checks.
- Request health data from the Android app; the watch should return `SENSOR` and update the subtitle as `HR x / O2 y / S z`.
- Check Zepp App Developer Mode logs for `ACK`, `SENSOR`, `PONG`, and Side Service messages.
- If preview download fails, confirm the phone can access the network, Zepp is not syncing/updating the watch, and the target device source includes `10092800`.

## Notes

- The simulator cannot validate real vibration, real health sensors, or the physical keep-awake behavior.
- Zepp OS may still enforce platform-level power rules outside the app's control; wake-up relaunch is enabled as a fallback.
- P0 does not provide a driving confirmation action on the watch.
