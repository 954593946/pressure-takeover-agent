# 高德地图接入车机 HMI

## 结论

当前车机 HMI 默认接入高德地图 JS API 2.0。页面加载时先从 Agent 获取安全配置，再初始化真实 2D 地图；只有配置、网络或额度保护异常时才降级到离线地图。

本项目使用高德能力的边界是：

- 高德负责真实底图、道路名称、驾车路线几何、实时交通图层和车辆沿路线移动。
- Agent World State 继续负责场景、ETA、晚到分钟数、压力等级、任务重排、动作组和确认入口。
- 高德接口失败、Key 缺失、弱网或现场断网时，自动保留当前 SVG 离线演示地图。

高德不能替代 Agent 的现实判断，也不能直接修改 `stage`、`risk`、`tasks`、`actions` 或 `confirmation`。

## 已接入能力

代码位置：

```text
apps/vehicle-hmi/amap-adapter.js
apps/vehicle-hmi/amap-adapter.test.cjs
```

已实现：

| 能力 | 高德接口 | HMI 用法 |
| --- | --- | --- |
| 真实底图 | `AMap.Map` | 使用 `normal` 2D 地图，显示真实道路、建筑、POI 和高德版权信息 |
| 驾车路线 | `AMap.Driving` | 获取路线坐标、道路指令、下一动作距离 |
| 实时路况 | `AMap.TileLayer.Traffic` | 驾驶时低透明度展示，风险阶段提高强调 |
| 路线绘制 | `AMap.Polyline` | 白色描边、灰色已行驶、蓝色剩余路线、黄色拥堵段 |
| 车辆位置 | `AMap.Marker` + MoveAnimation | 按 World State 阶段进度沿路线移动并调整方向 |
| 起点与目的地 | `AMap.Marker` | 显示“博世苏州・星龙街455号”和“阳光小学”标签 |
| 地图操作 | Map zoom / fit view | 右侧 `+`、`-`、`北`按钮真实控制在线地图 |
| 失败降级 | HMI adapter | 自动切回 SVG 离线演示地图 |

当前 Demo 起点采用博世公开办公地址“江苏省苏州工业园区星龙街455号”，坐标为高德公开地点页对应位置；终点使用非个人化冻结演示坐标并标记为“阳光小学”，不代表任何真实儿童学校，不提交家庭、联系人或个人轨迹。

## 申请正确的 Key

需要在高德开放平台：

```text
应用管理
-> 创建应用
-> 添加 Key
-> 服务平台选择“Web端（JS API）”
```

不要使用：

```text
Web 服务 Key
Android Key
iOS Key
```

这些 Key 与 JS API 平台不匹配，会返回 `USERKEY_PLAT_NOMATCH`。

2021-12-02 之后创建的 JS API Key 必须配套安全密钥。

## 自动配置链路

HMI 默认配置 `mapProvider=auto`。页面启动顺序：

```text
读取当前浏览器保存的 Agent API 与 Team Token
-> GET /v1/map-config
-> 获得 Web JS Key、/_AMapService 地址和 normal 样式
-> 设置 window._AMapSecurityConfig.serviceHost
-> 加载高德 JS API 2.0
-> 创建 2D 地图并规划一次驾车路线
-> 再连接 /v1/state 与 /v1/stream
```

`/v1/map-config` 不返回 Security JS Code。高德服务请求由 Agent `/_AMapService` 代理注入安全密钥，并限制允许的 HMI Origin。

## 本地 Demo 配置

打开 HMI：

```text
http://127.0.0.1:5174/apps/vehicle-hmi/
```

如果本地 Agent 已配置下面的环境变量，只需保存 Agent 地址并重连，无需在浏览器填写地图密钥：

```dotenv
AMAP_JS_API_KEY=<Web端 JS API Key>
AMAP_SECURITY_JS_CODE=<安全密钥>
AMAP_PUBLIC_BASE_URL=http://127.0.0.1:8000
AMAP_ALLOWED_ORIGINS=http://127.0.0.1:5174,http://localhost:5174
```

浏览器手动填写 Key 和 Security JS Code 仅保留给地图负责人本机诊断，不用于公网部署。

成功时：

```text
window.AURI_HMI.getMapStatus()
```

应返回：

```json
{
  "mode": "online",
  "message": "高德在线地图已连接"
}
```

失败时页面不会白屏，返回：

```json
{
  "mode": "offline",
  "message": "具体失败原因"
}
```

并继续展示离线导航。

## 公网域名配置

公网页面：

```text
https://wangwang20.github.io/auri-pressure-takeover-web/apps/vehicle-hmi/
```

高德控制台需要允许对应公网域名。至少检查：

```text
wangwang20.github.io
```

如果后续迁移到团队域名或其他静态托管，需要同步更新高德 Key 的域名限制。

常见错误：

| 错误 | 原因 |
| --- | --- |
| `INVALID_USER_KEY` | Key 错误或已过期 |
| `INVALID_USER_SCODE` | Security JS Code 与 Key 不匹配 |
| `INVALID_USER_DOMAIN` | 当前页面域名未被允许 |
| `USERKEY_PLAT_NOMATCH` | 使用了非 Web端 JS API Key |

## 正式环境安全方案

高德官方不建议在生产前端明文保存 Security JS Code。

推荐链路：

```text
HMI
  -> 高德 JS API Key
  -> AURI 服务端 /_AMapService 代理
  -> 服务端注入 Security JS Code
  -> 高德 Web API
```

HMI 自动获得的“安全代理地址”应为完整服务地址，例如：

```text
https://example.com/_AMapService
```

此时浏览器设置：

```js
window._AMapSecurityConfig = {
  serviceHost: "https://example.com/_AMapService"
};
```

服务端保存：

```text
AMAP_SECURITY_JS_CODE
```

禁止把安全密钥提交到：

```text
Git 仓库
公开 GitHub Pages
PR 描述
截图
前端默认配置
```

本轮公网 Demo 已按代理模式实现。浏览器本地明文方式只用于一次性诊断，不作为团队运行方式。

## 数据和隐私边界

使用在线地图意味着路线坐标会发送到高德服务。

Demo 要求：

- 只使用冻结演示坐标。
- 不使用团队成员真实家庭地址、孩子学校、实时位置或历史轨迹。
- 不把路线坐标写入 Event Log、公开截图或脱敏不足的报告。
- 产品化前由合规负责人确认地图服务条款、坐标数据处理、隐私告知和留存策略。

## 测试

2026-07-28 已使用有效的 Web端（JS API）Key 做真实浏览器冒烟测试，结果：

```text
AMap JS API 2.0 加载成功
AMap.Driving 路线规划成功
博世苏州起点、Demo 学校终点和真实路线正常显示
高德真实 2D 底图、道路 POI、实时交通图层、Logo 和版权信息正常显示
HMI map status = online
下一道路指令和距离已由高德路线结果更新
1600×814 车机视口无页面溢出或信息遮挡
```

测试密钥只写入临时浏览器 `localStorage`，未写入代码、Git、截图说明或团队文档。

离线和适配器逻辑：

```bash
node --check apps/vehicle-hmi/amap-adapter.js
node --check apps/vehicle-hmi/app.js
node apps/vehicle-hmi/amap-adapter.test.cjs
```

浏览器无 Key 回归标准：

```text
map.mode = offline
amapCanvas.hidden = true
SVG 导航继续显示
body.scrollWidth = body.clientWidth
```

真实 Key 联调标准：

```text
高德底图正常显示且官方标识可见
路线规划成功
车辆标记位于路线中心
控制台推进 stage 后车辆和拥堵段更新
Agent ETA 和 late_minutes 不被高德结果覆盖
断网或 Key 错误时回退离线地图
```

## 免费额度使用约束

当前 HMI 的调用行为：

```text
每次重新加载在线 HMI：
1 次 JS 地图图面初始化
1 次 AMap.Driving 驾车路线规划

Console 推进事件、SSE 更新、轮询更新和车辆沿路线移动：
不重新调用 AMap.Driving
```

浏览器本地保护阈值：

```text
地图初始化：200 次/月
驾车路线规划：200 次/月
```

该阈值远低于个人认证账号的免费月配额。达到阈值后，适配器不会加载高德脚本，而是直接回退离线 SVG 地图。该计数用于单浏览器 Demo 保护，不能替代高德控制台的账号级用量监控。

检查方式：

```js
window.AURI_HMI.getMapUsage()
```

示例：

```json
{
  "month": "2026-07",
  "mapLoads": 2,
  "routePlans": 2
}
```

Demo 使用建议：

- 演示前打开一次 HMI 并保持页面，不要反复强制刷新。
- 重跑故事线使用 Console 的 `重置 Demo`，不要通过刷新 HMI 重置。
- 不需要在线地图时，在“连接与地图”中切换为“离线演示地图”。
- 当前版本不调用 POI 搜索、输入提示、地理编码或逆地理编码。
- 在高德控制台查看用量并设置额度预警；不要为测试编写自动刷新脚本。

## 在线导航几何

高德路线通常在不同道路密度下返回数量不均匀的坐标点。HMI 不使用坐标点下标估算车辆进度，而是：

```text
计算整条路线每两个点之间的球面距离
-> 建立累计距离表
-> 按 World State 阶段进度定位真实距离位置
-> 在当前线段内插值车辆坐标
-> 从同一坐标切分已行驶路线和剩余路线
-> 计算前方拥堵段与地图视野中心
```

这可以避免车辆跳点、偏离路线、箭头方向错误以及不同路段进度不均匀。

## 官方资料

- 博世苏州公开地址：<https://www.bosch-engineering.cn/公司/全球办事处/>
- 高德“博世汽车部件（苏州）有限公司”地点页：<https://www.amap.com/place/B02000IQKF>
- 高德地图 JS API 2.0 准备：<https://lbs.amap.com/api/javascript-api-v2/prerequisites>
- JS API 安全密钥：<https://lbs.amap.com/api/javascript-api-v2/guide/abc/jscode>
- 驾车路线规划：<https://lbs.amap.com/api/javascript-api-v2/guide/services/navigation>
- 高德官方图层：<https://lbs.amap.com/api/javascript-api-v2/guide/layers/official-layers>
- 自定义地图样式：<https://lbs.amap.com/api/javascript-api-v2/guide/map/map-style/>
