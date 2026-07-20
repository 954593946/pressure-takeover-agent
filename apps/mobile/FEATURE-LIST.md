# 手机端功能清单

> 对照 `docs/workstreams.md` 手机 A/B P0 定义 + `docs/demo-scope.md` 范围。状态按实际代码判定。

## 状态标记说明

- ✅ 已完成 — 有完整实现，无需再动
- 🟡 骨架就位 — 页面/VM 存在但逻辑是 stub/mock，需要接真实数据
- 🔲 待开始 — 还没写
- ⛔ 本轮不做 — `demo-scope.md` 明确排除

---

## 一、页面与导航

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 1 | 4 Tab 底部导航（对话/日程/车辆/我的） | `AppNavigation.kt` | ✅ | v0.3 豆包风格 |
| 2 | 开屏页 Splash | `SplashScreen.kt` | ✅ | |
| 3 | 💬 对话 Tab — 豆包风格聊天 | `ChatScreen.kt` + `ChatViewModel.kt` | ✅ | Rich card + 语音栏常驻 |
| 4 | 📅 日程 Tab — 月历 + 当日任务 | `CalendarScreen.kt` + `CalendarViewModel.kt` | ✅ | 刚性红/弹性绿 |
| 5 | 🚗 车辆 Tab — 状态 + HMI + 行程 | `VehicleScreen.kt` + `VehicleViewModel.kt` | 🟡 | UI 完整，数据用 mock 默认值，待接 `/v1/vehicle/*` API |
| 6 | 👤 我的 Tab — 入口列表 | `ProfileScreen.kt` + `ProfileViewModel.kt` | 🟡 | 偏好/腕上/关于 的详情页还是空 TODO |
| 7 | 创建任务页 | `CreateTaskScreen.kt` + `CreateTaskViewModel.kt` | ✅ | |
| 8 | 停车复盘页 | `ReviewScreen.kt` + `ReviewViewModel.kt` | ✅ | |
| 9 | 腕上设备页 | `WearableScreen.kt` + `WearableViewModel.kt` | ✅ | |
| 10 | 调试页 | `DebugScreen.kt` + `DebugViewModel.kt` | ✅ | Mock 模式下手动跳转故事步骤 |
| 11 | 旧 AI 助手页 | `VoiceChatScreen.kt` + `VoiceChatViewModel.kt` + `ChatBubble.kt` + `ToolCallCard.kt` | ✅ | 未挂入导航，legacy |
| 12 | 共享组件 | `ui/common/SharedComponents.kt` | ✅ | TaskCard, RiskBanner, VehicleCard, WearableBar |

> v0.3 重构中移除的页面目录（现为空）：`ui/home/`、`ui/message/`、`ui/service/`，功能已合并到 ChatScreen 的 rich card 中。

---

## 二、Domain 层（纯 Kotlin 模型，对齐 contracts v0.2）

| # | 模型 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 13 | WorldState（15 stage + L0-L3 + Profile + Action + Wearable + Confirmation + ServiceOrder + InteractionOutput） | `WorldState.kt` | ✅ | 340 行，字段完整 |
| 14 | Event（12 种事件类型 + EventResponse） | `Event.kt` | ✅ | |
| 15 | VehicleInfo / VehicleStatus / TripSummary | `Vehicle.kt` | ✅ | v0.3 新增 |
| 16 | ChatMessage | `ChatMessage.kt` | ✅ | 旧 VoiceChat 遗留 |
| 17 | VoiceInputProvider 接口 | `VoiceInputProvider.kt` | ✅ | Flow<VoiceInputEvent> |
| 18 | VoiceOutputProvider 接口 | `VoiceOutputProvider.kt` | ✅ | TTS 抽象 |

---

## 三、Data 层 — API & SSE

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 18 | AgentApiService（Retrofit） | `AgentApiService.kt` | ✅ | GET v1/state, POST v1/event |
| 19 | VehicleApiService（Retrofit） | `VehicleApiService.kt` | ✅ | GET v1/vehicle/status, GET v1/vehicle/trips，接口已定义待后端 |
| 20 | SseClient（Ktor） | `SseClient.kt` | ✅ | v1/stream，自动重连 + 指数退避 |
| 21 | 旧 ChatSseClient | `ChatSseClient.kt` | ✅ | 旧 VoiceChat 用 |
| 22 | 旧 ChatApiService | `ChatApiService.kt` | ✅ | 旧 VoiceChat 用 |

---

## 四、Data 层 — Repository

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 23 | WorldStateRepository 接口 | `WorldStateRepository.kt` | ✅ | UI 层唯一数据依赖 |
| 24 | DefaultWorldStateRepository | `DefaultWorldStateRepository.kt` | ✅ | SSE 优先 → polling 降级 |
| 25 | ChatRepository（旧） | `ChatRepository.kt` + `DefaultChatRepository.kt` | ✅ | 旧 VoiceChat 用 |

---

## 五、Data 层 — Mock

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 26 | MockAgent（OkHttp Interceptor 状态机） | `MockAgent.kt` | ✅ | 任意 POST /v1/event 前进 1 步 |
| 27 | StoryScript（9 步预定义 WorldState 快照） | `StoryScript.kt` | ✅ | v0.2 完整 9 步 |
| 28 | MockChatRepository | `MockChatRepository.kt` | ✅ | 旧 |

---

## 六、Data 层 — Voice 实现

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 29 | MockVoiceInputProvider | `MockVoiceInputProvider.kt` | ✅ | 模拟 ASR |
| 30 | MockVoiceOutputProvider | `MockVoiceOutputProvider.kt` | ✅ | 模拟 TTS（log 到控制台） |
| 31 | AndroidVoiceInputProvider | `AndroidVoiceInputProvider.kt` | 🟡 | Intent-based SpeechRecognizer，`onResult()` 回调未接入 Flow 管道 |
| 32 | AndroidVoiceOutputProvider | `AndroidVoiceOutputProvider.kt` | ✅ | 真实 TextToSpeech |

---

## 七、DI

| # | 功能 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 33 | NetworkModule | `NetworkModule.kt` | ✅ | OkHttp, Retrofit, MockAgent 条件创建 |
| 34 | DataModule | `DataModule.kt` | ✅ | SseClient, WorldStateRepository |
| 35 | VoiceModule | `VoiceModule.kt` | ✅ | 语音 I/O 绑定（换实现只改这里） |
| 36 | ChatModule | `ChatModule.kt` | ✅ | 旧 Chat 绑定 |

---

## 八、手机开发 A — 业务 UI（workstreams P0）

| # | P0 项目 | 状态 | 说明 |
|---|---------|------|------|
| A1 | 启动/连接状态页 | 🟡 | Splash 有，但连接状态/SSE 状态指示待完善 |
| A2 | 今日任务 → 可从 WorldState 进入 | ✅ | CalendarScreen 展示 |
| A3 | 任务详情 | 🟡 | 列表有，点击详情/编辑待完善 |
| A4 | 风险展示（L0-L3） | 🟡 | ChatScreen 的 rich card 里有，独立风险面板待做 |
| A5 | 消息（老师/家人草稿消息） | 🟡 | 模型有 Action.message，UI 渲染待完善 |
| A6 | 服务方案（订单预览） | 🟡 | 模型有 ServiceOrder，UI 渲染待完善 |
| A7 | Profile（效率型 vs 品质型差异） | 🟡 | ProfileScreen 入口列表有，两种 Profile 差异展示待做 |
| A8 | 权限边界 | 🔲 | 驾驶中 Companion 只读模式有模型支持，UI 未区分 |
| A9 | 停车后复盘 | ✅ | ReviewScreen 有完整实现 |
| A10 | 驾驶中不出现完整商品列表和复杂确认 | 🔲 | 依赖于 primary_surface 判断，未做 |
| A11 | 状态刷新后不漂移 | 🔲 | 需实测验证 |

---

## 九、手机开发 B — 基础能力（workstreams P0）

| # | P0 项目 | 状态 | 说明 |
|---|---------|------|------|
| B1 | 统一 Client（API + SSE） | ✅ | AgentApiService + SseClient |
| B2 | 状态层（StateFlow from ViewModel） | ✅ | UDF 模式已建立 |
| B3 | 缓存/离线 | 🔲 | 无本地缓存层 |
| B4 | 重连 + 指数退避 | ✅ | SseClient 已实现 |
| B5 | 版本去重 | 🔲 | revision 字段有，去重逻辑未做 |
| B6 | ASR（语音输入） | ✅ | AndroidVoiceInputProvider 已实现 |
| B7 | TTS（语音输出） | ✅ | AndroidVoiceOutputProvider 已实现 |
| B8 | 调试页 | ✅ | DebugScreen 可用 |
| B9 | 离线包 | 🔲 | 未做 |
| B10 | 腕上网关 — HELLO / SET_STATE / ACK | 🔲 | 模型有 Wearable，BLE 网关未实现 |
| B11 | 腕上 — 可选 SENSOR / PING/PONG | 🔲 | 同上 |
| B12 | 腕上 — 重复 command_id 去重 | 🔲 | 同上 |
| B13 | BLE 离线降级不阻塞主流程 | 🔲 | 同上 |

---

## 十、联调就绪度

对照 `workstreams.md` 7 步联调顺序：

| 步骤 | 内容 | 手机端状态 |
|------|------|------------|
| 1 | 冻结共享对象 | ✅ contracts v0.2 已对齐 |
| 2 | Agent 提供假数据快照 | ✅ MockAgent + StoryScript 已对接 |
| 3 | 核心页面各自跑通 | ✅ 4 Tab + 二级页全部渲染 |
| 4 | 基础闭环 | 🟡 需端到端联调 |
| 5 | Profile + 订单预览 | 🟡 模型有，UI 待完善 |
| 6 | 断网/BLE/ASR/缺货/超预算回归 | 🔲 |
| 7 | 冻结 UI + 10 次回归 + 3 次彩排 | 🔲 |

---

## 十一、明确不做（来自 demo-scope.md）

- ⛔ 真实车辆/CAN/量产车机 SDK、车辆控制、完整导航
- ⛔ 真实微信/短信、电商账号、OAuth、支付、受限商品
- ⛔ 医疗诊断、单点生理信号推断情绪、自动猜测人格
- ⛔ 腕上长对话、商品明细、复杂编辑或导航
- ⛔ 通用语音助手、开放式陪聊、多场景泛化

---

## 统计

| 类别 | 总数 | ✅ 完成 | 🟡 骨架 | 🔲 待做 |
|------|------|---------|---------|---------|
| 页面与导航 | 12 | 10 | 2 | 0 |
| Domain 模型 | 6 | 6 | 0 | 0 |
| API & SSE | 5 | 5 | 0 | 0 |
| Repository | 3 | 3 | 0 | 0 |
| Mock | 3 | 3 | 0 | 0 |
| Voice 实现 | 4 | 3 | 1 | 0 |
| DI | 4 | 4 | 0 | 0 |
| 手机 A — 业务 UI | 11 | 2 | 5 | 4 |
| 手机 B — 基础能力 | 13 | 5 | 0 | 8 |
| **合计** | **61** | **41** | **8** | **12** |

---

> 更新于 2026-07-20，基于 `feat/mobile-app-init` @ `0089295`
