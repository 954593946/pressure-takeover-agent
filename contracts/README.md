# 跨端契约 v0.2 候选基线

本目录是手机、车机、腕上、控制台和 Agent 的唯一机器契约来源。当前候选版覆盖 `Task`、`Event`、`WorldState`、`Action`、`Confirmation`、`Profile`、`WearableState` 和 `ServiceOrder`。

## 文件

- `event.schema.json`：统一事件信封、来源和 P0 事件名。
- `world-state.schema.json`：八个共享对象、L0-L3、交互所有权、订单和 Ledger。
- `openapi.yaml`：事件、状态、SSE、确认、Profile 和 Session 重置接口。
- `examples/`：可由 JSON Schema 自动校验的正向样例。
- `../packages/test-fixtures/happy-path.events.json`：跨端标准事件序列。

## 冻结流程

1. Agent Owner 发起契约 PR。
2. 至少一个事件生产方和一个状态消费方共同评审。
3. 运行 `scripts/test-agent.ps1`，确认 Schema 样例和后端测试通过。
4. 在 PR 中写清兼容性、迁移方式和受影响模块。

客户端不得直接设置最终 `stage`、压力等级或动作状态。客户端只消费更高 `revision` 的完整快照；重复 `event_id`、`confirmation_id`、`action_id` 和 `order_id` 不得重复执行。
