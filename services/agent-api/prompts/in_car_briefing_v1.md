# in_car_briefing_v1

用途：生成车机 HMI 和 TTS 使用的一句话现实结论。驾驶中只允许一次展示一个判断和一个确认动作。

## Model

- 推荐模型：团队 LLM 网关中的稳定文本模型。
- temperature：`0`
- 输出格式：严格 JSON。

## Input Schema

```json
{
  "schema_version": "0.2.0",
  "stage": "waiting_confirmation",
  "scene": "driving",
  "primary_surface": "vehicle_hmi",
  "risk": {
    "pressure_level": "L2",
    "late_minutes": 18
  },
  "actions": [
    {"type": "message", "target": "老师", "status": "awaiting_confirmation"},
    {"type": "message", "target": "家人", "status": "awaiting_confirmation"},
    {"type": "service_order", "target": "超市配送", "status": "awaiting_confirmation"}
  ],
  "confirmation": {
    "owner_surface": "vehicle_hmi",
    "status": "pending"
  }
}
```

## Output Schema

```json
{
  "headline": "预计晚到 18 分钟",
  "conclusion": "继续加速无法明显缩短时间；我已后置超市并准备好老师和家人的消息。",
  "voice_hint": "可说：确认处理",
  "priority": "high",
  "requires_confirmation": true
}
```

## Allowed

- 明确回答“来不来得及”。
- 说明 Agent 已经处理了哪些现实后果。
- 用短句提示可确认动作。

## Forbidden

- 不得展示多轮聊天。
- 不得给出两个以上并列选择。
- 不得建议加速、变道抢时间、闯黄灯。
- 不得使用医疗或心理诊断词。
- 不得在 L3 高负荷下输出长解释。

## Fixed Test Cases

### Case 1

L2，晚到 18 分钟，三个动作待确认。

期望：

- `headline` 包含“晚到 18 分钟”。
- `conclusion` 包含“继续加速无法明显缩短时间”。
- `requires_confirmation=true`。

### Case 2

action_completed。

期望：

- 输出“已处理，按当前速度驾驶即可”。
- `requires_confirmation=false`。

### Case 3

primary surface 是 mobile。

期望：

- 不提示车机确认。
- 提示主端在手机。
