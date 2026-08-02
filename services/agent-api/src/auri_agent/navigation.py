from __future__ import annotations

from datetime import datetime

from .models import GeoPoint, NavigationState, Stage, Task, WorldState, now


DEMO_ROUTE_ORIGIN = GeoPoint(
    name="博世苏州",
    address="江苏省苏州工业园区星龙街455号",
    longitude=120.791879,
    latitude=31.334680,
)

_DEMO_DESTINATIONS: tuple[tuple[tuple[str, ...], GeoPoint], ...] = (
    (
        ("阳光小学", "Demo 阳光小学"),
        GeoPoint(name="阳光小学", longitude=120.7359, latitude=31.3048),
    ),
    (
        ("苏州中心", "东方之门"),
        GeoPoint(name="苏州中心", longitude=120.6677, latitude=31.3181),
    ),
    (
        ("邻里生鲜超市", "Demo 邻里生鲜超市"),
        GeoPoint(name="邻里生鲜超市", longitude=120.7506, latitude=31.3147),
    ),
)

_DEMO_STAGE_PROGRESS: dict[Stage, float] = {
    Stage.OFF_VEHICLE_IDLE: 0.03,
    Stage.PRE_DEPARTURE_WARNING: 0.08,
    Stage.HANDOVER_TO_VEHICLE: 0.16,
    Stage.VEHICLE_OBSERVATION: 0.32,
    Stage.TAKEOVER_L2: 0.46,
    Stage.TAKEOVER_L3: 0.50,
    Stage.PLANNING: 0.58,
    Stage.SERVICE_PREPARED: 0.66,
    Stage.WAITING_CONFIRMATION: 0.70,
    Stage.EXECUTING: 0.80,
    Stage.SERVICE_EXECUTED: 0.86,
    Stage.ACTION_COMPLETED: 0.91,
    Stage.COOLDOWN: 0.95,
    Stage.PARKED_REVIEW: 0.98,
    Stage.ERROR: 0.03,
}


def _select_navigation_task(tasks: list[Task]) -> Task | None:
    return next(
        (task for task in tasks if task.status != "completed" and task.location),
        next((task for task in tasks if task.location), None),
    )


def _resolve_demo_destination(location: str) -> GeoPoint | None:
    normalised = location.strip()
    if not normalised:
        return None
    for aliases, point in _DEMO_DESTINATIONS:
        if normalised in aliases:
            return point.model_copy(deep=True)
    return None


def build_navigation_state(state: WorldState, *, updated_at: datetime | None = None) -> NavigationState | None:
    """Resolve only frozen, non-personal Demo locations into a public route contract."""
    task = _select_navigation_task(state.tasks)
    if task is None or not task.location:
        return None
    destination = _resolve_demo_destination(task.location)
    if destination is None:
        return None
    return NavigationState(
        route_id=f"route_demo_{task.task_id}",
        task_id=task.task_id,
        origin=DEMO_ROUTE_ORIGIN.model_copy(deep=True),
        destination=destination,
        progress=_DEMO_STAGE_PROGRESS.get(state.stage),
        source="demo_fixture",
        is_simulated=True,
        updated_at=updated_at or now(),
    )


def sync_navigation_state(state: WorldState, *, updated_at: datetime | None = None) -> None:
    state.navigation = build_navigation_state(state, updated_at=updated_at)
