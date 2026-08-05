# 车机接入 Agent 实时消息与采购清单

> 更新时间：2026-08-05。本文可以直接交给车机负责人或其 AI 编程助手。

车机不需要调用新的业务后端，也不要保存一套预设消息。消息正文和采购清单都由 AURI Agent 写入同一个 `WorldState`，车机首次连接读取 `GET /v1/state`，之后通过 `GET /v1/stream` 接收实时完整快照。

契约权威文件是 [`contracts/world-state.schema.json`](../contracts/world-state.schema.json)，正向数据示例是 [`contracts/examples/world-state.json`](../contracts/examples/world-state.json)。

## 1. 连接方式

```http
GET https://auri-agent-api.onrender.com/v1/state
X-Agent-Token: <由项目负责人提供的团队令牌>
```

```http
GET https://auri-agent-api.onrender.com/v1/stream
Accept: text/event-stream
X-Agent-Token: <由项目负责人提供的团队令牌>
```

浏览器原生 `EventSource` 不能携带自定义 Header，因此 Web 车机应继续使用当前项目中的流式 `fetch` SSE 客户端。每条 `state.updated` 的 `data` 都是完整 `WorldState`，只接受同一 `session_id` 下更高的 `revision`；重连时先重新请求 `/v1/state` 对账。

## 2. 读取 Agent 生成的消息

消息位于：

```text
WorldState.actions[type == "message"]
```

新增的 `message_draft` 是可选字段，因此不会破坏旧客户端：

```json
{
  "action_id": "action_message_teacher",
  "type": "message",
  "target": "王老师",
  "status": "awaiting_confirmation",
  "summary": "给王老师的消息草稿：您好，我预计18:28到……",
  "message_draft": {
    "body": "您好，我预计18:28到，会晚18分钟。麻烦先照看一下孩子，谢谢。（Demo 模拟消息，未连接真实通讯服务）",
    "channel": "demo",
    "is_simulated": true
  },
  "details_ref": "message_teacher"
}
```

车机展示规则：

- 收件人读取 `target`。
- 完整正文优先读取 `message_draft.body`，不要再使用前端预设文案。
- 主驾驶屏只展示正文短预览；完整正文放在“处理 → 消息详情”二级页。
- `status=awaiting_confirmation` 表示待确认；确认后同一 Action 变为 `completed`，`message_draft.body` 保持不变，`summary` 会明确显示“已模拟发送”。
- `message_draft.is_simulated=true` 必须在 UI 中保留 Demo 标识，不能宣称真实短信已经送达。
- 兼容尚未部署新字段的旧后端时，可以临时回退到 `summary`；不得回退到前端固定句子。

## 3. 读取 Agent 生成的采购清单

采购动作位于 `actions[type == "service_order"]`，结构化清单位于 `service_orders[]`。使用动作的 `details_ref` 关联订单：

```js
const order = state.service_orders.find((item) =>
  item.preview_id === action.details_ref || item.order_id === action.details_ref
);
```

确认前 `details_ref` 对应 `preview_id`；确认后对应 `order_id`。不要从 `Action.summary` 或 `output.conclusion` 反向解析商品。

```json
{
  "order_id": null,
  "preview_id": "preview_demo_001",
  "items": [
    {
      "sku": "milk",
      "name": "牛奶",
      "quantity": 2,
      "unit_price": 16,
      "subtotal": 32,
      "substitution": null
    }
  ],
  "total": 186,
  "budget_limit": 200,
  "budget_status": "within_budget",
  "delivery_window": "20:00-21:00",
  "status": "awaiting_confirmation",
  "error_code": null
}
```

车机主屏最多展示前两项商品、品类数、总价和配送时段；“处理 → 生活服务方案”二级页可以逐项展示 `name`、`quantity`、`unit_price` 和 `subtotal`。所有订单均为 Demo 模拟，不发生真实支付。

## 4. 最小 TypeScript 映射

```ts
type AgentMessage = {
  id: string;
  recipient: string;
  body: string;
  status: string;
  simulated: boolean;
};

type PurchaseItem = {
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
};

export function selectAgentPlan(state: any) {
  const actions = Array.isArray(state.actions) ? state.actions : [];
  const orders = Array.isArray(state.service_orders) ? state.service_orders : [];

  const messages: AgentMessage[] = actions
    .filter((action: any) => action.type === "message")
    .map((action: any) => ({
      id: action.action_id,
      recipient: action.target,
      body: action.message_draft?.body || action.summary,
      status: action.status,
      simulated: action.message_draft?.is_simulated !== false
    }));

  const purchases = actions
    .filter((action: any) => action.type === "service_order")
    .map((action: any) => ({
      action,
      order: orders.find((order: any) =>
        order.preview_id === action.details_ref || order.order_id === action.details_ref
      ) || null
    }));

  return { messages, purchases };
}
```

正式实现还要校验 `schema_version`、`session_id`、`revision` 和数组字段；上面的代码只说明字段映射。

## 5. 确认与完成状态

只有同时满足以下条件时，车机才能显示唯一确认按钮：

```text
primary_surface == "vehicle_hmi"
confirmation.owner_surface == "vehicle_hmi"
confirmation.status == "pending"
confirmation.expires_at 尚未过期
```

确认仍使用现有接口：

```http
POST /v1/confirm
Content-Type: application/json
X-Agent-Token: <团队令牌>

{
  "confirmation_id": "从当前 WorldState.confirmation 读取",
  "decision": "accept",
  "confirmed_by": "vehicle_hmi",
  "input_mode": "button"
}
```

请求成功后直接使用响应中的新快照，并等待 SSE 对账。车机不能提前把 Action 改成完成，也不能在浏览器里生成订单号。

## 6. 联调验收清单

- [ ] 手机创建不同任务后，车机收到的收件人和正文随 Agent 状态变化，不再固定。
- [ ] 主屏显示 Agent 正文短预览，二级页显示完整正文。
- [ ] 采购二级页逐项显示商品、数量、单价和小计。
- [ ] 确认前后 `message_draft.body` 不变，Action 状态由后端从待确认变为完成。
- [ ] 确认后订单获得唯一 `order_id`，重复确认不产生第二个订单。
- [ ] 断开 SSE、推进 Agent、再连接后，车机从最新 `/v1/state` 恢复同一内容。
- [ ] 所有消息和采购均保留“Demo 模拟”标识。
