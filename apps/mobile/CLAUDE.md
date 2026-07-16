# 手机端 — AURI 随行压力接管 Agent

## 一句话理解

手机端只提交 `Event`，Agent/后端是唯一 `WorldState` 写入者。手机通过 SSE/polling 接收状态流，VoiceInputProvider 处理语音输入，VoiceOutputProvider 处理语音播报。

## 架构

```
Clean Architecture 3 层：
  UI (Compose screens + ViewModels)
  → Domain (纯 Kotlin models + voice interfaces, 零 Android 依赖)
  → Data (repository impls, remote API/SSE, mock, voice impls)

DI: Hilt (Singleton component, @HiltViewModel)
Navigation: Jetpack Navigation Compose (bottom nav, 5 tabs)
State: UDF — StateFlow from ViewModel, events up via lambda
Streaming: SSE (Ktor) with auto-reconnect, polling fallback
Voice: VoiceInputProvider / VoiceOutputProvider 接口 + Mock/Android 实现
```

## 分层

```
com.pressureagent.mobile/
├── domain/
│   ├── model/                # ★ 纯 Kotlin，对齐 contracts v0.2
│   │   ├── WorldState.kt     # 15 stage + L0-L3 + Profile + ServiceOrder + Action + Wearable + Confirmation
│   │   ├── Event.kt          # 13 种事件类型 + EventResponse
│   │   └── ChatMessage.kt    # 聊天消息（旧 VoiceChat 遗留）
│   └── voice/
│       ├── VoiceInputProvider.kt   # ★ ASR 接口（Flow<VoiceInputEvent>）
│       └── VoiceOutputProvider.kt  # ★ TTS 接口
│
├── data/
│   ├── remote/
│   │   ├── AgentApiService.kt    # Retrofit: v1/state, v1/event
│   │   └── SseClient.kt          # Ktor SSE: v1/stream, 自动重连 + 指数退避
│   ├── repository/
│   │   ├── WorldStateRepository.kt        # ★ Interface（UI 层唯一数据依赖）
│   │   └── DefaultWorldStateRepository.kt # SSE 优先 → polling 降级
│   ├── mock/
│   │   ├── MockAgent.kt       # OkHttp Interceptor 状态机
│   │   └── StoryScript.kt     # v0.2 9 步预定义 WorldState 快照
│   ├── voice/
│   │   ├── MockVoiceInputProvider.kt     # 模拟 ASR
│   │   ├── MockVoiceOutputProvider.kt    # 模拟 TTS（log 到控制台）
│   │   ├── AndroidVoiceInputProvider.kt  # 真实 SpeechRecognizer
│   │   └── AndroidVoiceOutputProvider.kt # 真实 TextToSpeech
│   └── local/CalendarHelper.kt
│
├── di/
│   ├── NetworkModule.kt       # OkHttp, Retrofit, MockAgent?
│   ├── DataModule.kt          # SseClient, WorldStateRepository
│   ├── ChatModule.kt          # Chat 绑定
│   └── VoiceModule.kt         # ★ 语音 I/O 绑定（换实现只改这里）
│
├── ui/
│   ├── navigation/
│   │   ├── Screen.kt          # Sealed class: Home/Tasks/Messages/ServicePlan/Profile + Debug/Review/TaskCreate
│   │   └── AppNavigation.kt  # NavHost + bottom nav bar (5 tabs)
│   ├── home/       HomeScreen + HomeViewModel（★ 豆包风格：聊天气泡 + 语音栏）
│   ├── task/       TaskListScreen + CreateTaskScreen + ViewModels
│   ├── message/    MessageListScreen + MessageListViewModel
│   ├── profile/    ProfileScreen + ViewModel（★ 新增）
│   ├── service/    ServicePlanScreen + ViewModel（★ 新增）
│   ├── review/     ReviewScreen + ViewModel（★ 新增：停车复盘）
│   ├── wearable/   WearableScreen + WearableViewModel
│   ├── debug/      DebugScreen + DebugViewModel（mock 模式可用）
│   ├── voice/      VoiceChatScreen 等（旧 AI 助手，未在导航中使用）
│   ├── common/     TaskCard, RiskBanner, VehicleCard, WearableBar
│   └── theme/      AURI 品牌色（navy #0B1B33 / gold #D4AF7A / ivory #F5F2EC + 状态色）
│
├── MobileApp.kt    # @HiltAndroidApp
└── MainActivity.kt # @AndroidEntryPoint → AppNavigation()
```

## 如何扩展

### 新增一个页面
1. 在 `ui/navigation/Screen.kt` 加一条 `data object`
2. 在 `AppNavigation.kt` 的 `composable()` 块加一行路由（如需 Tab 再加 NavigationBarItem）
3. 新建 `ui/your-feature/YourScreen.kt` + `YourViewModel.kt`
4. ViewModel 注入 `WorldStateRepository`，collect `repository.worldState`

### 新增一种事件类型
1. `domain/model/Event.kt` 的 `EventType` enum 加值 + `@SerialName`
2. 对应 ViewModel 里调用 `repository.submitEvent(event)`
3. `data/mock/MockAgent.kt` 里对应处理（可选）

### 切换语音实现
1. `di/VoiceModule.kt` 中 `MockVoiceInputProvider` → `AndroidVoiceInputProvider`
2. `di/VoiceModule.kt` 中 `MockVoiceOutputProvider` → `AndroidVoiceOutputProvider`
3. AndroidManifest 加 RECORD_AUDIO 权限
4. UI 层零改动

### 切换 mock ↔ 真实后端
- `gradle/libs.versions.toml` → 改 `USE_MOCK_AGENT` buildConfig
- MockAgent 只在 `BuildConfig.USE_MOCK_AGENT == true` 时创建

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| DI | Hilt + KSP | 2.53.1 |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| UI | Compose BOM 2024.12 + Material 3 | — |
| Network | Retrofit 2.11 + OkHttp 4.12 | — |
| SSE | Ktor Client 3.0.3 (OkHttp engine) | — |
| Serialization | kotlinx.serialization 1.7.3 | — |
| Min/Target SDK | 26 / 35, JVM 17 | — |

## MockAgent 故事脚本（v0.2 — 9 步）

| Step | Stage | Scene | 说明 |
|------|-------|-------|------|
| 0 | off_vehicle_idle | off_vehicle | 初始空闲 |
| 1 | pre_departure_warning | off_vehicle | 创建任务：接孩子(刚性) + 超市(弹性) |
| 2 | meeting_overrun | off_vehicle | 会议延迟，L1 预警 |
| 3 | handover_to_vehicle | approaching_vehicle | 上车，primary_surface → vehicle_hmi |
| 4 | vehicle_observation | driving | 驾驶观察 |
| 5 | takeover_L2 | high_load_driving | 拥堵 ETA 18:28，L2 接管 |
| 6 | planning | high_load_driving | 规划：顺延 + 草拟消息 + 服务方案 |
| 7 | waiting_confirmation | high_load_driving | ★ 核心 — 消息+服务方案待确认 |
| 8 | action_completed | driving | 消息已发送，订单已提交 |

任意 POST /v1/event 前进 1 步；Debug 页面可手动跳转。

## 约束

- 手机端只上报事件，不直接改 World State
- `WorldStateRepository` 是 UI 层唯一的 data 依赖接口
- 中高风险动作必须先确认；`confirmation_id` 幂等
- 驾驶中 `primary_surface != mobile` 时，手机进入 Companion 只读模式
- 语音通过 `VoiceInputProvider` / `VoiceOutputProvider` 接口，不硬编码实现
