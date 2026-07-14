# Agent API

本服务是唯一的 World State 写入者，负责：

- 校验和持久化事件。
- 任务结构化、刚性/弹性识别和等待方识别。
- 最晚出发、ETA 偏差和延迟分钟数等确定性计算。
- 压力接管状态机与介入策略。
- LLM 调用、消息草稿和短话术生成。
- 权限分级、安全拦截和 `confirmation_id` 幂等。
- 通过 WebSocket/SSE 广播 World State。
- LLM/ASR/TTS/网络失败时的固定话术和演示兜底。

建议的内部边界：

```text
api/          HTTP、SSE、WebSocket 适配层
domain/       World State、任务、动作、状态机
policies/     风险、权限、安全和幂等规则
agent/        LLM 结构化调用与输出校验
prompts/      版本化提示词与固定话术
adapters/     ASR、TTS、LLM、存储和 mock 外部服务
tests/        状态机、越权、幂等和完整脚本测试
```

实现前以 `contracts/` 为准，不在服务内部维护第二套对外 DTO。
