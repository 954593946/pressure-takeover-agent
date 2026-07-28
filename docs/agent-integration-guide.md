# AURI Agent 接入与跨端协作指南

> 面向对象：手机端、车机 Web、Demo 控制台、腕上设备开发者，以及帮助他们编程的 AI。假设读者第一次参与多端 Agent 项目。

## 1. 先理解：Agent 为什么是其他开发的基石

AURI 不是把同一套页面复制到手机、车机和手表。四个端必须看到同一个现实状态，同时只有一个端可以要求用户操作。这个一致性由 Agent/后端提供。

Agent/后端负责：

- 接收手机、车机、腕上和控制台提交的事件。
- 维护唯一的 `WorldState` 和递增的 `revision`。
- 计算任务、ETA、晚到分钟、L0-L3 和 `reason_codes`。
- 决定当前唯一主交互端 `primary_surface`。
- 生成消息、任务调整、模拟采购等结构化 Action。
- 检查权限、预算、地址、替代规则和确认是否有效。
- 保证重复事件、双击和语音并发只执行一次。
- 把完整状态广播给所有端。

其他端负责：

- 把现实变化和用户操作包装成标准 `Event` 上传。
- 接收完整 `WorldState`。
- 根据 `stage`、`scene`、`primary_surface` 和自己的端类型渲染 UI。
- 不在端内复制后端的状态机、风险算法、权限和订单逻辑。

最重要的协作关系是：

```text
现实变化 / 用户操作
        │
        ▼
客户端创建 Event
        │
        ▼
Agent 校验、去重、计算、规划、保存
        │
        ▼
产生更高 revision 的完整 WorldState
        │
        ├─────────┬──────────┬──────────┐
        ▼         ▼          ▼          ▼
      手机      车机       腕上      控制台日志
     完整信息   安全摘要    低干扰状态   调试复盘
```

如果每个端自行判断，必然出现三个问题：手机认为 L1、车机认为 L2；手机和车机同时显示确认；重复点击生成两个订单。因此跨端开发必须先有 Agent 契约。

## 2. 当前已经有什么

截至 2026-07-21，Agent v0.2 候选基础版已提供：

| 能力 | 当前状态 | 说明 |
|---|---|---|
| Event API | 可用 | Schema 校验、`event_id` 去重、Session 检查 |
| World State | 可用 | 唯一状态、递增 `revision`、完整快照 |
| L0-L3 | 可用 | 确定性规则；LLM 不参与等级判断 |
| 主交互端 | 可用 | 车外手机、车内车机、停车后手机 |
| Profile | 可用 | 效率型与品质型预算、配送、替代规则 |
| 动作规划 | 可用 | 只依据已有任务生成等待方模拟消息和采购方案，不再固定返回同一组动作 |
| 确认与幂等 | 可用 | 错误端拦截、重复/并发确认不重复执行 |
| Mock 采购 | 可用 | success、out_of_stock、over_budget |
| 实时状态 | 可用 | SSE `/v1/stream`；兼容 WebSocket `/v1/ws` |
| LLM Agent | 可用 | LangChain 根据自然语言选择受控工具并动态回复；超时保留已完成工具结果并安全兜底 |
| 持久数据库 | 未做 | 当前为单实例内存状态，符合六周 Demo 范围 |

当前契约是候选基线。必须由至少一个事件生产方和一个状态消费方评审后冻结。

## 3. 所有人必须共享的八个对象

不要先背全部字段，先理解每个对象解决什么问题：

| 对象 | 用途 | 典型生产方 | 典型消费方 |
|---|---|---|---|
| `Task` | 表示刚性/弹性责任、时间、等待方和可替代能力 | 手机输入、Agent 解析 | 手机、车机、Agent |
| `Event` | 表示已经发生的事实或用户操作 | 所有客户端 | Agent |
| `WorldState` | 当前唯一完整系统状态 | Agent | 所有客户端 |
| `Action` | Agent 准备或执行的消息、调整和服务动作 | Agent | 手机、车机、Ledger |
| `Confirmation` | 一次性动作组确认及所有权 | Agent | 手机或车机中的唯一主端 |
| `Profile` | 用户明确选择的交互、预算、配送和替代规则 | 手机 | Agent、手机、车机摘要 |
| `WearableState` | 腕上显示、颜色、触觉和命令编号 | Agent/手机网关 | 腕上设备 |
| `ServiceOrder` | 模拟订单预览、预算、配送、状态和错误 | Agent Mock Adapter | 手机完整信息、车机摘要 |

字段的精确定义以以下文件为准：

- `contracts/event.schema.json`
- `contracts/world-state.schema.json`
- `contracts/openapi.yaml`
- `contracts/examples/`

文档负责解释，Schema 负责判定一个 payload 是否有效。AI 写代码时必须同时读取 Schema 和示例。

## 4. 最小启动步骤

### 4.1 第一次安装

在仓库根目录运行：

```powershell
.\scripts\setup-agent.ps1
```

该脚本会创建 `services/agent-api/.venv`、安装依赖并执行测试。

### 4.2 启动服务

```powershell
.\scripts\run-agent.ps1
```

启动成功后打开：

- Swagger API：`http://127.0.0.1:8000/docs`
- 健康状态：`http://127.0.0.1:8000/health`
- 当前 World State：`http://127.0.0.1:8000/v1/state`

### 4.3 运行测试

```powershell
.\scripts\test-agent.ps1
```

标准事件序列位于：

```text
packages/test-fixtures/happy-path.events.json
```

## 5. 客户端接入的固定步骤

所有客户端都按同一个顺序接入。

如果团队使用共享 Agent，所有 `/v1/*` HTTP 请求必须增加：

```http
X-Agent-Token: 由 Agent Owner 单独提供的团队令牌
```

团队令牌只保护 Demo Agent 入口，不是 Bosch API Key。Bosch Key 永远只存在于运行 Agent 的服务器环境中，不能进入手机、车机、腕上或控制台代码。

公网联调时使用项目负责人提供的 Render HTTPS 地址作为 `AGENT_API_BASE_URL`，不要在客户端写死某台电脑的局域网 IP。SSE 地址在该基址后增加 `/v1/stream`，WebSocket 把协议改为 `wss` 并增加 `/v1/ws`。Render 免费实例可能休眠；连接失败时先请求公开的 `/health` 唤醒服务，再按退避策略重连。

### 第一步：获取当前 Session 和完整状态

```http
GET /v1/state
```

保存以下字段：

- `session_id`：后续 Event 必须携带同一个值。
- `revision`：只接受比本地版本更新的快照。
- `stage`：当前业务阶段。
- `primary_surface`：谁可以要求用户操作。

不要只取局部字段后在本地拼出 World State。首次连接和重连都应获取完整快照。

### 第二步：订阅实时状态

P0 推荐 SSE：

```http
GET /v1/stream
Accept: text/event-stream
```

也可以使用：

```text
ws://127.0.0.1:8000/v1/ws
```

客户端接到新快照时：

1. 比较 `session_id`。
2. 比较 `revision`。
3. 只接受相同 Session 且 revision 更高的快照。
4. 用新快照整体替换本地状态。
5. 根据当前端和 `primary_surface` 决定是否显示操作入口。

### 第三步：把操作上传为 Event

示例：控制台通知 Agent 已进入车辆。

```json
{
  "schema_version": "0.2.0",
  "event_id": "evt_vehicle_001",
  "session_id": "从 GET /v1/state 读取",
  "type": "scene.vehicle_entered",
  "source": "demo_console",
  "timestamp": "2026-07-15T18:00:00+08:00",
  "payload": {}
}
```

提交到：

```http
POST /v1/event
Content-Type: application/json
```

客户端不能上传下面这种命令：

```json
{
  "set_stage": "waiting_confirmation",
  "set_pressure_level": "L2",
  "show_page": "confirm"
}
```

因为它描述的是“想看到的结果”，不是“已经发生的事实”。

手机或车机的自然语言统一提交为 `type=user.utterance`，正文放在 `payload.text`。Agent 可以据此创建/查询/调整任务、记录会议延迟、准备协助方案，或处理用户明确确认；客户端仍以返回的结构化 WorldState 为准，不能从回复文本自行推导任务或执行状态。完整工具边界见 [`contracts/tool-calling-spec.md`](../contracts/tool-calling-spec.md)。

### 第四步：只在 Owner 端确认

当 `WorldState.confirmation` 不为空时，同时检查：

- `confirmation.status == "pending"`
- `confirmation.owner_surface == 当前端`
- `primary_surface == 当前端`
- 当前时间没有超过 `expires_at`

只有全部满足，才渲染确认按钮。

```json
{
  "confirmation_id": "confirm_xxx",
  "decision": "accept",
  "confirmed_by": "vehicle_hmi",
  "input_mode": "button"
}
```

提交到 `POST /v1/confirm`。按钮双击和语音并发可以重复发送同一确认，后端会返回同一个执行结果；客户端不得创建新的 confirmation ID。

## 6. 主要接口

| 接口 | 谁调用 | 用途 | 新手最容易犯的错 |
|---|---|---|---|
| `GET /health` | 调试页/控制台 | 查看服务和 LLM 是否已配置 | 不要在 UI 展示或记录密钥 |
| `GET /v1/state` | 所有端 | 首次连接和重连获取完整状态 | 不要只在 App 启动时调用一次 |
| `GET /v1/stream` | 手机/车机/控制台 | SSE 接收完整快照 | 不比较 revision，导致旧状态覆盖新状态 |
| `WS /v1/ws` | 需要 WS 的客户端 | WebSocket 兼容状态流 | 在 WS 消息基础上自行推演下一状态 |
| `POST /v1/event` | 所有端 | 上报事实和用户操作 | 上传最终页面名或压力等级 |
| `POST /v1/confirm` | 当前 Owner 端 | 接受/拒绝动作组 | 手机和车机各生成一次确认 |
| `PUT /v1/profile` | 手机 | 更新明确偏好 | Profile 改变安全权限 |
| `POST /v1/session/reset` | 控制台 | 重置标准 Demo | 直接清数据库或刷新页面冒充重置 |

## 7. P0 标准事件顺序

| 序号 | Event | Agent 预期结果 | 手机 | 车机 | 腕上 |
|---|---|---|---|---|---|
| 1 | `task.created` | `off_vehicle_idle` | 显示完整任务 | 静默 | 已记录 |
| 2 | `meeting.overrun` | L1 / `pre_departure_warning` | 风险卡 | 静默 | 黄态双短震一次 |
| 3 | `scene.vehicle_entered` | `primary_surface=vehicle_hmi` | 只读后台 | 驾驶页 | 交接提示一次 |
| 4 | `traffic.updated` | L2 / 晚到18分钟 | 只读 | 一句现实结论 | 单脉冲 |
| 5 | `user.utterance`（“帮我处理”） | 根据已有任务规划消息和订单 | 保存完整明细 | 处理中 | 处理中 |
| 6 | Agent 生成 Action | `waiting_confirmation` | 确认入口失效 | 唯一确认按钮 | 不可操作 |
| 7 | `POST /v1/confirm` | 动作组只执行一次 | 同步状态 | 按钮禁用 | 处理中 |
| 8 | 执行完成 | `action_completed` | 订单/消息卡 | 一句完成 | 绿态柔和短震 |
| 9 | `cooldown.elapsed` | `cooldown` | 后台 | 安静 | 静默 |
| 10 | `scene.parked` | `parked_review` | 完整复盘 | 结束 | 已同步 |

## 8. 每个成员具体怎么接

### 8.1 手机开发 A：业务 UI

你主要消费：

- `tasks`
- `risk`
- `actions`
- `profile`
- `service_orders`
- `action_ledger`

你可以提交：

- 创建/编辑任务对应的 Event。
- `PUT /v1/profile`。
- 车外或停车后的合法确认。

你不能：

- 自己把任务判定为 L1/L2/L3。
- 驾驶中显示商品长列表和复杂确认。
- 根据一次用户选择改变安全权限。

### 8.2 手机开发 B：连接、语音和腕上网关

你负责统一 API Client、SSE/WS、缓存、重连、revision 去重、ASR/TTS 和 BLE Gateway。

推荐状态层伪代码：

```text
onStart:
  state = GET /v1/state with X-Agent-Token
  connect stream

onSnapshot(next):
  if next.session_id != state.session_id:
    state = GET /v1/state
  else if next.revision > state.revision:
    state = next

onDisconnect:
  exponential backoff
  state = GET /v1/state with X-Agent-Token
  reconnect stream
```

腕上 `command_id` 必须来自统一命令；重试同一命令不能生成新 ID，也不能重复震动。

### 8.3 车机 Web

车机只按 `stage + primary_surface` 渲染：

- `primary_surface != vehicle_hmi`：不显示可操作确认。
- `takeover_L2/L3`：最多一条现实结论。
- `waiting_confirmation`：一组动作摘要和一个大按钮。
- 订单只显示商品数、总价、配送时段和异常标签。
- `action_completed`：一句完成信息，随后进入安静态。

按钮和语音确认必须使用同一个 `confirmation_id`。

### 8.4 Demo 控制台

控制台模拟外部世界，只提交 Event：

- 会议延迟。
- 接近/进入车辆。
- ETA 和拥堵。
- 心率趋势或驾驶辅助信号。
- 服务 success/out_of_stock/over_budget。
- 停车和 Session 重置。

控制台必须显示：当前 Session、revision、最后 Event、World State、连接状态和错误。禁止提供“直接进入 L2”“直接显示确认页”按钮。

### 8.5 腕上设备

腕上只消费 `WearableState` 子集：

- `mode`
- `text`
- `color`
- `haptic`
- `command_id`

收到同一 `command_id` 时返回 duplicate ACK，不重复震动。腕上不能显示商品明细、消息全文或复杂选择，也不能自己根据心率改变压力等级。

## 9. 契约变更流程

如果开发时发现缺少字段，不要先在 TypeScript、Flutter、Zepp 或后端内部新增私有字段。

正确流程：

1. 写清真实使用场景，例如“车机需要知道确认由谁拥有”。
2. 修改 `contracts/*.schema.json` 或 `contracts/openapi.yaml`。
3. 更新至少一个正向示例。
4. 更新 `packages/test-fixtures/`。
5. 写兼容方式；六周内优先新增可选字段。
6. 邀请一个生产方和一个消费方评审。
7. 契约合并后，各端再实现。

PR 中必须回答：

- 谁生产这个字段？
- 谁消费这个字段？
- 不提供时默认行为是什么？
- 是否影响旧快照和旧客户端？
- 如何测试重复、过期、断线和错误情况？

## 10. 给 AI 编程助手的任务模板

团队成员可以把下面模板复制给自己的 AI，再补充具体任务：

```text
你正在开发 AURI 多端压力接管 Agent 项目。

开始前必须完整读取：
1. 根 AGENTS.md
2. 根 README.md
3. docs/agent-integration-guide.md
4. 我负责模块的 README
5. contracts/openapi.yaml、event.schema.json、world-state.schema.json 和相关 examples

我的模块是：【mobile / vehicle-hmi / demo-console / wearable / agent-api】
本次任务是：【填写一个小而明确的任务】

硬约束：
- 客户端只提交 Event，不能写 WorldState。
- 不自行发明状态名、字段、压力等级或确认逻辑。
- 只按更高 revision 的完整 WorldState 渲染。
- 只有 primary_surface Owner 可以确认。
- LLM 不决定安全、权限、金额或执行。
- 重复 event/command/action/confirmation/order 必须幂等。
- 不提交密钥或真实个人数据。

先告诉我：
1. 你读到了哪些权威文件；
2. 本任务输入、输出和失败降级；
3. 会修改哪些文件；
4. 是否需要契约变更。

确认方案后再编码，并运行模块测试。
```

如果 AI 没有读这些文件就直接生成大量代码，应立即停止并重新要求它先做仓库分析。

## 11. 新手常见错误

### 错误一：页面按钮直接切换最终 UI

错误：点击“拥堵”后前端直接打开 L2 页面。

正确：提交 `traffic.updated`，等待 Agent 返回新的 World State，再渲染。

### 错误二：每个端复制一份状态枚举

错误：手机叫 `warning`，车机叫 `danger`，腕上叫 `stress`。

正确：业务状态全部来自 contracts；腕上显示模式只是 `WearableState.mode`。

### 错误三：把 LLM 当状态机

错误：让 Prompt 输出 `pressure_level=L3` 和“立即下单”。

正确：LLM 只解析任务和生成候选短文案；规则引擎判断等级和权限。

### 错误四：断线后继续使用本地旧状态

正确：重连先 `GET /v1/state`，再订阅流，只接受更高 revision。

### 错误五：为双击生成新确认 ID

正确：同一按钮和语音入口都发送后端提供的同一个 `confirmation_id`。

## 12. 联调前检查单

- [ ] 能启动 Agent 并看到 `/health` 返回 ok。
- [ ] 能从 `/v1/state` 读取 session_id 和 revision。
- [ ] 使用标准 Event 后 revision 增加。
- [ ] 刷新页面后 UI 从完整快照恢复。
- [ ] 进入车辆后手机确认入口消失，车机成为 Owner。
- [ ] 重复 Event 不产生第二次变化。
- [ ] 按钮和语音并发确认只产生一个 order_id。
- [ ] 超预算/缺货时订单被阻断，但消息动作可继续。
- [ ] 腕上重复 command_id 不重复震动。
- [ ] 所有 Demo 数据有模拟标识。
- [ ] `.env`、API Key 和真实个人数据未进入 Git。

完成以上检查后，才进入跨端 UI 精修和现场彩排。
