# AURI 正式车机 HMI 团队操作指南

本指南面向团队协作同事。不要在本文档、源码、Issue、截图或提交记录中写入 Team Token、高德安全码、OpenAI API Key、真实联系人或个人地址。

## 模块定位

`apps/vehicle-hmi/` 是唯一正式车机 HMI，负责只读消费 Agent World State，并在确认 owner 属于车机时提供唯一确认入口。旧版位于 `apps/vehicle-hmi-legacy/`，仅用于回溯。

HMI 不负责：

- 直接改写 `stage`、`risk`、`tasks`、`actions` 或 `vehicle_state`。
- 代替手机创建语音任务。
- 在前端自行计算业务 ETA 或伪造完成态。
- 绕过 `confirmation_id` 调用动作。

## 访问地址

团队 GitHub Pages 部署后：

```text
https://954593946.github.io/pressure-takeover-agent/apps/vehicle-hmi/
```

本机从仓库根目录启动：

```bash
python -m http.server 5174
```

然后打开：

```text
http://127.0.0.1:5174/apps/vehicle-hmi/
```

## 连接 Agent

推荐先在同源 Demo Console 中填写 Agent API 和负责人单独提供的 Team Token，再打开 HMI。两页会共享同源浏览器配置。

HMI 左上角连接状态可打开配置页。保存后必须同时满足：

```text
同步方式 = 实时流
Session != --
Revision != --
Agent Health = 正常
```

Health 正常只表示服务在线，不表示 Token 鉴权成功。错误 Token 应显示“Team Token 无效或缺失”，Session 和 Revision 保持 `--`。

## 高德地图

默认模式为“自动读取 Agent 配置”：

```text
GET /v1/map-config
```

公网真实地图必须由 Agent 返回 `provider=amap`。若返回 `provider=offline`，HMI 会显示 Bosch 离线地图；这不是空白或连接成功的假状态。

本机地图负责人可复制 `env.local.example.js` 为 `env.local.js` 做真实 Key 诊断。`env.local.js` 已被 Git 忽略，禁止提交。

高德 SDK、路线或额度异常时，HMI 应在 2 秒内回退离线地图。Agent 的 ETA、晚到分钟、任务和风险仍是业务真相，高德只提供道路和路线表现。

## 标准联调

1. Console 与 HMI 连接同一个 Agent，核对 Session 和 revision。
2. 初始应为 0 项任务；手机通过 `/v1/chat` 创建任务，或 Console 选择性载入演示预置。
3. Console 依次推进会议延迟、接近车辆、进入车辆、拥堵和压力辅助信号。
4. 手机语音求助后，HMI 显示语音转写、动态动作组和三端状态。
5. 只有 `primary_surface=vehicle_hmi`、confirmation pending 且未过期时出现确认按钮。
6. 确认后等待更高 revision 的完成态；HMI 不提前显示成功。
7. cooldown 后降低打扰；停车后主端回到手机，HMI 只保留结束摘要。

## 验收命令

先启动启用测试鉴权的隔离 Agent。服务端变量必须叫 `AGENT_SHARED_TOKEN`；测试脚本读取的客户端变量才叫 `AURI_AGENT_TOKEN`，两者不要混用：

```bash
AGENT_SHARED_TOKEN=test-shared-token LLM_ENABLED=false BUILD_SHA=local-audit \
/home/fly/miniconda3/envs/auri-agent-dev/bin/python -m uvicorn \
  auri_agent.app:app --app-dir services/agent-api/src \
  --host 127.0.0.1 --port 8795
```

静态服务 `127.0.0.1:5174` 也已启动后执行：

```bash
node apps/vehicle-hmi/tests/world-state-model.test.cjs
node apps/vehicle-hmi/tests/agent-client.test.cjs
node apps/vehicle-hmi/tests/amap-adapter.test.cjs

/home/fly/miniconda3/envs/bosch-agent-dev/bin/python \
  apps/vehicle-hmi/tests/e2e_config_interaction.py

AURI_AGENT_TOKEN=test-shared-token \
/home/fly/miniconda3/envs/bosch-agent-dev/bin/python \
  apps/vehicle-hmi/tests/e2e_ultrawide_readability.py

AURI_AGENT_TOKEN=test-shared-token \
/home/fly/miniconda3/envs/bosch-agent-dev/bin/python \
  apps/vehicle-hmi/tests/e2e_console_hmi_sync.py
```

破坏性 E2E 只允许指向隔离本地 Agent，禁止对共享公网 Session 执行 reset 或完整写入测试。

## 常见问题

### Health 正常，但 Session 为 `--`

检查 Token、浏览器 Network 中 `/v1/state` 的 HTTP 状态，以及 Console/HMI 是否同源。401 不是 SSE 问题。

### 地图设置自动收起

当前版本不应发生。强制刷新后重试；若仍发生，记录 revision、浏览器版本和页面错误，并运行 `e2e_config_interaction.py`。

### 显示离线地图

检查 Agent `/health` 的 `amap_configured` 和 `/v1/map-config` 的 `provider`。公网环境不要把安全码写进浏览器或仓库。

### Console 更新但 HMI 不更新

核对两端 Session、revision 和 API Origin。HMI 应优先使用 SSE，断开时进入轮询并自动重连追平。
