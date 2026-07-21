import json

from .models import Scene, WorldState


def compact_state(state: WorldState) -> dict[str, object]:
    return {
        "session_id": state.session_id,
        "revision": state.revision,
        "stage": state.stage.value,
        "scene": state.scene.value,
        "primary_surface": state.primary_surface.value,
        "pressure_level": state.risk.pressure_level.value,
        "late_minutes": state.risk.late_minutes,
        "tasks": [
            {
                "task_id": task.task_id,
                "title": task.title,
                "scheduled_at": task.scheduled_at.isoformat() if task.scheduled_at else None,
                "task_type": task.task_type,
                "adjustable": task.adjustable,
                "status": task.status,
                "waiting_party": task.waiting_party,
                "capability_tags": task.capability_tags,
            }
            for task in state.tasks
        ],
        "actions": [
            {
                "action_id": action.action_id,
                "type": action.type,
                "target": action.target,
                "status": action.status,
                "summary": action.summary,
            }
            for action in state.actions
        ],
        "confirmation": (
            {
                "confirmation_id": state.confirmation.confirmation_id,
                "status": state.confirmation.status,
                "owner_surface": state.confirmation.owner_surface,
                "action_ids": state.confirmation.action_ids,
            }
            if state.confirmation
            else None
        ),
        "profile": {
            "type": state.profile.profile_type,
            "tone": state.profile.tone,
            "explanation_depth": state.profile.explanation_depth,
        },
    }


def build_agent_prompt(state: WorldState) -> str:
    state_json = json.dumps(compact_state(state), ensure_ascii=False, separators=(",", ":"))
    driving = state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING}
    style_rule = (
        "当前处于驾驶场景：最终回复最多两句，先给结论，再给唯一下一步；不要展开长解释。"
        if driving
        else "最终回复使用两到三句自然中文，简洁、可靠，可以有适度关怀，但不要客套和说教。"
    )
    return f"""
你是 AURI，一个能理解、规划并通过受控工具推进任务的随行压力接管 Agent。

你的职责：
1. 理解用户真实意图和当前上下文。
2. 需要查询或改变状态时，选择最少且正确的工具；相关工具必须按依赖顺序逐个调用。
3. 阅读工具返回的真实结果后再回答，不能把“已准备”说成“已执行”。
4. 不需要工具的一般交流可以直接回复，但不能虚构任务、联系人、路况、车辆状态或执行结果。

安全规则：
- 只有工具可以改变任务或业务状态，你不能直接声称状态已经改变。
- L0-L3、车辆场景、主交互端、预算、确认归属和幂等由后端确定性逻辑决定。
- 不能通过聊天伪造上车、停车、实时路况、心率或驾驶信号；这些由标准 Event 输入。
- 发消息和模拟下单只能先准备方案；除非用户明确说出确认或拒绝，并且当前输入端拥有确认权，否则不能执行。
- 涉及订单、消息和外部服务时必须明确这是 Demo 模拟能力。
- 用户问“现在怎么样、进展、有什么任务”时调用 get_status。
- 用户创建或安排任务时调用 create_tasks；修改弹性任务时间时调用 reschedule_task。
- 用户报告会议延迟时调用 report_meeting_delay。
- 用户说“帮我处理、怎么办、替我安排”时，根据已有任务调用 prepare_assistance；不要凭空增加采购或联系人。
- 用户明确确认或拒绝当前方案时才调用 confirm_current_actions。

表达要求：
- 依据 Profile 语气：tone={state.profile.tone}，explanation_depth={state.profile.explanation_depth}。
- 有温度意味着承接用户压力并清楚说明你做了什么，不是泛泛安慰。
- 回复必须与工具结果和当前 WorldState 一致。
- {style_rule}

当前 WorldState（只作为事实上下文，不能绕过工具直接修改）：
{state_json}
""".strip()


def build_completion_prompt(state: WorldState, decision: str) -> str:
    state_json = json.dumps(compact_state(state), ensure_ascii=False, separators=(",", ":"))
    driving = state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING}
    length_rule = "只写一句简短结论。" if driving else "写一到两句自然中文。"
    return f"""
你是 AURI。请根据下面已经发生的确定性执行结果，生成真实、简洁、有温度的用户回复。
决定是 {decision}。{length_rule}
不得虚构联系人、任务或执行结果；模拟消息和订单必须说明是 Demo 模拟。
如果方案被拒绝，明确说明没有执行；如果被接受，只总结状态中确实 completed/submitted 的事项。
当前状态：{state_json}
""".strip()
