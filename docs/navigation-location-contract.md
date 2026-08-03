# Route / Location 导航契约

## 目的

`Task.location` 只表达用户任务中的地点文本，不能承担地图坐标、路线归属和数据来源。Agent 现在通过可选的 `WorldState.navigation` 发布车机可直接消费的导航位置；HMI 不再把地点文本解析作为主要路径。

本扩展保持 `schema_version=0.2.0`，因为字段是可选的，现有手机端已配置忽略未知字段。严格 JSON Schema 消费者应同步仓库中的最新 `contracts/world-state.schema.json`。

## 数据结构

```json
{
  "navigation": {
    "route_id": "route_demo_task_pickup_child",
    "task_id": "task_pickup_child",
    "origin": {
      "name": "博世苏州",
      "longitude": 120.791879,
      "latitude": 31.33468,
      "address": "江苏省苏州工业园区星龙街455号"
    },
    "destination": {
      "name": "阳光小学",
      "longitude": 120.7359,
      "latitude": 31.3048,
      "address": null
    },
    "current_location": null,
    "progress": 0.7,
    "source": "demo_fixture",
    "is_simulated": true,
    "updated_at": "2026-07-15T18:28:00+08:00"
  }
}
```

字段语义：

| 字段 | 责任与约束 |
| --- | --- |
| `route_id` | 路线稳定标识；相同路线的 revision 更新不得触发重复规划 |
| `task_id` | 导航对应的任务；端侧无需通过标题猜测任务 |
| `origin` / `destination` | WGS/GCJ 使用边界由地图接入层负责；本 Demo 坐标直接用于高德 Web JS API |
| `current_location` | 真实车辆定位接入后使用；当前 Demo 为 `null`，不伪造定位 |
| `progress` | `0..1`；当前由 Demo 阶段生成，必须结合 `is_simulated=true` 理解 |
| `source` | `agent`、`vehicle_api` 或 `demo_fixture` |
| `is_simulated` | 明确区分演示数据与真实车辆/地图数据 |
| `updated_at` | 本导航投影随 World State 更新的时间 |

经纬度在模型和 Schema 中都有范围校验：longitude `-180..180`，latitude `-90..90`。

## 数据流

```text
手机语音 / task.created
-> Agent 解析 Task.location
-> 导航解析器只匹配冻结的非个人 Demo 地点
-> Runtime 在每次 revision 提交时同步 WorldState.navigation
-> GET /v1/state、SSE /v1/stream、WS /v1/ws 发布同一完整快照
-> HMI 视图模型校验坐标并生成 route
-> 高德 AMap.Driving 只按 route_id + 坐标规划一次
-> 后续 revision 仅更新进度、ETA、风险和界面状态
```

Agent 是导航状态唯一写入者。HMI 只读消费，不把高德结果反写到 World State；`eta` 和 `risk.late_minutes` 仍以 Agent 为业务真相。

## 兼容与降级

消费优先级：

1. 有效的 `WorldState.navigation.origin/destination`。
2. 旧服务没有 `navigation` 时，HMI 临时使用原冻结 Demo 映射。
3. 未知地点、无坐标、地图 Key/网络/额度异常时使用 Bosch 离线地图。

兼容映射只用于迁移，不是正式位置解析能力。后续所有新增路线应由 Agent、车辆 API 或受控位置服务写入 `navigation`。

## 隐私与产品化边界

- 当前解析器只接受明确的冻结 Demo 地点名称，不匹配泛化词“学校”“超市”，也不对未知地址调用地理编码。
- 不提交家庭地址、真实学校、联系人位置或个人轨迹。
- 产品化后，位置解析、用户授权、留存期限、地图坐标系和跨境处理需要单独合规评审。
- 接入真实车辆定位时，由车辆适配器写入 `current_location`，并把 `source` 改为 `vehicle_api`、`is_simulated` 改为 `false`。

## 模块依赖

强依赖：Agent 模型与 JSON Schema、Runtime 导航同步、HMI ViewModel 必须同步交付。

可并行：车辆定位适配器、高德路线渲染、手机地点选择器和腕表状态展示可在本契约冻结后独立开发。

接口定义优先级：`GeoPoint` 范围、`route_id/task_id` 关联、`source/is_simulated` 语义和 `navigation=null` 降级必须先冻结；路线样式、相机、动效可以后置。
