# 协作开发约定

## 分支与合并

- 从最新 `main` 创建短分支：`feat/<area>-<topic>`、`fix/<area>-<topic>`、`docs/<topic>`。
- 一个 PR 只解决一个可验收问题，优先保持在 300 行有效改动以内。
- 至少由一个受影响端负责人评审；修改 `contracts/` 时至少需要一个生产方和一个消费方共同评审。
- 不在 `main` 上直接开发，不提交不可用的密钥、真实联系人信息或大体积构建产物。

## Commit 格式

推荐使用：

```text
feat(mobile): add task voice entry
feat(wearable): render warning state
fix(agent): make confirmation idempotent
docs(contract): clarify traffic event payload
```

## 契约变更流程

1. 先修改 `contracts/*.schema.json` 或 `contracts/openapi.yaml`。
2. 同步修改 `contracts/examples/` 中至少一个示例。
3. 在 PR 描述中写清生产方、消费方、兼容性和迁移方式。
4. 删除或重命名字段属于破坏性变更；六周 Demo 期间优先新增可选字段。

## 完成定义

一个功能只有同时满足以下条件才算完成：

- 有明确的输入、输出和失败兜底。
- 不越过文档中的安全边界。
- 单端自测通过；涉及联动时，至少与一个相邻端联调通过。
- 更新必要文档、契约和固定样例。
- 不依赖演示者手工修改数据库或代码才能重跑。

## 本地配置

复制 `.env.example` 为 `.env`，只填自己环境所需的值。每个子项目可以补充自己的 `.env.example`，但不得提交真实值。
