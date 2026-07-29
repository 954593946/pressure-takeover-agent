import asyncio
import json
import logging
import secrets
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request as UrlRequest, urlopen

from fastapi import Depends, FastAPI, HTTPException, Request, Security, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from fastapi.security import APIKeyHeader
from pydantic import BaseModel

from .config import Settings
from .models import ConfirmationRequest, Event, EventAccepted, Profile, ResetRequest, WorldState
from .runtime import AgentRuntime, RuntimeErrorWithCode
from .chat import ChatAgent


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
    app.state.chat_agent = ChatAgent(app.state.runtime)
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
            "llm_framework": current_runtime.task_parser.framework,
            "llm_model": current_settings.openai_model,
            "llm_last_mode": current_runtime.llm_last_mode,
            "agent_tools_enabled": current_runtime.conversation_agent.configured,
            "agent_last_tools": current_runtime.conversation_agent.last_tools,
            "shared_access_enabled": current_settings.shared_access_enabled,
            "amap_configured": current_settings.amap_configured,
        }

    @app.get("/v1/map-config", dependencies=[Depends(require_shared_access)])
    async def map_config(request: Request) -> dict[str, object]:
        current_settings: Settings = request.app.state.settings
        if not current_settings.amap_configured:
            return {"enabled": False, "provider": "offline"}
        public_base = current_settings.amap_public_base_url.strip().rstrip("/") or str(request.base_url).rstrip("/")
        return {
            "enabled": True,
            "provider": "amap",
            "key": current_settings.amap_js_api_key,
            "service_host": f"{public_base}/_AMapService",
            "style": "amap://styles/normal",
        }

    @app.get("/_AMapService/{proxy_path:path}")
    async def amap_proxy(proxy_path: str, request: Request) -> Response:
        current_settings: Settings = request.app.state.settings
        if not current_settings.amap_configured:
            raise HTTPException(status_code=503, detail={"code": "AMAP_NOT_CONFIGURED", "message": "AMap proxy is disabled"})
        origin = (request.headers.get("origin") or "").rstrip("/")
        if origin not in current_settings.amap_allowed_origin_list:
            raise HTTPException(status_code=403, detail={"code": "AMAP_ORIGIN_DENIED", "message": "origin is not allowed"})
        clean_path = proxy_path.lstrip("/")
        if not clean_path or ".." in clean_path or "://" in clean_path:
            raise HTTPException(status_code=400, detail={"code": "AMAP_PATH_INVALID", "message": "invalid proxy path"})
        upstream_base = "https://webapi.amap.com/" if clean_path.startswith("v4/map/styles") else "https://restapi.amap.com/"
        query_items = [(key, value) for key, value in request.query_params.multi_items() if key != "jscode"]
        query_items.append(("jscode", current_settings.amap_security_js_code))
        target_url = f"{upstream_base}{clean_path}?{urlencode(query_items, doseq=True)}"
        try:
            upstream_status, content_type, body = await asyncio.to_thread(
                _fetch_amap,
                target_url,
                current_settings.amap_proxy_timeout_seconds,
            )
        except (HTTPError, URLError, TimeoutError) as exc:
            raise HTTPException(
                status_code=502,
                detail={"code": "AMAP_UPSTREAM_ERROR", "message": "AMap upstream request failed"},
            ) from exc
        return Response(
            content=body,
            status_code=upstream_status,
            media_type=content_type.split(";", 1)[0] if content_type else "application/json",
        )

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

    # ── Chat endpoints (SSE streaming for mobile ChatRepository) ──────────

    class ChatRequest(BaseModel):
        message: str
        inputMode: str = "text"
        sessionId: str | None = None

    class ChatConfirmRequest(BaseModel):
        sessionId: str
        confirmationId: str
        decision: str

    @app.post("/v1/chat", dependencies=[Depends(require_shared_access)])
    async def chat(body: ChatRequest, request: Request) -> StreamingResponse:
        chat_agent: ChatAgent = request.app.state.chat_agent
        current_runtime: AgentRuntime = request.app.state.runtime
        session_id = body.sessionId or current_runtime._state.session_id

        async def sse_stream():
            async for event in chat_agent.chat_stream(body.message, session_id, body.inputMode):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

        return StreamingResponse(
            sse_stream(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    @app.post("/v1/chat/confirm", dependencies=[Depends(require_shared_access)])
    async def chat_confirm(body: ChatConfirmRequest, request: Request) -> dict:
        current_runtime: AgentRuntime = request.app.state.runtime
        try:
            req = ConfirmationRequest(
                confirmation_id=body.confirmationId,
                decision="accept" if body.decision == "accept" else "reject",
                confirmed_by="mobile",
                input_mode="button",
            )
            state, _ = await current_runtime.confirm(req)
            return {"accepted": True, "revision": state.revision}
        except Exception:
            return {"accepted": False, "revision": 0}

    return app


def _token_is_valid(settings: Settings, candidate: str | None) -> bool:
    if not settings.shared_access_enabled:
        return True
    return bool(candidate) and secrets.compare_digest(candidate, settings.agent_shared_token)


def _http_error(exc: RuntimeErrorWithCode) -> HTTPException:
    status_code = {
        "NOT_FOUND": 404,
        "SESSION_MISMATCH": 409,
        "CONCURRENT_UPDATE": 409,
        "EXPIRED": 409,
        "WRONG_SURFACE": 409,
        "USE_RESET_ENDPOINT": 409,
        "INVALID_MOCK_MODE": 400,
    }.get(exc.code, 400)
    return HTTPException(status_code=status_code, detail={"code": exc.code, "message": str(exc)})


def _fetch_amap(url: str, timeout_seconds: float) -> tuple[int, str, bytes]:
    request = UrlRequest(url, headers={"User-Agent": "AURI-Agent-Map-Proxy/1.0", "Accept": "application/json,*/*"})
    with urlopen(request, timeout=timeout_seconds) as response:
        return response.status, response.headers.get("Content-Type", "application/json"), response.read()


app = create_app()
