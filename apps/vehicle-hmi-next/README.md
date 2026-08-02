# AURI Vehicle HMI Next

这是基于 Bosch-Agent 真实运行底座重建的 AURI 车机 HMI 候选版本。

当前进入 Phase 6 候选验收：AURI 品牌外壳、只读 World State、高德真实导航、驾驶员侧接管确认、本地完整主线和 AURI 体验补全均已实现。页面保留 Bosch-Agent 的 1920x1080 固定画布、整屏缩放、高清车辆、地图舞台、路线控制器、右侧玻璃浮层和底部 Dock；任务、ETA、风险、手机语音、腕上设备、动作和空调状态来自 Agent 完整快照。

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

## Phase 1-6 已完成能力

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
- 高德 Web JS API 真实底图、驾车路线、交通图层、车辆位置和下一导航动作。
- 高德地图挂载在 Bosch 中央舞台内；路线成功后才切换，加载失败、无任务、无坐标或额度触发时保留 Bosch 离线地图。
- 同一 Session 和目的地只规划一次路线；SSE revision 更新只推进车辆和路线分段，不重复消耗路线规划次数。
- 车辆进度按路线实际累计距离插值，箭头朝向来自路线切线；跟车相机随路线旋转，路线总览和缩放可操作。
- 目的地优先来自动态任务；契约提供坐标时直接使用，核心演示地点缺少坐标时使用冻结映射，未知地址不额外调用地理编码。
- Agent `eta` 和 `risk.late_minutes` 仍是业务真相；高德距离、道路和转向只用于导航表现。
- 浏览器按月软限制默认 200 次地图初始化和 200 次路线规划，避免演示误触持续消耗免费额度。
- 风险接管阶段在驾驶员侧原导航卡位置切换为 AURI 判断卡，不增加第二列网页卡片。
- 判断卡保持一句结论、最多三条动态动作、三端状态和一个主要确认入口。
- 手机语音转写在接管卡状态行和 AURI 二级页同步显示，车机不提供语音输入。
- 腕上状态以一次性通知横幅出现，按 `session_id + command_id` 去重，可自动消失或手动关闭。
- 只有 `primary_surface=vehicle_hmi`、owner 为车机且 confirmation pending/未过期时才显示确认按钮。
- 点击和方向盘 Enter 共用后端 `confirmation_id`；重复点击不会产生第二次执行请求。
- `/v1/confirm` 成功后消费 Agent 返回快照；完成态来自更高 revision，不由 HMI 提前推演。
- 401、WRONG_SURFACE、EXPIRED、NOT_FOUND 和网络失败显示低干扰错误，保留导航和原方案，不显示假成功。
- 停车后原导航卡切换为“手机继续处理”，完整消息、订单和处理记录转回手机端，车机不继续显示过期 ETA。
- 本地真实 Agent 主线从空任务推进到停车复盘，连续 10 次通过；公网 Agent 的 State/SSE 只读兼容验证通过。
- 原导航卡加入动态责任摘要：最多显示两项刚性/弹性任务及 `+N`，点击进入完整任务页，不写死任务数量或名称。
- 导航卡恢复 ETA、剩余分钟和剩余公里；高德路线元数据提供动态剩余时长，离线时仅显示可确认的数据。
- 增加行程详情、设备状态、任务详情、联系人/动作详情等 Bosch 风格二级页；长消息和订单明细不占据驾驶主屏。
- 手机、腕表、车机的主端和同步状态可随 `primary_surface`、`last_utterance`、`wearable` 和连接状态查看。
- 车辆接续、正常导航、完成恢复使用非模态通知；车外/L1 和停车后车机保持静默，不抢占手机主端。
- 腕表提示仅在车机成为主端后展示；设备未连接时明确显示“等待同步”，不冒充已送达。
- 服务订单主卡使用结构化摘要，显示模拟属性、件数/种类、金额和配送时段；失败时保留消息和任务调整方案。
- `action_completed` 只进行一次恢复 TTS，并按 `session_id + output.message_id` 去重；cooldown 不重复播报。
- 确认按钮按过期时间自动关闭；网络结果未知时保持锁定，完成 `/v1/state` 对账后才允许重试。
- World State 校验覆盖必填字段、Stage、Scene、Owner 和主要数组；非车机 Owner 的 output 不进入车机主结论。
- 候选页不再加载旧 Leaflet/二维码外部依赖，旧咖啡订单恢复、旧语音和旧导演控制器在 AURI 模式下不启动。

## 当前没有实现

- 全部 Demo 阶段的连续自动播放与性能长稳测试。
- WebSocket 可选兼容路径和四端完整联调。
- 后端 `/health` 的服务名、构建 SHA 和启动时间字段；当前 HMI 只展示后端实际提供的健康、Schema 和 LLM 状态。
- 正式 Route/Location 坐标契约；当前已知 Demo 地点使用冻结映射，未知地址保持离线降级。

剩余能力按 `myProj/Bosch-Agent底座_AURI重构/todolist.md` 完成四端联调、长稳测试和正式切换。禁止从旧 `apps/vehicle-hmi/` 复制 DOM、CSS 或卡片布局；只允许迁移经过测试的数据和接口逻辑。

## 开发约束

- `apps/vehicle-hmi-bosch-reference/` 是只读视觉基准，不得修改。
- `index.html` 仍包含休眠的原业务控制器。Phase 1 不删除它们，避免破坏视觉和动效；后续按场景逐步替换。
- AURI 覆盖层位于 `auri-theme.css` 和 `auri-shell.js`。
- Agent 同步层位于 `src/agent-client.js`；纯视图模型位于 `src/world-state-model.js`，两者不依赖旧 HMI DOM/CSS。
- 页面默认不显示开发控制条。后续 Debug 能力必须显式受 `?debug=1` 控制。
- 任何完成态必须来自更高 revision 的 Agent World State，不能由前端自行推演。

## Agent 配置

点击左上角连接状态，在 Bosch 风格浮层中选择公网、本地或 LangChain 服务并填写 Team Token。配置只保存在当前浏览器的 `localStorage`，仓库内没有默认 Token。

地图默认选择“自动读取 Agent 配置”，通过鉴权接口 `GET /v1/map-config` 获取公开 Web Key 和服务端安全代理；接口不会返回安全码。当前公网部署若返回 `{"enabled":false,"provider":"offline"}`，页面会继续使用 Bosch 离线地图。仅限本机诊断时，可在折叠的“地图连接设置”中填写 Web Key 和安全码，它们同样只保存在当前浏览器，不得写入仓库或共享截图。

也可在本机 `env.js` 中设置（不得提交真实 Token）：

```js
window.AURI_HMI_CONFIG = {
  apiBase: "https://auri-agent-api.onrender.com",
  token: "",
  stream: true,
  mapProvider: "auto"
};
```

使用 `?offline=1` 可跳过自动连接，用于固定夹具和视觉回归。

## 验证

```bash
node apps/vehicle-hmi-next/tests/world-state-model.test.cjs
node apps/vehicle-hmi-next/tests/agent-client.test.cjs
node apps/vehicle-hmi-next/tests/amap-adapter.test.cjs
```

浏览器回归覆盖空任务、契约示例、全部二级面板、1920x1080、1600x900、1280x720，以及公网 Agent 的 `/v1/state` 和 `/v1/stream` 实时同步。

### 本地完整主线

先在独立端口启动本地 Agent，并在仓库根目录启动静态服务器。随后运行：

```bash
AURI_AGENT_URL=http://127.0.0.1:8795 \
AURI_HMI_URL=http://127.0.0.1:5174/apps/vehicle-hmi-next/ \
/home/fly/miniconda3/envs/bosch-agent-dev/bin/python \
  apps/vehicle-hmi-next/tests/e2e_local_happy_path.py
```

脚本会重置目标 Agent Session，只能指向独立本地 Agent；检测到 `onrender.com` 会直接拒绝执行。它真实提交标准事件、等待 SSE revision、点击车机确认，并验证停车后主端回到手机。脚本不包含任何 API Key 或 Team Token。
