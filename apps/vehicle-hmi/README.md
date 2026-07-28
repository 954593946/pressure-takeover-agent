# 车机 HMI

> 开发前先阅读根 [README](../../README.md) 的 P0 闭环、唯一主交互端和 AURI 视觉基线。

团队协作同事请先阅读 [TEAM_GUIDE.md](./TEAM_GUIDE.md)。

定位：驾驶阶段的安全展示和单一确认入口，运行在横屏平板、大屏或电脑浏览器，不接真实车辆。

P0 区域：路线/ETA、Agent 状态、现实结论、动作列表和“确认发送”大按钮。驾驶中不做长文本、多选决策、完整地图、多轮聊天或真实车控。

驾驶主屏遵循“一屏一事”：中央区域优先保留导航、ETA、任务卡和确认入口；Agent 交互固定在主驾驶侧，便于驾驶员侧快速求助和查看接管方案；右侧只保留速度、腕上/手机/控制台同步状态。消息草稿、方案、行程详情、车况和三端同步等信息通过二级弹窗查看，不在主屏平铺，避免遮挡导航和任务卡。

## Agent 接入方式

HMI 是 World State 渲染器，不是状态机。页面启动后读取 `/v1/state`，并默认连接 `/v1/stream` 接收 SSE 更新。

```html
<script>
  window.AURI_CONFIG = {
    apiBase: "https://auri-langchain-agent-api.onrender.com",
    token: "",
    stream: true
  };
</script>
```

如果使用云端 Agent，可在本地调试页注入 `apiBase` 和 `token`。不要把团队 Token 或 API Key 提交到代码仓库。

当前 HMI 底部提供 `Agent` 配置入口。团队协同时：

1. 打开车机 HMI 页面。
2. 点击底部 `Agent`。
3. 选择 `LangChain 公网` 或手动填写 `Agent API`。
4. 在 `Team Token` 输入框填写团队令牌。
5. 点击 `保存并重连`。

配置保存在当前浏览器 `localStorage`，不会写入仓库。公网页面不能继续使用 `127.0.0.1` 作为 Agent API，因为那只代表访问者自己的电脑。

HMI 同时兼容本地 Agent 和公网 Agent：

- 本地开发：`http://127.0.0.1:8000`
- 团队公网联调：`https://auri-langchain-agent-api.onrender.com`
- 旧版回退：`https://auri-agent-api.onrender.com`

状态同步采用 SSE `/v1/stream` 加 `/v1/state` 轮询兜底。公网环境中如果 SSE 被浏览器、代理或部署平台中断，HMI 仍会通过轮询更新状态。

新版公网 Agent 使用 LangChain 工具编排自然语言，但 HMI 不直接调用工具。HMI 仍只消费 `WorldState`，并通过 `/v1/confirm` 处理车机确认。

## 主驾驶侧 Agent 交互

左侧 Agent 面板提供驾驶中可操作的轻量入口：

- `我还来得及吗？`：在 `primary_surface=vehicle_hmi` 且未进入待确认状态时启用，点击后向 Agent 发送标准 `user.utterance` 事件。
- `方案`：查看刚性责任、弹性任务、动作组和服务方案摘要。
- `车况`：查看空调、风量、驾驶场景、主交互端和腕上反馈。
- `同步`：查看手机、腕上和车机三端状态。
- `消息草稿` / `行程详情`：查看草稿摘要和 ETA 解释。

这些入口只承载驾驶中可快速理解的信息；长文本、商品明细和复杂选择仍放到手机端或停车后复盘。

## 车辆状态展示

HMI 读取 `WorldState.vehicle_state` 展示空调状态：

- `ac_on`
- `ac_target_temp`
- `ac_mode`
- `fan_speed`

该字段由 Agent 的 `control_ac` 工具写入。HMI 只读展示，不提供直接改写空调的按钮；如果后续需要车机语音或方向盘键控制，应提交标准用户意图或事件，让 Agent 工具链处理。

## 允许的写操作

- 标准事件：`POST /v1/event`
- 车机确认：`POST /v1/confirm`
- 演示重置：`POST /v1/session/reset`

页面禁止直接改写 stage、pressure、tasks、actions、confirmation 或 service order。语音和按钮使用同一个 `confirmation_id`，由后端保证幂等。

## 确认入口规则

确认按钮只有在以下条件同时满足时才可点击：

- `primary_surface=vehicle_hmi`
- `confirmation.owner_surface=vehicle_hmi`
- `confirmation.status=pending`

生活服务方案在车机只显示商品数、总价和配送时间，不显示完整商品列表。

调试时可在 URL 后追加 `?debug=1` 显示 HMI 内置事件按钮；正式展示默认隐藏，现场推进统一使用独立 Demo 控制台。
