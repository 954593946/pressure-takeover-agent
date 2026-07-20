# message_draft_v1

用途：在 Agent 判断用户可能无法按时接孩子后，生成给老师和家人的消息草稿。消息必须等待用户确认后才能发送。

## Model

- 推荐模型：团队 LLM 网关中的稳定文本模型。
- temperature：`0.2`
- 输出格式：严格 JSON。

## Input Schema

```json
{
  "schema_version": "0.2.0",
  "risk": {
    "pressure_level": "L2",
    "late_minutes": 18,
    "reason_codes": ["TRAFFIC_DELAY", "RIGID_TASK_AT_RISK"]
  },
  "eta": "2026-07-15T18:28:00+08:00",
  "rigid_task": {
    "title": "接孩子",
    "scheduled_at": "2026-07-15T18:10:00+08:00",
    "location": "阳光小学",
    "waiting_party": ["孩子", "老师"]
  },
  "audiences": ["teacher", "family"],
  "tone": "direct"
}
```

## Output Schema

```json
{
  "drafts": [
    {
      "target": "teacher",
      "channel": "message",
      "body": "老师您好，我这边路况拥堵，预计 18:28 左右到校，会晚到约 18 分钟。麻烦您帮忙照看一下孩子，我到达后立即联系您。",
      "requires_confirmation": true
    },
    {
      "target": "family",
      "channel": "message",
      "body": "我这边会议延迟又遇到拥堵，预计接孩子会晚到约 18 分钟。AURI 已把超市任务后置，我会按当前路线安全驾驶。",
      "requires_confirmation": true
    }
  ]
}
```

## Allowed

- 说明晚到原因、预计到达时间和请求协助。
- 保持短句，适合驾驶中快速确认。
- 对家人说明弹性任务已后置。

## Forbidden

- 不得未经确认发送。
- 不得责备用户、老师或家人。
- 不得承诺精确到分钟以外的不可控结果。
- 不得建议用户加速、抢灯或危险驾驶。
- 不得编造联系人姓名。

## Fixed Test Cases

### Case 1

晚到 18 分钟，目标老师和家人。

期望：

- 生成两条草稿。
- 两条都 `requires_confirmation=true`。
- 文案包含预计到达时间或晚到分钟数。

### Case 2

晚到 3 分钟。

期望：

- 文案语气更轻，不夸大风险。
- 仍需要确认。

### Case 3

压力等级 L3。

期望：

- 只输出更短消息。
- 不输出长解释。
