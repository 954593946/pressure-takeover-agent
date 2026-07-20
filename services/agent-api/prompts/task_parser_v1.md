# task_parser_v1

用途：把手机端语音转写文本解析为结构化任务，并区分刚性责任与弹性任务。

## Model

- 推荐模型：团队 LLM 网关中的稳定文本模型。
- temperature：`0`
- top_p：`1`
- 输出格式：严格 JSON。

## Input Schema

```json
{
  "schema_version": "0.2.0",
  "locale": "zh-CN",
  "user_text": "今天 18:10 接孩子，之后去超市",
  "now": "2026-07-15T16:30:00+08:00",
  "known_places": [
    {"name": "阳光小学", "type": "school"},
    {"name": "盒马鲜生", "type": "grocery"}
  ],
  "profile": {
    "profile_type": "efficiency",
    "budget_limit": 200,
    "delivery_priority": "fastest"
  }
}
```

## Output Schema

```json
{
  "tasks": [
    {
      "title": "接孩子",
      "scheduled_at": "2026-07-15T18:10:00+08:00",
      "location": "阳光小学",
      "task_type": "rigid",
      "priority": "high",
      "adjustable": false,
      "waiting_party": ["孩子", "老师"],
      "capability_tags": ["pickup", "school"]
    },
    {
      "title": "去超市",
      "scheduled_at": null,
      "location": "盒马鲜生",
      "task_type": "flexible",
      "priority": "medium",
      "adjustable": true,
      "waiting_party": [],
      "capability_tags": ["grocery_delivery"]
    }
  ],
  "confidence": 0.92,
  "needs_clarification": false,
  "clarification_question": null
}
```

## Allowed

- 把涉及接孩子、接老人、接病人、赶飞机、高价值会议等识别为刚性任务。
- 把购物、取快递、加油、买咖啡等识别为弹性任务。
- 在缺少地点时使用 `known_places` 中最匹配的位置。
- 对日期做相对时间归一化，例如“今天”转为输入 `now` 的同一天。

## Forbidden

- 不得擅自新增用户没有表达的任务。
- 不得把刚性责任后置为可调整任务。
- 不得输出自然语言解释。
- 不得请求发送消息、下单或导航。

## Fixed Test Cases

### Case 1

输入：`今天 18:10 接孩子，之后去超市`

期望：

- `接孩子` 为 `rigid/high/adjustable=false`。
- `去超市` 为 `flexible/medium/adjustable=true`。
- `needs_clarification=false`。

### Case 2

输入：`下班先买菜再去接孩子`

期望：

- 如果接孩子时间不明确，`needs_clarification=true`。
- 不得把买菜排在可能影响接孩子的位置。

### Case 3

输入：`晚上有空帮我买点东西`

期望：

- 只输出弹性任务。
- 不生成刚性责任。
