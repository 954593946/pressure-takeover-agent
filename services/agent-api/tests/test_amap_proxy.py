import httpx
import pytest

import auri_agent.app as app_module
from auri_agent.app import create_app
from auri_agent.config import Settings


@pytest.mark.asyncio
async def test_map_config_requires_team_token_and_never_exposes_security_code() -> None:
    app = create_app(
        Settings(
            llm_enabled=False,
            openai_api_key="",
            agent_shared_token="team-test-token",
            amap_js_api_key="public-js-key",
            amap_security_js_code="server-only-security-code",
            amap_public_base_url="https://agent.example.com",
        )
    )
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get("/v1/map-config")).status_code == 401
        response = await client.get("/v1/map-config", headers={"X-Agent-Token": "team-test-token"})

    assert response.status_code == 200
    assert response.json() == {
        "enabled": True,
        "provider": "amap",
        "key": "public-js-key",
        "service_host": "https://agent.example.com/_AMapService",
        "style": "amap://styles/normal",
    }
    assert "server-only-security-code" not in response.text


@pytest.mark.asyncio
async def test_amap_proxy_injects_server_security_code_and_rejects_other_origins(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    def fake_fetch(url: str, timeout_seconds: float) -> tuple[int, str, bytes]:
        captured["url"] = url
        captured["timeout"] = timeout_seconds
        return 200, "application/json; charset=utf-8", b'{"status":"1"}'

    monkeypatch.setattr(app_module, "_fetch_amap", fake_fetch)
    app = create_app(
        Settings(
            llm_enabled=False,
            openai_api_key="",
            agent_shared_token="",
            amap_js_api_key="public-js-key",
            amap_security_js_code="server-only-security-code",
            amap_allowed_origins="https://hmi.example.com,http://127.0.0.1:5174",
        )
    )
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get(
            "/_AMapService/v3/direction/driving?key=public-js-key&jscode=attacker-value",
            headers={"Origin": "https://hmi.example.com"},
        )
        denied = await client.get(
            "/_AMapService/v3/direction/driving?key=public-js-key",
            headers={"Origin": "https://attacker.example.com"},
        )

    assert response.status_code == 200
    assert response.json() == {"status": "1"}
    assert "jscode=server-only-security-code" in str(captured["url"])
    assert "attacker-value" not in str(captured["url"])
    assert captured["timeout"] == 12.0
    assert denied.status_code == 403
