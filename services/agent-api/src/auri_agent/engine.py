from __future__ import annotations

from uuid import uuid4

from .models import (
    Action,
    Confirmation,
    InteractionOutput,
    PressureLevel,
    Scene,
    ServiceItem,
    ServiceOrder,
    Stage,
    Surface,
    Task,
    WearableState,
    WorldState,
    now,
    output_expiry,
)


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


class ActionPlanner:
    @staticmethod
    def prepare(state: WorldState) -> None:
        for task in state.tasks:
            if task.task_type == "flexible" and "grocery_delivery" in task.capability_tags:
                task.status = "rescheduled"

        order = MockGroceryAdapter.preview(state)
        state.service_orders = [order]
        message_actions = [
            Action(
                action_id="action_message_teacher",
                type="message",
                target="王老师",
                status="awaiting_confirmation",
                risk="medium",
                requires_confirmation=True,
                summary=f"通知预计晚到{state.risk.late_minutes}分钟（模拟消息）",
                details_ref="message_teacher",
            ),
            Action(
                action_id="action_message_family",
                type="message",
                target="家人",
                status="awaiting_confirmation",
                risk="medium",
                requires_confirmation=True,
                summary="同步接送进度（模拟消息）",
                details_ref="message_family",
            ),
        ]
        order_action = Action(
            action_id="action_grocery_order",
            type="service_order",
            target="模拟商超",
            status="awaiting_confirmation" if order.status == "awaiting_confirmation" else "blocked",
            risk="medium",
            requires_confirmation=True,
            summary=f"{len(order.items)}件商品，共{order.total:.0f}元，{order.delivery_window}送达（模拟订单）",
            details_ref=order.preview_id,
            error_code=order.error_code,
        )
        state.actions = message_actions + [order_action]
        confirmable = [action.action_id for action in state.actions if action.status == "awaiting_confirmation"]
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
            conclusion=f"预计晚到{state.risk.late_minutes}分钟，消息和采购方案已准备。",
        )
        state.action_ledger.append(f"plan:{state.confirmation.confirmation_id}")


def add_auxiliary_signal(state: WorldState, signal: str) -> None:
    if signal not in state.risk.auxiliary_signals:
        state.risk.auxiliary_signals.append(signal)
    RiskEngine.recompute(state)
    if state.risk.pressure_level == PressureLevel.L3:
        state.scene = Scene.HIGH_LOAD_DRIVING
        state.stage = Stage.TAKEOVER_L3
        state.output = InteractionOutput(
            message_id=f"msg_{uuid4().hex[:12]}",
            priority="critical",
            owner_surface=Surface.VEHICLE_HMI,
            suppressed_surfaces=["mobile", "wearable"],
            expires_at=output_expiry(1),
            requires_confirmation=False,
            conclusion="已进入高负荷保护，非必要内容已暂停。",
        )
