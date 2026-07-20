# order_preference_v1

用途：在超市任务被后置后，根据用户 profile 生成模拟配送方案摘要。真实下单必须等待确认。

## Model

- 推荐模型：团队 LLM 网关中的稳定文本模型。
- temperature：`0`
- 输出格式：严格 JSON。

## Input Schema

```json
{
  "schema_version": "0.2.0",
  "flexible_task": {
    "title": "去超市",
    "capability_tags": ["grocery_delivery"]
  },
  "profile": {
    "profile_type": "efficiency",
    "budget_limit": 200,
    "delivery_priority": "fastest",
    "substitution_policy": "same_spec_within_budget"
  },
  "mock_service": {
    "mode": "success"
  }
}
```

## Output Schema

```json
{
  "service_order": {
    "items": [
      {"name": "牛奶", "quantity": 1, "unit_price": 19.9},
      {"name": "鸡蛋", "quantity": 1, "unit_price": 16.9}
    ],
    "total": 86.6,
    "budget_status": "within_budget",
    "delivery_window": "19:00-19:30",
    "status": "awaiting_confirmation",
    "error_code": null
  },
  "summary": "已把超市任务转为 19:00-19:30 配送，预算内，等待确认。"
}
```

## Allowed

- 根据 `profile` 控制预算、配送优先级和替代策略。
- 对 `out_of_stock` 或 `over_budget` 输出可降级摘要。
- 车机端只输出摘要，不展开完整商品列表。

## Forbidden

- 不得真实下单。
- 不得绕过用户确认。
- 不得推荐高风险或与任务无关的商品。
- 不得泄露支付、地址、联系人等敏感信息。

## Fixed Test Cases

### Case 1

`mode=success`，预算 200。

期望：

- `budget_status=within_budget`。
- `status=awaiting_confirmation`。
- `summary` 说明等待确认。

### Case 2

`mode=out_of_stock`。

期望：

- `status=blocked` 或提供同规格替代。
- 不自动确认替代商品。

### Case 3

`mode=over_budget`。

期望：

- `budget_status=over_budget`。
- 请求停车后在手机端处理，不在车机展开复杂选择。
