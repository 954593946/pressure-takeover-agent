# 开发踩坑记录

> feat/mobile-app-init 分支，手机端 v0.4 → 迭代中遇到的问题与解决方案。

---

## 一、闪退类

### 1. sherpa-onnx 每次 listen() 重建 Recognizer 导致 5s 延迟 + 闪退

**现象**：点语音按钮后等 ~5 秒才开始识别，多次点击闪退。

**根因**：`SherpaVoiceInputProvider.listen()` 每次调用都重新创建 `OnlineRecognizer`（加载 3 个 ONNX 模型文件，~80MB），用完又释放。模型加载耗时 3-5s，且频繁创建/销毁原生对象容易触发 native 层 SIGSEGV。

**修复**（`SherpaVoiceInputProvider.kt`）：
- Recognizer 改为构造时后台线程预加载，之后复用
- 每次 `listen()` 只创建轻量的 `OnlineStream`
- 原子锁 `listenInProgress` 防重叠调用

### 2. 快速点击语音按钮闪退

**现象**：快速点停止→开始，App 崩溃。

**根因**：两个独立问题叠加。
- **UI 状态不同步**：`onVoiceToggle()` 用 `voiceInput.isListening` 判断启动/停止，但这个值要等麦克风打开后才变 true。UI 已经显示停止按钮，点下去底层判断还没在听，走了启动分支，两个 `listen()` 并发。
- **Stream 未 reset 就 release**：协程取消时跳过了 `rec.reset(stream)`，sherpa-onnx 内部状态残留。

**修复**：
- `onVoiceToggle()` 改用 `_uiState.isListening` 判断（`ChatViewModel.kt`）
- `rec.reset(stream)` 移到 `finally` 块确保所有退出路径都执行（`SherpaVoiceInputProvider.kt`）

### 3. 日历页闪退 — LazyColumn key 重复

**现象**：点日历 Tab 直接崩。

**根因**：`IllegalArgumentException: Key "task_manual_review" was already used.` — Render 后端返回的任务列表里有重复的 `taskId`，LazyColumn 要求 key 唯一。

**修复**（`CalendarViewModel.kt` + `CalendarScreen.kt`）：
- ViewModel 层 `distinctBy { it.taskId }` 源头去重
- Screen 层 LazyColumn items 加 `distinctBy` 兜底

---

## 二、连接状态

### 4. 不知道连没连上后端

**现象**：没有连接状态指示，用户不知道 App 和后端的通信状态。

**修复**（`WorldStateRepository.kt` → `ChatScreen.kt`）：
- 新增 `ConnectionStatus` 枚举：`INITIALIZING / CONNECTED / POLLING / DISCONNECTED`
- `DefaultWorldStateRepository` 跟踪 SSE 和 polling 状态
- 对话页顶栏显示状态灯：🟢 实时 / 🟡 轮询 / 🔴 断开

> **轮询（黄色）= 也是连上的**。SSE 流断了，降级到每秒 HTTP 轮询。数据还在同步，只是比实时慢一点。

---

## 三、功能问题

### 5. TTS 语音播报静默失效

**现象**：Agent 回复有文字但手机不念。

**根因**：TTS 引擎初始化失败时无声失败，没有错误提示。且默认开启，用户以为没开声音。

**修复**（`ChatScreen.kt` + `ChatViewModel.kt`）：
- 顶栏加 🔊/🔇 切换按钮
- 默认关闭

### 6. 创建任务不显示

**现象**：创建任务页提交后返回日历，任务不出现。

**根因**：创建任务发了 `TASK_CREATED` 事件给 Render 后端 → 返回 409（后端不支持该事件类型）。改成 `USER_UTTERANCE` 后，后端处理是异步的，WorldState 不会立刻更新。

**最终方案**（`LocalTaskStore.kt`）：
- 简单任务不走后端，纯本地创建
- `LocalTaskStore` 内存单例 + JSON 文件持久化（`local_tasks.json`）
- `CalendarViewModel` 用 `combine` 合并后端任务 + 本地任务
- 进程被杀不丢

### 7. 创建任务表单太复杂

**现象**：标题、地点、日期选择器、刚性/弹性、优先级、等待方…太多字段。

**修复**：简化为标题 + 日期时间选择器两个字段。复杂任务去对话 Tab 直接说，让 Agent 解析。

### 8. 日历月视图太满

**现象**：6 行月历格子占满屏幕，不实用。

**修复**：默认只显示选中日期所在的那一周，点 ▼ 展开全月。

### 9. 日历卡片时间不显眼

**修复**：时间从右侧小字移到卡片最左边，大字显示 `HH:mm` + 小字日期。

---

## 四、架构债务（已知，未修）

| 问题 | 位置 | 风险 |
|------|------|------|
| SSE 断线后 client.close() 致重连永久失败 | `SseClient.kt:94` | 网络抖动后需要杀进程才能恢复 |
| sherpa-onnx 模型 ~80MB 永远不释放 | `SherpaVoiceInputProvider.kt` | 低端机内存压力 |
| TTS 引擎初始化失败无抛错 | `AndroidVoiceOutputProvider.kt` | 静默失效 |
| `DefaultWorldStateRepository` scope 从未 cancel | `DataModule.kt` | 长期运行资源泄漏 |

---

## 五、经验教训

1. **sherpa-onnx 原生库是最大不稳定源**。离线 ASR 的 native 代码一旦崩，Java 层 try-catch 完全兜不住。快速点击、内存压力、模型文件损坏都可能导致 SIGSEGV。防御策略：复用 Recognizer、原子锁防重叠、reset 后再 release。

2. **后端状态 ≠ 前端状态**。不要假设后端同步返回更新后的数据。简单本地操作（如创建任务）应该本地存储 + 前端展示优先，后端同步是次要的。

3. **LazyColumn key 必须唯一**。这个崩溃不会在 AS 里警告，运行时直接炸。后端返回数据不可信，前端必须去重。

4. **UI 状态和底层状态要同源**。`onVoiceToggle()` 用 provider 状态判断就是典型的双源不一致 bug。应该以 UI 的 `StateFlow` 为准。
