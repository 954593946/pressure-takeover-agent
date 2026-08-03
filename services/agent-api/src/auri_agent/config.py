from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


REPO_ROOT = Path(__file__).resolve().parents[4]
SERVICE_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    app_name: str = "AURI Agent API"
    service_name: str = "auri-agent-api"
    build_sha: str = ""
    render_git_commit: str = ""
    environment: str = "development"
    log_level: str = "INFO"
    demo_mode: bool = True

    host: str = "127.0.0.1"
    port: int = 8000
    cors_origins: str = (
        "http://localhost:3000,"
        "http://localhost:5173,"
        "http://127.0.0.1:5173,"
        "http://localhost:5174,"
        "http://127.0.0.1:5174"
    )

    openai_api_key: str = Field(default="", repr=False)
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-5.5"
    openai_timeout_seconds: float = 30.0
    llm_enabled: bool = True
    agent_shared_token: str = Field(default="", repr=False)
    amap_js_api_key: str = Field(default="", repr=False)
    amap_security_js_code: str = Field(default="", repr=False)
    amap_public_base_url: str = ""
    amap_allowed_origins: str = (
        "http://localhost:5174,"
        "http://127.0.0.1:5174,"
        "https://954593946.github.io,"
        "https://wangwang20.github.io"
    )
    amap_proxy_timeout_seconds: float = 12.0

    model_config = SettingsConfigDict(
        env_file=(REPO_ROOT / ".env", SERVICE_ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def llm_configured(self) -> bool:
        return self.llm_enabled and bool(self.openai_api_key and self.openai_base_url and self.openai_model)

    @property
    def shared_access_enabled(self) -> bool:
        return bool(self.agent_shared_token)

    @property
    def amap_configured(self) -> bool:
        return bool(self.amap_js_api_key and self.amap_security_js_code)

    @property
    def deployment_build_sha(self) -> str:
        return self.build_sha or self.render_git_commit or "local"

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def amap_allowed_origin_list(self) -> list[str]:
        return [origin.strip().rstrip("/") for origin in self.amap_allowed_origins.split(",") if origin.strip()]
