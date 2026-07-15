# 五人工作流与模块所有权

团队配置：手机端 2 人、腕上硬件 1 人、车机 Web 1 人、Agent/后端 1 人。Owner 对模块接口、实现和完成定义负责；互相 Review 不改变唯一 Owner。

| Owner | 主目录 | 拥有的模块 | 必须共同评审 |
|---|---|---|---|
| 手机开发 A：业务 UI | `apps/mobile/` | 任务、风险、Profile、权限、消息、服务方案、停车后复盘 | 手机 B、Agent |
| 手机开发 B：基础能力 | `apps/mobile/` | API Client、WebSocket、ASR/TTS、缓存、通知、调试页、腕上网关/BLE | 硬件、Agent |
| 腕上硬件 | `apps/watch/active2-pressure-watch/`、`devices/` | Zepp OS/ESP32、显示、触觉、通信、ACK、传感器与离线兜底 | 手机 B |
| 车机 Web | `apps/vehicle-hmi/`、`apps/demo-console/` | HMI、输出预算、单一确认、实时状态、场景/故障控制台 | Agent、手机 B |
| Agent / 后端 | `services/agent-api/`、`contracts/`、`packages/test-fixtures/` | World State、风险、交互调度、Profile、动作编排、权限幂等、Adapter、日志 | 四端对应 Owner |

## 共享所有权规则

- `contracts/`：Agent 主责；每次变更至少由一个事件生产方和一个状态消费方共同评审。
- 根 README、`docs/demo-scope.md`：项目范围入口；任何 P0 增删必须显式评审。
- `packages/ui/`：只有两个以上 Web 端真实复用的视觉 Token/组件才能进入。
- `packages/test-fixtures/`：Agent 维护标准场景；四端使用相同快照，禁止复制后各自修改。
- `品牌.png`：当前 AURI 品牌板。任何视觉实现都遵守根 README，不能从现有原型反推品牌。

## 各 Owner 的 P0 完成定义

### 手机开发 A

- 九个业务模块可由真实 World State 进入：启动/连接、今日任务、任务详情、风险、消息、服务方案、Profile、权限、停车后复盘。
- 效率型与品质型的预算、配送和替代结果明显不同；权限边界一致。
- 驾驶中不出现完整商品列表和复杂确认，状态刷新后不漂移。

### 手机开发 B

- 统一 Client、状态层、缓存、重连、版本去重、ASR/TTS、调试页和离线包可用。
- 腕上网关支持 HELLO、SET_STATE、ACK、可选 SENSOR、PING/PONG。
- 重复 `command_id` 不重复震动；BLE 离线可以一键降级且不阻塞主流程。

### 腕上硬件

- 真机可佩戴、亮屏、真实震动；支持 idle、warning、handover、processing、completed、error。
- 四类触觉可区分；ACK 能识别 duplicate；断连、低电量和 unsupported sensor 有明确状态。
- Zepp OS 截止点失败后立即切 ESP32-S3，不同时消耗两条主线资源。

### 车机 Web

- 按后端 `stage` 和 `primary_surface` 渲染；不在前端推演状态。
- 驾驶中始终遵守 1 句结论、1 个动作组、1 个确认；按钮/语音共用一个 confirmation。
- 控制台通过标准事件覆盖任务、会议、车辆、拥堵、辅助信号、服务异常、确认、停车和重置。

### Agent / 后端

- Session、Event、World State、Task、Risk、Interaction、Message、Permission、Confirmation、Broadcast、Logging 可独立测试。
- Profile、Capability Registry、Mock Catalog、Action Planner、Order Preview、Service Adapter、Ledger 和 Fallback 跑通。
- 固定输入决策一致；10 个越权场景全部拦截；重复确认不重复发消息、下单或震动。

## 联调顺序

1. 冻结八个共享对象、API、事件、错误码和样例。
2. Agent 提供假数据版快照、事件、状态流和确认；四端使用固定快照。
3. 手机核心页面/状态层、车机状态页、腕上 SET_STATE/ACK 各自跑通。
4. 跑通基础闭环：任务 → 会议 → 上车 → 拥堵 → 接管 → 消息确认 → 完成。
5. 加入 Profile、订单预览和 Mock Adapter，跑通生活服务结尾。
6. 加入断网、BLE、ASR、缺货、超预算和重复确认回归。
7. 冻结 UI、文案和演示数据，完成 10 次回归和 3 次彩排。
