# AURI Active 2 Watch

A Zepp OS app for Amazfit Active 2 NFC (Round). The watch acts as a low-interruption wrist terminal for AURI: it displays compact takeover states, plays haptic feedback, reads a small health snapshot, and exchanges ACK/SENSOR/PONG messages with the phone Side Service.

## Target

- Device: Amazfit Active 2 NFC (Round)
- Zepp OS: 5.0
- API level: 4.2
- Screen: 466 x 466 round display
- Device sources: `8913152`, `8913153`, `10092800`, `10092801`

## Current Status

- AURI UI has six modes: `idle`, `warning`, `handover`, `processing`, `completed`, `error`.
- Short press on the `调试` button cycles local states and plays haptic feedback.
- Long press on the `调试` button reads a health snapshot and displays `HR / O2 / S` in the subtitle.
- Haptics have been tested on a real Active 2 device.
- Health snapshot display has been tested on a real Active 2 device.
- Local debug commands now generate a fresh `command_id` on each short press, so repeated debug cycles do not incorrectly show `duplicate`.
- Duplicate command handling is still preserved for real repeated `command_id` messages.
- The red `error` haptic no longer uses a continuous reminder scene; it uses bounded short pulses and explicitly stops.
- Side Service is registered and uses a lightweight raw BLE/Messaging JSON bridge instead of ZML.

## Protocol

`SET_STATE` from phone/Side Service to watch:

```json
{
  "command_id": "cmd-001",
  "mode": "warning",
  "icon": "!",
  "title": "风险提醒",
  "text": "请关注接管准备",
  "color": 15116032,
  "dimColor": 5061387,
  "haptic": "double_short",
  "duration_ms": 3000
}
```

`ACK` from watch to phone/Side Service:

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
- `warning`: notification-style double short vibration
- `handover`: one short vibration
- `processing`: three short pulses
- `completed`: gentle short vibration
- `error`: bounded strong/middle/strong pulse combo, then stop

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

## Debug Checklist

- Short press `调试`: cycles states and haptics.
- Long press `调试`: reads health data and updates subtitle as `HR x / O2 y / S z`.
- Check Zepp App Developer Mode logs for `ACK`, `SENSOR`, `PONG`, and Side Service messages.
- If preview download fails, confirm the phone can access the network, Zepp is not syncing/updating the watch, and the target device source includes `10092800`.

## Notes

- The simulator cannot validate real vibration or real health sensors.
- Strict "yesterday natural-day sleep" should be calculated by the phone or backend; the watch reports the sleep summary exposed by Zepp OS.
- P0 does not provide a driving confirmation action on the watch.
