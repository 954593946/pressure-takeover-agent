# 手机端开发手册

两个人协作的开发规范、代码模式和任务拆解。

---

## 目录

1. [当前状态](#0-当前状态)
2. [任务拆解](#1-任务拆解)
3. [分工](#2-分工)
4. [架构速览](#3-架构速览)
5. [包职责](#4-包职责)
6. [约定](#5-约定)
7. [How-to：新增一个页面](#6-how-to新增一个页面)
8. [How-to：新增一种事件类型](#7-how-to新增一种事件类型)
9. [How-to：新增一个领域字段](#8-how-to新增一个领域字段)
10. [错误处理](#9-错误处理)
11. [文件所有权](#10-文件所有权)

---

## 0. 当前状态

**2026-07-14 — 雏形可用**

- 26 个 Kotlin 源文件，编译通过，已安装到模拟器
- MockAgent 9 步故事脚本完整，Debug 构建自动启用
- 5 个 Tab 页面框架就绪，数据从 MockAgent 正常流入
- 底部导航完整（首页 / 任务 / 消息 / 腕上设备 / 调试）
- 首页：聚合视图（任务、风险、车辆、确认、腕上设备）
- 任务页：空列表展示，只读
- 消息页：草稿列表 + 确认/拒绝按钮（7→8 步验证通过）
- 腕上设备页：基础信息展示
- 调试页：Mock 故事手动跳转 + 快捷跳按钮

**可运行的 Demo 流程：切到调试 Tab → 点"下一步" → 回到首页看变化。**

---

## 1. 任务拆解

### P0 — 核心体验闭环（2 周目标）

| # | 任务 | 说明 | 估时 | 依赖 |
|---|------|------|------|------|
| 1 | **首页操作按钮** | 根绝当前 stage 显示对应按钮：创建任务 / 报告延迟 / 接管 / 确认发送 | 1d | — |
| 2 | **任务创建入口** | 任务页加 FAB → 创建表单 → POST 事件 | 2d | #1 |
| 3 | **语音输入页面** | 新建 `ui/voice/`，语音 → 文本 → 解析为事件（mock 阶段用文本兜底） | 5d | — |
| 4 | **风险卡增强** | 风险详情展开（历史风险、时间线），辅助信号标记 | 2d | — |
| 5 | **降级话术** | 网络断开 / Agent 超时 / 确认重复提交 的场景文案 | 1d | — |
| 6 | **SSE 真实对接** | 对接真实 agent-api，测试 SSE 重连 + polling 降级 | 3d | 后端就绪 |

### P1 — 完善与韧性（后续迭代）

| # | 任务 | 说明 | 估时 |
|---|------|------|------|
| 7 | **腕上设备详情** | 心率曲线、连接历史、震动模拟 | 3d |
| 8 | **消息时间线** | 老师/群聊风格，已发送 / 已读状态 | 2d |
| 9 | **任务编辑** | 修改时间、地点、类型 | 2d |
| 10 | **暗色模式** | DarkColors theme | 1d |
| 11 | **错误处理完善** | AsyncState sealed class，全局错误分发 | 2d |

### P2 — 工程化

| # | 任务 | 说明 | 估时 |
|---|------|------|------|
| 12 | **单元测试** | `StoryScript` / `MockAgent` / `DefaultWorldStateRepository` | 3d |
| 13 | **UI 测试** | Compose Testing 覆盖五大页面关键路径 | 3d |
| 14 | **设置页** | 服务器地址、Mock 开关、Debug 选项 | 2d |

---

## 2. 分工

```
开发 A（UI/体验）                   开发 B（集成/韧性）
──────────────────────────       ──────────────────────────
#3 语音输入 ★                    #6 SSE 真实对接 ★
#1 首页操作按钮                   #5 降级话术
#2 任务创建入口                   #11 错误处理完善
#4 风险卡增强                     #12 单元测试
#7 腕上设备详情                   #14 设置页
#8 消息时间线                     domain/model/（两人都改，先沟通）
#10 暗色模式
ui/ 下所有页面组件                data/  + di/ 下所有文件
ui/common/ 共享组件               gradle 配置 + 构建脚本
```

### 交汇点（需要沟通的改动）

| 文件 | 谁改什么 |
|------|---------|
| `domain/model/*.kt` | 两人都可能改——改之前群里说一声 |
| `data/mock/StoryScript.kt` | B 维护故事脚本，A 需要新数据时找 B |
| `ui/navigation/AppNavigation.kt` | A 加页面路由，B 加深度链接 |
| `ui/common/SharedComponents.kt` | 两人都加组件——改完立刻 push，避免冲突 |

### Git 分支建议

```
main                              ← 只通过 PR 合并
├── feature/voice-input           ← A
├── feature/home-actions          ← A
├── feature/sse-integration       ← B
├── feature/degradation-handling  ← B
└── chore/update-schema           ← 合作分支
```

### 工作流

1. 从 `main` 开 feature 分支
2. 每人独立在自己的模拟器上用 MockAgent 开发
3. MockAgent 足够跑完整流程，不需要等对方
4. 做完提 PR → 对方 Review → 合并 main
5. 合并后 `git checkout main && git pull && git rebase main` 到自己的分支

---

## 3. 架构速览

```
┌──────────────────────────────────────────────────┐
│  UI 层                                           │
│  ui/home/  ui/task/  ui/message/  ui/wearable/  │
│  ui/debug/  ui/voice/（待建）                    │
│  ui/common/     ← 跨页面共享组件                  │
│  ui/navigation/ ← Screen sealed class + NavHost  │
│                                                  │
│  每个 feature 包 = Screen.kt + ViewModel.kt      │
│  ViewModel 只依赖 WorldStateRepository 接口      │
├──────────────────────────────────────────────────┤
│  Domain 层                                       │
│  domain/model/  ← 纯 Kotlin，零 Android 依赖      │
│  WorldState.kt、Event.kt                          │
│                                                   │
│  所有 enum + data class 完全对齐 contracts/       │
├──────────────────────────────────────────────────┤
│  Data 层                                         │
│  data/remote/     AgentApiService + SseClient    │
│  data/repository/ WorldStateRepository（接口）    │
│                   DefaultWorldStateRepository    │
│  data/mock/       MockAgent + StoryScript        │
├──────────────────────────────────────────────────┤
│  DI 层                                           │
│  di/NetworkModule.kt  di/DataModule.kt           │
│  Hilt 自动注入，不手写 factory                    │
└──────────────────────────────────────────────────┘
```

**数据流向：**

```
Agent API ──SSE/polling──→ DefaultWorldStateRepository
                                    │
                              worldState: Flow<WorldState>
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            HomeViewModel    TaskListViewModel  MessageListViewModel
                    │               │               │
                    ▼               ▼               ▼
              HomeScreen     TaskListScreen   MessageListScreen

User tap → ViewModel.submitEvent(Event)
    → repository.submitEvent(event) → POST /v1/events
    → server processes → new WorldState via SSE → UI updates
```

**唯一数据源：** `WorldStateRepository.worldState: Flow<WorldState>`
所有 ViewModel 从同一个 Flow 拿数据，不各自缓存、不各自轮询。

---

## 4. 包职责

| 包 | 放什么 | 不放什么 |
|----|--------|---------|
| `domain/model/` | `data class`、`enum`、与 schema 一一对应 | 业务逻辑、格式化、计算 |
| `data/repository/` | `WorldStateRepository` 接口 + impl | UI 相关代码 |
| `data/remote/` | HTTP 调用、SSE 解析 | 状态管理 |
| `data/mock/` | 开发阶段的假数据 | 生产代码 |
| `di/` | Hilt `@Module` `@Provides` | 业务代码 |
| `ui/home/` | 首页仪表盘 | 其他页面的组件 |
| `ui/common/` | 被**两个以上** feature 使用的 `@Composable` | 只被一个 feature 用的组件 |
| `ui/navigation/` | 路由定义 + NavHost | 页面内容 |

**铁律：**

1. `ui/` 只认识 `domain/model/` 和 `data/repository/WorldStateRepository`，不认识 `data/remote/`、`OkHttp`、`Retrofit`。
2. `domain/model/` 不认识任何其他包——它是纯 Kotlin。
3. 跨层 import 方向：`ui → domain ← data`。绝对不出现 `domain → data` 或 `data → ui`。

---

## 5. 约定

### 文件命名

```
Screen 文件:     [Feature]Screen.kt      例: TaskListScreen.kt
ViewModel 文件:  [Feature]ViewModel.kt   例: TaskListViewModel.kt
UiState:         [Feature]UiState        例: TaskListUiState
组件文件:         PascalCase.kt           例: RiskBanner.kt
```

### ViewModel 模式

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExampleUiState())
    val uiState: StateFlow<ExampleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.value = mapToUiState(ws)
            }
        }
    }

    // public fun doSomething() → viewModelScope.launch { … }
}
```

不要：
- 在 ViewModel 里写 `OkHttp`、`Retrofit`
- 在 ViewModel 里手动 `delay()` 做轮询
- 在 `init` 之外手动 collect Flow

### Composable 模式

```kotlin
@Composable
fun ExampleScreen(viewModel: ExampleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    // 只用 state 渲染，不在这里写业务逻辑
}
```

### 新增依赖

在 `gradle/libs.versions.toml` 的 `[versions]` 和 `[libraries]` 加条目，然后在 `app/build.gradle.kts` 引用。

不要直接在 `build.gradle.kts` 里写硬编码版本号。

---

## 6. How-to：新增一个页面

以新增"设置页"为例：

### Step 1：定义路由

```kotlin
// ui/navigation/Screen.kt
sealed class Screen(…) {
    // … existing …
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}
```

### Step 2：注册导航

```kotlin
// ui/navigation/AppNavigation.kt
// 在 NavHost 的 composable 块里加：
composable(Screen.Settings.route) { SettingsScreen() }

// 如果要加到底部导航栏，在 tabs 列表里加上 Screen.Settings
```

### Step 3：写 UiState

```kotlin
// ui/settings/SettingsViewModel.kt
data class SettingsUiState(
    val serverUrl: String = "",
    val isMockMode: Boolean = true,
)
```

### Step 4：写 ViewModel

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
}
```

### Step 5：写 Screen

```kotlin
// ui/settings/SettingsScreen.kt
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    // Compose UI here
}
```

**完成。** Hilt 自动提供 ViewModel，不需要写 factory、不需要改 DI 模块。

---

## 7. How-to：新增一种事件类型

### Step 1：在 Event.kt 注册

```kotlin
// domain/model/Event.kt
enum class EventType {
    // … existing …
    @SerialName("settings.changed") SETTINGS_CHANGED,  // ← 新增
}
```

### Step 2：在 ViewModel 中发送

```kotlin
// 在任意 ViewModel 中
fun updateSetting(key: String, value: String) {
    viewModelScope.launch {
        val event = Event(
            eventId = UUID.randomUUID().toString(),
            type = EventType.SETTINGS_CHANGED,
            source = EventSource.MOBILE,
            occurredAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            payload = buildJsonObject {
                put("key", key)
                put("value", value)
            },
        )
        repository.submitEvent(event)
    }
}
```

### Step 3：（可选）MockAgent 中处理

```kotlin
// data/mock/MockAgent.kt — handlePostEvent() 中
EventType.SETTINGS_CHANGED -> {
    // mock 逻辑：记录设置变更，不推进故事
}
```

---

## 8. How-to：新增一个领域字段

以给 `Task` 加 `notes: String?` 为例：

### Step 1：改 Schema

```json
// contracts/world-state.schema.json
"task": {
  "properties": {
    // … existing …
    "notes": { "type": "string" }
  }
}
```

### Step 2：改 Kotlin 模型

```kotlin
// domain/model/WorldState.kt
data class Task(
    // … existing …
    val notes: String? = null,  // ← 新增，nullable 保证向后兼容
)
```

### Step 3：改 StoryScript

```kotlin
// data/mock/StoryScript.kt — 在用到 Task 的快照里加上 notes 字段
Task(
    // … existing …
    notes = null,
)
```

### Step 4：改 UI（如果需要展示）

在 `ui/common/SharedComponents.kt` 的 `TaskCard` 中加一行展示逻辑。

**不需要改：** ViewModel、Repository、DI——因为 `Flow<WorldState>` 自动包含新字段。

---

## 9. 错误处理

### Repository 层

```kotlin
// DefaultWorldStateRepository: SSE 断开 → 自动切 polling
// polling 失败 → 保留上次值，不抛异常
```

### ViewModel 层

```kotlin
// 后续可改为 sealed class:
sealed class AsyncState<out T> {
    data class Success<T>(val data: T) : AsyncState<T>()
    data class Error(val message: String) : AsyncState<Error>()
    data object Loading : AsyncState<Nothing>()
}
```

### UI 层

```kotlin
// 网络失败 → Snackbar + 保留上次数据
// LLM 超时 → 展示固定话术
```

### 降级话术（待实现）

| 场景 | 展示内容 |
|------|---------|
| 网络断开 | "连接中断，使用离线数据" |
| Agent 超时 | "正在处理，请稍候…" |
| SSE 断开 | 静默切 polling，不提示用户 |
| 确认重复提交 | "该操作已处理，请勿重复操作" |

---

## 10. 文件所有权

```
开发 A（UI/体验）                   开发 B（集成/韧性）
──────────────────────────       ──────────────────────────
ui/home/                          data/remote/
ui/voice/（新建）                  data/repository/
ui/task/                          di/
ui/message/                       domain/model/（两人都改，先沟通）
ui/wearable/                      tests/
ui/common/SharedComponents.kt
ui/theme/Theme.kt
ui/navigation/Screen.kt
ui/navigation/AppNavigation.kt
```

---

## 附录：快速参考

### 关键类名

| 类 | 位置 | 作用 |
|----|------|------|
| `WorldState` | `domain/model/` | 根模型，所有 UI 的数据源 |
| `Event` | `domain/model/` | 事件信封，手机端唯一的写入方式 |
| `WorldStateRepository` | `data/repository/` | **接口**——UI 层唯一依赖 |
| `DefaultWorldStateRepository` | `data/repository/` | 生产实现，SSE + polling |
| `MockAgent` | `data/mock/` | OkHttp Interceptor，仅 debug |
| `SseClient` | `data/remote/` | Ktor SSE，自动重连 |

### 常用 Gradle 命令

```bash
./gradlew assembleDebug          # 编译
./gradlew installDebug           # 装到模拟器
./gradlew test                   # 单元测试
./gradlew ktlintCheck            # 代码风格（待配置）
```
