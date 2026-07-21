# 车机 HMI

> 开发前先阅读根 [README](../../README.md) 的 P0 闭环、唯一主交互端和 AURI 视觉基线。

定位：驾驶阶段的安全展示和单一确认入口，运行在横屏平板、大屏或电脑浏览器，不接真实车辆。

P0 区域：路线/ETA、Agent 状态、现实结论、动作列表和“确认发送”大按钮。驾驶中不做长文本、多选决策、完整地图、多轮聊天或真实车控。

## Agent 接入方式

HMI 是 World State 渲染器，不是状态机。页面启动后读取 `/v1/state`，并默认连接 `/v1/stream` 接收 SSE 更新。

```html
<script>
  window.AURI_CONFIG = {
    apiBase: "http://127.0.0.1:8000",
    streamUrl: "http://127.0.0.1:8000/v1/stream",
    token: "",
    stream: true
  };
</script>
```

如果使用云端 Agent，可在本地调试页注入 `apiBase` 和 `token`。不要把团队 Token 或 API Key 提交到代码仓库。

当前 HMI 底部提供 `Agent` 配置入口。团队协同时：

1. 打开车机 HMI 页面。
2. 点击底部 `Agent`。
3. 选择 `公网 Agent` 或手动填写 `Agent API`。
4. 在 `Team Token` 输入框填写团队令牌。
5. 点击 `保存并重连`。

配置保存在当前浏览器 `localStorage`，不会写入仓库。公网页面不能继续使用 `127.0.0.1` 作为 Agent API，因为那只代表访问者自己的电脑。

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
