# AURI Vehicle HMI Next

这是基于 Bosch-Agent 真实运行底座重建的 AURI 车机 HMI 候选版本。

当前完成到 Phase 2：AURI 品牌外壳和只读 World State。页面保留 Bosch-Agent 的 1920x1080 固定画布、整屏缩放、高清车辆、地图舞台、路线控制器、右侧玻璃浮层和底部 Dock；任务、ETA、风险、手机语音、腕上设备、动作和空调状态来自 Agent 完整快照。

## 运行

在仓库根目录执行：

```bash
python -m http.server 5174
```

访问：

```text
http://127.0.0.1:5174/apps/vehicle-hmi-next/
```

正式 HMI 仍为：

```text
http://127.0.0.1:5174/apps/vehicle-hmi/
```

完成视觉和功能验收前，不替换正式目录。

## Phase 1-2 已完成

- AURI Logo、名称、口号和品牌 Token。
- 无任务首屏与“等待手机同步路线”状态。
- AURI、任务、消息、座舱四个 Bosch 风格玻璃浮层入口。
- 旧疲劳、咖啡和演示控制入口在正式页面休眠并不可操作。
- 车辆可见 Bosch 字样由 AURI 标识层遮盖，保持原高清车辆资产质量。
- 原地图路线、道路层次、车辆路径控制器和驾驶区动画机制保持可用。
- 1920x1080、1600x900、1280x720 浏览器回归通过。
- `GET /health`、`GET /v1/state` 和带 `X-Agent-Token` 的流式 `fetch` SSE。
- SSE 中断后轮询兜底、指数退避、重连快照对账和请求超时。
- 相同 Session 只接受更高 revision；Session 切换后拒绝已退休 Session 的延迟响应。
- 任务支持 0-N 项；不写死接孩子、超市、目的地、18:28 或动作数量。
- 只读展示 `last_utterance`、`wearable`、`vehicle_state`、`actions` 和 `service_orders`。
- 空调类输出只进入座舱状态，不占用驾驶现实结论。
- 本地、公网和 LangChain Agent 地址可配置；Token 不写入仓库，界面和诊断 API 均脱敏。

## 当前没有实现

- 高德真实地图。
- 车机确认写操作和幂等闭环。
- 腕上通知弹窗、接管卡和完整 Demo 阶段动效。
- WebSocket 可选兼容路径和四端完整联调。

这些能力按 `myProj/Bosch-Agent底座_AURI重构/todolist.md` 的 Phase 2-5 逐步接入。禁止从旧 `apps/vehicle-hmi/` 复制 DOM、CSS 或卡片布局；只允许迁移经过测试的数据和接口逻辑。

## 开发约束

- `apps/vehicle-hmi-bosch-reference/` 是只读视觉基准，不得修改。
- `index.html` 仍包含休眠的原业务控制器。Phase 1 不删除它们，避免破坏视觉和动效；后续按场景逐步替换。
- AURI 覆盖层位于 `auri-theme.css` 和 `auri-shell.js`。
- Agent 同步层位于 `src/agent-client.js`；纯视图模型位于 `src/world-state-model.js`，两者不依赖旧 HMI DOM/CSS。
- 页面默认不显示开发控制条。后续 Debug 能力必须显式受 `?debug=1` 控制。
- 任何完成态必须来自更高 revision 的 Agent World State，不能由前端自行推演。

## Agent 配置

点击左上角连接状态，在 Bosch 风格浮层中选择公网、本地或 LangChain 服务并填写 Team Token。配置只保存在当前浏览器的 `localStorage`，仓库内没有默认 Token。

也可在本机 `env.js` 中设置（不得提交真实 Token）：

```js
window.AURI_HMI_CONFIG = {
  apiBase: "https://auri-agent-api.onrender.com",
  token: "",
  stream: true
};
```

使用 `?offline=1` 可跳过自动连接，用于固定夹具和视觉回归。

## 验证

```bash
node apps/vehicle-hmi-next/tests/world-state-model.test.cjs
node apps/vehicle-hmi-next/tests/agent-client.test.cjs
```

浏览器回归覆盖空任务、契约示例、全部二级面板、1920x1080、1600x900、1280x720，以及公网 Agent 的 `/v1/state` 和 `/v1/stream` 实时同步。
