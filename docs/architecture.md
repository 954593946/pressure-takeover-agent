# AURI 系统架构

## 不可违反的原则

1. Agent/后端是唯一 World State 写入者；所有端只上传事件。
2. LLM 负责语义理解和候选内容，不负责压力等级、数学计算、权限或金额决策。
3. 任一时刻只有一个 `primary_surface` 可以要求注意或确认。
4. 所有不可逆动作都经过 Permission Engine 和 Confirmation Service；事件、命令、动作、确认和订单均幂等。
5. 模拟的是外部来源与第三方结果，状态机、动作编排、安全和多端同步必须真实。

## 最小架构

```text
mobile / vehicle-hmi / wearable-gateway / demo-console
                           |
                           | Event
                           v
                       agent-api
  Event Ingestor -> World State -> Risk Engine (L0-L3)
                           |
               Interaction Orchestrator
                    /              \
            Profile Policy     Action Orchestrator
                                   |
         Permission -> Confirmation -> Mock Service Adapter
                                   |
                    Action Ledger + State version
                           |
                snapshot + real-time stream
                           v
      mobile / vehicle HMI / wearable / console log
```

腕上设备可以通过 Zepp Side Service/BLE 手机网关或后端链路接入；进入系统边界后必须转换成统一 `WearableState` 和 Event。

## LangChain Agent 编排层

`user.utterance` 不再对应一个固定动作。后端使用 LangChain `create_agent`，按本轮自然语言和当前 WorldState 选择工具，再根据工具真实结果生成回复：

```text
自然语言 + WorldState 快照
          |
          v
 LangChain AuriAgent
   |      |       |
任务工具  查询工具  方案/确认工具
   \      |       /
    确定性 Tool Layer
          |
 RiskEngine / ActionPlanner / Confirmation
          |
 revision 检查后提交 WorldState
```

模型不直接持有可写状态。每轮工具先操作隔离副本，Runtime 只在 `revision` 未变化时原子替换；并发冲突会重试一次，仍冲突则返回 `CONCURRENT_UPDATE`。模型或最终文案超时不会撤销已经成功的确定性工具结果，也不会绕过确认。

实际工具及安全边界见 [AURI LangChain Agent 工具与安全边界](../contracts/tool-calling-spec.md)。车辆、停车、路况、腕上和驾驶信号仍只能通过标准 Event 输入，不能由聊天伪造。

## 共享对象

| 对象 | 最小职责 |
|---|---|
| `Task` | 刚性/弹性、时间、地点、等待方、依赖、`capability_tags` |
| `Event` | 唯一 ID、来源、时间、session 和 payload；重复 ID 不重复处理 |
| `WorldState` | session、version、stage、scene、主端、压力、任务、ETA、动作、Profile 和腕上状态 |
| `Action` | message/reschedule/service_order、风险、确认要求、摘要、详情引用和状态 |
| `Confirmation` | 动作组、过期、状态、确认来源；只能消费一次 |
| `Profile` | 交互阈值、触觉、话术、预算、配送和替代规则；不改变安全权限 |
| `WearableState` | mode、短文本、颜色、触觉、可选心率和信号置信度 |
| `ServiceOrder` | 预览、商品数/明细、总价、预算、配送、状态和错误码 |

## 主状态流

```text
off_vehicle_idle
  -> pre_departure_warning (L1)
  -> handover_to_vehicle
  -> vehicle_observation
  -> takeover_L2 / takeover_L3
  -> planning
  -> service_prepared
  -> waiting_confirmation
  -> executing
  -> service_executed / action_completed
  -> cooldown
  -> parked_review
```

控制台只能注入 meeting/scene/traffic/signal/service mock 等事件，不能直接指定最终页面或跳过 Agent 判断。

## L0-L3 与输出

| 等级 | 现实条件 | 输出 |
|---|---|---|
| L0 | 无刚性责任冲突 | 保持安静 |
| L1 | 时间窗压缩但仍可能完成 | 车外手机风险卡 + 腕上一次双短震；不重排、不联系 |
| L2 | 确定晚到或用户明确求助，并有辅助证据 | 车机一句结论 + 动作组 + 单确认；准备消息和服务方案 |
| L3 | 明确责任风险 + 至少两类持续高负荷辅助信号 | 车机保护态，抑制非必要内容；敏感动作仍需确认 |
| Recovery | 动作完成或风险解除 | 一句完成提示后 cooldown，停车后手机复盘 |

心率、急刹、语气和视觉只能作为辅助证据。等级迁移必须有迟滞、冷却和可解释 `reason_codes`。

## 交互所有权

| Scene | 主端 | 其他端行为 |
|---|---|---|
| off_vehicle | mobile | 腕上轻提示；车机静默 |
| approaching_vehicle | mobile → vehicle_hmi | 手机停止新确认；腕上提示交接；车机预加载 |
| driving / high_load_driving | vehicle_hmi | 手机后台只读；腕上只做一次状态/触觉回执 |
| parked | mobile | 车机结束/待机；腕上同步后静默 |

每条输出包含 message ID、priority、owner surface、expires at 和 confirmation 属性。非 Owner 端不得振动、播报或提供可操作确认。

## 动作编排与服务执行

1. `Task.capability_tags` 把“去超市”匹配为 `grocery_delivery`。
2. Profile Store 返回当前动作所需的最少偏好数据。
3. Action Planner 生成消息、重排和订单候选动作。
4. Mock Catalog 与 Adapter 生成结构化 preview；手机显示明细，车机显示摘要。
5. Permission Engine 判断 allow / confirm / block，以及能否合并确认。
6. Confirmation Service 成功消费后调用 execute；重复调用返回同一结果。
7. Action Ledger 保存 action/order 状态；重连、刷新和停车后复盘可恢复。

缺货时消息照常处理，订单降级为停车后选择替代；超预算或地址变化不得在驾驶中追加复杂选择。

## 契约演进

当前仓库 Schema 是早期 v0.1。v0.2 冻结必须补齐上述八个对象、主端/输出字段、L0-L3、Profile、ServiceOrder、Adapter 错误和标准事件序列。契约变更应保持向后兼容；确需破坏性修改时升级版本并提供迁移样例。
