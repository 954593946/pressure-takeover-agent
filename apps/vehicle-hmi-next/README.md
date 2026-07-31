# AURI Vehicle HMI Next

这是基于 Bosch-Agent 真实运行底座重建的 AURI 车机 HMI 候选版本。

当前只完成 Phase 1：AURI 品牌空壳。页面保留 Bosch-Agent 的 1920x1080 固定画布、整屏缩放、高清车辆、地图舞台、路线控制器、右侧玻璃浮层和底部 Dock；尚未接入 AURI World State。

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

## Phase 1 已完成

- AURI Logo、名称、口号和品牌 Token。
- 无任务首屏与“等待手机同步路线”状态。
- AURI、任务、消息、座舱四个 Bosch 风格玻璃浮层入口。
- 旧疲劳、咖啡和演示控制入口在正式页面休眠并不可操作。
- 车辆可见 Bosch 字样由 AURI 标识层遮盖，保持原高清车辆资产质量。
- 原地图路线、道路层次、车辆路径控制器和驾驶区动画机制保持可用。
- 1920x1080、1600x900、1280x720 浏览器回归通过。

## 当前没有实现

- `/v1/state`、SSE、WebSocket 和 revision 同步。
- 动态任务、ETA、风险、动作、确认、腕上和空调状态。
- 高德真实地图。
- AURI 完整 Demo 阶段和多端联调。

这些能力按 `myProj/Bosch-Agent底座_AURI重构/todolist.md` 的 Phase 2-5 逐步接入。禁止从旧 `apps/vehicle-hmi/` 复制 DOM、CSS 或卡片布局；只允许迁移经过测试的数据和接口逻辑。

## 开发约束

- `apps/vehicle-hmi-bosch-reference/` 是只读视觉基准，不得修改。
- `index.html` 仍包含休眠的原业务控制器。Phase 1 不删除它们，避免破坏视觉和动效；后续按场景逐步替换。
- AURI 覆盖层位于 `auri-theme.css` 和 `auri-shell.js`。
- 页面默认不显示开发控制条。后续 Debug 能力必须显式受 `?debug=1` 控制。
- 任何完成态必须来自更高 revision 的 Agent World State，不能由前端自行推演。
