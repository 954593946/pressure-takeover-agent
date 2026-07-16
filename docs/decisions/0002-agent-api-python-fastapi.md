# ADR-0002：Agent API 使用 Python 3.11 与 FastAPI

- 状态：已采用
- 日期：2026-07-15

## 背景

六周 Demo 需要快速交付可验证的 JSON Schema、确定性状态机、SSE/WebSocket、OpenAI 兼容模型调用和并发幂等测试。后端必须在外部 LLM 不可用时继续运行。

## 决策

Agent API 使用 Python 3.11、FastAPI、Pydantic v2 和进程内状态存储完成 P0。外部模型通过独立 `TaskParser` 适配器接入；风险、权限、预算、交互所有权、确认和订单执行不使用 LLM。P0 实时主通道使用 SSE，同时提供 WebSocket 兼容入口。

## 结果

- 优点：契约模型与运行时校验一致；测试和 OpenAI 兼容客户端生态成熟；失败降级简单。
- 代价：当前进程内存状态只支持单实例 Demo；生产化需要数据库、分布式锁和持久事件账本。
- 约束：模型供应商、密钥和 Base URL 只通过环境变量配置，不写入仓库或日志。
