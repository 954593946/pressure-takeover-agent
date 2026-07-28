# 手机端 — AURI 随行压力接管 Agent

非驾驶状态下的任务与权限中心。当用户遭遇突发状况（会议超时、交通拥堵）时，AURI Agent 自动监控风险、重调度弹性任务、草拟通知消息、生成服务方案（如超市配送），并在高风险操作前请求用户确认。

## 快速开始

```bash
# 1. 下载 sherpa-onnx 离线 ASR 模型（~80MB，只需一次）
bash setup-sherpa.sh

# 2. 编译 & 安装
./gradlew assembleDebug      # 编译 APK
./gradlew installDebug       # 安装到模拟器/设备
./gradlew test               # 运行单元测试
```

> **模型和 APK 都不入 git**（`.gitignore` 已排除 `assets/models/` 和 `/app/build`）。克隆后必须跑 `setup-sherpa.sh` 才能编译。

**Debug 构建默认连接 Render 后端**，也可切回本地 MockAgent（修改 `app/build.gradle.kts` 中 `USE_MOCK_AGENT` 为 `true`）。

---

## 页面总览（7 个 Screen）

| 页面 | 路由 | 入口 | 功能 |
|------|------|------|------|
| 首页 | `home` | 底部 Tab | ★ 豆包风格：聊天对话流 + 底部语音输入栏、L0-L3 压力等级、驾驶 Companion 模式、上下文感知操作 |
| 任务列表 | `tasks` | 底部 Tab | 今日任务列表（刚性/弹性，状态，优先级，等待方，能力标签） |
| 消息 | `messages` | 底部 Tab | 消息动作列表（老师/家人），草稿预览、确认/拒绝 |
| 服务方案 | `service_plan` | 底部 Tab | 超市配送订单详情：商品明细、总价、配送窗口、预算状态 |
| 个人偏好 | `profile` | 底部 Tab | Profile 设置：效率/品质偏好、预算、触觉、替代策略 |
| 复盘 | `review` | 导航 | 停车后复盘：时间线、驾驶摘要、已执行动作 |
| 创建任务 | `task_create` | 任务页 FAB | 完整表单：标题、地点、时间、类型、优先级、等待方 |

---

## 文件结构

```
apps/mobile/
├── build.gradle.kts
├── gradle/libs.versions.toml                 # ★ 版本目录
│
└── app/src/main/java/com/pressureagent/mobile/
    ├── MobileApp.kt                          # @HiltAndroidApp
    ├── MainActivity.kt                       # @AndroidEntryPoint → AppNavigation()
    │
    ├── domain/
    │   ├── model/
    │   │   ├── WorldState.kt                 # v0.2 根模型：15 stage、L0-L3、Profile、ServiceOrder
    │   │   ├── Event.kt                      # v0.2 事件信封：13 种事件类型
    │   │   └── ChatMessage.kt                # 聊天消息（旧 VoiceChat 遗留，待整合）
    │   └── voice/
    │       ├── VoiceInputProvider.kt         # ★ ASR 抽象接口 + Flow 事件
    │       └── VoiceOutputProvider.kt        # ★ TTS 抽象接口
    │
    ├── data/
    │   ├── remote/
    │   │   ├── AgentApiService.kt            # Retrofit: v1/state、v1/event
    │   │   ├── SseClient.kt                  # Ktor SSE: v1/stream，自动重连
    │   │   ├── ChatApiService.kt
    │   │   └── ChatSseClient.kt
    │   ├── repository/
    │   │   ├── WorldStateRepository.kt       # ★ 接口 — UI 层唯一数据依赖
    │   │   ├── DefaultWorldStateRepository.kt # SSE 优先 → polling 降级
    │   │   ├── ChatRepository.kt
    │   │   └── DefaultChatRepository.kt
    │   ├── mock/
    │   │   ├── MockAgent.kt                  # OkHttp Interceptor 9 步状态机
    │   │   ├── StoryScript.kt                # v0.2 9 步 WorldState 快照
    │   │   └── MockChatRepository.kt
    │   ├── voice/
    │   │   ├── MockVoiceInputProvider.kt     # 模拟 ASR：延迟 800ms 返回文字
    │   │   ├── MockVoiceOutputProvider.kt    # 模拟 TTS：log 到控制台
    │   │   ├── AndroidVoiceInputProvider.kt  # ★ 真实 ASR（SpeechRecognizer）
    │   │   └── AndroidVoiceOutputProvider.kt # ★ 真实 TTS（TextToSpeech）
    │   └── local/
    │       └── CalendarHelper.kt
    │
    ├── di/
    │   ├── NetworkModule.kt                  # OkHttp + Retrofit + MockAgent
    │   ├── DataModule.kt                     # SseClient + WorldStateRepository
    │   ├── ChatModule.kt                     # Chat 相关绑定
    │   └── VoiceModule.kt                    # ★ 语音 I/O 绑定（换实现只改这里）
    │
    └── ui/
        ├── navigation/
        │   ├── Screen.kt                     # Sealed class: 8 个路由
        │   └── AppNavigation.kt              # NavHost + 5 Tab 底部导航
        ├── home/
        │   ├── HomeScreen.kt                 # ★ 豆包风格首页：聊天气泡 + 语音栏
        │   └── HomeViewModel.kt              # 聊天管理 + 语音流程 + 确认
        ├── task/
        │   ├── TaskListScreen.kt
        │   ├── TaskListViewModel.kt
        │   ├── CreateTaskScreen.kt
        │   └── CreateTaskViewModel.kt
        ├── message/
        │   ├── MessageListScreen.kt
        │   └── MessageListViewModel.kt
        ├── profile/
        │   ├── ProfileScreen.kt              # ★ 新增：个人偏好
        │   └── ProfileViewModel.kt
        ├── service/
        │   ├── ServicePlanScreen.kt           # ★ 新增：服务方案详情
        │   └── ServicePlanViewModel.kt
        ├── review/
        │   ├── ReviewScreen.kt               # ★ 新增：停车复盘
        │   └── ReviewViewModel.kt
        ├── wearable/
        │   ├── WearableScreen.kt
        │   └── WearableViewModel.kt
        ├── debug/
        │   ├── DebugScreen.kt
        │   └── DebugViewModel.kt
        ├── voice/                            # 旧 VoiceChat（未在导航中使用）
        ├── common/
        │   └── SharedComponents.kt           # TaskCard、RiskBanner、VehicleCard、WearableBar
        └── theme/
            └── Theme.kt                      # ★ AURI 品牌色（navy/gold/ivory + 状态色）
```

---

## 架构

```
┌──────────────────────────────────────────────────────────────────┐
│  UI 层                                                           │
│  ui/home/  ui/task/  ui/message/  ui/profile/  ui/service/      │
│  ui/review/  ui/wearable/  ui/debug/  ui/common/                │
│                                                                  │
│  ViewModel 只依赖 Repository 接口 + VoiceProvider 接口           │
│  不认识 OkHttp / Retrofit / SpeechRecognizer                    │
├──────────────────────────────────────────────────────────────────┤
│  Domain 层                                                       │
│  domain/model/    ← 纯 Kotlin，零 Android 依赖，对齐 contracts   │
│  domain/voice/    ← VoiceInputProvider / VoiceOutputProvider     │
├──────────────────────────────────────────────────────────────────┤
│  Data 层                                                         │
│  data/remote/     AgentApiService + SseClient（Ktor SSE）        │
│  data/repository/ WorldStateRepository（接口）+ impl              │
│  data/mock/       MockAgent + StoryScript（开发阶段）             │
│  data/voice/      Mock / Android 实现（ASR + TTS）               │
├──────────────────────────────────────────────────────────────────┤
│  DI 层                                                           │
│  di/NetworkModule.kt  di/DataModule.kt  di/ChatModule.kt         │
│  di/VoiceModule.kt                                               │
│  Hilt 自动注入，不手写 factory                                    │
└──────────────────────────────────────────────────────────────────┘
```

**铁律：**
1. `ui/` 只认识 `domain/` 和 `data/repository/*` 接口。不认识 `OkHttp`、`Retrofit`
2. `domain/` 是纯 Kotlin，不认识任何其他包
3. 跨层 import 方向：`ui → domain ← data`。绝对不出现 `domain → data` 或 `data → ui`
4. 手机端只上报事件，不直接改 World State
5. 中高风险动作必须先确认，`confirmation_id` 幂等

### 数据流

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

User tap → ViewModel.submitEvent(Event)
    → POST /v1/event → server → new WorldState via SSE → UI updates
```

---

## 语音扩展架构

语音输入/输出通过接口抽象，**换实现只改 DI 绑定，不改 UI 代码**：

```
VoiceInputProvider (接口)        VoiceOutputProvider (接口)
    ├── MockVoiceInputProvider        ├── MockVoiceOutputProvider
    └── AndroidVoiceInputProvider     └── AndroidVoiceOutputProvider

VoiceModule.kt ← 切换实现只改这里:
    fun provideVoiceInput(): VoiceInputProvider = MockVoiceInputProvider()   // 当前
    fun provideVoiceInput(): VoiceInputProvider = AndroidVoiceInputProvider() // 切真实
```

语音流程：
```
用户按麦克风 → VoiceInputProvider.listen()
    → Flow: ListeningStarted → PartialResult → FinalResult
    → ViewModel 收到 FinalResult → addUserChat() + submitUtterance()
    → Agent 回复 → VoiceOutputProvider.speak() 播报
```

---

## MockAgent 故事脚本（v0.2 — 9 步）

| Step | Stage | Scene | 关键变化 |
|------|-------|-------|---------|
| 0 | `off_vehicle_idle` | off_vehicle | 初始空闲 |
| 1 | `pre_departure_warning` | off_vehicle | 创建任务：接孩子(刚性) + 超市(弹性) |
| 2 | `meeting_overrun` | off_vehicle | 会议延迟，L1 预警 |
| 3 | `handover_to_vehicle` | approaching_vehicle | 上车，primary_surface → vehicle_hmi |
| 4 | `vehicle_observation` | driving | 驾驶中，ETA 18:10 |
| 5 | `takeover_L2` | high_load_driving | 拥堵，ETA 18:28（+18 min），L2 协调接管 |
| 6 | `planning` | high_load_driving | 规划：顺延超市 + 草拟消息 |
| 7 | `waiting_confirmation` | high_load_driving | ★ 核心 Demo — 消息+服务方案待确认 |
| 8 | `action_completed` | driving | 确认完成，消息已发送，订单已提交 |

任意 POST `/v1/event` 前进 1 步；Debug 页面可手动跳转。

---

## AURI 品牌色

| Token | 色值 | 用途 |
|-------|------|------|
| `--auri-navy` | `#0B1B33` | 主品牌色、标题、深色按钮 |
| `--auri-gold` | `#D4AF7A` | 品牌强调、Logo 光环 |
| `--auri-ivory` | `#F5F2EC` | 背景色、卡片底 |
| `--state-processing` | `#2F6BFF` | 驾驶连接、处理中 |
| `--state-warning` | `#E6A700` | L1/L2 预警 |
| `--state-success` | `#2E9D6F` | 已完成、恢复态 |
| `--state-critical` | `#D1495B` | L3 高负荷、错误 |

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
1. `Screen.kt` 加 `data object`
2. `AppNavigation.kt` 加 `composable()` 路由（如需底部 Tab 再加 `NavigationBarItem`）
3. 新建 `ui/feature/FeatureScreen.kt` + `FeatureViewModel.kt`
4. ViewModel 注入 `WorldStateRepository`，collect `repository.worldState`

### 新增事件类型
1. `Event.kt` 的 `EventType` enum 加值 + `@SerialName`
2. ViewModel 中调用 `repository.submitEvent(event)`
3. （可选）`MockAgent.kt` 中对应处理

### 新增领域字段
1. `WorldState.kt` 加字段（nullable 保证向后兼容）
2. `StoryScript.kt` 快照补上字段
3. UI 中展示

### 切换 Mock ↔ 真实后端
- Debug 构建：`buildConfigField USE_MOCK_AGENT = true`
- Release 构建：`buildConfigField USE_MOCK_AGENT = false`
- MockAgent 只在 `USE_MOCK_AGENT == true` 时创建

### 切换语音实现
- `VoiceModule.kt` 中替换 `MockVoiceInputProvider` → `AndroidVoiceInputProvider`
- `VoiceModule.kt` 中替换 `MockVoiceOutputProvider` → `AndroidVoiceOutputProvider`
- UI 层零改动
