# Active 2 Pressure Watch

> 开发前先阅读根 [README](../../../README.md) 的 AURI 品牌、腕上状态和触觉基线。当前代码中的 `EMO`、旧状态名和颜色是待迁移的原型内容，不得继续扩散。

A minimal Zepp OS app framework for the Amazfit Active 2 round watch demo.

## Current Scope

- Static simulator-ready status page.
- Active 2 round layout at 466 x 466.
- Local mock state and shared state mapping.
- Reserved hooks for Side Service sync, vibration, and health signals.

Target P0 states: `idle`, `warning`, `handover`, `processing`, `completed`, `error`. Each command must carry a `command_id` and receive an idempotent ACK; repeated commands must not repeat vibration.

## Commands

```sh
zeus dev
zeus build
zeus preview
```

Run commands from this directory.
