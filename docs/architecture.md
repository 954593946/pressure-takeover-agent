# 系统架构

## 原则

1. `services/agent-api` 是 World State 的唯一写入者。
2. 手机、车机、腕上设备和控制台只上报事件，不直接互相改状态。
3. 所有生产方使用统一事件信封；所有消费方渲染同一份 World State 的子集。
4. 数学计算和权限判断不交给 LLM：ETA 偏差、最晚出发、确认幂等和越权拦截由确定性代码负责。
5. LLM 用于语义理解、责任判断、消息和短话术生成，并必须经过结构化输出和安全校验。

## 数据流

```text
mobile / vehicle-hmi / wearable / demo-console
                     |
                     | POST /v1/events
                     v
                 agent-api
      event validation -> state machine -> policy
              -> optional LLM -> safety gate
                     |
                     | WebSocket / SSE
                     v
mobile UI / vehicle HMI / wearable gateway / console log
```

腕上设备可以直连后端，也可以通过手机网关转发；无论链路如何，进入 Agent 的事件格式不变。

## 状态机

主流程按以下顺序推进：

```text
idle
  -> task_created
  -> meeting_delay
  -> departure_warning
  -> vehicle_mode
  -> traffic_delay
  -> pressure_takeover
  -> waiting_confirmation
  -> action_completed
  -> role_transition (P1)
```

控制台只能注入“会议延迟、车辆、路况、辅助信号”等外部事件。是否进入 `pressure_takeover`、哪些动作可自动执行、是否需要确认，由 Agent 后端决定。

## 安全门

- 低风险动作（例如后置超市）可自动执行。
- 中风险动作（例如通知家人晚到）需要简单确认。
- 高风险动作（例如联系老师、共享位置、改变接送人）必须明确确认。
- `confirmation_id` 只能成功消费一次；语音和按钮并发提交也只执行一次。
- 主动介入至少需要“现实风险成立 + 一个用户求助或辅助信号”。心率不得作为单点依据。

## 接口版本

- 当前契约版本：`0.1.0`。
- HTTP 接口前缀：`/v1`。
- 事件和 World State 都携带 `schemaVersion`。
- 破坏性契约变更需要新增版本；六周 Demo 内优先使用向后兼容的可选字段。
