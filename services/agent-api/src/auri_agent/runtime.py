from __future__ import annotations

import asyncio
from datetime import datetime
from uuid import uuid4

from .config import Settings
from .engine import ActionPlanner, RiskEngine, add_auxiliary_signal
from .llm import TaskParser
from .models import (
    ConfirmationRequest,
    Event,
    EventAccepted,
    InteractionOutput,
    PressureLevel,
    Profile,
    Scene,
    Stage,
    Surface,
    Task,
    WearableState,
    WorldState,
    initial_state,
    now,
    output_expiry,
)


class RuntimeErrorWithCode(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class AgentRuntime:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.task_parser = TaskParser(settings)
        self._state = initial_state()
        self._event_ids: set[str] = set()
        self._lock = asyncio.Lock()
        self._subscribers: set[asyncio.Queue[WorldState]] = set()

    async def get_state(self) -> WorldState:
        async with self._lock:
            return self._state.model_copy(deep=True)

    async def submit_event(self, event: Event) -> EventAccepted:
        parsed_tasks: list[Task] | None = None
        async with self._lock:
            if event.event_id in self._event_ids:
                state = self._state.model_copy(deep=True)
                return EventAccepted(event_id=event.event_id, accepted=True, duplicate=True, revision=state.revision, state=state)
            if self._state.revision == 0 and not self._event_ids:
                self._state.session_id = event.session_id
            elif event.session_id != self._state.session_id:
                raise RuntimeErrorWithCode("SESSION_MISMATCH", "event session_id does not match the active session")

        if event.type == "task.created":
            payload_tasks = event.payload.get("tasks")
            if payload_tasks:
                parsed_tasks = [Task.model_validate(task) for task in payload_tasks]
            else:
                parsed_tasks = await self.task_parser.parse(str(event.payload.get("text", "")))

        async with self._lock:
            if event.event_id in self._event_ids:
                state = self._state.model_copy(deep=True)
                return EventAccepted(event_id=event.event_id, accepted=True, duplicate=True, revision=state.revision, state=state)
            self._apply_event(event, parsed_tasks)
            self._event_ids.add(event.event_id)
            self._touch(f"event:{event.event_id}")
            state = self._state.model_copy(deep=True)
        await self._broadcast(state)
        return EventAccepted(event_id=event.event_id, accepted=True, duplicate=False, revision=state.revision, state=state)

    def _apply_event(self, event: Event, parsed_tasks: list[Task] | None) -> None:
        payload = event.payload
        if event.type == "task.created":
            self._state.tasks = parsed_tasks or []
            self._state.stage = Stage.OFF_VEHICLE_IDLE
            self._state.scene = Scene.OFF_VEHICLE
            self._state.primary_surface = Surface.MOBILE
        elif event.type == "meeting.overrun":
            self._state.stage = Stage.PRE_DEPARTURE_WARNING
            self._state.risk.reason_codes = ["MEETING_OVERRUN"]
            RiskEngine.recompute(self._state)
            self._state.wearable = WearableState(
                connected=self._state.wearable.connected,
                mode="warning",
                text="出发时间临近",
                color="yellow",
                haptic="double_short",
            )
        elif event.type == "scene.approaching":
            self._state.scene = Scene.APPROACHING_VEHICLE
            self._state.stage = Stage.HANDOVER_TO_VEHICLE
        elif event.type == "scene.vehicle_entered":
            self._state.scene = Scene.DRIVING
            self._state.stage = Stage.VEHICLE_OBSERVATION
            self._state.primary_surface = Surface.VEHICLE_HMI
            self._state.wearable = WearableState(connected=self._state.wearable.connected, mode="handover", text="驾驶已连接", color="blue", haptic="single_pulse")
        elif event.type == "traffic.updated":
            eta = payload.get("eta")
            self._state.eta = datetime.fromisoformat(eta) if isinstance(eta, str) else eta
            self._state.risk.late_minutes = max(0, int(payload.get("late_minutes", 0)))
            RiskEngine.recompute(self._state)
            self._state.stage = Stage.TAKEOVER_L2 if self._state.risk.pressure_level == PressureLevel.L2 else Stage.TAKEOVER_L3
            self._state.output = InteractionOutput(
                message_id=f"msg_{uuid4().hex[:12]}",
                priority="high",
                owner_surface=Surface.VEHICLE_HMI,
                suppressed_surfaces=["mobile", "wearable"],
                expires_at=output_expiry(),
                requires_confirmation=False,
                conclusion=f"预计晚到{self._state.risk.late_minutes}分钟。",
            )
        elif event.type == "wearable.signal":
            heart_rate = payload.get("heart_rate")
            confidence = float(payload.get("confidence", 0))
            self._state.wearable.heart_rate = int(heart_rate) if heart_rate is not None else None
            self._state.wearable.signal_confidence = confidence
            if heart_rate is not None and int(heart_rate) >= 110 and confidence >= 0.7:
                add_auxiliary_signal(self._state, "WEARABLE_HIGH_TREND")
        elif event.type == "driving.signal":
            if payload.get("harsh_brake") is True:
                add_auxiliary_signal(self._state, "DRIVING_HARSH_BRAKE")
        elif event.type == "user.utterance":
            self._state.stage = Stage.PLANNING
            RiskEngine.recompute(self._state, assistance_requested=True)
            ActionPlanner.prepare(self._state)
        elif event.type == "service.mock.config":
            mode = payload.get("mode", "success")
            if mode not in {"success", "out_of_stock", "over_budget"}:
                raise RuntimeErrorWithCode("INVALID_MOCK_MODE", "unsupported service mock mode")
            self._state.service_mock_mode = mode
        elif event.type == "confirmation.confirmed":
            confirmed_by = payload.get("confirmed_by")
            if confirmed_by not in {"mobile", "vehicle_hmi"}:
                confirmed_by = "vehicle_hmi" if self._state.scene in {Scene.DRIVING, Scene.HIGH_LOAD_DRIVING} else "mobile"
            request = ConfirmationRequest(
                confirmation_id=str(payload.get("confirmation_id")),
                decision=payload.get("decision", "accept"),
                confirmed_by=confirmed_by,
                input_mode=payload.get("input_mode", "button"),
            )
            self._consume_confirmation(request)
        elif event.type == "cooldown.elapsed":
            self._state.stage = Stage.COOLDOWN
            self._state.output = None
            self._state.wearable.haptic = "none"
        elif event.type == "scene.parked":
            self._state.scene = Scene.PARKED
            self._state.stage = Stage.PARKED_REVIEW
            self._state.primary_surface = Surface.MOBILE
            self._state.risk.pressure_level = PressureLevel.RECOVERY
        elif event.type == "session.reset":
            raise RuntimeErrorWithCode("USE_RESET_ENDPOINT", "use POST /v1/session/reset")

    async def confirm(self, request: ConfirmationRequest) -> tuple[WorldState, bool]:
        async with self._lock:
            duplicate = self._state.confirmation is not None and self._state.confirmation.status != "pending"
            self._consume_confirmation(request)
            if not duplicate:
                self._touch(f"confirm:{request.confirmation_id}:{request.input_mode}")
            state = self._state.model_copy(deep=True)
        if not duplicate:
            await self._broadcast(state)
        return state, duplicate

    def _consume_confirmation(self, request: ConfirmationRequest) -> None:
        confirmation = self._state.confirmation
        if confirmation is None or confirmation.confirmation_id != request.confirmation_id:
            raise RuntimeErrorWithCode("NOT_FOUND", "confirmation not found")
        if confirmation.status != "pending":
            return
        if confirmation.expires_at < now():
            confirmation.status = "expired"
            raise RuntimeErrorWithCode("EXPIRED", "confirmation expired")
        if request.confirmed_by != confirmation.owner_surface:
            raise RuntimeErrorWithCode("WRONG_SURFACE", "confirmation is not owned by this surface")
        confirmation.confirmed_by = request.confirmed_by
        if request.decision == "reject":
            confirmation.status = "rejected"
            for action in self._state.actions:
                if action.action_id in confirmation.action_ids:
                    action.status = "blocked"
            self._state.stage = Stage.ACTION_COMPLETED
            return

        confirmation.status = "accepted"
        self._state.stage = Stage.EXECUTING
        for action in self._state.actions:
            if action.action_id in confirmation.action_ids:
                action.status = "completed"
        for order in self._state.service_orders:
            if order.status == "awaiting_confirmation":
                order.order_id = order.order_id or f"order_{order.preview_id.removeprefix('preview_')}"
                order.status = "submitted"
        self._state.stage = Stage.ACTION_COMPLETED
        self._state.risk.pressure_level = PressureLevel.RECOVERY
        self._state.wearable = WearableState(
            connected=self._state.wearable.connected,
            mode="completed",
            text="已同步完成",
            color="green",
            haptic="soft_short",
        )
        self._state.output = InteractionOutput(
            message_id=f"msg_{uuid4().hex[:12]}",
            priority="normal",
            owner_surface=self._state.primary_surface,
            suppressed_surfaces=["mobile", "wearable"] if self._state.primary_surface == Surface.VEHICLE_HMI else ["vehicle_hmi", "wearable"],
            expires_at=output_expiry(1),
            requires_confirmation=False,
            conclusion="消息已处理，订单已模拟提交，按当前速度驾驶即可。",
        )

    async def update_profile(self, profile: Profile) -> WorldState:
        async with self._lock:
            self._state.profile = profile
            self._touch(f"profile:{profile.profile_id}")
            state = self._state.model_copy(deep=True)
        await self._broadcast(state)
        return state

    async def reset(self, scenario_id: str) -> WorldState:
        async with self._lock:
            self._state = initial_state(f"{scenario_id}_{uuid4().hex[:8]}")
            self._event_ids.clear()
            state = self._state.model_copy(deep=True)
        await self._broadcast(state)
        return state

    def _touch(self, ledger_entry: str) -> None:
        self._state.revision += 1
        self._state.updated_at = now()
        self._state.action_ledger.append(ledger_entry)

    async def subscribe(self) -> asyncio.Queue[WorldState]:
        queue: asyncio.Queue[WorldState] = asyncio.Queue(maxsize=10)
        self._subscribers.add(queue)
        await queue.put(await self.get_state())
        return queue

    def unsubscribe(self, queue: asyncio.Queue[WorldState]) -> None:
        self._subscribers.discard(queue)

    async def _broadcast(self, state: WorldState) -> None:
        for queue in tuple(self._subscribers):
            if queue.full():
                try:
                    queue.get_nowait()
                except asyncio.QueueEmpty:
                    pass
            await queue.put(state.model_copy(deep=True))
