# 手机端开发手册

协作开发规范、代码模式和任务拆解。

---

## 目录

1. [当前状态](#1-当前状态)
2. [架构速览](#2-架构速览)
3. [包职责](#3-包职责)
4. [约定](#4-约定)
5. [How-to：新增一个页面](#5-how-to新增一个页面)
6. [How-to：新增一种事件类型](#6-how-to新增一种事件类型)
7. [How-to：新增一个领域字段](#7-how-to新增一个领域字段)
8. [How-to：切换语音实现](#8-how-to切换语音实现)
9. [错误处理](#9-错误处理)
10. [文件所有权](#10-文件所有权)

---

## 1. 当前状态

**2026-07-16 — v0.2 对齐完成**

- 54 个 Kotlin 源文件，编译通过，APK 可安装
- Domain model 对齐 contracts v0.2（15 stage、L0-L3、Profile、ServiceOrder）
- MockAgent 9 步故事脚本完整，Debug 构建自动启用
- 5 个底部 Tab + 2 个二级页面
- 首页：豆包风格对话流 + 底部语音输入栏（默认可语音）
- AURI 品牌色全局应用（navy/gold/ivory + 状态色）
- 语音 I/O 抽象层就绪（接口 + Mock + Android 实现）

**可运行的 Demo 流程：**
1. 打开 App → 首页语音按钮 → 说"帮我创建任务"
2. 或切到调试 Tab → 点"下一步" → 回首页看变化
3. 主操作按钮随 Stage 自动变化，推进完整 9 步故事

---

## 2. 架构速览

```
┌──────────────────────────────────────────────────────────────┐
│  UI 层                                                       │
│  ui/home/  ui/task/  ui/message/  ui/profile/               │
│  ui/service/  ui/review/  ui/wearable/  ui/debug/            │
│  ui/common/  ui/navigation/  ui/theme/                       │
│                                                              │
│  ViewModel 依赖: WorldStateRepository + VoiceInputProvider   │
│                + VoiceOutputProvider                         │
├──────────────────────────────────────────────────────────────┤
│  Domain 层                                                   │
│  domain/model/    ← 纯 Kotlin，零 Android 依赖               │
│  domain/voice/    ← VoiceInputProvider / VoiceOutputProvider │
├──────────────────────────────────────────────────────────────┤
│  Data 层                                                     │
│  data/remote/     AgentApiService + SseClient                │
│  data/repository/ WorldStateRepository（接口）+ impl          │
│  data/mock/       MockAgent + StoryScript                    │
│  data/voice/      Mock / Android 语音实现                    │
├──────────────────────────────────────────────────────────────┤
│  DI 层                                                       │
│  di/NetworkModule.kt  di/DataModule.kt                       │
│  di/ChatModule.kt     di/VoiceModule.kt                      │
└──────────────────────────────────────────────────────────────┘
```

**数据流向：**

```
Agent API ──SSE/polling──→ WorldStateRepository
                                    │
                            worldState: Flow<WorldState>
                                    │
            ┌───────────┬───────────┼───────────┬───────────┐
            ▼           ▼           ▼           ▼           ▼
        HomeVM      TaskVM     MessageVM   ProfileVM   ServicePlanVM
            │           │           │           │           │
            ▼           ▼           ▼           ▼           ▼
      HomeScreen  TaskScreen  MessageScreen  ...

User → Event → POST /v1/event → server → new WorldState via SSE → UI updates
```

**唯一数据源：** `WorldStateRepository.worldState: Flow<WorldState>`
所有 ViewModel 从同一个 Flow 拿数据，不各自缓存、不各自轮询。

---

## 3. 包职责

| 包 | 放什么 | 不放什么 |
|----|--------|---------|
| `domain/model/` | `data class`、`enum`、与 contracts 对齐 | 业务逻辑、格式化 |
| `domain/voice/` | ASR/TTS 抽象接口 + 事件类 | Android 平台代码 |
| `data/repository/` | `WorldStateRepository` 接口 + impl | UI 相关代码 |
| `data/remote/` | HTTP 调用、SSE 解析 | 状态管理 |
| `data/mock/` | 开发阶段假数据 | 生产代码 |
| `data/voice/` | 语音 I/O 具体实现 | UI 逻辑 |
| `di/` | Hilt `@Module` `@Provides` | 业务代码 |
| `ui/home/` | 首页（豆包风格对话流） | 其他页面组件 |
| `ui/common/` | 被**两个以上** feature 用的 `@Composable` | 单 feature 组件 |
| `ui/navigation/` | 路由定义 + NavHost | 页面内容 |

**铁律：**
1. `ui/` 只认识 `domain/` 和 `data/repository/`，不认识 `data/remote/`、`OkHttp`
2. `domain/` 不认识任何其他包——它是纯 Kotlin
3. 跨层 import 方向：`ui → domain ← data`

---

## 4. 约定

### 文件命名

```
Screen 文件:     [Feature]Screen.kt      例: ProfileScreen.kt
ViewModel 文件:  [Feature]ViewModel.kt   例: ProfileViewModel.kt
UiState:         [Feature]UiState        例: ProfileUiState
接口文件:         PascalCase.kt           例: VoiceInputProvider.kt
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

在 `gradle/libs.versions.toml` 加条目，然后在 `app/build.gradle.kts` 引用。
不要直接在 `build.gradle.kts` 里写硬编码版本号。

---

## 5. How-to：新增一个页面

以新增"设置页"为例：

### Step 1：定义路由

```kotlin
// ui/navigation/Screen.kt
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // … existing …
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}
```

### Step 2：注册导航

```kotlin
// ui/navigation/AppNavigation.kt
// NavHost 的 composable 块里加：
composable(Screen.Settings.route) { SettingsScreen() }

// 加到底部导航：在 tabs 列表里加上 Screen.Settings
```

### Step 3：写 ViewModel + UiState

```kotlin
// ui/settings/SettingsViewModel.kt
data class SettingsUiState(val serverUrl: String = "")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws -> /* map to UI */ }
        }
    }
}
```

### Step 4：写 Screen

```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    // Compose UI
}
```

**完成。** Hilt 自动提供 ViewModel，不需要写 factory、不需要改 DI 模块。

---

## 6. How-to：新增一种事件类型

### Step 1：在 Event.kt 注册

```kotlin
// domain/model/Event.kt
enum class EventType {
    // … existing …
    @SerialName("settings.changed") SETTINGS_CHANGED,
}
```

### Step 2：在 ViewModel 中发送

```kotlin
val event = Event(
    eventId = UUID.randomUUID().toString(),
    sessionId = "",
    type = EventType.SETTINGS_CHANGED,
    source = EventSource.MOBILE,
    timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    payload = buildJsonObject { put("key", key); put("value", value) },
)
repository.submitEvent(event)
```

### Step 3：（可选）MockAgent 中处理

```kotlin
// data/mock/MockAgent.kt — handlePostEvent() 中
EventType.SETTINGS_CHANGED -> { /* 记录设置，不推进故事 */ }
```

**不需要改：** Repository 接口、DI、UI 层。

---

## 7. How-to：新增一个领域字段

以给 `Task` 加 `notes: String?` 为例：

### Step 1：改 Schema（contracts/）

```json
// contracts/world-state.schema.json — Task properties
"notes": { "type": "string" }
```

### Step 2：改 Kotlin 模型

```kotlin
// domain/model/WorldState.kt
data class Task(
    // … existing …
    val notes: String? = null,  // nullable 保证向后兼容
)
```

### Step 3：改 Mock 数据

```kotlin
// data/mock/StoryScript.kt — 用到的快照加上 notes = null
```

### Step 4：改 UI（如果需要展示）

```kotlin
// 在对应 Screen 或 common 组件中加展示逻辑
```

**不需要改：** ViewModel、Repository、DI——`Flow<WorldState>` 自动包含新字段。

---

## 8. How-to：切换语音实现

当前使用 Mock 语音（模拟识别 + log 播报）。切换到真实 Android ASR/TTS：

### 改一行

```kotlin
// di/VoiceModule.kt

// 当前（Mock）：
fun provideVoiceInputProvider(): VoiceInputProvider = MockVoiceInputProvider()
fun provideVoiceOutputProvider(): VoiceOutputProvider = MockVoiceOutputProvider()

// 切换到真实语音：
fun provideVoiceInputProvider(): VoiceInputProvider = AndroidVoiceInputProvider()
fun provideVoiceOutputProvider(
    @ApplicationContext context: Context,
): VoiceOutputProvider = AndroidVoiceOutputProvider(context).also { it.initialize() }
```

### 加权限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### UI 层零改动

HomeScreen / HomeViewModel 只管注入 `VoiceInputProvider` / `VoiceOutputProvider` 接口，不感知具体实现。

---

## 9. 错误处理

### 当前策略

| 场景 | 处理 |
|------|------|
| SSE 断开 | 静默切 polling，不提示 |
| polling 失败 | 保留上次值，不抛异常 |
| 网络失败 | Snackbar + 保留上次数据 |
| 重复确认 | confirmation_id 幂等，后端去重 |
| 语音识别失败 | VoiceInputEvent.Error → Snackbar |

### Repository 层

```kotlin
// DefaultWorldStateRepository: SSE 断开 → 自动切 polling
// polling 失败 → 保留上次值，不抛异常
```

---

## 10. 文件所有权

```
共享基础（两人都改，先沟通）
────────────────────────────────
domain/model/WorldState.kt       ← 领域模型变更
domain/model/Event.kt            ← 事件类型变更
data/mock/StoryScript.kt         ← Mock 故事数据
ui/common/SharedComponents.kt    ← 共享组件
ui/navigation/                   ← 路由 + Tab 配置
gradle/libs.versions.toml        ← 版本管理

业务 UI（A）
────────────────────────────────
ui/home/         ← 首页 + 语音交互
ui/task/         ← 任务列表 + 创建
ui/message/      ← 消息中心
ui/profile/      ← 个人偏好
ui/service/      ← 服务方案
ui/review/       ← 复盘
ui/theme/        ← 主题

集成/韧性（B）
────────────────────────────────
data/remote/     ← API + SSE 客户端
data/repository/ ← 仓库实现
di/              ← Hilt 模块
data/voice/      ← 语音实现
data/mock/       ← MockAgent 状态机
```

---

## 附录：关键类速查

| 类 | 位置 | 作用 |
|----|------|------|
| `WorldState` | `domain/model/` | v0.2 根模型，所有 UI 的数据源 |
| `Event` | `domain/model/` | 事件信封，手机端唯一写入方式 |
| `WorldStateRepository` | `data/repository/` | ★ 接口 — UI 层唯一数据依赖 |
| `DefaultWorldStateRepository` | `data/repository/` | 生产实现，SSE + polling |
| `MockAgent` | `data/mock/` | OkHttp Interceptor，仅 debug |
| `StoryScript` | `data/mock/` | 9 步 v0.2 预定义 WorldState |
| `VoiceInputProvider` | `domain/voice/` | ★ ASR 接口 |
| `VoiceOutputProvider` | `domain/voice/` | ★ TTS 接口 |
| `HomeUiState` | `ui/home/` | 首页状态（含聊天消息列表） |
| `ChatItem` | `ui/home/` | 聊天消息（用户/AURI） |

### 常用命令

```bash
./gradlew assembleDebug          # 编译 APK
./gradlew installDebug           # 安装到模拟器/设备
./gradlew compileDebugKotlin     # 仅编译 Kotlin（快）
./gradlew test                   # 单元测试
```
