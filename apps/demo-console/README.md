# 演示控制台

定位：模拟本轮未接入的外部世界并保证现场可复现，不替 Agent 做判断。

P0 操作：创建标准任务、会议延迟、进入车辆、触发拥堵、注入辅助信号、用户求助、确认发送和重置 Demo。控制台还应展示事件日志、当前 World State、连接状态和错误。

所有按钮发送标准事件；禁止通过直接改数据库或内存对象来跳过 Agent 状态机。

## 当前实现

当前目录提供一个独立 Web 控制台：

- `index.html`：控制台页面。
- `styles.css`：浅背景、卡片化、蓝/橙主色视觉。
- `app.js`：Agent API 客户端、SSE 订阅、标准事件发送和日志。

## 本地运行

先启动 Agent 后端：

```bash
/home/fly/miniconda3/envs/auri-agent-dev/bin/python -m uvicorn \
  auri_agent.app:app \
  --app-dir services/agent-api/src \
  --host 127.0.0.1 \
  --port 8000
```

再用任意静态服务打开本目录，或从项目根目录启动：

```bash
python -m http.server 5174
```

访问：

```text
http://127.0.0.1:5174/apps/demo-console/
```

## 配置

页面支持在顶部输入：

- `Agent API`：例如 `http://127.0.0.1:8000` 或云端 Agent 地址。
- `Team Token`：仅本地运行时填写，保存到浏览器 `localStorage`。

不要把团队 Token、OpenAI API Key 或其他密钥提交到仓库。

## 事件映射

| 控制台按钮 | 接口 | 事件或操作 | 说明 |
| --- | --- | --- | --- |
| 创建任务 | `POST /v1/event` | `task.created` | 模拟手机语音创建“18:10 接孩子，之后去超市”。 |
| 会议延迟 | `POST /v1/event` | `meeting.overrun` | 延迟 20 分钟，触发最晚出发风险。 |
| 接近车辆 | `POST /v1/event` | `scene.approaching` | 准备从随行/手机交接到车机。 |
| 进入车辆 | `POST /v1/event` | `scene.vehicle_entered` | 车机成为主展示端。 |
| 拥堵加剧 | `POST /v1/event` | `traffic.updated` | ETA 变为 18:28，晚到 18 分钟。 |
| 压力辅助信号 | `POST /v1/event` | `wearable.signal` | 注入心率和置信度，只作辅助信号。 |
| 急刹信号 | `POST /v1/event` | `driving.signal` | 模拟驾驶负荷升高。 |
| 用户求助 | `POST /v1/event` | `user.utterance` | 用户说“我还来得及吗？帮我处理”。 |
| 服务成功/缺货/超预算 | `POST /v1/event` | `service.mock.config` | 控制模拟服务后端返回。 |
| 确认发送 | `POST /v1/confirm` | `confirmed_by=vehicle_hmi` | 模拟车机大按钮确认。 |
| 语音确认 | `POST /v1/confirm` | `input_mode=voice` | 模拟车机/手机语音确认兜底。 |
| 低干扰恢复 | `POST /v1/event` | `cooldown.elapsed` | 完成后降低打扰。 |
| 停车复盘 | `POST /v1/event` | `scene.parked` | 主交互端回到手机。 |
| 重置 Demo | `POST /v1/session/reset` | - | 回到 happy-path 初始状态。 |

## 设计约束

- 控制台只注入外部世界事件，不生成业务结论。
- 控制台不直接设置 stage、pressure、actions、confirmation。
- 所有状态展示都来自 Agent 返回的 World State。
- 现场失败时，控制台作为演示兜底入口，但仍走正式 API。
