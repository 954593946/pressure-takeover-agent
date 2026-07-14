# 五人工作流与目录边界

下面按当前“硬件 2 + 软件 2 + Web/Agent 1”的分工建立。姓名和远程仓库账号确定后，再补 CODEOWNERS。

| 工作流 | 主目录 | 主责 | 必须共同评审 |
|---|---|---|---|
| 硬件 A：Zepp OS | `devices/zepp-os/` | Active 2 真机安装、状态页、震动、Side Service/网关验证 | 手机集成负责人 |
| 硬件 B：ESP32-S3 | `devices/esp32-s3/` | T-Watch 固件、显示、震动、BLE/Wi-Fi 和兜底按键 | 手机集成负责人 |
| 软件 A：手机体验 | `apps/mobile/` | 今日页、语音入口、任务/风险/草稿页面 | Agent/API 负责人 |
| 软件 B：手机集成 | `apps/mobile/` + 契约客户端 | 实时连接、腕上网关、确认和失败兜底 | 两位硬件 + Agent/API 负责人 |
| Web/Agent | `apps/vehicle-hmi/`、`apps/demo-console/`、`services/agent-api/` | HMI、控制台、Agent 提示词、World State 和状态机 | 手机集成负责人 |

## 共享所有权

- `contracts/`：五人共享；任何改动至少由一个生产方和一个消费方确认。
- `docs/demo-scope.md`：产品/项目负责人冻结范围，开发人员不得在单端 PR 中悄悄扩展。
- `packages/ui/`：仅放真正跨两个以上 Web 界面复用的内容，避免过早抽象。
- `packages/test-fixtures/`：Agent/API 负责人维护主场景，所有端消费同一批固定状态。

## 第一轮并行任务

1. 硬件 A：在 Active 2 上跑通自研页面、一次短震和手机侧消息接收；两到三天内给出 Go/No-Go。
2. 硬件 B：跑通蓝/黄/绿三态、震动和一种通信方式，保证兜底路线不空转。
3. 软件 A：用固定 `world-state.json` 做今日页、风险卡和消息草稿。
4. 软件 B：实现事件发送/状态订阅适配层，并定义腕上网关接口。
5. Web/Agent：实现最小 Agent API、HMI 静态状态页和控制台四个基础事件。

## 联调里程碑

- M1：所有端能读取同一个固定 World State。
- M2：控制台创建任务/会议延迟，手机和腕上设备同步变化。
- M3：进入车辆/拥堵，车机成为主界面。
- M4：接管、草稿、确认幂等、三端完成态跑通。
- M5：断网、LLM 超时、ASR 失败和腕上掉线均有演示兜底。
