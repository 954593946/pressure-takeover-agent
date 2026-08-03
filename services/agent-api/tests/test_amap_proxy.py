from pathlib import Path

import httpx
import pytest

import auri_agent.app as app_module
from auri_agent.app import create_app
from auri_agent.config import Settings


REPO_ROOT = Path(__file__).resolve().parents[3]


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


def test_amap_origin_allows_private_lan_only_on_static_web_port() -> None:
    settings = Settings(amap_allowed_origins="https://hmi.example.com")

    assert settings.amap_origin_allowed("https://hmi.example.com") is True
    assert settings.amap_origin_allowed("http://192.168.8.23:5174") is True
    assert settings.amap_origin_allowed("http://10.20.30.40:5174") is True
    assert settings.amap_origin_allowed("http://172.20.1.5:5174") is True
    assert settings.amap_origin_allowed("http://192.168.8.23:8080") is False
    assert settings.amap_origin_allowed("https://192.168.8.23:5174") is False
    assert settings.amap_origin_allowed("http://8.8.8.8:5174") is False
    assert Settings(
        amap_allowed_origins="https://hmi.example.com",
        amap_allow_private_origins=False,
    ).amap_origin_allowed("http://192.168.8.23:5174") is False


def test_default_and_render_amap_origins_include_team_personal_and_local_pages() -> None:
    required_origins = {
        "https://954593946.github.io",
        "https://wangwang20.github.io",
        "http://localhost:5174",
        "http://127.0.0.1:5174",
    }

    assert required_origins <= set(Settings().amap_allowed_origin_list)
    for manifest_name in ("render.yaml", "render-langchain.yaml"):
        manifest = (REPO_ROOT / manifest_name).read_text(encoding="utf-8")
        allowed_origins_line = next(
            line.strip()
            for line in manifest.splitlines()
            if line.strip().startswith("value: https://954593946.github.io")
        )
        for origin in required_origins:
            assert origin in allowed_origins_line, f"{origin} missing from {manifest_name}"
