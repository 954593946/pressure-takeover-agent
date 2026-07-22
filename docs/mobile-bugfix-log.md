# 手机端 Bug 修复记录

## 2026-07-22 — SSE 鉴权 + 自动滚底 + 日志查看器 + 闪退防护

### BUG #1: 聊天发消息无响应

**现象**: 创建任务正常，但聊天发消息后无反应，AI placeholder 一直空着。

**根因**:
1. `ChatSseClient` 自建 `HttpClient` 没带 `X-Agent-Token` header，后端返回 `401`，SSE 无事件闭合。
2. `ChatSseClient.awaitClose { client.close() }` — 每次流结束关闭共享客户端，导致后续请求全挂。
3. `ChatViewModel` — 空流静默完成时无 fallback，`isLoading` 永远 `true`。

**文件**:
- `data/remote/ChatSseClient.kt` — 添加 `token` 参数并在 POST 头中加入 `X-Agent-Token`；HTTP 状态非 2xx 直接报错；`awaitClose` 不再关闭共享客户端。
- `di/ChatModule.kt` — 注入 `BuildConfig.AGENT_API_TOKEN`。
- `ui/chat/ChatViewModel.kt` — 流完成无内容时删除空 placeholder 并降级到 Event API。
- `data/repository/DefaultChatRepository.kt` — SSE 失败后尝试 Retrofit 非流式降级，添加日志。

---

### BUG #2: 聊天列表不自动滚到底部

**现象**: 新消息到达后列表停留在顶部，需手动下滑。

**根因**: `ChatScreen.kt:97` — `listState.animateScrollToItem(0)` 滚到了顶部。

**修复**: 改为 `animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)` 滚到最后一个 item。

**文件**: `ui/chat/ChatScreen.kt`

---

### BUG #3: 多轮对话后闪退

**现象**: 连续发送约 6 条消息后 App 闪退，无 ERROR 日志。

**根因**: `ChatSseClient` 和 `SseClient` 的 OkHttp engine 配置 `readTimeout(0)`（永不超时）。当协程被 `cancel()` 中断时，底层 OkHttp `read()` 在 native 层阻塞无法被中断，线程永久卡死。5-6 条消息后 OkHttp 线程池耗尽，系统杀死进程。

**修复**:
1. `ChatSseClient` — `readTimeout`: `0` → `120s`（有限超时可被中断）
2. `SseClient` — `readTimeout`: `0` → `300s`（5 分钟超时，到期自动重连）
3. `DefaultWorldStateRepository` — 轮询间隔 `1s` → `5s`，减少连接压力
4. 新增 `CrashHandler` — 全局未捕获异常兜底，崩溃前写日志到文件

**文件**:
- `data/remote/ChatSseClient.kt`
- `data/remote/SseClient.kt`
- `data/repository/DefaultWorldStateRepository.kt`
- `data/local/CrashHandler.kt`（新增）

---

### FEATURE: 日志查看器

**说明**: 在「我的」tab 新增日志查看器入口，方便在 App 内直接查看运行日志排查问题。

**能力**:
- 内存环形缓冲区（500 条）+ 文件持久化（`auri_app.log`）
- 按级别过滤（DEBUG / INFO / WARN / ERROR）
- 双数据源切换：AppLogger 应用日志 / 系统 Logcat
- 自动滚动到最新 + 暂停/恢复滚动
- App 启动时自动初始化并记录配置信息

**文件**:
- `data/local/AppLogger.kt`（新增）
- `ui/log/LogViewerScreen.kt`（新增）
- `ui/navigation/Screen.kt` — 新增 `LogViewer` 路由
- `ui/navigation/AppNavigation.kt` — 注册 log_viewer 页面
- `ui/profile/ProfileScreen.kt` — 新增 📋 日志查看器入口
- `MobileApp.kt` — 启动时初始化 AppLogger + CrashHandler
