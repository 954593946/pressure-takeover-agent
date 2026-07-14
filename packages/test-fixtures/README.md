# 跨端固定场景

这里保存不含隐私的标准输入、事件序列和 World State 快照，用于各端独立开发、回归测试和无网演示兜底。

建议至少维护：

- `happy-path`：完整接孩子接管流程。
- `duplicate-confirmation`：语音和按钮并发确认。
- `unauthorized-action`：未确认尝试联系老师。
- `llm-timeout`：使用固定草稿和话术。
- `wearable-offline`：手机与车机继续闭环。
