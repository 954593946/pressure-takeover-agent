# AURI Agent API

本服务是 AURI World State 的唯一写入者。v0.2 基础版已经实现事件幂等、确定性 L0-L3、主交互端、Profile、动作规划、一次性确认、模拟订单、Action Ledger、SSE/WebSocket 和 Bosch OpenAI 兼容适配器。

## 运行环境

- Python 3.11（不要使用当前尚未纳入项目支持范围的 Python 3.14）
- 配置从仓库根目录 `.env` 或本目录 `.env` 读取
- 外部 LLM 失败、超时或不支持 `response_format` 时自动回退到固定任务解析；安全等级、权限、金额和执行从不交给 LLM

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

## 最小测试

```powershell
.\.venv\Scripts\python.exe -m pytest
```

标准事件序列位于 `packages/test-fixtures/happy-path.events.json`。客户端应先读取 `/v1/state` 获得当前 `session_id`，再上报事件；重复 `event_id` 或重复确认只返回第一次的状态，不重复发送消息、创建订单或震动。

## 已知边界

- 当前状态存储为进程内存，适合六周单实例 Demo；生产化前需换成持久存储并增加事务锁。
- 消息、商品、库存、价格和订单均为显著标注的模拟数据。
- SSE 是 P0 主实时通道，同时提供 `/v1/ws` 供需要 WebSocket 的客户端联调。
