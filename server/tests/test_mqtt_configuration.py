from __future__ import annotations

from pathlib import Path
import sqlite3

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app


TOKEN = {"Authorization": "Bearer mqtt-config-test"}


def _configure(tmp_path: Path, monkeypatch) -> Path:
    path = tmp_path / "mqtt-config.sqlite"
    monkeypatch.setenv("DATABASE_PATH", str(path))
    monkeypatch.setenv("API_TOKEN", "mqtt-config-test")
    monkeypatch.setenv("MQTT_ENABLED", "false")
    monkeypatch.setenv("MQTT_HOST", "env-broker")
    monkeypatch.setenv("MQTT_PASSWORD", "env-secret")
    get_settings.cache_clear()
    return path


def _saved_payload(**changes) -> dict:
    value = {
        "enabled": True,
        "host": "saved-broker",
        "port": 8883,
        "tls": True,
        "username": "home-assistant",
        "password": "saved-secret",
        "topic_prefix": "home/component-vault",
        "client_id": "component-vault-prod",
    }
    value.update(changes)
    return value


def test_config_routes_require_auth_and_get_masks_password(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        assert client.get("/admin-api/mqtt/config").status_code == 401
        saved = client.post("/admin-api/mqtt/config", headers=TOKEN, json=_saved_payload())
        loaded = client.get("/admin-api/mqtt/config", headers=TOKEN)
    assert saved.status_code == 200
    assert loaded.json()["password_configured"] is True
    assert loaded.json()["source"] == "saved"
    assert "saved-secret" not in loaded.text
    assert "password" not in loaded.json()


def test_blank_password_preserves_and_explicit_clear_removes_it(tmp_path: Path, monkeypatch) -> None:
    path = _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        client.post("/admin-api/mqtt/config", headers=TOKEN, json=_saved_payload())
        retained = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(host="changed", password=""),
        )
        cleared = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(password="", clear_password=True),
        )
    assert retained.json()["password_configured"] is True
    assert cleared.json()["password_configured"] is False
    connection = sqlite3.connect(path)
    assert connection.execute("SELECT password FROM mqtt_configuration").fetchone()[0] == ""
    connection.close()


def test_saved_config_overrides_environment_only_after_restart(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        saved = client.post("/admin-api/mqtt/config", headers=TOKEN, json=_saved_payload())
        runtime = client.get("/admin-api/mqtt/status", headers=TOKEN)
    assert saved.json()["restart_required"] is True
    assert runtime.json()["enabled"] is False

    get_settings.cache_clear()
    restarted = get_settings()
    assert restarted.mqtt_enabled is True
    assert restarted.mqtt_host == "saved-broker"
    assert restarted.mqtt_password == "saved-secret"
    get_settings.cache_clear()


def test_validation_error_never_echoes_password_and_does_not_touch_inventory(
    tmp_path: Path, monkeypatch
) -> None:
    path = _configure(tmp_path, monkeypatch)
    secret = "DO-NOT-ECHO-ME"
    with TestClient(create_app()) as client:
        invalid = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(host="", password=secret),
        )
    assert invalid.status_code == 422
    assert secret not in invalid.text
    connection = sqlite3.connect(path)
    assert connection.execute("SELECT COUNT(*) FROM components").fetchone()[0] == 0
    assert connection.execute("SELECT COUNT(*) FROM mqtt_configuration").fetchone()[0] == 0
    connection.close()


def test_malformed_and_overlong_passwords_are_redacted(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    long_secret = "RAW-SECRET-" + "x" * 1100
    with TestClient(create_app()) as client:
        malformed_object = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(password={"secret": "OBJECT-SECRET"}),
        )
        malformed_list = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(password=["LIST-SECRET"]),
        )
        overlong = client.post(
            "/admin-api/mqtt/config", headers=TOKEN,
            json=_saved_payload(password=long_secret),
        )
    for response in (malformed_object, malformed_list, overlong):
        assert response.status_code == 422
        assert response.json()["detail"] == "MQTT configuration validation failed."
        assert response.json()["fields"] == ["password"]
        assert "SECRET" not in response.text
        assert "input" not in response.text
