# 跨端契约

> 当前 JSON Schema/OpenAPI 是早期 `0.1.0` 基线，尚未完整覆盖 2026-07-15 功能清单。不得因为现有 Schema 缺少字段，就在各端建立私有模型；先完成 v0.2 契约 PR。

## 权威边界

- 根目录功能清单定义目标对象、接口与验收。
- 本目录定义已冻结、可生成代码和可验证的机器契约。
- README/正式文档与当前 Schema 冲突时，暂停跨端实现，先由 Agent Owner 发起契约变更并邀请生产方/消费方评审。

## v0.2 必须覆盖

- 对象：`Task`、`WorldState`、`Event`、`Action`、`Confirmation`、`Profile`、`WearableState`、`ServiceOrder`。
- 状态：车内外 Scene、L0-L3/Recovery、主交互端、服务准备/执行、cooldown 和停车后复盘。
- 接口：事件上报、状态快照、实时流、确认、Profile 更新和 Session 重置。
- 幂等：`event_id`、`message_id`、`command_id`、`action_id`、`confirmation_id`、`order_id`。
- 生活服务：Capability、订单 preview/execute/status、缺货/超预算/过期/未授权错误。
- 输出调度：priority、owner surface、suppressed surfaces、expires at 和 requires confirmation。

## 当前通道草案

- `POST /v1/event`：各端上报事件。
- `GET /v1/state`：首次连接或重连获取完整 World State。
- `WS /v1/stream`：广播 state/output/action 更新；断线后以完整快照恢复。
- `POST /v1/confirm`：一次性消费 confirmation 下的动作组。
- `PUT /v1/profile`：更新显式 Profile 与生活服务规则。
- `POST /v1/session/reset`：重置标准 Demo Session。

当前 `openapi.yaml` 仍保留旧复数路径用于兼容参考，v0.2 评审时必须统一命名，不允许不同客户端分别实现 `/event` 与 `/events`。

## 契约规则

- 客户端不得直接设置最终 stage、压力等级或动作完成状态。
- 客户端只消费后端版本号更高的快照，重复事件不得重复执行。
- 腕上端可只消费 World State 子集，但字段和值域必须来自同一 Schema。
- 每次变更同时更新 Schema、OpenAPI、正向样例、错误样例和跨端固定场景。
- 删除/重命名字段属于破坏性变更；六周 Demo 内优先新增可选字段并保留兼容映射。
