# 跨端契约 v0.2 候选基线

本目录是手机、车机、腕上、控制台和 Agent 的唯一机器契约来源。当前候选版覆盖 `Task`、`Event`、`WorldState`、`Action`、`Confirmation`、`Profile`、`WearableState`、`VehicleState`、`UtteranceState` 和 `ServiceOrder`。

## 文件

- `event.schema.json`：统一事件信封、来源和 P0 事件名。
- `world-state.schema.json`：共享对象、L0-L3、交互所有权、车辆状态、订单和 Ledger。
- `openapi.yaml`：事件、状态、SSE、确认、Profile 和 Session 重置接口。
- `examples/`：可由 JSON Schema 自动校验的正向样例。
- `../packages/test-fixtures/happy-path.events.json`：跨端标准事件序列。

## 冻结流程

1. Agent Owner 发起契约 PR。
2. 至少一个事件生产方和一个状态消费方共同评审。
3. 运行 `scripts/test-agent.ps1`，确认 Schema 样例和后端测试通过。
4. 在 PR 中写清兼容性、迁移方式和受影响模块。

客户端不得直接设置最终 `stage`、压力等级或动作状态。客户端只消费更高 `revision` 的完整快照；重复 `event_id`、`confirmation_id`、`action_id` 和 `order_id` 不得重复执行。

## UtteranceState

手机端完成 ASR 后提交 `user.utterance`，Agent 将最近一次转写保存到 `WorldState.last_utterance`。车机只读消费该字段并随 revision 更新，不提供车机语音输入。字段包含 `text`、`source`、`input_mode` 和 `received_at`；旧状态没有该字段时客户端显示“等待用户在手机端求助”。

## VehicleState

`vehicle_state` 是 Agent 写入、手机端和车机端只读消费的共享车辆状态，随每个完整 `WorldState` 快照返回：

- `ac_on`：空调开关，默认 `false`。
- `ac_target_temp`：目标温度，范围为 16-30°C，默认 `24`。
- `ac_mode`：空调模式，可取 `auto`、`cool`、`heat`、`fan`，默认 `auto`。
- `fan_speed`：风量，可取 `low`、`medium`、`high`，默认 `medium`。

该对象及其四个字段都是完整快照的必填内容，并与后端 `VehicleState` 的 `extra="forbid"` 行为一致：对象中出现未声明字段会校验失败。后端仍使用 `schema_version: "0.2.0"`；宽松客户端可直接兼容新增字段，使用旧版 `additionalProperties: false` Schema 的严格客户端必须先更新本目录契约，否则会拒绝包含 `vehicle_state` 的实际响应。
