# 车载空调控制 — 手机车机联动对接说明

> 给车机 HMI 负责人  
> 2026-07-27

## 一句话理解

用户在手机 Chat 里说"打开空调 26 度"→ Agent 调用 `control_ac` 工具 → 写入 WorldState → **手机和车机通过 SSE 同时看到空调状态变化**。不需要车机自己实现空调逻辑，也不需要手机直接控制车辆硬件。

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

## 工作流程

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

## 车机要做什么

### 仅需渲染，不需实现控制逻辑

1. **订阅 SSE**：和现有 state stream 同一个端点（`/v1/stream`），`vehicle_state` 字段已经包含在推送中
2. **读取字段**：`ws.vehicle_state.ac_on` / `ac_target_temp` / `ac_mode` / `fan_speed`
3. **渲染 UI**：参考手机端 VehicleScreen 的 AC 卡片设计（见下方截图区域）

### 不要做

- ❌ 不要自己创建空调开关按钮——控制指令应该通过 Agent 工具链，确保确定性规则（温度范围限制、场景限制等）生效
- ❌ 不要在 HMI 端直接修改 WorldState——HMI 通过提交 Event 间接影响状态
- ❌ 不要让 LLM 决定温度范围——`control_ac` 工具已经用 Pydantic `Field(ge=16, le=30)` 做了硬约束

### 可以做（后续）

- 车机可以提交 `user.utterance` 事件（如"太热了"），Agent 会自行决定是否调用 `control_ac`
- 如需直接控制（如 HMI 物理按钮），应提交结构化 `vehicle.control` 事件（待后续版本新增）

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
| 温度范围 16-30°C | 后端 Pydantic | 超出范围拒绝 |
| 模式必须是 4 个枚举值之一 | 后端 Pydantic | 无效值拒绝 |
| 风量必须是 3 个枚举值之一 | 后端 Pydantic | 无效值拒绝 |
| AC 开启时离车场景切换 | 后端工具逻辑 | `scene: off_vehicle → approaching_vehicle` |
| 权限无关 | N/A | AC 控制不影响 L0-L3、primary_surface、confirmation owner |

## 测试方式

在手机 Chat 或 Demo 控制台发送以下自然语言，观察车机 HMI：

| 输入 | 预期 vehicle_state |
|------|--------------------|
| "打开空调" | `ac_on: true, temp: 24, mode: auto, fan: medium` |
| "空调调到 26 度制冷大风" | `ac_on: true, temp: 26, mode: cool, fan: high` |
| "关空调" | `ac_on: false` |
| "太热了开制热 28 度" | `ac_on: true, temp: 28, mode: heat, fan: medium` |

## 相关文件

| 文件 | 说明 |
|------|------|
| `services/agent-api/src/auri_agent/models.py` | `VehicleState` 模型定义 |
| `services/agent-api/src/auri_agent/tools.py` | `control_ac` 工具 + `AgentToolbox.control_ac()` |
| `apps/mobile/.../domain/model/WorldState.kt` | `VehicleControl` + `AcMode` + `FanSpeed` |
| `apps/mobile/.../ui/vehicle/VehicleScreen.kt` | AC 控制卡片 UI |
| `apps/mobile/.../ui/vehicle/VehicleViewModel.kt` | AC 状态从 WorldState 读取 |
| `apps/mobile/.../ui/chat/ChatScreen.kt` | "打开空调"快捷 chip |

## 待车机侧完成后联调

1. 手机发送"打开空调 26 度"
2. 确认车机 HMI 在同一 WorldState revision 下显示 `ac_on: true, temp: 26`
3. 车机端确认 SSE 推送延迟 < 1s
4. 车机端确认 revision 单调递增
5. 手机和车机关闭空调时两端同步
