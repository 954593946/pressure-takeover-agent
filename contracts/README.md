# 跨端契约 v0.1

`contracts/` 是手机、车机、控制台、Agent 服务和腕上网关之间的唯一对外字段定义。

## 通道

- `POST /v1/events`：所有客户端上报事件。
- `GET /v1/world-state`：首次连接或重连时获取完整快照。
- `GET /v1/stream`：SSE 状态流草案。
- `/v1/ws`：如采用 WebSocket，应发送同一份 World State，不再定义另一套消息模型。

## 规则

- 客户端不直接设置 `stage` 或 `agentMode`，它们由 Agent 状态机推导。
- 所有事件必须有唯一 `eventId`；用户确认还必须携带 `confirmationId`。
- `revision` 单调递增；客户端忽略小于等于本地 revision 的旧快照。
- 腕上端只需消费 World State 子集，但字段含义不能另起一套。
- 当前 payload 为了便于第一周联调保持开放；每个事件类型冻结后应增加对应的细分 Schema。

## 第一周必须共同评审的字段

- 事件类型命名和来源枚举。
- `stage` 与 `agentMode` 的区别。
- 腕上 `state/text/vibration` 的最小字段。
- `confirmationId` 的生成者、有效期和一次性消费语义。
- 重连后是发完整快照还是增量事件（v0.1 建议完整快照）。
