# AURI Agent API

本服务是 AURI World State 的唯一写入者。v0.2 已实现事件幂等、确定性 L0-L3、主交互端、Profile、动作规划、一次性确认、模拟订单、Action Ledger、SSE/WebSocket，以及基于 LangChain `create_agent` 的完整自然语言工具编排。

LangChain 负责理解自然语言、选择受控工具并生成贴合状态的回复；工具层负责创建/查询/调整任务、记录会议延迟、准备协助方案和消费明确确认。它仍不决定压力等级、权限、金额、确认归属或执行真实性，这些由可测试的确定性状态机负责。因此客户端继续使用原有 v0.2 API，不直接依赖 LangChain。

## 运行环境

- Python 3.11（不要使用当前尚未纳入项目支持范围的 Python 3.14）
- 配置从仓库根目录 `.env` 或本目录 `.env` 读取
- 外部 LLM 失败或超时时自动回退到确定性任务解析；安全等级、权限、金额和执行从不交给 LLM

## 安装与启动

```powershell
cd services/agent-api
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\python.exe -m uvicorn auri_agent.app:app --host 127.0.0.1 --port 8000
```

启动后访问：

- `GET http://127.0.0.1:8000/health`
- `GET http://127.0.0.1:8000/docs`
- `GET http://127.0.0.1:8000/v1/state`
- `GET http://127.0.0.1:8000/v1/stream`（SSE）
- `WS  ws://127.0.0.1:8000/v1/ws`
- `POST http://127.0.0.1:8000/v1/chat`（手机 Chat SSE，要求 `clientEventId`）
- `POST http://127.0.0.1:8000/v1/chat/sync`（断流同步兜底，复用同一 `clientEventId`）
- `POST http://127.0.0.1:8000/v1/chat/confirm`（复用标准确认 Ledger）

`GET /health` 中的 `llm_framework=langchain` 表示本构建使用 LangChain。`llm_last_mode=langchain_agent` 表示最近一次完整走过模型；`deterministic_tool` 表示“打开/关闭/调节空调”等明确低风险指令直接走受控工具，不受历史消息或模型网络波动影响；`langchain_agent_fallback_reply` 表示模型已选择并执行工具、但最终文案超时后使用了状态兜底；`fallback` 表示模型调用前失败。`agent_last_tools` 会列出最近实际调用的工具名。

Health 还提供 `llm_last_success_at`、`llm_last_fallback_reason` 和 `llm_last_error_code`。错误码只使用 `UPSTREAM_AUTH`、`UPSTREAM_RATE_LIMIT`、`UPSTREAM_5XX`、`UPSTREAM_TIMEOUT`、`UPSTREAM_ERROR` 或 `LLM_NOT_CONFIGURED`，不会返回供应商响应正文、Key 或用户文本。

完整工具、确认和 Event 边界见 [`contracts/tool-calling-spec.md`](../../contracts/tool-calling-spec.md)。

导航位置使用可选的 `WorldState.navigation`：Agent 发布路线对应任务、起终点坐标、来源和模拟标识，HMI 只读消费。字段定义、数据流、兼容和隐私边界见 [`docs/navigation-location-contract.md`](../../docs/navigation-location-contract.md)。

## 配置

```dotenv
OPENAI_API_KEY=
OPENAI_BASE_URL=https://example.com/v1
OPENAI_MODEL=gpt-5.5
OPENAI_TIMEOUT_SECONDS=30
LLM_ENABLED=true
DEMO_MODE=true
```

密钥不得提交。`/health` 只返回是否完成配置，不返回密钥或完整请求信息。

### 高德车机地图代理

车机 HMI 默认通过 Agent 自动获取高德地图配置。服务端环境变量：

```dotenv
AMAP_JS_API_KEY=<Web端 JS API Key>
AMAP_SECURITY_JS_CODE=<安全密钥>
AMAP_PUBLIC_BASE_URL=https://auri-agent-api.onrender.com
AMAP_ALLOWED_ORIGINS=https://954593946.github.io,https://wangwang20.github.io,http://127.0.0.1:5174,http://localhost:5174
```

接口职责：

- `GET /v1/map-config`：需要 `X-Agent-Token`，只返回公开 Web JS Key、代理地址和样式，不返回安全密钥。
- `GET /_AMapService/*`：按允许的浏览器 Origin 转发高德请求，并在服务端注入 `AMAP_SECURITY_JS_CODE`。
- `GET /health`：仅返回 `amap_configured=true/false`。

公网部署必须把两个高德密钥配置为 Render Secret，不得写入 YAML、前端或 Git。

## 团队共享模式

共享后端必须设置独立的团队令牌；它不是 Bosch API Key，只用于阻止同一网络上的陌生客户端调用 Demo 控制接口。

```dotenv
AGENT_SHARED_TOKEN=使用随机值
CORS_ORIGINS=*
```

启动共享监听：

```powershell
.\scripts\configure-shared-firewall.ps1
.\scripts\run-agent.ps1 -BindAddress 0.0.0.0 -NoAccessLog
```

防火墙脚本需要管理员 PowerShell，只开放 TCP 8000 给直接连接的本地子网，不创建公网全开放规则。

伙伴调用所有 `/v1/*` HTTP 接口时携带：

```http
X-Agent-Token: 团队令牌
```

WebSocket 客户端优先使用 `X-Agent-Token` 请求头；浏览器原生 WebSocket 无法设置自定义请求头时，可临时使用 `/v1/ws?access_token=团队令牌`。不要把 Bosch API Key 分发给客户端。`/health` 保持无认证，且只公开配置状态，不公开任何令牌。

## Render 公网部署

仓库根目录的 `render.yaml` 是可复现的 Render Blueprint，固定 Python 3.11、Singapore 区域、单实例和 `/health` 健康检查。创建 Blueprint 时，Render 只要求人工填写两个 Secret：

- `OPENAI_API_KEY`：仅保存在 Render 环境中的 Bosch Key。
- `AGENT_SHARED_TOKEN`：伙伴调用 Agent API 使用的团队令牌，不是 Bosch Key。

部署完成后，客户端配置改为：

```dotenv
AGENT_API_BASE_URL=https://auri-agent-api.onrender.com
AGENT_STREAM_URL=https://auri-agent-api.onrender.com/v1/stream
```

实际子域名以 Render 分配结果为准。所有 `/v1/*` 请求继续携带 `X-Agent-Token`；WebSocket 使用 `wss://<Render 域名>/v1/ws`。

团队当前唯一 canonical 地址是 `https://auri-agent-api.onrender.com`，必须与根 README、手机、HMI 和 Demo Console 保持一致。仓库根目录的 `render-langchain.yaml` 用于维护独立的 LangChain 备用服务 `https://auri-langchain-agent-api.onrender.com`；只有负责人明确切换并通知所有端时才能使用，不能由单个客户端自行改成备用地址。不要把 Bosch Key 写进 YAML、README 或客户端。

免费实例适合团队开发联调，但空闲后会休眠，首次请求可能需要约一分钟唤醒；休眠、重启或重新部署都会清空当前进程内 World State。正式演示前应提前唤醒并执行一次标准场景重置，或临时升级到不会空闲休眠的实例。

## 最小测试

```powershell
.\.venv\Scripts\python.exe -m pytest
```

标准事件序列位于 `packages/test-fixtures/happy-path.events.json`。客户端应先读取 `/v1/state` 获得当前 `session_id`，再上报事件；重复 `event_id` 或重复确认只返回第一次的状态，不重复发送消息、创建订单或震动。

## Demo 消息与采购回执

协助方案不再只返回“已准备/已处理”的泛化文案。客户端无需增加第二套接口，直接读取现有 v0.2 World State；车机的完整接入示例见 [`docs/hmi-agent-actions-contract.md`](../../docs/hmi-agent-actions-contract.md)：

- 消息类 `Action.message_draft.body` 是给客户端直接渲染的结构化正文；`target` 是收件人，`is_simulated=true` 表示 Demo 模拟。
- `message_draft` 是 v0.2 的向后兼容可选字段。旧客户端可继续读取 `Action.summary`，新客户端不得再用前端预设消息覆盖它。
- 确认后，同一 `Action.summary` 变为“已模拟发送”，`message_draft.body` 保持不变，方便手机复盘和 HMI 动作卡核验。
- 采购类 `Action.summary` 包含全部商品与数量、总件数/品类数、总价、配送方式、配送时段、选择策略和替换规则；确认后还包含模拟 `order_id`。
- `service_orders[].items` 仍是商品卡片的结构化权威数据；前端不要从自然语言回复反向解析商品或执行状态。
- `output.conclusion` 和 Chat `tool_result.summary` 提供适合现场演示的简短回执，至少包含收件人/消息要点，以及前两项商品、金额和配送时段。

所有这些结果仍是 Demo 模拟：不会真的发送短信，也不会发起真实支付。真实状态以 `Action.status`、`ServiceOrder.status` 和 `Confirmation.status` 为准。

使用本机 `.env` 中的 Bosch 配置做完整模型验收：

```powershell
.\services\agent-api\.venv\Scripts\python.exe -X utf8 .\scripts\smoke-langchain-agent.py
```

脚本覆盖“自然语言创建任务 → 记录会议延迟 → 准备方案 → 明确确认”，只打印模型名、工具名、回复和检查结果，不打印 API Key。

## 已知边界

- 当前状态存储为进程内存，适合六周单实例 Demo；生产化前需换成持久存储并增加事务锁。
- 消息、商品、库存、价格和订单均为显著标注的模拟数据。
- SSE 是 P0 主实时通道：订阅后立即返回当前 World State，并每 15 秒发送注释心跳，避免公网代理关闭空闲连接；同时提供 `/v1/ws` 供需要 WebSocket 的客户端联调。
- LangChain Checkpointer 和 World State 当前都在单进程内存中；Render 重启或扩成多实例前必须迁移到共享持久存储。
- 工具是受控业务入口；新增工具必须同时定义权限、幂等键、确认边界、失败结果和测试，不能把任意 Python/HTTP 能力直接交给模型。
