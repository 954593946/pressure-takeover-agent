# 手机端 — 随行压力接管 Agent

非驾驶状态下的任务与权限中心，同时承担腕上设备网关。当用户遭遇突发状况（会议超时、交通拥堵）时，AI Agent 自动监控风险、重调度弹性任务、草拟通知消息，并在高风险操作前请求用户确认。

## 快速开始

```bash
# 用 Android Studio 打开 apps/mobile/ 目录，Sync Gradle，Run 'app'
#
# 或者命令行：
./gradlew assembleDebug      # 编译
./gradlew installDebug       # 装到模拟器/设备
./gradlew test               # 运行单元测试
```

**Debug 构建自动启用 MockAgent**——无需后端即可看到完整 9 步 Demo 故事线，且 AI 助手内置模糊意图匹配，可直接对话交互。

---

## 页面总览（7 个 Screen）

| 页面 | 路由 | 入口 | 功能 |
|------|------|------|------|
| 首页 | `home` | 底部 Tab | 聚合仪表盘：任务、风险、车辆、消息确认、腕上设备、上下文感知操作按钮 |
| 任务列表 | `tasks` | 底部 Tab | 今日任务列表（刚性/弹性，状态，优先级，等待方） |
| 消息 | `messages` | 底部 Tab | 消息草稿列表（老师/家人），确认/拒绝操作 |
| AI 助手 | `voice_chat` | 首页麦克风图标 | 流式对话，模糊意图匹配，工具调用卡片，确认卡片，文本/语音双模式 |
| 腕上设备 | `wearable` | 底部 Tab | 设备连接状态、当前模式、震动、心率 |
| 调试 | `debug` | 底部 Tab | Mock 故事脚本手动跳转（仅 Debug 构建可用） |
| 创建任务 | `task_create` | 任务页 FAB | 完整表单：标题、地点、时间、类型、优先级、等待方 |

---

## 文件结构

```
apps/mobile/
├── build.gradle.kts                          # app 级 build 配置
├── settings.gradle.kts                       # Gradle 项目设置
├── gradle/libs.versions.toml                 # ★ 版本目录（所有依赖版本在这里）
├── gradle.properties                         # JVM 参数 & Android 属性
│
└── app/src/main/
    ├── AndroidManifest.xml
    │
    ├── java/com/pressureagent/mobile/
    │   │
    │   ├── MobileApp.kt                      # @HiltAndroidApp
    │   ├── MainActivity.kt                   # @AndroidEntryPoint → AppNavigation()
    │   │
    │   ├── domain/model/                     # ★ 纯 Kotlin，零 Android 依赖
    │   │   ├── WorldState.kt                 # 17 enum + 8 data class（根模型）
    │   │   ├── Event.kt                      # 事件信封 + 9 种事件类型 + EventResponse
    │   │   └── ChatMessage.kt                # 聊天消息模型（角色、内容类型、工具调用、确认）
    │   │
    │   ├── data/
    │   │   ├── remote/
    │   │   │   ├── AgentApiService.kt        # Retrofit: GET health, GET world-state, POST events
    │   │   │   ├── SseClient.kt              # Ktor SSE: GET /v1/stream，自动重连 + 指数退避
    │   │   │   ├── ChatApiService.kt         # Retrofit: POST /v1/chat, POST /v1/chat/confirm
    │   │   │   └── ChatSseClient.kt          # Ktor SSE: POST /v1/chat 流式响应
    │   │   ├── repository/
    │   │   │   ├── WorldStateRepository.kt   # ★ 接口 — UI 层世界状态唯一依赖
    │   │   │   ├── DefaultWorldStateRepository.kt  # SSE 优先 → polling 降级
    │   │   │   ├── ChatRepository.kt         # ★ 接口 — 聊天消息流 + ChatStreamEvent
    │   │   │   └── DefaultChatRepository.kt  # SSE 优先 → 非流式降级
    │   │   ├── mock/
    │   │   │   ├── MockAgent.kt              # OkHttp Interceptor 状态机
    │   │   │   ├── StoryScript.kt            # 9 步预定义 WorldState 快照
    │   │   │   └── MockChatRepository.kt     # 模糊意图匹配（10 种意图），模拟流式打字
    │   │   └── local/
    │   │       └── CalendarHelper.kt         # Android 系统日历 ContentResolver 集成
    │   │
    │   ├── di/
    │   │   ├── NetworkModule.kt              # Hilt: OkHttp, Retrofit, AgentApiService, MockAgent
    │   │   ├── DataModule.kt                 # Hilt: SseClient, WorldStateRepository
    │   │   └── ChatModule.kt                 # Hilt: ChatApiService, ChatSseClient, ChatRepository, CalendarHelper
    │   │
    │   └── ui/
    │       ├── navigation/
    │       │   ├── Screen.kt                 # Sealed class: 7 个路由（5 tab + 2 二级页面）
    │       │   └── AppNavigation.kt          # NavHost + 底部导航栏（5 个 Tab）
    │       ├── home/
    │       │   ├── HomeScreen.kt             # 聚合仪表盘：任务、风险、车辆、消息确认、腕上设备
    │       │   └── HomeViewModel.kt          # 上下文感知主按钮 + 确认/拒绝操作
    │       ├── task/
    │       │   ├── TaskListScreen.kt         # 任务列表（LazyColumn + FAB 跳转创建）
    │       │   ├── TaskListViewModel.kt      # 监听 worldState.tasks
    │       │   ├── CreateTaskScreen.kt       # 任务创建表单（日期/时间选择器、chip 选择器）
    │       │   └── CreateTaskViewModel.kt    # 表单状态、校验、TASK_INPUT_RECEIVED 事件提交
    │       ├── message/
    │       │   ├── MessageListScreen.kt      # 消息草稿列表 + 内联确认栏
    │       │   └── MessageListViewModel.kt   # 监听 worldState.messages + confirmation
    │       ├── voice/
    │       │   ├── VoiceChatScreen.kt        # 聊天 UI：气泡、工具卡片、确认卡片、文本/语音切换
    │       │   ├── VoiceChatViewModel.kt     # 流式聊天处理、工具调用、确认状态管理
    │       │   ├── VoiceChatUiState.kt       # 聊天 UI 状态定义
    │       │   ├── ChatBubble.kt             # 用户/AI/系统气泡 + 流式指示器 + 欢迎页
    │       │   └── ToolCallCard.kt           # 工具调用卡片 + 内联确认卡片
    │       ├── wearable/
    │       │   ├── WearableScreen.kt         # 腕上设备详情页
    │       │   └── WearableViewModel.kt      # 监听 worldState.wearable
    │       ├── debug/
    │       │   ├── DebugScreen.kt            # Mock 故事脚本手动跳转
    │       │   └── DebugViewModel.kt         # 控制 MockAgent advance/reset/jumpTo
    │       ├── common/
    │       │   └── SharedComponents.kt       # TaskCard, RiskBanner, VehicleCard, WearableBar
    │       └── theme/
    │           └── Theme.kt                  # Material 3 蓝色系 Light Color Scheme
    │
    └── res/
        ├── values/strings.xml
        └── values/themes.xml
```

**文件总数：** 39 个 `.kt` 源文件 + 配置

---

## 架构

```
┌──────────────────────────────────────────────────────────────────┐
│  UI 层                                                           │
│  ui/home/  ui/task/  ui/message/  ui/voice/  ui/wearable/       │
│  ui/debug/  ui/common/  ui/navigation/  ui/theme/                │
│                                                                  │
│  每个 feature 包 = Screen.kt + ViewModel.kt (+ UiState.kt)      │
│  ViewModel 只依赖 Repository 接口，不认识 OkHttp/Retrofit       │
├──────────────────────────────────────────────────────────────────┤
│  Domain 层                                                       │
│  domain/model/  ← 纯 Kotlin，零 Android 依赖                     │
│  WorldState.kt、Event.kt、ChatMessage.kt                          │
│                                                                  │
│  所有 enum + data class 完全对齐后端 contracts/                  │
├──────────────────────────────────────────────────────────────────┤
│  Data 层                                                         │
│  data/remote/     AgentApiService + SseClient                    │
│                   ChatApiService + ChatSseClient                 │
│  data/repository/ WorldStateRepository + ChatRepository（接口）  │
│                   DefaultWorldStateRepository                    │
│                   DefaultChatRepository                          │
│  data/mock/       MockAgent + StoryScript + MockChatRepository   │
│  data/local/      CalendarHelper                                 │
├──────────────────────────────────────────────────────────────────┤
│  DI 层                                                           │
│  di/NetworkModule.kt  di/DataModule.kt  di/ChatModule.kt         │
│  Hilt 自动注入，不手写 factory                                    │
└──────────────────────────────────────────────────────────────────┘
```

### 世界状态数据流

```
Agent API ──SSE/polling──→ DefaultWorldStateRepository
                                    │
                              worldState: Flow<WorldState>
                                    │
                    ┌───────────────┼───────────────┬────────────────┐
                    ▼               ▼               ▼                ▼
            HomeViewModel    TaskListViewModel  MessageListVM   WearableVM
                    │               │               │                │
                    ▼               ▼               ▼                ▼
              HomeScreen     TaskListScreen   MessageListScreen  WearableScreen

User tap → ViewModel.submitEvent(Event)
    → repository.submitEvent(event) → POST /v1/events
    → server processes → new WorldState via SSE → UI updates
```

### 聊天数据流

```
User input → VoiceChatViewModel.sendMessage(text, mode)
    → chatRepository.sendMessage(message, inputMode, sessionId)
        → ChatSseClient (SSE streaming) ─┐
        → ChatApiService (fallback) ─────┤
              │                          │
              ▼                          ▼
        Flow<ChatStreamEvent>    Response body
              │
              ▼
    VoiceChatViewModel 处理事件:
      TextDelta          → 逐字追加到 partialResponse
      ToolCallStarted    → 提交 pending 文本 + 添加 ToolCallCard
      ToolCallResult     → 更新卡片结果
      ConfirmationRequired → 显示内联确认卡片
      Done               → 提交剩余文本 + 清理状态
      Error              → 显示错误
```

**唯一数据源：** 所有 ViewModel 均从 Repository 接口获取数据，不各自缓存、不各自轮询。

**铁律：**
1. `ui/` 只认识 `domain/model/` 和 `data/repository/*` 接口，不认识 `data/remote/`、`OkHttp`、`Retrofit`
2. `domain/model/` 是纯 Kotlin，不认识任何其他包
3. 跨层 import 方向：`ui → domain ← data`。绝对不出现 `domain → data` 或 `data → ui`
4. 手机端只上报事件，不直接改 World State；中高风险动作必须先确认，`confirmation_id` 幂等

---

## MockAgent 故事脚本（9 步）

| Step | Stage | Agent Mode | 关键变化 |
|------|-------|------------|---------|
| 0 | `idle` | quiet | 初始空白 |
| 1 | `task_created` | observing | 接孩子(刚性) + 超市采购(弹性) |
| 2 | `meeting_delay` | observing | 会议延迟，风险 ↑ watch |
| 3 | `departure_warning` | observing | 最晚出发警告，腕上设备短震 |
| 4 | `vehicle_mode` | observing | 驾驶中，目的地阳光小学 |
| 5 | `traffic_delay` | observing | 拥堵，ETA 18:28（+18 min），风险 ↑ high |
| 6 | `pressure_takeover` | taking_over | Agent 接管：重调度超市任务，草拟通知消息 |
| 7 | `waiting_confirmation` | awaiting_confirmation | ★ 核心状态 — 消息草稿待用户确认 |
| 8 | `action_completed` | resolved | 确认完成，消息已发送（SIMULATED_SENT） |

任意 POST `/v1/events` 前进 1 步；`DEMO_RESET_REQUESTED` 重置。Debug 页面可手动跳转任意步骤。

### 首页上下文感知按钮

主操作按钮随 Stage 自动变化：
`创建演示任务` → `报告会议延迟` → `我已出发` → `查看路况` → `让 Agent 接管` → `继续处理` → 确认/拒绝 → `重新演示`

---

## Mock 聊天 — 意图识别

MockChatRepository 内置**模糊意图匹配引擎**，支持 10 种意图：

| 意图 | 触发示例 | 效果 |
|------|---------|------|
| `create_task` | "帮我创建一个明天下午3点接孩子的任务" | 创建任务 + 提交事件 |
| `create_calendar_event` | "在日历里加一个周五的家长会" | 写入系统日历 |
| `report_delay` | "会议延迟了30分钟" | 推进故事 + 更新风险 |
| `report_vehicle` | "我已经上车出发了" | 切换驾驶模式 |
| `report_traffic` | "路上堵车很严重" | 更新交通延迟 |
| `request_assistance` | "帮我处理一下" | 触发 Agent 接管 |
| `draft_message` | "给老师发个消息说会迟到" | 草拟通知消息 |
| `get_status` | "现在什么情况" | 返回当前状态摘要 |
| `help` | "你能做什么" | 显示能力清单 |
| 多意图 | "帮我推迟超市任务，再给家人发消息说晚点到" | 依次处理多个意图 |

支持同义词组 + 置信度评分，模糊输入给出部分匹配提示。

---

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| DI | Hilt + KSP | 2.53.1 |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| UI | Compose BOM + Material 3 | 2024.12 |
| Network | Retrofit + OkHttp | 2.11 / 4.12 |
| SSE | Ktor Client (OkHttp engine) | 3.0.3 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Min/Target SDK | 26 / 35, JVM 17 | — |

---

## 如何扩展

### 新增页面
1. `Screen.kt` 加 `data object`（路由名 + 中文标签 + 图标）
2. `AppNavigation.kt` 加 `composable()` 路由（如需底部 Tab 再加 `NavigationBarItem`）
3. 新建 `ui/feature/FeatureScreen.kt` + `FeatureViewModel.kt`
4. ViewModel 注入所需 Repository，collect 对应 Flow

### 新增事件类型
1. `Event.kt` 的 `EventType` enum 加值
2. ViewModel 中调用 `repository.submitEvent(event)`
3. （可选）`MockAgent.kt` 中对应处理

### 新增领域字段
1. `WorldState.kt` 对应 data class 加字段（nullable 保证向后兼容）
2. `StoryScript.kt` 用到的快照补上字段
3. UI 中展示（如需要）

### 切换 mock ↔ 真实后端
- Debug 构建：`buildConfigField USE_MOCK_AGENT = true`
- Release 构建：`buildConfigField USE_MOCK_AGENT = false`
- MockAgent / MockChatRepository 只在 `USE_MOCK_AGENT == true` 时通过 Hilt 注入
- 也可以运行时通过 Hilt `@Named` 替换 binding
