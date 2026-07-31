# Bosch-Agent 运行效果基准

此目录用于冻结 Bosch-Agent 原工程的实际运行效果，作为 AURI 车机 HMI 后续重构的视觉、布局、动效和交互基准。

## 访问方式

在仓库根目录启动静态服务：

```bash
python -m http.server 5174
```

浏览器打开：

```text
http://127.0.0.1:5174/apps/vehicle-hmi-bosch-reference/
```

## 基准范围

- `index.html` 是 `Bosch-Agent/bosch_fatigue_monitor/bosch_unified_demo_v2.html` 的逐字节副本。
- `rds_figma_theme.css`、`icons/` 和 `sounds/` 均从 Bosch-Agent 原工程复制。
- 原工程与本目录的 HTML SHA-256 均为 `d87419d14e287dcb8c6658102e0667684e6a2c45d84705444486097ff434c087`。
- 原工程与本目录的 CSS SHA-256 均为 `ad7a196e5e0978cd2ff10dcf8492b3e113e77f410567f049252348d99f522918`。
- 图像资源为 `63/63`，声音资源为 `3/3`，页面引用的本地资源无缺失。

## 使用约束

此目录不承载 AURI 业务接入，不直接修改。后续开发应从本目录复制出新的实现目录，在保留以下能力的前提下逐步替换业务：

1. 固定 1920x1080 设计画布和整屏等比缩放。
2. 顶部系统栏、左侧驾驶区、中央地图舞台、底部 Dock 的完整层级。
3. 高清车辆素材、地图多层渲染、路线流动、车辆移动和场景切换动画。
4. Bosch-Agent 原有组件密度、阴影、边框、光效和交互反馈质量。

当前 AURI 页面 `apps/vehicle-hmi/` 与此目录相互独立。完成视觉基准验收之前，不将 AURI 功能写入此目录。
