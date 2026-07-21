# 车机 HMI 团队协作操作指南

本指南面向团队协作同事，用于运行和联调 `apps/vehicle-hmi/`。不要在本文档或代码中写入真实 Team Token、OpenAI API Key、联系人、地址或支付信息。

## 模块定位

车机 HMI 是驾驶阶段的安全展示端，不是业务状态机。

它只做三件事：

- 消费 Agent 返回的完整 `WorldState`。
- 按 `stage`、`primary_surface`、`risk`、`actions` 和 `confirmation` 渲染驾驶界面。
- 在车机是唯一确认端时调用 `/v1/confirm`。

它不能做：

- 直接设置 `stage`、`pressure_level` 或动作完成状态。
- 自行生成 `confirmation_id`。
- 绕过 Agent 直接让页面进入“已处理”。
- 在驾驶中展示长聊天、多选复杂决策或完整商品明细。

## 启动页面

从仓库根目录启动静态服务：

```bash
python -m http.server 5174
```

打开：

```text
http://127.0.0.1:5174/apps/vehicle-hmi/
```

如果页面部署到局域网或公网，访问地址会变成部署后的页面地址，但 Agent API 仍需在页面内单独配置。

## 连接本地 Agent

本地开发时先启动 Agent：

```bash
python -m uvicorn \
  auri_agent.app:app \
  --app-dir services/agent-api/src \
  --host 127.0.0.1 \
  --port 8000
```

打开车机 HMI 后：

1. 点击底部 `Agent`。
2. 点击 `本地 Agent`。
3. Team Token 留空，除非本地后端开启了共享访问。
4. 点击 `保存并重连`。

## 连接团队公网 Agent

公网 Agent 地址：

```text
https://auri-agent-api.onrender.com
```

打开车机 HMI 后：

1. 点击底部 `Agent`。
2. 点击 `公网 Agent`。
3. 在 `Team Token` 输入框填写团队负责人提供的令牌。
4. 点击 `保存并重连`。

注意：

- Team Token 只保存在当前浏览器 `localStorage`。
- 不要把 Team Token 写入仓库、截图、PR 描述或聊天记录。
- 不要把 OpenAI API Key 写入任何前端文件。
- 如果页面部署在公网，Agent API 不能填 `127.0.0.1`，否则会访问使用者自己的电脑。

## 状态同步机制

HMI 使用两种方式同步 Agent 状态：

- SSE：`GET /v1/stream`
- 轮询兜底：`GET /v1/state`

客户端只接受相同 `session_id` 且更高 `revision` 的快照。

如果公网 SSE 被代理或浏览器中断，HMI 会通过轮询继续更新，不需要手动保存重连。

## 车机确认规则

确认按钮只有在以下条件同时满足时启用：

```text
primary_surface = vehicle_hmi
confirmation.owner_surface = vehicle_hmi
confirmation.status = pending
```

确认请求：

```http
POST /v1/confirm
```

请求体由页面根据 `WorldState.confirmation.confirmation_id` 生成。前端不得自己创建新的确认 ID。

## 标准联调流程

建议同时打开：

```text
车机 HMI:
http://127.0.0.1:5174/apps/vehicle-hmi/

Demo 控制台:
http://127.0.0.1:5174/apps/demo-console/
```

在控制台按顺序推进：

| 步骤 | 控制台按钮 | 车机期望表现 |
| --- | --- | --- |
| 1 | 重置 Demo | 车机回到初始状态。 |
| 2 | 创建任务 | 任务卡出现接孩子和超市。 |
| 3 | 会议延迟 | 显示出发窗口压缩，风险 L1。 |
| 4 | 接近车辆 | 进入交接到车机状态。 |
| 5 | 进入车辆 | 主交互端切到车机。 |
| 6 | 拥堵加剧 | 显示晚到 18 分钟，进入 L2。 |
| 7 | 用户求助 | 出现动作组和确认按钮。 |
| 8 | 确认发送 | 进入已处理状态。 |
| 9 | 低干扰恢复 | 进入 cooldown。 |
| 10 | 停车复盘 | 主端回到手机。 |

## 常见问题

### 页面打开但状态不更新

检查：

- HMI 和控制台是否连接同一个 Agent API。
- Team Token 是否正确。
- 浏览器 Network 中 `/v1/state` 是否 200。
- 浏览器 Network 中 `/v1/stream` 是否成功或轮询是否持续。

如果配置曾经连过旧地址，可在浏览器 Console 执行：

```js
localStorage.removeItem("auri-hmi-config")
location.reload()
```

然后重新通过底部 `Agent` 配置。

### 确认按钮不可点击

这通常是正确行为。只有车机是 `primary_surface` 且确认 owner 是 `vehicle_hmi` 时才可点击。

用 `/v1/state` 检查：

```text
primary_surface
confirmation.owner_surface
confirmation.status
```

### 页面连接公网 Agent 返回 401

说明 Team Token 缺失或错误。请向 Agent Owner 获取令牌，并只填在浏览器配置中。

## 提交前检查

修改 HMI 后至少运行：

```bash
node --check apps/vehicle-hmi/app.js
git diff --check
```

还要确认：

- 没有提交 Team Token。
- 没有提交 OpenAI API Key。
- 没有让前端直接设置最终 World State。
- HMI 仍可连接本地 Agent 和公网 Agent。
