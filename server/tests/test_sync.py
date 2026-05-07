from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app


def test_health_endpoint() -> None:
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_admin_ui_mounts(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    with TestClient(app) as client:
        response = client.get("/admin/")

    assert response.status_code == 200
    assert "Component Vault" in response.text
    assert "Admin Console" in response.text


def test_sync_requires_token(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    with TestClient(app) as client:
        response = client.post("/sync/push", json={"device_id": "desktop-1"})

    assert response.status_code == 401


def test_push_then_pull_round_trip(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    component = _component_payload()
    stock_movement = _movement_payload()

    with TestClient(app) as client:
        push_response = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={
                "device_id": "desktop-1",
                "components": [component],
                "stock_movements": [stock_movement],
            },
        )
        pull_response = client.get(
            "/sync/pull",
            headers={"Authorization": "Bearer test-token"},
            params={"since": "2026-05-07T00:00:00Z"},
        )

    assert push_response.status_code == 200
    assert pull_response.status_code == 200
    body = pull_response.json()
    assert len(body["components"]) == 1
    assert len(body["stock_movements"]) == 1
    assert body["components"][0]["sku"] == "ATMEGA328P-AU"


def test_older_component_push_is_ignored(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    newer_component = _component_payload(
        quantity=24,
        updated_at="2026-05-07T06:00:00Z",
    )
    older_component = _component_payload(
        name="Older MCU name",
        quantity=3,
        updated_at="2026-05-07T05:00:00Z",
    )

    with TestClient(app) as client:
        first_push = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [newer_component]},
        )
        second_push = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [older_component]},
        )
        pull_response = client.get(
            "/sync/pull",
            headers={"Authorization": "Bearer test-token"},
        )

    assert first_push.status_code == 200
    assert second_push.status_code == 200
    body = pull_response.json()
    assert body["components"][0]["name"] == "MCU"
    assert body["components"][0]["quantity"] == 24


def test_duplicate_active_sku_returns_conflict(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    first_component = _component_payload(id="cmp-1", sku="NE555P")
    conflicting_component = _component_payload(id="cmp-2", sku="NE555P")

    with TestClient(app) as client:
        first_push = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [first_component]},
        )
        second_push = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [conflicting_component]},
        )

    assert first_push.status_code == 200
    assert second_push.status_code == 409


def test_soft_deleted_component_is_returned_in_pull(tmp_path: Path, monkeypatch) -> None:
    _configure_env(tmp_path, monkeypatch)

    active_component = _component_payload(updated_at="2026-05-07T06:00:00Z")
    deleted_component = _component_payload(
        updated_at="2026-05-07T07:00:00Z",
        deleted=True,
    )

    with TestClient(app) as client:
        client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [active_component]},
        )
        delete_response = client.post(
            "/sync/push",
            headers={"Authorization": "Bearer test-token"},
            json={"device_id": "desktop-1", "components": [deleted_component]},
        )
        pull_response = client.get(
            "/sync/pull",
            headers={"Authorization": "Bearer test-token"},
            params={"since": "2026-05-07T06:30:00Z"},
        )

    assert delete_response.status_code == 200
    body = pull_response.json()
    assert len(body["components"]) == 1
    assert body["components"][0]["deleted"] is True


def _configure_env(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "sync.db"))
    get_settings.cache_clear()


def _component_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": "cmp-1",
        "sku": "ATMEGA328P-AU",
        "name": "MCU",
        "category": "Microcontroller",
        "package_name": "TQFP-32",
        "location": "Drawer A1",
        "description": "Main controller",
        "quantity": 24,
        "min_stock": 5,
        "updated_at": "2026-05-07T06:00:00Z",
        "deleted": False,
    }
    payload.update(overrides)
    return payload


def _movement_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": "mov-1",
        "component_id": "cmp-1",
        "movement_type": "inbound",
        "quantity": 24,
        "reason": "Initial stock",
        "note": "",
        "happened_at": "2026-05-07T06:00:00Z",
        "updated_at": "2026-05-07T06:00:00Z",
        "deleted": False,
    }
    payload.update(overrides)
    return payload
