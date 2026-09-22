from __future__ import annotations

import json
from pathlib import Path
import sqlite3
from concurrent.futures import ThreadPoolExecutor
from threading import Event
import time

from fastapi import HTTPException
from fastapi.testclient import TestClient
import pytest

from app.config import ConfigurationError, get_settings
from app.database import init_db
from app.main import create_app
from app import deployment_configuration as deployment_module


TOKEN = "a-valid-first-token"


@pytest.fixture
def deployment(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    for name in (
        "API_TOKEN", "ADMIN_WEB_ORIGINS", "WEB_INVENTORY_ENABLED",
        "CONFIG_PATH", "LOG_DIR",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "component.db"))
    monkeypatch.setenv("MQTT_ENABLED", "false")
    get_settings.cache_clear()
    yield tmp_path
    get_settings.cache_clear()


def _payload(token: str = TOKEN, origin: str = "http://nas.local:8081") -> dict:
    return {
        "api_token": token,
        "admin_web_origins": [origin],
        "web_inventory_enabled": True,
    }


def test_first_setup_and_restart(deployment: Path) -> None:
    with TestClient(create_app()) as client:
        assert client.get("/", follow_redirects=False).headers["location"] == "/setup"
        assert client.get("/setup/status").json() == {"configured": False}
        assert client.get("/setup/config").status_code == 401
        response = client.post("/setup/config", json=_payload())
        assert response.status_code == 200
        assert "api_token" not in response.json()
        assert client.get("/setup/status").json() == {"configured": True}
        assert client.post("/setup/config", json=_payload("another-valid-token")).status_code == 401

    get_settings.cache_clear()
    assert get_settings().api_token == TOKEN
    saved = json.loads((deployment / "config.json").read_text(encoding="utf-8"))
    assert saved["API_TOKEN"] == TOKEN
    assert set(saved) == {
        "API_TOKEN", "ADMIN_WEB_ORIGINS", "WEB_INVENTORY_ENABLED"
    }


def test_concurrent_first_setup_has_one_winner(
    deployment: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    entered_write = Event()
    release_write = Event()
    original_write = deployment_module._write_configuration

    def delayed_write(payload: dict[str, object]) -> None:
        entered_write.set()
        assert release_write.wait(timeout=2)
        original_write(payload)

    monkeypatch.setattr(deployment_module, "_write_configuration", delayed_write)
    with TestClient(create_app()) as client, ThreadPoolExecutor(max_workers=2) as pool:
        first = pool.submit(client.post, "/setup/config", json=_payload())
        assert entered_write.wait(timeout=2)
        second = pool.submit(
            client.post, "/setup/config", json=_payload("another-valid-token")
        )
        time.sleep(0.05)
        release_write.set()
        statuses = sorted((first.result().status_code, second.result().status_code))
    assert statuses == [200, 409]


def test_failed_save_keeps_first_setup_open(
    deployment: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    def fail_write(payload: dict[str, object]) -> None:
        raise HTTPException(status_code=500, detail="Could not save configuration.")

    monkeypatch.setattr(deployment_module, "_write_configuration", fail_write)
    with TestClient(create_app()) as client:
        response = client.post("/setup/config", json=_payload())
        status_response = client.get("/setup/status")
    assert response.status_code == 500
    assert status_response.json() == {"configured": False}
    assert not (deployment / "config.json").exists()


def test_concurrent_token_rotation_rechecks_old_credentials(
    deployment: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    entered_write = Event()
    release_write = Event()
    original_write = deployment_module._write_configuration
    headers = {"Authorization": f"Bearer {TOKEN}"}
    first_token = "first-replacement-token"
    second_token = "second-replacement-token"

    with TestClient(create_app()) as client:
        assert client.post("/setup/config", json=_payload()).status_code == 200

        def delayed_write(payload: dict[str, object]) -> None:
            if payload["api_token"] == first_token:
                entered_write.set()
                assert release_write.wait(timeout=2)
            original_write(payload)

        monkeypatch.setattr(deployment_module, "_write_configuration", delayed_write)
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(
                client.post, "/setup/config", headers=headers,
                json=_payload(first_token),
            )
            assert entered_write.wait(timeout=2)
            second = pool.submit(
                client.post, "/setup/config", headers=headers,
                json=_payload(second_token),
            )
            time.sleep(0.05)
            release_write.set()
            statuses = sorted((first.result().status_code, second.result().status_code))
        assert statuses == [200, 401]
        assert client.get(
            "/setup/config",
            headers={"Authorization": f"Bearer {first_token}"},
        ).status_code == 200


def test_replace_failure_preserves_configuration_and_retry_succeeds(
    deployment: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    replacement = "replacement-token-after-retry"
    with TestClient(create_app()) as client:
        assert client.post("/setup/config", json=_payload()).status_code == 200
        path = deployment / "config.json"
        original = path.read_bytes()
        headers = {"Authorization": f"Bearer {TOKEN}"}
        real_replace = deployment_module.os.replace

        with monkeypatch.context() as scoped:
            def fail_replace(source, destination) -> None:
                raise OSError("simulated replace failure")

            scoped.setattr(deployment_module.os, "replace", fail_replace)
            failed = client.post(
                "/setup/config", headers=headers, json=_payload(replacement)
            )
        assert failed.status_code == 500
        assert path.read_bytes() == original
        assert client.get("/setup/config", headers=headers).status_code == 200
        assert deployment_module.os.replace is real_replace
        retried = client.post(
            "/setup/config", headers=headers, json=_payload(replacement)
        )
        assert retried.status_code == 200


@pytest.mark.parametrize("invalid_token", ["é" * 16, "valid token with spaces"])
def test_new_tokens_require_visible_ascii_without_whitespace(
    deployment: Path, invalid_token: str
) -> None:
    with TestClient(create_app()) as client:
        response = client.post("/setup/config", json=_payload(invalid_token))
    assert response.status_code == 422
    assert invalid_token not in response.text


def test_setup_update_logs_and_dynamic_cors(deployment: Path) -> None:
    with TestClient(create_app()) as client:
        assert client.post("/setup/config", json=_payload()).status_code == 200
        headers = {"Authorization": f"Bearer {TOKEN}"}
        assert client.get("/setup/config", headers=headers).status_code == 200
        logs = client.get("/setup/logs", headers=headers)
        assert logs.status_code == 200
        assert all(TOKEN not in line for line in logs.json()["lines"])
        preflight = client.options(
            "/admin-api/dashboard",
            headers={
                "Origin": "http://nas.local:8081",
                "Access-Control-Request-Method": "GET",
            },
        )
        assert preflight.headers["access-control-allow-origin"] == "http://nas.local:8081"


def test_environment_wins_and_locked_change_is_rejected(
    deployment: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("API_TOKEN", TOKEN)
    monkeypatch.setenv("ADMIN_WEB_ORIGINS", "https://admin.example")
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        headers = {"Authorization": f"Bearer {TOKEN}"}
        response = client.post(
            "/setup/config", headers=headers,
            json=_payload("", "https://different.example"),
        )
    assert response.status_code == 409
    assert response.json()["detail"].startswith("ADMIN_WEB_ORIGINS")


def test_bad_configuration_is_not_treated_as_first_setup(deployment: Path) -> None:
    (deployment / "config.json").write_text("{broken", encoding="utf-8")
    get_settings.cache_clear()
    with pytest.raises(ConfigurationError, match="Cannot read deployment configuration"):
        create_app()


def test_legacy_business_database_keeps_old_default_token(deployment: Path) -> None:
    database = deployment / "component.db"
    settings = get_settings()
    init_db(settings)
    connection = sqlite3.connect(database)
    try:
        connection.execute(
            "INSERT INTO components "
            "(id, sku, name, category, package_name, location, quantity, "
            "min_stock, updated_at) VALUES "
            "('existing', 'OLD', 'Old', 'Legacy', 'N/A', 'A1', 1, 0, "
            "'2026-01-01T00:00:00Z')"
        )
        connection.commit()
    finally:
        connection.close()
    (deployment / "config.json").write_text(
        json.dumps({
            "API_TOKEN": "",
            "ADMIN_WEB_ORIGINS": ["http://localhost:5173"],
            "WEB_INVENTORY_ENABLED": False,
        }),
        encoding="utf-8",
    )
    get_settings.cache_clear()
    assert get_settings().api_token == "change-me"
    with TestClient(create_app()) as client:
        assert client.get("/setup/status").json() == {"configured": True}


def test_unreadable_existing_database_is_not_treated_as_new(deployment: Path) -> None:
    (deployment / "component.db").write_bytes(b"not a sqlite database")
    get_settings.cache_clear()
    with pytest.raises(ConfigurationError, match="Cannot inspect existing database"):
        get_settings()


def test_validation_never_echoes_token(deployment: Path) -> None:
    secret = "s" * 5000
    with TestClient(create_app()) as client:
        response = client.post("/setup/config", json=_payload(secret))
    assert response.status_code == 422
    assert secret not in response.text
