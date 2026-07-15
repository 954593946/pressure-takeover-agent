# 共享 UI

根 README 的 AURI 视觉基线是当前权威来源。共享实现应优先提供 `--auri-navy: #0B1B33`、`--auri-gold: #D4AF7A`、`--auri-ivory: #F5F2EC` 及 processing/warning/success/critical 语义 Token。

仅当手机 PWA、车机 HMI、演示控制台中至少两个界面确实复用时，再放入颜色 Token、AURI 光晕、状态图标和基础组件。

状态不能只靠颜色表达，必须配合文字和图标。具体对比度、真机显示和 HMI 夜间可读性通过验证后，才能将 Token 标记为冻结。
