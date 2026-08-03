from __future__ import annotations

from datetime import datetime, timezone


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat()


def classify_provider_error(exc: Exception) -> tuple[str, str]:
    """Return a stable public error code and a non-sensitive fallback reason."""
    if isinstance(exc, TimeoutError):
        return "UPSTREAM_TIMEOUT", "timeout"

    response = getattr(exc, "response", None)
    status_code = getattr(response, "status_code", None) or getattr(exc, "status_code", None)
    if status_code == 401:
        return "UPSTREAM_AUTH", "http_401"
    if status_code == 429:
        return "UPSTREAM_RATE_LIMIT", "http_429"
    if isinstance(status_code, int) and status_code >= 500:
        return "UPSTREAM_5XX", "http_5xx"

    name = type(exc).__name__.lower()
    message = str(exc).lower()
    if "timeout" in name or "timed out" in message:
        return "UPSTREAM_TIMEOUT", "timeout"
    if "401" in message or "unauthorized" in message:
        return "UPSTREAM_AUTH", "http_401"
    if "429" in message or "rate limit" in message:
        return "UPSTREAM_RATE_LIMIT", "http_429"
    if any(code in message for code in ("500", "502", "503", "504")):
        return "UPSTREAM_5XX", "http_5xx"
    return "UPSTREAM_ERROR", "provider_error"
