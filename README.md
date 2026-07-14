# 随行压力接管 Agent

面向车机、手机、腕上设备三端的六周 Demo 单仓库。目标不是做普通提醒或情绪陪聊，而是在“会议延迟 + 拥堵 + 接孩子可能迟到”的刚性责任场景中，真实跑通：发现风险 → 判断介入 → 重排弹性任务 → 生成消息草稿 → 用户确认 → 多端恢复安静。

本轮模拟车辆、路况、ETA、部分腕上信号和消息发送结果；手机、车机 HMI、腕上设备反馈、Agent 状态机、确认幂等和多端同步必须真实运行。

## 仓库结构

```text
apps/
  mobile/            手机端任务与权限中心
  vehicle-hmi/       车机横屏 HMI
  demo-console/      演示控制台与事件日志
services/
  agent-api/         World State、状态机、Agent、安全校验和广播
devices/
  zepp-os/           Amazfit Active 2 / Zepp OS 主验证路线
  esp32-s3/          T-Watch S3 Plus 兜底路线
contracts/
  openapi.yaml       HTTP/SSE 接口草案
  event.schema.json  统一事件信封
  world-state.schema.json
  examples/          联调样例
packages/
  ui/                三个 Web 界面的共享视觉资产（可选）
  test-fixtures/     跨端测试场景与固定输入（可选）
docs/
  architecture.md    架构、状态机和数据流
  workstreams.md     五人分工与目录所有权
  demo-scope.md      六周 Demo 范围和验收重点
  decisions/         需要长期保留的架构决策
```

产品原始材料保留在仓库根目录：`随行压力接管Agent_六周Demo汇报材料_v2_回应评审意见.docx`。

## 开工顺序

1. 全员先阅读 [docs/demo-scope.md](docs/demo-scope.md) 和 [docs/architecture.md](docs/architecture.md)。
2. 三端开发共同评审并冻结 `contracts/` 的 v0.1 字段。
3. Agent 后端先实现 `GET /health`、`POST /v1/events`、`GET /v1/world-state` 和实时广播。
4. 各端先用 `contracts/examples/` 的固定 World State 独立渲染，再接实时服务。
5. 第一次联调只跑四步：创建任务 → 会议延迟 → 进入车辆 → 重置；稳定后再接拥堵、接管和确认。

## 协作约定

- `main` 始终保持可演示；功能通过短分支和 Pull Request 合入。
- 分支名：`feat/mobile-voice-input`、`feat/watch-vibration`、`fix/confirmation-idempotency`。
- 跨端字段只在 `contracts/` 修改，并在同一个 PR 中更新示例和变更说明。
- 密钥只放本地 `.env`，禁止提交 API Key、手机号、联系人真实信息或设备凭据。
- 详细流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 尚待团队冻结

- 手机端最终采用 PWA 还是原生/跨端 App（文档当前建议 PWA）。
- Web 技术栈和 Agent 后端语言。
- 实时通道最终用 WebSocket、SSE，还是二者并存。
- Zepp OS 与 ESP32-S3 的网关方式和止损日期。
- ASR、TTS、LLM 服务提供方。

这些选择不影响当前目录和 v0.1 契约，可以由对应负责人提交 ADR 决策。
