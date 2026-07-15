# AURI 仓库 AI 编程规则

本文件适用于整个仓库。任何 AI 编程助手在修改代码前必须先读本文件、根 `README.md`、`docs/agent-integration-guide.md` 和目标模块的 README。

## 一句话理解系统

所有端只提交 `Event`；Agent/后端是唯一 `WorldState` 写入者；所有端只渲染后端状态，不在本地推演全局状态机。

```text
mobile / vehicle-hmi / wearable / demo-console
                    │
                    │ Event
                    ▼
               Agent API
                    │
                    │ WorldState snapshot / stream
                    ▼
mobile / vehicle-hmi / wearable / demo-console
```

## 权威顺序

发生冲突时按以下顺序处理，不得静默选择：

1. `随行压力接管Agent_开发功能清单_v1_五人分工版.docx`
2. `随行压力接管Agent_六周Demo汇报材料_v4_生活服务执行增强版.docx`
3. 根 `README.md`
4. 已完成跨端评审并冻结的 `contracts/`
5. `docs/agent-integration-guide.md` 和模块 README
6. 现有代码

发现冲突时停止跨端实现，在 PR 中说明冲突并请求 Agent Owner、一个生产方和一个消费方共同确认。

## 不可违反的系统规则

- Agent/后端是唯一 `WorldState` 写入者。
- 客户端不得自行计算或直接设置 `stage`、`pressure_level`、动作完成状态或订单结果。
- LLM 不得决定 L0-L3、权限、金额、确认所有权或是否执行动作。
- 任一时刻只有 `primary_surface` 指定的端可以要求注意或提供确认。
- 驾驶输出最多包含：1 句结论、1 个动作组、1 个确认入口。
- `event_id`、`command_id`、`action_id`、`confirmation_id`、`order_id` 必须幂等。
- 控制台只能提交标准事件，不能直接改变最终 UI 或数据库状态。
- 心率、急刹、语气等只能作为辅助信号，不能单独触发 L2/L3。
- 外部世界和第三方结果可以 Mock；状态机、权限、幂等、同步和动作编排必须真实运行。
- 所有联系人、地址、金额、消息和订单必须标明“模拟”或“Demo 数据”。

## 当前接口基线

以 `contracts/openapi.yaml`、`contracts/event.schema.json` 和 `contracts/world-state.schema.json` 为机器真相。当前 v0.2 候选接口为：

- `POST /v1/event`：提交事件。
- `GET /v1/state`：获取完整 World State。
- `GET /v1/stream`：订阅 SSE 完整状态快照。
- `WS /v1/ws`：WebSocket 兼容状态流。
- `POST /v1/confirm`：消费一次性确认。
- `PUT /v1/profile`：更新 Profile。
- `POST /v1/session/reset`：重置 Demo Session。

不要新增 `/api/...`、`/events/...` 或端侧私有 DTO。确实缺字段时先修改 Schema、OpenAPI、示例和测试夹具。

## 目录所有权

- `services/agent-api/`、`contracts/`、`packages/test-fixtures/`：Agent/后端 Owner。
- `apps/mobile/`：手机 A/B；两人共享同一个 API Client 和状态层。
- `apps/vehicle-hmi/`、`apps/demo-console/`：车机 Web Owner。
- `apps/watch/`、`devices/`：腕上硬件 Owner。
- `packages/ui/`：只有两个以上 Web 端实际复用的组件才能进入。

只修改任务需要的目录。跨模块字段变化必须在 PR 中点名生产方和消费方 Reviewer。

## AI 实现步骤

1. 读取任务对应的产品主依据和模块 README。
2. 查 `contracts/` 是否已经存在输入、输出、状态和错误码。
3. 使用 `packages/test-fixtures/` 的固定事件和快照开发，不复制后私改。
4. 先写最小失败测试，再实现功能。
5. 验证刷新、重连、重复事件、重复确认和错误降级。
6. 更新必要的 Schema、示例、文档和启动命令。
7. 运行相关测试后再提交 PR。

## Agent 后端命令

从仓库根目录运行：

```powershell
.\scripts\setup-agent.ps1
.\scripts\test-agent.ps1
.\scripts\run-agent.ps1
```

服务启动后使用：

- API 文档：`http://127.0.0.1:8000/docs`
- 健康检查：`http://127.0.0.1:8000/health`
- 完整状态：`http://127.0.0.1:8000/v1/state`

## 完成定义

一个功能只有同时满足以下条件才算完成：

- 由真实 Event/World State 进入，不是页面静态跳转。
- 固定输入得到一致的关键决策。
- 断线重连后用更高 `revision` 的完整快照恢复。
- 重复请求不会重复震动、发消息或生成订单。
- 驾驶态遵守唯一主端与输出预算。
- 有失败降级、测试和可复现启动说明。
- 没有提交 `.env`、API Key、真实联系人、地址、支付或设备凭据。

## 禁止提交

- `.env`、API Key、Token、证书、真实联系人、真实地址和支付信息。
- `node_modules/`、`.venv/`、构建产物、日志、数据库和设备私密配置。
- AI 生成但未运行、未验证、与契约不一致的代码。
