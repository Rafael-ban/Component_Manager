from __future__ import annotations

from pathlib import Path
import sqlite3

from fastapi.testclient import TestClient

from app.config import get_settings
from app.accounts import authenticate
from app.database import init_db
from app.main import create_app


ADMIN = {"Authorization": "Bearer account-admin-test"}


def _headers(token: str, account_id: str | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if account_id:
        headers["X-Component-Vault-Account-Id"] = account_id
    return headers


def _component(quantity: int) -> dict[str, object]:
    return {
        "id": "same-id", "sku": "SAME-SKU", "name": "Part", "category": "Test",
        "package_name": "SMD", "location": "Shelf", "quantity": quantity,
        "min_stock": 1, "updated_at": "2026-05-07T06:00:00Z", "deleted": False,
    }


def test_accounts_isolate_sync_and_web_inventory(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "account-admin-test")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "inventory.db"))
    monkeypatch.setenv("WEB_INVENTORY_ENABLED", "true")
    monkeypatch.setenv("MQTT_ENABLED", "false")
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        admin_identity = client.get("/auth/me", headers=ADMIN).json()
        assert admin_identity["account_id"] == "admin"
        first = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "Alice"})
        second = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "Bob"})
        assert first.status_code == second.status_code == 201
        alice, bob = first.json(), second.json()
        assert alice["account_id"] != bob["account_id"]
        assert client.get("/auth/me", headers=_headers(alice["api_token"])).json() == {
            "server_id": admin_identity["server_id"],
            "account_id": alice["account_id"], "name": "Alice", "role": "user",
        }
        assert client.get("/admin-api/accounts", headers=_headers(alice["api_token"])).status_code == 403
        assert client.post("/admin-api/accounts", headers=_headers(alice["api_token"]), json={"name": "Eve"}).status_code == 403
        assert client.patch(f"/admin-api/accounts/{bob['account_id']}", headers=_headers(alice["api_token"]), json={"active": False}).status_code == 403
        assert client.get("/setup/config", headers=_headers(alice["api_token"])).status_code == 401
        assert client.get("/setup/logs", headers=_headers(alice["api_token"])).status_code == 401
        assert client.get("/admin-api/mqtt/config", headers=_headers(alice["api_token"])).status_code == 403
        assert client.get("/sync/pull", headers=_headers(alice["api_token"])).status_code == 409
        assert client.post("/sync/push", headers=_headers(alice["api_token"]), json={"device_id": "test"}).status_code == 409
        assert client.get("/sync/pull", headers=_headers(alice["api_token"], bob["account_id"])).status_code == 403

        for account, quantity in ((alice, 2), (bob, 9)):
            response = client.post(
                "/sync/push", headers=_headers(account["api_token"], account["account_id"]),
                json={"device_id": "test", "components": [_component(quantity)]},
            )
            assert response.status_code == 200, response.text
        for account, quantity in ((alice, 2), (bob, 9)):
            headers = _headers(account["api_token"], account["account_id"])
            pulled = client.get("/sync/pull", headers=headers).json()
            assert pulled["components"][0]["quantity"] == quantity
            assert pulled["sync_cursor"] == 1
            detail = client.get("/admin-api/components/same-id", headers=headers).json()
            assert detail["quantity"] == quantity
            dashboard = client.get("/admin-api/dashboard", headers=headers).json()
            assert dashboard["metrics"]["total_units"] == quantity
            settings = client.get("/admin-api/settings", headers=headers).json()
            assert "Database path" not in str(settings)
            location = client.post(
                "/admin-api/storage-locations", headers=headers,
                json={"request_id": "same-location-request", "id": "A", "name": "Shelf A"},
            )
            assert location.status_code == 201, location.text
            created = client.post(
                "/admin-api/components", headers=headers,
                json={
                    "request_id": "same-component-request", "sku": "WEB-SKU",
                    "name": "Web part", "category": "Test", "package_name": "SMD",
                    "min_stock": 1, "location_id": "A",
                },
            )
            assert created.status_code == 201, created.text
        alice_headers = _headers(alice["api_token"], alice["account_id"])
        bob_headers = _headers(bob["api_token"], bob["account_id"])
        unique = _component(7) | {"id": "alice-only", "sku": "ALICE-ONLY"}
        assert client.post(
            "/sync/push", headers=alice_headers,
            json={"device_id": "test", "components": [unique]},
        ).status_code == 200
        assert client.get("/admin-api/components/alice-only", headers=bob_headers).status_code == 404
        guessed_update = {
            "request_id": "guess-alice-id", "expected_updated_at": unique["updated_at"],
            "sku": "ALICE-ONLY", "name": "Changed", "category": "Test",
            "package_name": "SMD", "description": "", "min_stock": 1,
        }
        assert client.put(
            "/admin-api/components/alice-only", headers=bob_headers, json=guessed_update,
        ).status_code == 404
        assert client.get("/admin-api/components/alice-only", headers=alice_headers).json()["name"] == "Part"
        assert client.get("/sync/pull", headers=ADMIN).json()["components"] == []
        assert client.get("/admin-api/components/same-id", headers=ADMIN).status_code == 404

        rotated = client.post(f"/admin-api/accounts/{alice['account_id']}/rotate-key", headers=ADMIN)
        assert rotated.status_code == 200
        assert client.get("/auth/me", headers=_headers(alice["api_token"])).status_code == 401
        new_token = rotated.json()["api_token"]
        assert client.get("/auth/me", headers=_headers(new_token)).status_code == 200
        disabled = client.patch(
            f"/admin-api/accounts/{alice['account_id']}", headers=ADMIN, json={"active": False},
        )
        assert disabled.status_code == 200
        assert client.get("/auth/me", headers=_headers(new_token)).status_code == 401
        assert client.get("/auth/me", headers=ADMIN).status_code == 200
    with TestClient(create_app()) as restarted:
        assert restarted.get("/auth/me", headers=_headers(bob["api_token"])).status_code == 200
        pulled = restarted.get(
            "/sync/pull", headers=_headers(bob["api_token"], bob["account_id"]),
        ).json()
        assert pulled["components"][0]["quantity"] == 9
        assert restarted.get("/auth/me", headers=_headers(new_token)).status_code == 401
    get_settings.cache_clear()


def test_non_ascii_token_returns_unauthorized_not_server_error(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "account-admin-test")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "inventory.db"))
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        assert authenticate(get_settings(), "é") is None
    get_settings.cache_clear()


def test_missing_account_database_never_recreated(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "account-admin-test")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "inventory.db"))
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        created = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "Alice"}).json()
    user_path = tmp_path / "users" / f"{created['account_id']}.db"
    user_path.unlink()
    with TestClient(create_app()) as restarted:
        assert restarted.get("/auth/me", headers=ADMIN).status_code == 200
        response = restarted.get("/auth/me", headers=_headers(created["api_token"]))
        assert response.status_code == 503
        assert "restore" in response.json()["detail"]
        assert not user_path.exists()
    get_settings.cache_clear()


def test_legacy_admin_data_and_duplicate_name_preserve_user_data(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "account-admin-test")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "inventory.db"))
    get_settings.cache_clear()
    settings = get_settings()
    init_db(settings, initialize_accounts=False)
    with sqlite3.connect(settings.database_path) as connection:
        connection.execute(
            "INSERT INTO components(id, sku, name, category, package_name, location, "
            "quantity, min_stock, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ("legacy", "LEGACY", "Old stock", "Test", "SMD", "Shelf", 11, 1, "2026-05-07T06:00:00Z"),
        )
    with TestClient(create_app()) as client:
        assert client.get("/admin-api/components/legacy", headers=ADMIN).json()["quantity"] == 11
        created = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "Alice"}).json()
        headers = _headers(created["api_token"], created["account_id"])
        assert client.post(
            "/sync/push", headers=headers,
            json={"device_id": "test", "components": [_component(5)]},
        ).status_code == 200
        duplicate = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "ALICE"})
        assert duplicate.status_code == 409
        assert client.get("/admin-api/components/same-id", headers=headers).json()["quantity"] == 5
        user_path = tmp_path / "users" / f"{created['account_id']}.db"
        assert user_path.is_file()
    get_settings.cache_clear()


def test_ordinary_writes_never_enqueue_admin_mqtt(tmp_path: Path, monkeypatch) -> None:
    from app.mqtt import MqttPublisher

    monkeypatch.setenv("API_TOKEN", "account-admin-test")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "inventory.db"))
    monkeypatch.setenv("WEB_INVENTORY_ENABLED", "true")
    monkeypatch.setenv("MQTT_ENABLED", "true")
    monkeypatch.setenv("MQTT_HOST", "localhost")
    monkeypatch.setattr(MqttPublisher, "start", lambda self: None)
    monkeypatch.setattr(MqttPublisher, "stop", lambda self: None)
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        created = client.post("/admin-api/accounts", headers=ADMIN, json={"name": "Alice"}).json()
        headers = _headers(created["api_token"], created["account_id"])
        assert client.post(
            "/sync/push", headers=headers,
            json={"device_id": "test", "components": [_component(5)]},
        ).status_code == 200
        assert client.post(
            "/admin-api/storage-locations", headers=headers,
            json={"request_id": "location-mqtt-request", "id": "A", "name": "Shelf A"},
        ).status_code == 201
        assert client.post(
            "/admin-api/components", headers=headers,
            json={"request_id": "component-mqtt-request", "sku": "WEB", "name": "Web part", "category": "Test", "package_name": "SMD", "min_stock": 1, "location_id": "A"},
        ).status_code == 201
    for path in (tmp_path / "inventory.db", tmp_path / "users" / f"{created['account_id']}.db"):
        with sqlite3.connect(path) as connection:
            assert connection.execute("SELECT COUNT(*) FROM mqtt_outbox").fetchone()[0] == 0
    get_settings.cache_clear()
