from __future__ import annotations

import asyncio
from datetime import datetime
from uuid import uuid4

from .agent import AuriAgent
from .config import Settings
from .engine import DomainError, RiskEngine, add_auxiliary_signal, consume_confirmation
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


RuntimeErrorWithCode = DomainError


class AgentRuntime:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.task_parser = TaskParser(settings)
        self.conversation_agent = AuriAgent(settings)
        self.llm_last_mode = "fallback"
        self._state = initial_state()
        self._event_ids: set[str] = set()
        self._lock = asyncio.Lock()
        self._subscribers: set[asyncio.Queue[WorldState]] = set()

    async def get_state(self) -> WorldState:
        async with self._lock:
            return self._state.model_copy(deep=True)

    async def submit_event(self, event: Event) -> EventAccepted:
        if event.type == "user.utterance":
            return await self._submit_agent_event(event)

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
                self.llm_last_mode = self.task_parser.last_mode

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

    async def _submit_agent_event(self, event: Event) -> EventAccepted:
        for _attempt in range(2):
            async with self._lock:
                if event.event_id in self._event_ids:
                    state = self._state.model_copy(deep=True)
                    return EventAccepted(event_id=event.event_id, accepted=True, duplicate=True, revision=state.revision, state=state)
                if self._state.revision == 0 and not self._event_ids:
                    self._state.session_id = event.session_id
                elif event.session_id != self._state.session_id:
                    raise RuntimeErrorWithCode("SESSION_MISMATCH", "event session_id does not match the active session")
                base_revision = self._state.revision
                working_state = self._state.model_copy(deep=True)

            result = await self.conversation_agent.handle(
                str(event.payload.get("text", "")),
                working_state,
                source=event.source,
                event_id=event.event_id,
            )

            async with self._lock:
                if event.event_id in self._event_ids:
                    state = self._state.model_copy(deep=True)
                    return EventAccepted(event_id=event.event_id, accepted=True, duplicate=True, revision=state.revision, state=state)
                if event.session_id != self._state.session_id:
                    raise RuntimeErrorWithCode("SESSION_MISMATCH", "event session_id does not match the active session")
                if self._state.revision != base_revision:
                    continue
                self._state = result.state
                self.llm_last_mode = result.mode
                self._event_ids.add(event.event_id)
                self._touch(f"event:{event.event_id}")
                state = self._state.model_copy(deep=True)
            await self._broadcast(state)
            return EventAccepted(event_id=event.event_id, accepted=True, duplicate=False, revision=state.revision, state=state)

        raise RuntimeErrorWithCode("CONCURRENT_UPDATE", "state changed while the agent was planning; retry the event")

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
            consume_confirmation(self._state, request)
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
            consume_confirmation(self._state, request)
            if not duplicate:
                self._touch(f"confirm:{request.confirmation_id}:{request.input_mode}")
            expected_revision = self._state.revision
            confirmed_state = self._state.model_copy(deep=True)
        if not duplicate:
            reply = await self.conversation_agent.compose_confirmation_reply(
                confirmed_state,
                decision=request.decision,
            )
            async with self._lock:
                if self._state.revision == expected_revision and self._state.output is not None:
                    self._state.output.conclusion = reply
                    self.llm_last_mode = self.conversation_agent.last_mode
                state = self._state.model_copy(deep=True)
        else:
            state = confirmed_state
        if not duplicate:
            await self._broadcast(state)
        return state, duplicate

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
