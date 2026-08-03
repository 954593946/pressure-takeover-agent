# 车载空调控制 — 手机车机联动对接说明

> 给手机、车机 HMI 与 Agent 联调负责人
> 初版：2026-07-27；更新：2026-08-03

## 一句话理解

手机自然语言控制和车机触控使用两条受控入口，但最终都由 Agent 写入同一个 `WorldState.vehicle_state`；手机与车机只按同一 `session_id + revision` 渲染结果，不允许客户端直接改本地状态冒充成功。

## 数据模型

### WorldState 新增字段：`vehicle_state`

```json
{
  "vehicle_state": {
    "ac_on": true,
    "ac_target_temp": 24.0,
    "ac_mode": "auto",
    "fan_speed": "medium"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `ac_on` | bool | 空调开关 |
| `ac_target_temp` | float | 目标温度 16-30°C |
| `ac_mode` | enum | `auto` / `cool` / `heat` / `fan` |
| `fan_speed` | enum | `low` / `medium` / `high` |

### 完整 WorldState 结构（新增最后一行）

```json
{
  "schema_version": "0.2.0",
  "session_id": "...",
  "revision": 42,
  "stage": "takeover_L2",
  "scene": "high_load_driving",
  "primary_surface": "vehicle_hmi",
  "risk": { ... },
  "tasks": [ ... ],
  "actions": [ ... ],
  "confirmation": null,
  "profile": { ... },
  "wearable": { ... },
  "service_orders": [ ... ],
  "output": { ... },
  "action_ledger": [ ... ],
  "vehicle_state": {       // ← 新增
    "ac_on": true,
    "ac_target_temp": 24.0,
    "ac_mode": "auto",
    "fan_speed": "medium"
  }
}
```

## 两条控制路径

### 路径 A：手机自然语言

```
用户在手机 Chat 说 "打开空调，26度，制冷，大风"
  │
  ▼
手机 Chat SSE → Agent（LLM 理解意图）
  │
  ▼
Agent 调用 control_ac 工具:
  ac_on=true, target_temp=26, mode="cool", fan_speed="high"
  │
  ▼
Agent 写入 WorldState.vehicle_state → revision++
  │
  ├─→ SSE 推送到手机 → VehicleScreen 卡片实时更新
  └─→ SSE 推送到车机 → HMI 渲染 AC 状态
```

### 路径 B：车机直接调节

```text
驾驶员在 HMI 调节温度、开关、模式或风量
  │
  ▼
HMI POST /v1/event，type=vehicle.control，source=vehicle_hmi
  │
  ▼
Agent 校验来源、字段、枚举和 16-30°C 温度范围
  │
  ▼
Agent 写入 WorldState.vehicle_state → revision++
  │
  ├─→ SSE 推送到手机 → VehicleScreen 显示相同状态
  └─→ SSE 推送到车机 → 控件显示“设置已同步”
```

两条路径共享同一个状态真相。HMI 在请求完成前只保留“待提交草稿”，不提前修改已同步状态；请求失败时显示错误并继续展示上一版 World State。

## 车机要做什么

1. **订阅 SSE**：使用现有 `/v1/stream`，接收包含 `vehicle_state` 的完整快照。
2. **读取字段**：读取 `ws.vehicle_state.ac_on`、`ac_target_temp`、`ac_mode` 和 `fan_speed`。
3. **提供安全控件**：开关、温度步进、模式分段、风量分段和一个“应用设置”主按钮。
4. **提交标准 Event**：通过 `POST /v1/event` 提交 `vehicle.control`，不能直接写 World State。
5. **状态对账**：消费接口返回快照并继续订阅 SSE；只接受当前 Session 的更高 revision。

### 不要做

- ❌ 不要在 HMI 端直接修改 WorldState——HMI 通过提交 Event 间接影响状态
- ❌ 不要让 LLM 决定温度范围——`control_ac` 工具已经用 Pydantic `Field(ge=16, le=30)` 做了硬约束
- ❌ 不要把空调状态写进“现实结论”——它属于座舱状态，不应干扰驾驶接管判断
- ❌ 不要声称空调设置会同步腕表——当前契约只保证共享 World State 同步手机与车机

车机语音“太热了”等自然语言入口仍可通过 `user.utterance` 扩展；本轮直接触控已经使用结构化 `vehicle.control`，不依赖 LLM 可用性。

## `vehicle.control` 事件契约

```json
{
  "schema_version": "0.2.0",
  "event_id": "evt_vehicle_control_20260803_001",
  "session_id": "happy-path_ab12cd34",
  "type": "vehicle.control",
  "source": "vehicle_hmi",
  "timestamp": "2026-08-03T14:30:00+08:00",
  "payload": {
    "ac_on": true,
    "ac_target_temp": 23.5,
    "ac_mode": "cool",
    "fan_speed": "high"
  }
}
```

- `source` 必须是 `vehicle_hmi`。
- `payload` 至少包含一个允许字段；不接受未知字段。
- 温度必须在 16-30°C；模式仅允许 `auto/cool/heat/fan`；风量仅允许 `low/medium/high`。
- 相同 `event_id` 重试返回 `duplicate=true`，不会再次增加 revision。
- 失败返回结构化错误，HMI 不显示假成功。

## Agent 工具签名

```python
@tool
def control_ac(
    ac_on: bool | None = None,      # 开关
    target_temp: float | None = None, # 温度 16-30
    mode: str | None = None,          # auto/cool/heat/fan
    fan_speed: str | None = None,     # low/medium/high
) -> dict:
    """控制车载空调；写入共享 WorldState"""
```

工具返回值示例：

```json
{
  "ok": true,
  "ac_on": true,
  "target_temp": 26.0,
  "mode": "cool",
  "fan_speed": "high",
  "summary": "空调已开启，26°C，制冷，风量高"
}
```

## 确定性约束（无需 HMI 参与）

| 规则 | 实现位置 | 说明 |
|------|----------|------|
| 温度范围 16-30°C | Agent 工具参数 + Runtime | 超出范围拒绝 |
| 模式必须是 4 个枚举值之一 | Agent 工具参数 + Runtime | 无效值拒绝 |
| 风量必须是 3 个枚举值之一 | Agent 工具参数 + Runtime | 无效值拒绝 |
| Event 来源 | Runtime | `vehicle.control` 只接受 `vehicle_hmi` |
| Event 幂等 | Runtime Ledger | 相同 `event_id` 不重复执行 |
| AC 开启时离车场景切换 | Agent 工具 + Runtime | `scene: off_vehicle → approaching_vehicle` |
| 权限无关 | N/A | AC 控制不影响 L0-L3、primary_surface、confirmation owner |

## 测试方式

### 手机自然语言

在手机 Chat 发送以下自然语言，观察手机和车机：

| 输入 | 预期 vehicle_state |
|------|--------------------|
| "打开空调" | `ac_on: true, temp: 24, mode: auto, fan: medium` |
| "空调调到 26 度制冷大风" | `ac_on: true, temp: 26, mode: cool, fan: high` |
| "关空调" | `ac_on: false` |
| "太热了开制热 28 度" | `ac_on: true, temp: 28, mode: heat, fan: medium` |

### 车机结构化控制

1. 手机和 HMI 连接同一个 Agent URL、Session。
2. 在 HMI 底部进入“座舱”，调整温度、模式和风量，点击“应用设置”。
3. 请求 `GET /v1/state`，确认 `vehicle_state` 与 HMI 一致且 revision 增加一次。
4. 确认手机 VehicleScreen 在相同 revision 下显示一致值。
5. 用同一 `event_id` 重放请求，确认 `duplicate=true` 且 revision 不再增加。

## 相关文件

| 文件 | 说明 |
|------|------|
| `services/agent-api/src/auri_agent/models.py` | `VehicleState` 模型定义 |
| `services/agent-api/src/auri_agent/runtime.py` | `vehicle.control` 校验、写入和幂等 |
| `contracts/event.schema.json` | 标准 Event 类型清单 |
| `services/agent-api/src/auri_agent/tools.py` | `control_ac` 工具 + `AgentToolbox.control_ac()` |
| `apps/vehicle-hmi/src/agent-client.js` | HMI 标准 Event 提交 |
| `apps/vehicle-hmi/auri-shell.js` | 座舱控件、草稿与状态对账 |
| `apps/mobile/.../domain/model/WorldState.kt` | `VehicleControl` + `AcMode` + `FanSpeed` |
| `apps/mobile/.../ui/vehicle/VehicleScreen.kt` | AC 控制卡片 UI |
| `apps/mobile/.../ui/vehicle/VehicleViewModel.kt` | AC 状态从 WorldState 读取 |
| `apps/mobile/.../ui/chat/ChatScreen.kt` | "打开空调"快捷 chip |

## 联调验收

1. 手机发送"打开空调 26 度"
2. 确认车机 HMI 在同一 WorldState revision 下显示 `ac_on: true, temp: 26`
3. 车机端确认 SSE 推送延迟 < 1s
4. 车机端确认 revision 单调递增
5. 手机和车机关闭空调时两端同步
6. 车机调节 23.5°C、制冷、高风量，确认手机同 revision 更新
7. 车机提交非法温度或非车机来源，确认 Agent 拒绝且 World State 不变
