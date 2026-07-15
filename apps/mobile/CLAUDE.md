# 手机端 — 随行压力接管 Agent

## 架构

```
Clean Architecture 3 层：
  UI (Compose screens + ViewModels)
  → Domain (models, no Android dependency)
  → Data (repository impls, remote, mock)

DI: Hilt (Singleton component, @HiltViewModel)
Navigation: Jetpack Navigation Compose (bottom nav, 5 tabs)
State: UDF — StateFlow from ViewModel, events up via lambda
Streaming: SSE (Ktor) with auto-reconnect, polling fallback
```

## 分层

```
com.pressureagent.mobile/
├── domain/model/            # ★ 纯 Kotlin，无 Android 依赖
│   ├── WorldState.kt        # 17 enum + 8 data class（schema 完整映射）
│   └── Event.kt             # 事件信封 + 9 种事件类型
│
├── data/
│   ├── remote/
│   │   ├── AgentApiService.kt    # Retrofit 接口
│   │   └── SseClient.kt          # Ktor SSE，自动重连 + 指数退避
│   ├── repository/
│   │   ├── WorldStateRepository.kt        # ★ Interface（可测试，可替换）
│   │   └── DefaultWorldStateRepository.kt # SSE 优先 → polling 降级
│   └── mock/
│       ├── MockAgent.kt       # OkHttp Interceptor 状态机
│       └── StoryScript.kt     # 9 步预定义世界状态快照
│
├── di/
│   ├── NetworkModule.kt       # Hilt module: OkHttp, Retrofit, MockAgent?
│   └── DataModule.kt          # Hilt module: SseClient, WorldStateRepository
│
├── ui/
│   ├── navigation/
│   │   ├── Screen.kt          # Sealed class: Home/Tasks/Messages/Wearable/Debug
│   │   └── AppNavigation.kt  # NavHost + bottom nav bar
│   ├── home/       HomeScreen + HomeViewModel
│   ├── task/       TaskListScreen + TaskListViewModel
│   ├── message/    MessageListScreen + MessageListViewModel
│   ├── wearable/   WearableScreen + WearableViewModel
│   ├── debug/      DebugScreen + DebugViewModel（mock 模式可用）
│   ├── common/     TaskCard, RiskBanner, VehicleCard, WearableBar
│   └── theme/      Material 3 color scheme
│
├── MobileApp.kt    # @HiltAndroidApp
└── MainActivity.kt # @AndroidEntryPoint → AppNavigation()
```

## 如何扩展

### 新增一个页面
1. 在 `ui/navigation/Screen.kt` 加一条 `data object`
2. 在 `AppNavigation.kt` 的 `composable()` 块加一行路由
3. 新建 `ui/your-feature/YourScreen.kt` + `YourViewModel.kt`
4. ViewModel 注入 `WorldStateRepository`，collect `repository.worldState`

### 新增一种事件类型
1. `domain/model/Event.kt` 的 `EventType` enum 加值
2. 对应 ViewModel 里调用 `repository.submitEvent(event)`
3. `data/mock/MockAgent.kt` 里对应处理（可选）

### 切换 mock ↔ 真实后端
- `gradle/libs.versions.toml` → 改 `USE_MOCK_AGENT` buildConfig
- 或运行时通过 Hilt `@Named` 替换 binding
- MockAgent 只在 `BuildConfig.USE_MOCK_AGENT == true` 时创建

### 替换 SSE 实现
- 实现 `WorldStateRepository` 接口的新类
- 在 `DataModule.kt` 替换 `@Provides` 绑定
- 不改任何 UI 代码

## 技术栈

| 层 | 技术 |
|----|------|
| DI | Hilt 2.53 + KSP |
| Navigation | Jetpack Navigation Compose 2.8 |
| UI | Compose BOM 2024.12 + Material 3 |
| Network | Retrofit 2.11 + OkHttp 4.12 |
| SSE | Ktor Client 3.0 (OkHttp engine) |
| Serialization | kotlinx.serialization 1.7 |
| Min/Target SDK | 26 / 35, JVM 17 |

## MockAgent 故事脚本（9 步）

| Step | Stage | 说明 |
|------|-------|------|
| 0 | idle | 初始空白 |
| 1 | task_created | 接孩子(刚性) + 超市(弹性) |
| 2 | meeting_delay | 会议延迟，风险 watch |
| 3 | departure_warning | 最晚出发警告 |
| 4 | vehicle_mode | 驾驶中 |
| 5 | traffic_delay | 拥堵，ETA 18:28（+18 min） |
| 6 | pressure_takeover | Agent 接管 |
| 7 | waiting_confirmation | 消息待确认 ← 核心状态 |
| 8 | action_completed | 确认，消息已发送 |

任意 POST /v1/events 前进 1 步；Debug 页面可手动跳转。

## 约束

- 手机端只上报事件，不直接改 World State
- repo 接口 `WorldStateRepository` 是 UI 层唯一的 data 依赖
- 中高风险动作必须先确认；`confirmation_id` 幂等
