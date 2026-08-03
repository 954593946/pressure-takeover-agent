# 跨端固定场景

这里保存不含隐私的标准输入、事件序列和 World State 快照，用于各端独立开发、回归测试和无网演示兜底。

建议至少维护：

- `happy-path`：完整接孩子接管流程。
- `duplicate-event`：相同 Event 重试只推进一次 revision。
- `duplicate-confirmation`：相同确认连续提交只执行一次。
- `wrong-surface`：非 owner 端确认返回 `WRONG_SURFACE`。
- `over-budget` / `out-of-stock`：生活服务异常不得越权执行。
- `new-session`：旧 Session 事件返回 `SESSION_MISMATCH`。
- `offline-reconnect`：断线期间不假更新，恢复后以完整快照追平。
- `llm-timeout`：使用固定草稿和话术。
- `wearable-offline`：手机与车机继续闭环。

异常 fixture 统一包含 `scenario`、`purpose`、`steps` 和 `expected`。`$session_id`、`$confirmation_id` 等 `$` 前缀值由测试运行器从当前隔离 Runtime 注入；fixture 本身不得包含真实 Token、Key 或个人信息。
