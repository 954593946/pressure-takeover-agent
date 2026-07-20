# AURI Agent Tool Calling 接口规范

> 手机端发给后端同学的工具定义清单。后端在收到 `user.utterance` 事件时，由 LLM 根据此定义自主选择调用哪些 tool。

## 架构

```
手机 app                               后端 Agent
────────                               ──────────
POST /v1/event                         收到 user.utterance
  type: user.utterance                   ↓
  payload: { text: "创建任务..." }      LLM 决定调哪些 tool
                                         ↓
                                      执行 tool → 改 WorldState
                                         ↓
                                      LLM 生成回复结论
                                         ↓
SSE /v1/stream ←─────────────────── 推 WorldState
  output.conclusion = "已创建2项任务"
```

手机端不需要改动 — 已经发送 `user.utterance`，已经展示 `output.conclusion`。

---

## Tool 定义（OpenAI Function Calling 格式）

### 1. create_tasks

```json
{
    "type": "function",
    "function": {
        "name": "create_tasks",
        "description": "根据用户描述创建一项或多项任务。自动判断刚性/弹性、提取时间和地点。如用户说「18:10去阳光小学接孩子，之后去超市采购」应创建2项任务。",
        "parameters": {
            "type": "object",
            "properties": {
                "tasks": {
                    "type": "array",
                    "description": "任务列表",
                    "items": {
                        "type": "object",
                        "properties": {
                            "title": { "type": "string", "description": "任务标题" },
                            "scheduled_at": { "type": "string", "description": "计划时间，ISO 8601 格式。如无明确时间可省略" },
                            "location": { "type": "string", "description": "地点。如「阳光小学」「家乐福」" },
                            "task_type": { "type": "string", "enum": ["rigid", "flexible"], "description": "刚性(不可变时间)/弹性(可调整)" },
                            "priority": { "type": "string", "enum": ["low", "medium", "high"] },
                            "waiting_party": { "type": "array", "items": { "type": "string" }, "description": "等待方。如孩子→['老师','家人']" }
                        },
                        "required": ["title", "task_type"]
                    }
                }
            },
            "required": ["tasks"]
        }
    }
}
```

**执行效果**：设置 `WorldState.tasks`，stage 回到 `off_vehicle_idle`，primary_surface 设为 `mobile`。

---

### 2. report_meeting_delay

```json
{
    "type": "function",
    "function": {
        "name": "report_meeting_delay",
        "description": "报告会议延迟，触发出发前预警。",
        "parameters": {
            "type": "object",
            "properties": {
                "late_minutes": { "type": "integer", "description": "预计晚到分钟数" }
            },
            "required": ["late_minutes"]
        }
    }
}
```

**执行效果**：stage → `pre_departure_warning`，风险等级根据延迟分钟数自动计算，腕上设备震动。

---

### 3. enter_vehicle

```json
{
    "type": "function",
    "function": {
        "name": "enter_vehicle",
        "description": "用户已上车，切换到驾驶模式。手机进入 Companion 只读模式，车机接管主控。",
        "parameters": { "type": "object", "properties": {} }
    }
}
```

**执行效果**：scene → `driving`，primary_surface → `vehicle_hmi`，stage → `vehicle_observation`，腕上 → handover 模式。

---

### 4. report_traffic

```json
{
    "type": "function",
    "function": {
        "name": "report_traffic",
        "description": "报告交通拥堵情况，触发压力接管评估。",
        "parameters": {
            "type": "object",
            "properties": {
                "delay_minutes": { "type": "integer", "description": "预计额外延误分钟数" }
            },
            "required": ["delay_minutes"]
        }
    }
}
```

**执行效果**：根据延误时间自动升级到 L2/L3，生成 ETA 更新通知。

---

### 5. request_assistance

```json
{
    "type": "function",
    "function": {
        "name": "request_assistance",
        "description": "请求 AURI 协助处理当前任务。Agent 会生成消息草稿（通知老师/家人）和超市采购方案。",
        "parameters": { "type": "object", "properties": {} }
    }
}
```

**执行效果**：stage → `planning` → `waiting_confirmation`，生成 actions（消息草稿 ×2 + 订单预览），创建 confirmation 等待用户确认。

---

### 6. confirm_actions

```json
{
    "type": "function",
    "function": {
        "name": "confirm_actions",
        "description": "确认或拒绝当前待处理的方案。",
        "parameters": {
            "type": "object",
            "properties": {
                "decision": { "type": "string", "enum": ["accept", "reject"] }
            },
            "required": ["decision"]
        }
    }
}
```

**执行效果**：accept → 消息"已发送"、订单"已提交"，stage → `action_completed`。reject → actions 标为 blocked。

---

### 7. park_vehicle

```json
{
    "type": "function",
    "function": {
        "name": "park_vehicle",
        "description": "车辆已停车，进入复盘模式。手机端恢复主控权，展示停车复盘。",
        "parameters": { "type": "object", "properties": {} }
    }
}
```

**执行效果**：scene → `parked`，primary_surface → `mobile`，stage → `parked_review`，风险 → Recovery。

---

### 8. get_status

```json
{
    "type": "function",
    "function": {
        "name": "get_status",
        "description": "查询当前状态摘要，包括任务列表、风险等级、所在场景、待确认事项。用户问「现在什么状态」「看看进展」时调用。",
        "parameters": { "type": "object", "properties": {} }
    }
}
```

**执行效果**（只读，不改 state）：返回当前 tasks、stage、scene、risk、pending confirmation 的文本摘要，供 LLM 回复用户。

---

### 9. chat

```json
{
    "type": "function",
    "function": {
        "name": "chat",
        "description": "一般性对话，不涉及具体操作。用户问候、闲聊、问问题时使用。",
        "parameters": {
            "type": "object",
            "properties": {
                "message": { "type": "string", "description": "用户消息原文" }
            },
            "required": ["message"]
        }
    }
}
```

**执行效果**（只读）：LLM 直接回复用户，不改任何状态。

---

## 实现建议

### 后端改造点

1. **新增 `tools.py`** — 把上面的 tool definitions 和 system prompt 放一起
2. **改造 `user.utterance` 处理** — 从关键词匹配改为 LLM tool calling loop：
   - 带 tool definitions 发请求
   - LLM 返回 `tool_calls` → 执行对应逻辑 → 结果回传
   - LLM 最终生成文本回复 → 写入 `output.conclusion`
3. **保留降级** — LLM 不可用时，fallback 到现有关键词匹配逻辑
4. **执行逻辑已有** — 所有 tool 对应的状态变更代码已在 `runtime.py` 和 `engine.py` 中

### System Prompt 建议

```
你是 AURI，一个车载随行压力接管助手。用户正在开车或准备出行，
需要你帮忙管理任务、处理延误、协调沟通。

规则：
- 用户提到创建任务/安排事情 → 先调 create_tasks
- 用户说上车/到车里了 → 调 enter_vehicle
- 用户说堵车/晚到/迟到 → 调 report_traffic，不要直接安慰
- 用户说会议延迟 → 调 report_meeting_delay
- 用户说帮我处理/帮忙/怎么办 → 调 request_assistance
- 用户说确认/好的/执行 → 调 confirm_actions
- 用户说停车/到了 → 调 park_vehicle
- 用户问状态/进展 → 调 get_status
- 纯聊天 → 调 chat
- 回复用中文，2-3句话，简洁友好
```

---

## 手机端要做的事

**什么都不用做。** 当前代码已经：
- ✅ `ChatScreen` 发送 `user.utterance` 事件
- ✅ `ChatViewModel` 展示 `output.conclusion` 作为 AI 回复
- ✅ 确认卡片有按钮可以确认/拒绝

后端实现 Tool Calling 后，用户体验自动变成：打字 → AI 理解并执行 → 回复结果。
