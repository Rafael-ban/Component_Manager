from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import sqlite3

from fastapi.testclient import TestClient

from app.config import get_settings
from app.database import _connect, init_db
from app.main import create_app


AUTH = {"Authorization": "Bearer test-token"}


def test_cursor_delivers_late_offline_write(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        initial = client.get("/sync/pull", headers=AUTH, params={"cursor": 0})
        cursor = initial.json()["sync_cursor"]
        pushed = client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "offline-device",
                "components": [_component(updated_at="2020-01-01T00:00:00Z")],
            },
        )
        pulled = client.get(
            "/sync/pull", headers=AUTH, params={"cursor": cursor}
        )

    assert pushed.status_code == 200
    assert [item["id"] for item in pulled.json()["components"]] == ["cmp-1"]
    assert pulled.json()["sync_cursor"] > cursor


def test_lww_compares_instants_with_mixed_precision(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "a",
                "components": [
                    _component(name="half second", updated_at="2026-01-01T00:00:00.5Z")
                ],
            },
        )
        response = client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "b",
                "components": [
                    _component(name="whole second", updated_at="2026-01-01T00:00:00Z")
                ],
            },
        )
        pulled = client.get("/sync/pull", headers=AUTH, params={"cursor": 0})

    assert response.json()["accepted_components"] == 0
    assert pulled.json()["components"][0]["name"] == "half second"


def test_legacy_since_compares_instants_with_mixed_precision(
    tmp_path: Path, monkeypatch
) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "a",
                "components": [
                    _component(updated_at="2026-01-01T00:00:00.5Z")
                ],
            },
        )
        response = client.get(
            "/sync/pull",
            headers=AUTH,
            params={"since": "2026-01-01T00:00:00Z"},
        )

    assert [item["id"] for item in response.json()["components"]] == ["cmp-1"]


def test_foreign_key_rejects_orphan_movement(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        response = client.post(
            "/sync/push",
            headers=AUTH,
            json={"device_id": "a", "stock_movements": [_movement()]},
        )

    assert response.status_code == 409
    assert "does not exist" in response.json()["detail"]


def test_failed_push_rolls_back_all_rows(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        response = client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "a",
                "components": [_component()],
                "stock_movements": [_movement(component_id="missing")],
            },
        )
        pulled = client.get("/sync/pull", headers=AUTH, params={"cursor": 0})

    assert response.status_code == 409
    assert pulled.json()["components"] == []
    assert pulled.json()["stock_movements"] == []
    assert pulled.json()["sync_cursor"] == 0


def test_old_database_migrates_with_existing_orphan(tmp_path: Path) -> None:
    database_path = tmp_path / "legacy.db"
    connection = sqlite3.connect(database_path)
    connection.executescript(
        """
        CREATE TABLE components (
            id TEXT PRIMARY KEY, sku TEXT NOT NULL, name TEXT NOT NULL,
            category TEXT NOT NULL, package_name TEXT NOT NULL,
            location TEXT NOT NULL, description TEXT, quantity INTEGER NOT NULL,
            min_stock INTEGER NOT NULL, updated_at TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE stock_movements (
            id TEXT PRIMARY KEY, component_id TEXT NOT NULL,
            movement_type TEXT NOT NULL, quantity INTEGER NOT NULL,
            reason TEXT NOT NULL, note TEXT, happened_at TEXT NOT NULL,
            updated_at TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (component_id) REFERENCES components(id)
        );
        INSERT INTO stock_movements VALUES (
            'legacy-orphan', 'missing', 'inbound', 1, 'legacy', NULL,
            '2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z', 0
        );
        """
    )
    connection.commit()
    connection.close()

    init_db(replace(get_settings(), database_path=str(database_path)))

    migrated = _connect(str(database_path))
    revision = migrated.execute(
        "SELECT sync_revision FROM stock_movements WHERE id = 'legacy-orphan'"
    ).fetchone()[0]
    foreign_keys = migrated.execute("PRAGMA foreign_keys").fetchone()[0]
    migrated.close()
    assert revision == 1
    assert foreign_keys == 1


def test_cursor_marks_a_closed_snapshot_boundary(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        client.post(
            "/sync/push",
            headers=AUTH,
            json={"device_id": "a", "components": [_component(id="cmp-1")]},
        )
        first = client.get("/sync/pull", headers=AUTH, params={"cursor": 0}).json()
        client.post(
            "/sync/push",
            headers=AUTH,
            json={
                "device_id": "a",
                "components": [_component(id="cmp-2", sku="SKU-2")],
            },
        )
        second = client.get(
            "/sync/pull", headers=AUTH, params={"cursor": first["sync_cursor"]}
        ).json()
        empty = client.get(
            "/sync/pull", headers=AUTH, params={"cursor": second["sync_cursor"]}
        ).json()

    assert [item["id"] for item in first["components"]] == ["cmp-1"]
    assert [item["id"] for item in second["components"]] == ["cmp-2"]
    assert empty["components"] == []


def _configure(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "sync.db"))
    get_settings.cache_clear()


def test_equal_timestamp_preserves_existing_last_arrival_policy(tmp_path: Path, monkeypatch) -> None:
    _configure(tmp_path, monkeypatch)
    with TestClient(create_app()) as client:
        for quantity in (1, 2):
            response = client.post(
                "/sync/push", headers=AUTH,
                json={"device_id": "a", "components": [_component(quantity=quantity)]},
            )
            assert response.status_code == 200
        pulled = client.get("/sync/pull", headers=AUTH, params={"cursor": 0}).json()
    assert pulled["components"][0]["quantity"] == 2


def test_write_during_pull_is_deferred_to_next_cursor(tmp_path: Path, monkeypatch) -> None:
    from app.repositories import pull_sync_snapshot, save_sync_payload
    from app.schemas import PushRequest

    _configure(tmp_path, monkeypatch)
    settings = get_settings()
    init_db(settings)
    reader = _connect(settings.database_path)
    writer = _connect(settings.database_path)
    injected = False

    def write_between_cursor_and_rows(statement: str) -> None:
        nonlocal injected
        if not injected and statement.startswith("SELECT * FROM components"):
            injected = True
            save_sync_payload(writer, PushRequest.model_validate({
                "device_id": "late", "components": [_component()],
            }))

    reader.set_trace_callback(write_between_cursor_and_rows)
    try:
        first_cursor, first_rows, _ = pull_sync_snapshot(reader, cursor=0, since=None)
        reader.set_trace_callback(None)
        next_cursor, next_rows, _ = pull_sync_snapshot(reader, cursor=first_cursor, since=None)
    finally:
        reader.close()
        writer.close()
    assert injected
    assert first_cursor == 0 and first_rows == []
    assert next_cursor > first_cursor
    assert [row.id for row in next_rows] == ["cmp-1"]


def test_sync_accepts_legacy_name_longer_than_200_characters(
    tmp_path: Path, monkeypatch,
) -> None:
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "sync.db"))
    get_settings.cache_clear()
    legacy_name = "Long imported English description. " * 20
    with TestClient(create_app()) as client:
        pushed = client.post(
            "/sync/push", headers=AUTH,
            json={"device_id": "legacy-import", "components": [_component(name=legacy_name)]},
        )
        pulled = client.get("/sync/pull", headers=AUTH)
    assert pushed.status_code == 200, pushed.text
    assert pulled.json()["components"][0]["name"] == legacy_name


def _component(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": "cmp-1",
        "sku": "SKU-1",
        "name": "Part",
        "category": "IC",
        "package_name": "QFN",
        "location": "A1",
        "quantity": 1,
        "min_stock": 0,
        "updated_at": "2026-01-01T00:00:00Z",
    }
    payload.update(overrides)
    return payload


def _movement(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": "mov-1",
        "component_id": "cmp-1",
        "movement_type": "inbound",
        "quantity": 1,
        "reason": "stock",
        "happened_at": "2026-01-01T00:00:00Z",
        "updated_at": "2026-01-01T00:00:00Z",
    }
    payload.update(overrides)
    return payload
