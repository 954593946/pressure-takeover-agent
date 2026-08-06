from __future__ import annotations

import re
from uuid import uuid4

from .models import (
    Action,
    Confirmation,
    ConfirmationRequest,
    InteractionOutput,
    MessageDraft,
    PressureLevel,
    Scene,
    ServiceItem,
    ServiceOrder,
    Stage,
    Surface,
    Task,
    TZ,
    WearableState,
    WorldState,
    now,
    output_expiry,
)


def format_local_time(value) -> str:
    """Format an Agent datetime in the frozen Demo business timezone."""
    if value.tzinfo is None:
        value = value.replace(tzinfo=TZ)
    return value.astimezone(TZ).strftime("%H:%M")


class RiskEngine:
    @staticmethod
    def recompute(state: WorldState, assistance_requested: bool = False) -> None:
        reasons = [reason for reason in state.risk.reason_codes if not reason.startswith("RISK_")]
        late = state.risk.late_minutes
        auxiliaries = set(state.risk.auxiliary_signals)
        if late <= 0:
            level = PressureLevel.L1 if "MEETING_OVERRUN" in reasons else PressureLevel.L0
        elif len(auxiliaries) >= 2:
            level = PressureLevel.L3
            reasons.append("RISK_MULTI_SOURCE_HIGH_LOAD")
        else:
            level = PressureLevel.L2
            reasons.append(f"RISK_RIGID_TASK_LATE_{late}_MIN")
        if assistance_requested and "USER_REQUESTED_ASSISTANCE" not in reasons:
            reasons.append("USER_REQUESTED_ASSISTANCE")
            level = max(level, PressureLevel.L2, key=lambda item: list(PressureLevel).index(item))
        state.risk.pressure_level = level
        state.risk.reason_codes = list(dict.fromkeys(reasons))


class MockGroceryAdapter:
    _EFFICIENCY_ITEMS = [
        ("milk", "牛奶", 2, 16),
        ("eggs", "鸡蛋", 1, 24),
        ("fruit", "水果组合", 1, 38),
        ("vegetables", "蔬菜组合", 1, 32),
        ("rice", "大米", 1, 28),
        ("bread", "面包", 1, 12),
        ("yogurt", "酸奶", 1, 10),
        ("tissue", "纸巾", 1, 10),
    ]
    _QUALITY_ITEMS = [
        ("milk_q", "有机牛奶", 2, 22),
        ("eggs_q", "可生食鸡蛋", 1, 32),
        ("fruit_q", "精选水果组合", 1, 52),
        ("vegetables_q", "有机蔬菜组合", 1, 38),
        ("rice_q", "品牌大米", 1, 32),
        ("bread_q", "全麦面包", 1, 16),
        ("yogurt_q", "低糖酸奶", 1, 12),
        ("tissue_q", "纸巾", 1, 10),
    ]

    @classmethod
    def preview(cls, state: WorldState) -> ServiceOrder:
        raw_items = cls._QUALITY_ITEMS if state.profile.profile_type == "quality" else cls._EFFICIENCY_ITEMS
        items = [
            ServiceItem(sku=sku, name=name, quantity=quantity, unit_price=price, subtotal=quantity * price)
            for sku, name, quantity, price in raw_items
        ]
        total = float(sum(item.subtotal for item in items))
        error_code = None
        status = "awaiting_confirmation"
        if state.service_mock_mode == "over_budget":
            total = state.profile.budget_limit + 36
            error_code = "OVER_BUDGET"
            status = "blocked"
        elif state.service_mock_mode == "out_of_stock":
            error_code = "OUT_OF_STOCK"
            status = "blocked"
        budget_status = "over_budget" if total > state.profile.budget_limit else "within_budget"
        if budget_status == "over_budget":
            error_code = "OVER_BUDGET"
            status = "blocked"
        return ServiceOrder(
            preview_id=f"preview_{uuid4().hex[:12]}",
            items=items,
            total=total,
            budget_limit=state.profile.budget_limit,
            budget_status=budget_status,
            delivery_window="20:00-21:00" if state.profile.profile_type == "efficiency" else "20:30-21:30",
            status=status,
            error_code=error_code,
        )


def _timing_text(state: WorldState) -> str:
    if state.eta is not None and state.risk.late_minutes > 0:
        return f"预计{format_local_time(state.eta)}到，比原计划晚{state.risk.late_minutes}分钟"
    if state.eta is not None:
        return f"预计{format_local_time(state.eta)}到"
    if state.risk.late_minutes > 0:
        return f"预计比原计划晚{state.risk.late_minutes}分钟"
    return "到达时间有变化"


def _message_task(state: WorldState, target: str) -> Task | None:
    rigid_tasks = [task for task in state.tasks if task.task_type == "rigid"]
    return next(
        (task for task in rigid_tasks if target in task.waiting_party),
        rigid_tasks[0] if rigid_tasks else None,
    )


def _is_child_target(target: str) -> bool:
    return target.strip() in {"孩子", "儿子", "女儿", "小朋友"}


def _is_family_target(target: str) -> bool:
    family_terms = ("家人", "妈妈", "爸爸", "奶奶", "爷爷", "外婆", "外公", "爱人", "伴侣", "妻子", "丈夫")
    return any(term in target for term in family_terms)


def _journey_text(task: Task | None) -> str:
    if task is None:
        return "赶往约定地点"
    title = re.sub(r"^\s*(?:今天|今晚|之后)?\s*\d{1,2}:\d{2}\s*", "", task.title).strip()
    title = re.sub(r"[（(].*?[）)]", "", title).strip()
    if "接孩子" in title:
        return "去学校接孩子" if "学校" in title else "去接孩子"
    if "机场" in title:
        return "前往机场"
    if title.startswith("去"):
        return title
    if title.startswith("接"):
        return f"去{title}"
    if title.startswith(("参加", "出席", "拜访", "办理", "送", "取", "看")):
        return f"去{title}"
    return f"按计划前往“{title}”" if title else "赶往约定地点"


def _delay_context(state: WorldState) -> str:
    return "路上有些拥堵，" if state.risk.late_minutes > 0 else ""


def build_message_body(state: WorldState, target: str) -> str:
    """Build a concise recipient-aware message persisted in the Agent action."""
    task = _message_task(state, target)
    timing = _timing_text(state)
    journey = _journey_text(task)
    delay = _delay_context(state)
    child_pickup = bool(task and "孩子" in task.title)
    if _is_child_target(target):
        child_journey = journey.replace("接孩子", "接你")
        body = f"我正在{child_journey}，{delay}{timing}。你先安心等我，我会安全驾驶，到达后马上联系你。"
    elif "老师" in target:
        request = "麻烦您先帮我照看一下孩子" if child_pickup else "麻烦您先协助等候"
        body = f"{target}您好，我正在{journey}，{delay}{timing}。{request}，我到达后马上联系您，谢谢。"
    elif _is_family_target(target):
        request = "麻烦你先和孩子说一声，" if child_pickup else ""
        body = f"我正在{journey}，{delay}{timing}。{request}我会安全驾驶，到达后马上联系你。"
    else:
        body = f"{target}您好，我正在{journey}，{delay}{timing}。抱歉让您久等，我到达后马上联系您。"
    return f"{body}（Demo 模拟消息，未连接真实通讯服务）"


def _item_text(order: ServiceOrder, *, limit: int | None = None) -> str:
    items = order.items if limit is None else order.items[:limit]
    text = "、".join(f"{item.name}×{item.quantity}" for item in items)
    if limit is not None and len(order.items) > limit:
        text += f"等{len(order.items)}种商品"
    return text


def build_order_summary(state: WorldState, order: ServiceOrder, *, submitted: bool = False) -> str:
    """Describe what is bought, how it is selected, and how it is delivered."""
    total_quantity = sum(item.quantity for item in order.items)
    priority = "按最快配送选择" if state.profile.delivery_priority == "fastest" else "按品质优先选择"
    substitution = (
        "缺货时仅同规格且预算内替换"
        if state.profile.substitution_policy == "same_spec_within_budget"
        else "缺货时仅同品牌替换"
    )
    phase = "已通过模拟商超配送提交" if submitted else "模拟商超配送预览"
    order_id = f"；订单号 {order.order_id}" if submitted and order.order_id else ""
    return (
        f"{phase}：{_item_text(order)}；共{total_quantity}件（{len(order.items)}种），"
        f"合计{order.total:.0f}元，{order.delivery_window}送达{order_id}；"
        f"{priority}，{substitution}。（Demo 模拟订单，未发生真实支付）"
    )


def _receipt_message_request(state: WorldState, actions: list[Action]) -> str:
    if len(actions) == 1 and _is_child_target(actions[0].target):
        return "请安心等我"
    if len(actions) == 1 and "老师" in actions[0].target:
        task = _message_task(state, actions[0].target)
        return "请先照看孩子" if task and "孩子" in task.title else "请先协助等候"
    return "我会安全驾驶，到达后马上联系"


def build_preparation_receipt(state: WorldState) -> str:
    message_actions = [action for action in state.actions if action.type == "message"]
    parts: list[str] = []
    if message_actions:
        targets = "、".join(action.target for action in message_actions)
        request = _receipt_message_request(state, message_actions)
        parts.append(f"给{targets}的消息草稿为“{_timing_text(state)}，{request}”")
    order = next((order for order in state.service_orders if order.status == "awaiting_confirmation"), None)
    if order:
        parts.append(
            f"模拟商超配送预览为{_item_text(order, limit=2)}，"
            f"共{order.total:.0f}元，{order.delivery_window}送达"
        )
    blocked_order = next((order for order in state.service_orders if order.status == "blocked"), None)
    if blocked_order:
        reason = "超出预算" if blocked_order.error_code == "OVER_BUDGET" else "商品缺货"
        parts.append(f"模拟采购因{reason}已阻止，不会提交订单")
    if not parts:
        return "当前没有需要确认的可执行事项。"
    return "已准备：" + "；".join(parts) + "。确认后才会模拟执行。"


def build_execution_receipt(state: WorldState, *, include_order_id: bool = True) -> str:
    completed_messages = [
        action for action in state.actions if action.type == "message" and action.status == "completed"
    ]
    parts: list[str] = []
    if completed_messages:
        targets = "、".join(action.target for action in completed_messages)
        request = _receipt_message_request(state, completed_messages)
        parts.append(f"给{targets}发“{_timing_text(state)}，{request}”")
    order = next((order for order in state.service_orders if order.status == "submitted"), None)
    if order:
        order_id = f"，订单号{order.order_id}" if include_order_id and order.order_id else ""
        parts.append(
            f"通过模拟商超配送购买{_item_text(order, limit=2)}，"
            f"共{order.total:.0f}元，{order.delivery_window}送达{order_id}"
        )
    if not parts:
        return "当前没有已执行的消息或订单。"
    return "Demo 已模拟执行：" + "；".join(parts) + "。"


class ActionPlanner:
    @staticmethod
    def prepare(
        state: WorldState,
        *,
        include_messages: bool = True,
        include_grocery: bool = True,
    ) -> list[Action]:
        grocery_tasks = [task for task in state.tasks if "grocery_delivery" in task.capability_tags]
        rigid_tasks = [task for task in state.tasks if task.task_type == "rigid"]

        for task in grocery_tasks:
            if include_grocery and task.adjustable:
                task.status = "rescheduled"

        message_targets: list[str] = []
        if include_messages:
            for task in rigid_tasks:
                # Recipients are a World State contract. Do not replace broad
                # labels, add demo recipients, or cap the number of contacts.
                message_targets.extend(task.waiting_party)
        message_targets = list(dict.fromkeys(target.strip() for target in message_targets if target and target.strip()))

        message_actions: list[Action] = []
        for index, target in enumerate(message_targets):
            # An index-based identifier remains stable for the ordered World
            # State list and avoids collisions when several teachers/family
            # members are legitimate recipients.
            action_id = f"action_message_{index + 1}"
            details_ref = f"message_contact_{index + 1}"
            message_body = build_message_body(state, target)
            summary = f"给{target}的消息草稿：{message_body}"
            message_actions.append(
                Action(
                    action_id=action_id,
                    type="message",
                    target=target,
                    status="awaiting_confirmation",
                    risk="medium",
                    requires_confirmation=True,
                    summary=summary,
                    message_draft=MessageDraft(body=message_body),
                    details_ref=details_ref,
                )
            )

        order_actions: list[Action] = []
        if include_grocery and grocery_tasks:
            order = MockGroceryAdapter.preview(state)
            state.service_orders = [order]
            order_actions.append(
                Action(
                    action_id="action_grocery_order",
                    type="service_order",
                    target="模拟商超配送",
                    status="awaiting_confirmation" if order.status == "awaiting_confirmation" else "blocked",
                    risk="medium",
                    requires_confirmation=True,
                    summary=build_order_summary(state, order),
                    details_ref=order.preview_id,
                    error_code=order.error_code,
                )
            )
        else:
            state.service_orders = []

        state.actions = message_actions + order_actions
        confirmable = [action.action_id for action in state.actions if action.status == "awaiting_confirmation"]
        if not confirmable:
            state.confirmation = None
            state.stage = Stage.PLANNING
            state.output = InteractionOutput(
                message_id=f"msg_{uuid4().hex[:12]}",
                priority="normal",
                owner_surface=state.primary_surface,
                suppressed_surfaces=["mobile", "wearable"] if state.primary_surface == Surface.VEHICLE_HMI else ["vehicle_hmi", "wearable"],
                expires_at=output_expiry(),
                requires_confirmation=False,
                conclusion="当前没有需要代办或确认的可执行事项。",
            )
            state.action_ledger.append("plan:no_applicable_actions")
            return state.actions

        state.confirmation = Confirmation(
            confirmation_id=f"confirm_{uuid4().hex[:12]}",
            action_ids=confirmable,
            expires_at=output_expiry(),
            owner_surface="vehicle_hmi" if state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING} else "mobile",
        )
        state.stage = Stage.WAITING_CONFIRMATION
        state.wearable = WearableState(
            connected=state.wearable.connected,
            mode="processing",
            text="方案已准备",
            color="blue",
            haptic="three_beat",
        )
        state.output = InteractionOutput(
            message_id=f"msg_{uuid4().hex[:12]}",
            priority="high",
            owner_surface=state.primary_surface,
            suppressed_surfaces=["mobile", "wearable"] if state.primary_surface == Surface.VEHICLE_HMI else ["vehicle_hmi", "wearable"],
            expires_at=output_expiry(),
            requires_confirmation=True,
            conclusion=build_preparation_receipt(state),
        )
        state.action_ledger.append(f"plan:{state.confirmation.confirmation_id}")
        return state.actions


class DomainError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def consume_confirmation(state: WorldState, request: ConfirmationRequest) -> None:
    confirmation = state.confirmation
    if confirmation is None or confirmation.confirmation_id != request.confirmation_id:
        raise DomainError("NOT_FOUND", "confirmation not found")
    if confirmation.status != "pending":
        return
    if confirmation.expires_at < now():
        confirmation.status = "expired"
        raise DomainError("EXPIRED", "confirmation expired")
    if request.confirmed_by != confirmation.owner_surface:
        raise DomainError("WRONG_SURFACE", "confirmation is not owned by this surface")
    confirmation.confirmed_by = request.confirmed_by
    if request.decision == "reject":
        confirmation.status = "rejected"
        for action in state.actions:
            if action.action_id in confirmation.action_ids:
                action.status = "blocked"
        rejected_order_refs = {
            action.details_ref
            for action in state.actions
            if action.action_id in confirmation.action_ids and action.type == "service_order"
        }
        for order in state.service_orders:
            if order.preview_id in rejected_order_refs and order.status == "awaiting_confirmation":
                order.status = "blocked"
        if rejected_order_refs:
            for task in state.tasks:
                if "grocery_delivery" in task.capability_tags and task.status == "rescheduled":
                    task.status = "pending"
        state.stage = Stage.ACTION_COMPLETED
        state.output = InteractionOutput(
            message_id=f"msg_{uuid4().hex[:12]}",
            priority="normal",
            owner_surface=state.primary_surface,
            suppressed_surfaces=["mobile", "wearable"] if state.primary_surface == Surface.VEHICLE_HMI else ["vehicle_hmi", "wearable"],
            expires_at=output_expiry(1),
            requires_confirmation=False,
            conclusion="已取消本次处理方案，没有执行消息或订单。",
        )
        return

    confirmation.status = "accepted"
    state.stage = Stage.EXECUTING
    for action in state.actions:
        if action.action_id in confirmation.action_ids:
            action.status = "completed"
            if action.type == "message":
                action.summary = action.summary.replace(
                    f"给{action.target}的消息草稿：",
                    f"已模拟发送给{action.target}：",
                    1,
                )
    for order in state.service_orders:
        if order.status == "awaiting_confirmation":
            order.order_id = order.order_id or f"order_{order.preview_id.removeprefix('preview_')}"
            order.status = "submitted"
            for action in state.actions:
                if action.type == "service_order" and action.details_ref == order.preview_id:
                    action.details_ref = order.order_id
                    action.summary = build_order_summary(state, order, submitted=True)
    state.stage = Stage.ACTION_COMPLETED
    state.risk.pressure_level = PressureLevel.RECOVERY
    state.wearable = WearableState(
        connected=state.wearable.connected,
        mode="completed",
        text="已同步完成",
        color="green",
        haptic="soft_short",
    )
    state.output = InteractionOutput(
        message_id=f"msg_{uuid4().hex[:12]}",
        priority="normal",
        owner_surface=state.primary_surface,
        suppressed_surfaces=["mobile", "wearable"] if state.primary_surface == Surface.VEHICLE_HMI else ["vehicle_hmi", "wearable"],
        expires_at=output_expiry(1),
        requires_confirmation=False,
        conclusion=build_execution_receipt(state, include_order_id=False),
    )


def add_auxiliary_signal(state: WorldState, signal: str) -> None:
    if signal not in state.risk.auxiliary_signals:
        state.risk.auxiliary_signals.append(signal)
    RiskEngine.recompute(state)
    if state.risk.pressure_level == PressureLevel.L3:
        state.scene = Scene.HIGH_LOAD_DRIVING
        state.stage = Stage.TAKEOVER_L3
        state.wearable = WearableState(
            connected=state.wearable.connected,
            mode="warning",
            text="高负荷保护",
            color="red",
            haptic="error_once",
            heart_rate=state.wearable.heart_rate,
            signal_confidence=state.wearable.signal_confidence,
        )
        state.output = InteractionOutput(
            message_id=f"msg_{uuid4().hex[:12]}",
            priority="critical",
            owner_surface=Surface.VEHICLE_HMI,
            suppressed_surfaces=["mobile", "wearable"],
            expires_at=output_expiry(1),
            requires_confirmation=False,
            conclusion="已进入高负荷保护，非必要内容已暂停。",
        )
