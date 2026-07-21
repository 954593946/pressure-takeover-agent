# AURI LangChain Agent 工具与安全边界

> 本文描述后端已经实现的工具，不是待办建议。手机、车机和控制台仍只调用 v0.2 HTTP/Event 接口，不直接调用 LangChain 工具。

## 1. 实际调用链

```text
客户端 POST /v1/event (type=user.utterance)
  -> AuriAgent 读取当前 WorldState
  -> LangChain create_agent 理解意图并选择工具
  -> 工具在隔离的 WorldState 副本上执行确定性逻辑
  -> LLM 根据真实工具结果生成回复
  -> Runtime 校验 revision 后一次性提交新状态
  -> output.conclusion + 完整 WorldState 通过 API/SSE/WS 返回
```

LLM 负责语义理解、工具选择和自然语言表达；后端确定性代码继续拥有 WorldState、L0-L3、预算、交互所有权、确认、幂等和执行结果。这是“LangChain 编排 + 确定性业务内核”，不是让模型直接改 JSON。

## 2. 当前工具

| 工具 | 何时调用 | 可以改变什么 | 关键限制 |
|---|---|---|---|
| `create_tasks` | 新增、记录、安排任务 | `tasks` | 不得补充用户没说的任务；孩子任务强制刚性，采购任务规范为 `grocery_delivery` |
| `get_status` | 查询任务、风险、进展、确认状态 | 只写审计 Ledger，不改业务状态 | 回复必须依据当前快照 |
| `report_meeting_delay` | 用户明确报告会议延迟 | 风险原因、stage、腕上预警 | L0-L3 由 `RiskEngine` 计算，模型不能指定 |
| `reschedule_task` | 调整已有任务时间 | 指定任务的时间和状态 | 只能修改 `flexible + adjustable` 任务，刚性任务会被拒绝 |
| `prepare_assistance` | “帮我处理/怎么办/替我安排” | 候选 Actions、模拟订单预览、Confirmation | 只依据已有任务；只准备，不执行；已有待确认方案会复用 |
| `confirm_current_actions` | 用户明确接受或拒绝当前方案 | Confirmation、Action、模拟订单执行状态 | 原话必须明确；输入端必须是 Confirmation Owner；只能消费一次 |

工具参数的唯一实现来源是 `services/agent-api/src/auri_agent/tools.py`。不要把本文中的说明复制成客户端私有协议。

## 3. 不能做成聊天工具的事实

下列事实只能由受信 Event 上报，不能因为用户在聊天里说“我上车了/这里堵车”就直接修改：

- `scene.vehicle_entered`、`scene.parked`
- `traffic.updated`
- `wearable.signal`
- `driving.signal`
- `service.mock.config`

这样可以防止 LLM 伪造车辆状态、实时路况、心率和故障结果。控制台负责模拟外部世界，Agent 负责根据标准 Event 更新状态。

## 4. 确认规则

`prepare_assistance` 生成的消息和订单都处于 `awaiting_confirmation`。此时模型只能说“已经准备好”，不能说“已发送/已下单”。执行必须同时满足：

1. 当前存在 `pending` Confirmation。
2. 用户原话明确包含确认或拒绝意图；“好的”“给我确认一下”不算确认执行。
3. `source` 是 `mobile` 或 `vehicle_hmi`。
4. `source` 与 `confirmation.owner_surface` 一致。
5. Confirmation 未过期且尚未消费。

UI 按钮仍优先调用 `POST /v1/confirm`；语音自然语言也可通过 `user.utterance` 触发相同的确定性消费函数。

## 5. 客户端示例

```json
{
  "schema_version": "0.2.0",
  "event_id": "evt_mobile_20260721_001",
  "session_id": "先从 GET /v1/state 获取",
  "type": "user.utterance",
  "source": "mobile",
  "timestamp": "2026-07-21T18:00:00+08:00",
  "payload": {
    "text": "请创建两个任务：18:10去学校接孩子，之后去超市采购"
  }
}
```

客户端只需展示返回的 `state.output.conclusion`，并继续以完整 WorldState 为准渲染任务、动作和确认卡。不要从回复文字反推状态。

## 6. 模型失败时发生什么

- 模型在调用工具前超时：进入安全关键词降级，只处理可明确识别的有限意图。
- 工具已经成功、最终文案生成超时：保留工具产生的真实状态，并从状态生成贴合本次任务的兜底回复。
- 所有模式都经过相同的权限、预算、确认和状态提交规则。

`GET /health` 可观察：

- `llm_last_mode=langchain_agent`：工具选择和最终回复均由 Agent 完成。
- `langchain_agent_fallback_reply`：模型选择并执行了工具，最终文案使用状态兜底。
- `fallback`：本轮未成功调用模型，使用安全降级。
- `fallback_reply`：确认已由确定性后端执行，模型总结超时。
- `agent_last_tools`：最近一轮实际调用的工具名，不含参数和密钥。

## 7. 后端开发入口与验证

- 编排器：`services/agent-api/src/auri_agent/agent.py`
- 动态提示词：`services/agent-api/src/auri_agent/prompts.py`
- 工具及确定性边界：`services/agent-api/src/auri_agent/tools.py`
- 状态提交和并发控制：`services/agent-api/src/auri_agent/runtime.py`
- 风险、方案和确认执行：`services/agent-api/src/auri_agent/engine.py`

```powershell
.\scripts\test-agent.ps1
.\services\agent-api\.venv\Scripts\python.exe -X utf8 .\scripts\smoke-langchain-agent.py
```

第二条命令会使用本地 `.env` 中的模型配置运行“创建任务 → 会议延迟 → 准备方案 → 明确确认”完整链路，但不会打印 API Key。

LangChain 官方参考：[Agents](https://docs.langchain.com/oss/python/langchain/agents)、[Tools 与 ToolRuntime](https://docs.langchain.com/oss/python/langchain/tools)、[Runtime context](https://docs.langchain.com/oss/python/langchain/runtime)、[LangGraph persistence](https://docs.langchain.com/oss/python/langgraph/persistence)。
