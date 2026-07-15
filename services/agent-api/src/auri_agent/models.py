from __future__ import annotations

from datetime import datetime, timedelta, timezone
from enum import StrEnum
from typing import Any, Literal
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field


TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


def now() -> datetime:
    return datetime.now(TZ)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Stage(StrEnum):
    OFF_VEHICLE_IDLE = "off_vehicle_idle"
    PRE_DEPARTURE_WARNING = "pre_departure_warning"
    HANDOVER_TO_VEHICLE = "handover_to_vehicle"
    VEHICLE_OBSERVATION = "vehicle_observation"
    TAKEOVER_L2 = "takeover_L2"
    TAKEOVER_L3 = "takeover_L3"
    PLANNING = "planning"
    SERVICE_PREPARED = "service_prepared"
    WAITING_CONFIRMATION = "waiting_confirmation"
    EXECUTING = "executing"
    SERVICE_EXECUTED = "service_executed"
    ACTION_COMPLETED = "action_completed"
    COOLDOWN = "cooldown"
    PARKED_REVIEW = "parked_review"
    ERROR = "error"


class Scene(StrEnum):
    OFF_VEHICLE = "off_vehicle"
    APPROACHING_VEHICLE = "approaching_vehicle"
    DRIVING = "driving"
    HIGH_LOAD_DRIVING = "high_load_driving"
    PARKED = "parked"


class Surface(StrEnum):
    MOBILE = "mobile"
    VEHICLE_HMI = "vehicle_hmi"
    NONE = "none"


class PressureLevel(StrEnum):
    L0 = "L0"
    L1 = "L1"
    L2 = "L2"
    L3 = "L3"
    RECOVERY = "Recovery"


class Task(StrictModel):
    task_id: str
    title: str
    scheduled_at: datetime | None = None
    location: str | None = None
    task_type: Literal["rigid", "flexible"]
    priority: Literal["low", "medium", "high"]
    adjustable: bool
    status: Literal["pending", "rescheduled", "completed"] = "pending"
    waiting_party: list[str] = Field(default_factory=list)
    capability_tags: list[str] = Field(default_factory=list)


class Event(StrictModel):
    schema_version: Literal["0.2.0"] = "0.2.0"
    event_id: str
    session_id: str
    type: Literal[
        "task.created",
        "meeting.overrun",
        "scene.approaching",
        "scene.vehicle_entered",
        "scene.parked",
        "traffic.updated",
        "wearable.signal",
        "driving.signal",
        "user.utterance",
        "service.mock.config",
        "confirmation.confirmed",
        "cooldown.elapsed",
        "session.reset",
    ]
    source: Literal["mobile", "vehicle_hmi", "wearable", "demo_console", "agent_api"]
    timestamp: datetime
    correlation_id: str | None = None
    device_id: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)


class Risk(StrictModel):
    pressure_level: PressureLevel = PressureLevel.L0
    late_minutes: int = 0
    reason_codes: list[str] = Field(default_factory=list)
    auxiliary_signals: list[str] = Field(default_factory=list)


class Profile(StrictModel):
    profile_id: str = "profile_efficiency"
    profile_type: Literal["efficiency", "quality"] = "efficiency"
    tone: str = "direct"
    proactive_voice_threshold: Literal["L1", "L2", "L3"] = "L2"
    haptic_mode: Literal["clear", "gentle"] = "clear"
    budget_limit: float = 200.0
    delivery_priority: Literal["fastest", "quality_first"] = "fastest"
    substitution_policy: Literal["same_spec_within_budget", "same_brand_only"] = "same_spec_within_budget"
    explanation_depth: Literal["brief", "detailed"] = "brief"

    @classmethod
    def preset(cls, profile_type: Literal["efficiency", "quality"]) -> "Profile":
        if profile_type == "quality":
            return cls(
                profile_id="profile_quality",
                profile_type="quality",
                tone="warm",
                proactive_voice_threshold="L2",
                haptic_mode="gentle",
                budget_limit=260,
                delivery_priority="quality_first",
                substitution_policy="same_brand_only",
                explanation_depth="detailed",
            )
        return cls()


class WearableState(StrictModel):
    connected: bool = False
    mode: Literal["idle", "warning", "handover", "processing", "completed", "error"] = "idle"
    text: str = "AURI 就绪"
    color: Literal["navy", "blue", "yellow", "green", "red"] = "navy"
    haptic: Literal["none", "double_short", "single_pulse", "three_beat", "soft_short", "error_once"] = "none"
    command_id: str = Field(default_factory=lambda: f"cmd_{uuid4().hex[:12]}")
    heart_rate: int | None = Field(default=None, ge=20, le=250)
    signal_confidence: float | None = Field(default=None, ge=0, le=1)


class Action(StrictModel):
    action_id: str
    type: Literal["message", "reschedule", "service_order"]
    target: str
    status: Literal["planned", "ready", "awaiting_confirmation", "completed", "blocked", "failed"]
    risk: Literal["low", "medium", "high"]
    requires_confirmation: bool
    summary: str
    details_ref: str | None = None
    error_code: str | None = None


class Confirmation(StrictModel):
    confirmation_id: str
    action_ids: list[str]
    expires_at: datetime
    status: Literal["pending", "accepted", "rejected", "expired"] = "pending"
    confirmed_by: str | None = None
    owner_surface: Literal["mobile", "vehicle_hmi"]


class ServiceItem(StrictModel):
    sku: str
    name: str
    quantity: int = Field(ge=1)
    unit_price: float = Field(ge=0)
    subtotal: float = Field(ge=0)
    substitution: str | None = None


class ServiceOrder(StrictModel):
    order_id: str | None = None
    preview_id: str
    items: list[ServiceItem]
    total: float = Field(ge=0)
    budget_limit: float = Field(ge=0)
    budget_status: Literal["within_budget", "over_budget"]
    delivery_window: str
    status: Literal["preview", "awaiting_confirmation", "submitted", "blocked", "failed"]
    error_code: Literal["OUT_OF_STOCK", "OVER_BUDGET", "DUPLICATE", "EXPIRED", "NOT_AUTHORIZED", "NOT_FOUND"] | None = None


class InteractionOutput(StrictModel):
    message_id: str
    priority: Literal["low", "normal", "high", "critical"]
    owner_surface: Surface
    suppressed_surfaces: list[Literal["mobile", "vehicle_hmi", "wearable"]]
    expires_at: datetime
    requires_confirmation: bool
    conclusion: str


class WorldState(StrictModel):
    schema_version: Literal["0.2.0"] = "0.2.0"
    session_id: str
    revision: int = 0
    updated_at: datetime = Field(default_factory=now)
    stage: Stage = Stage.OFF_VEHICLE_IDLE
    scene: Scene = Scene.OFF_VEHICLE
    primary_surface: Surface = Surface.MOBILE
    risk: Risk = Field(default_factory=Risk)
    tasks: list[Task] = Field(default_factory=list)
    eta: datetime | None = None
    actions: list[Action] = Field(default_factory=list)
    confirmation: Confirmation | None = None
    profile: Profile = Field(default_factory=Profile)
    wearable: WearableState = Field(default_factory=WearableState)
    service_orders: list[ServiceOrder] = Field(default_factory=list)
    output: InteractionOutput | None = None
    action_ledger: list[str] = Field(default_factory=list)
    service_mock_mode: Literal["success", "out_of_stock", "over_budget"] = "success"


class ConfirmationRequest(StrictModel):
    confirmation_id: str
    decision: Literal["accept", "reject"]
    confirmed_by: Literal["mobile", "vehicle_hmi"]
    input_mode: Literal["button", "voice"] = "button"


class ResetRequest(StrictModel):
    scenario_id: str = "happy-path"


class EventAccepted(StrictModel):
    event_id: str
    accepted: bool
    duplicate: bool
    revision: int
    state: WorldState


def initial_state(session_id: str | None = None) -> WorldState:
    return WorldState(session_id=session_id or f"demo_{uuid4().hex[:12]}")


def output_expiry(minutes: int = 5) -> datetime:
    return now() + timedelta(minutes=minutes)
