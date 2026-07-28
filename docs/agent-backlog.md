# Agent / 后端六周执行清单

## Milestone 1：契约与骨架（第 1 周）

- [x] contracts v0.2 候选对象、事件、错误码与样例
- [x] FastAPI、配置、健康检查、SSE/WebSocket
- [x] Bosch OpenAI 兼容 LangChain Tool Calling Agent、动态回复与安全兜底
- [ ] 手机、车机、腕上各一名 Owner 完成契约评审

## Milestone 2：确定性主闭环（第 2 周）

- [x] Session、Event 幂等、World State revision
- [x] L0-L3 风险与多源 L3 门槛
- [x] primary_surface 与旧入口失效
- [ ] Demo 控制台接标准事件序列
- [ ] 手机/车机消费相同完整快照

## Milestone 3：动作与生活服务（第 3 周）

- [x] Profile 两套预置策略
- [x] 消息、采购动作规划与模拟订单预览
- [x] 超预算、缺货阻断和停车后处理语义
- [x] Confirmation 与订单幂等
- [ ] 手机展示明细，车机只展示摘要

## Milestone 4：设备与语音（第 4 周）

- [ ] 手机 ASR/TTS 统一 Client
- [ ] 腕上 SET_STATE、ACK、重复 command_id 去重
- [ ] BLE 断线与手机模拟腕上降级

## Milestone 5：异常回归（第 5 周）

- [x] LLM 超时保留已执行工具状态并回退到状态化回复
- [ ] ASR 失败、SSE 断线、BLE 离线
- [ ] 缺货、超预算、重复事件、并发确认
- [ ] 50 个 Schema 样例与 10 个越权场景

## Milestone 6：冻结与彩排（第 6 周）

- [ ] 完整脚本连续 10 次至少 9 次无人工改状态
- [ ] 三次现场彩排、离线包、故障预案和备用视频
- [ ] `release/demo-v1` 标记与 SHA-256 交付清单
