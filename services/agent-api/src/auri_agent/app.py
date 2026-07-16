import json
import logging
import secrets

from fastapi import Depends, FastAPI, HTTPException, Request, Security, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.security import APIKeyHeader

from .config import Settings
from .models import ConfirmationRequest, Event, EventAccepted, Profile, ResetRequest, WorldState
from .runtime import AgentRuntime, RuntimeErrorWithCode


shared_token_header = APIKeyHeader(name="X-Agent-Token", auto_error=False)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level)
    app = FastAPI(title=settings.app_name, version="0.2.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials="*" not in settings.cors_origin_list,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.state.runtime = AgentRuntime(settings)
    app.state.settings = settings

    def runtime(request: Request) -> AgentRuntime:
        return request.app.state.runtime

    async def require_shared_access(
        request: Request,
        header_token: str | None = Security(shared_token_header),
    ) -> None:
        current_settings: Settings = request.app.state.settings
        if not _token_is_valid(current_settings, header_token):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"code": "UNAUTHORIZED", "message": "missing or invalid X-Agent-Token"},
            )

    @app.get("/health")
    async def health(request: Request) -> dict[str, object]:
        current_settings: Settings = request.app.state.settings
        current_runtime: AgentRuntime = request.app.state.runtime
        return {
            "status": "ok",
            "schema_version": "0.2.0",
            "demo_mode": current_settings.demo_mode,
            "llm_configured": current_settings.llm_configured,
            "llm_model": current_settings.openai_model,
            "llm_last_mode": current_runtime.task_parser.last_mode,
            "shared_access_enabled": current_settings.shared_access_enabled,
        }

    @app.post("/v1/event", response_model=EventAccepted, status_code=status.HTTP_202_ACCEPTED, dependencies=[Depends(require_shared_access)])
    @app.post("/v1/events", response_model=EventAccepted, status_code=status.HTTP_202_ACCEPTED, include_in_schema=False, dependencies=[Depends(require_shared_access)])
    async def submit_event(event: Event, request: Request) -> EventAccepted:
        try:
            return await runtime(request).submit_event(event)
        except RuntimeErrorWithCode as exc:
            raise _http_error(exc) from exc

    @app.get("/v1/state", response_model=WorldState, dependencies=[Depends(require_shared_access)])
    @app.get("/v1/world-state", response_model=WorldState, include_in_schema=False, dependencies=[Depends(require_shared_access)])
    async def get_state(request: Request) -> WorldState:
        return await runtime(request).get_state()

    @app.post("/v1/confirm", response_model=WorldState, dependencies=[Depends(require_shared_access)])
    async def confirm(body: ConfirmationRequest, request: Request) -> WorldState:
        try:
            state, _duplicate = await runtime(request).confirm(body)
            return state
        except RuntimeErrorWithCode as exc:
            raise _http_error(exc) from exc

    @app.put("/v1/profile", response_model=WorldState, dependencies=[Depends(require_shared_access)])
    async def update_profile(profile: Profile, request: Request) -> WorldState:
        return await runtime(request).update_profile(profile)

    @app.post("/v1/session/reset", response_model=WorldState, dependencies=[Depends(require_shared_access)])
    async def reset_session(body: ResetRequest, request: Request) -> WorldState:
        return await runtime(request).reset(body.scenario_id)

    @app.get("/v1/stream", dependencies=[Depends(require_shared_access)])
    async def stream(request: Request) -> StreamingResponse:
        current_runtime = runtime(request)

        async def event_stream():
            queue = await current_runtime.subscribe()
            try:
                while True:
                    if await request.is_disconnected():
                        break
                    state = await queue.get()
                    yield f"event: state.updated\ndata: {state.model_dump_json()}\n\n"
            finally:
                current_runtime.unsubscribe(queue)

        return StreamingResponse(event_stream(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})

    @app.websocket("/v1/ws")
    async def websocket_stream(websocket: WebSocket) -> None:
        current_settings: Settings = websocket.app.state.settings
        websocket_token = websocket.headers.get("x-agent-token") or websocket.query_params.get("access_token")
        if not _token_is_valid(current_settings, websocket_token):
            await websocket.close(code=4401, reason="missing or invalid team token")
            return
        await websocket.accept()
        current_runtime: AgentRuntime = websocket.app.state.runtime
        queue = await current_runtime.subscribe()
        try:
            while True:
                state = await queue.get()
                await websocket.send_text(json.dumps({"type": "state.updated", "data": state.model_dump(mode="json")}, ensure_ascii=False))
        except WebSocketDisconnect:
            pass
        finally:
            current_runtime.unsubscribe(queue)

    return app


def _token_is_valid(settings: Settings, candidate: str | None) -> bool:
    if not settings.shared_access_enabled:
        return True
    return bool(candidate) and secrets.compare_digest(candidate, settings.agent_shared_token)


def _http_error(exc: RuntimeErrorWithCode) -> HTTPException:
    status_code = {
        "NOT_FOUND": 404,
        "SESSION_MISMATCH": 409,
        "EXPIRED": 409,
        "WRONG_SURFACE": 409,
        "USE_RESET_ENDPOINT": 409,
        "INVALID_MOCK_MODE": 400,
    }.get(exc.code, 400)
    return HTTPException(status_code=status_code, detail={"code": exc.code, "message": str(exc)})


app = create_app()
