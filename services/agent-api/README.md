# AURI Agent API

本服务是 AURI World State 的唯一写入者。v0.2 基础版已经实现事件幂等、确定性 L0-L3、主交互端、Profile、动作规划、一次性确认、模拟订单、Action Ledger、SSE/WebSocket，以及基于 LangChain `create_agent` 的 Bosch 模型适配器。

LangChain 只负责把自然语言拆成结构化 `Task`。它不拥有业务工具，也不决定压力等级、权限、金额、确认归属或执行；这些仍由可测试的确定性状态机负责。因此切换框架不会改变手机、车机、腕上和控制台使用的 v0.2 API。

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

`GET /health` 中的 `llm_framework=langchain` 表示本构建使用 LangChain；提交过一次 `task.created` 后，`llm_last_mode=langchain_agent` 表示本次真实走过 Agent，`fallback` 表示模型失败后使用了安全降级。

## 配置

```dotenv
OPENAI_API_KEY=
OPENAI_BASE_URL=https://example.com/v1
OPENAI_MODEL=gpt-5.5
OPENAI_TIMEOUT_SECONDS=12
LLM_ENABLED=true
DEMO_MODE=true
```

密钥不得提交。`/health` 只返回是否完成配置，不返回密钥或完整请求信息。

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
AGENT_API_BASE_URL=https://auri-langchain-agent-api.onrender.com
AGENT_STREAM_URL=https://auri-langchain-agent-api.onrender.com/v1/stream
```

实际子域名以 Render 分配结果为准。所有 `/v1/*` 请求继续携带 `X-Agent-Token`；WebSocket 使用 `wss://<Render 域名>/v1/ws`。

仓库根目录的 `render-langchain.yaml` 用于创建不影响旧服务的独立 LangChain 服务。当前公网地址为 `https://auri-langchain-agent-api.onrender.com`，已验证 `/health`、团队令牌鉴权和两条不同自然语言任务，均真实进入 `langchain_agent` 模式。PR 合并后应把 Render 服务的代码分支切到 `main`；不要把 Bosch Key 写进 YAML、README 或客户端。

免费实例适合团队开发联调，但空闲后会休眠，首次请求可能需要约一分钟唤醒；休眠、重启或重新部署都会清空当前进程内 World State。正式演示前应提前唤醒并执行一次标准场景重置，或临时升级到不会空闲休眠的实例。

## 最小测试

```powershell
.\.venv\Scripts\python.exe -m pytest
```

标准事件序列位于 `packages/test-fixtures/happy-path.events.json`。客户端应先读取 `/v1/state` 获得当前 `session_id`，再上报事件；重复 `event_id` 或重复确认只返回第一次的状态，不重复发送消息、创建订单或震动。

## 已知边界

- 当前状态存储为进程内存，适合六周单实例 Demo；生产化前需换成持久存储并增加事务锁。
- 消息、商品、库存、价格和订单均为显著标注的模拟数据。
- SSE 是 P0 主实时通道，同时提供 `/v1/ws` 供需要 WebSocket 的客户端联调。
- 当前 LangChain Agent 没有业务执行工具，仅承担结构化理解；后续增加工具时必须先定义权限、幂等键和确认边界。
