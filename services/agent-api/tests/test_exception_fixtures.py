import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_ROOT = REPO_ROOT / "packages" / "test-fixtures"
REQUIRED = {
    "wrong-surface",
    "duplicate-event",
    "duplicate-confirmation",
    "over-budget",
    "out-of-stock",
    "new-session",
    "offline-reconnect",
}


def test_exception_fixtures_are_complete_and_contain_no_credentials() -> None:
    loaded = {}
    for path in FIXTURE_ROOT.glob("*.json"):
        if path.name == "happy-path.events.json":
            continue
        fixture = json.loads(path.read_text(encoding="utf-8"))
        scenario = fixture["scenario"]
        assert fixture["purpose"].strip()
        assert fixture["steps"]
        assert fixture["expected"]
        serialised = json.dumps(fixture, ensure_ascii=False).lower()
        assert "x-agent-token" not in serialised
        assert "api_key" not in serialised
        assert "auri-team-" not in serialised
        loaded[scenario] = path.name

    assert REQUIRED <= set(loaded), f"missing exception fixtures: {sorted(REQUIRED - set(loaded))}"
