from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app


TIME = "2026-09-15T00:00:00Z"
LATER = "2026-09-15T00:01:00Z"
HEADERS = {"Authorization": "Bearer test-token"}


@pytest.fixture
def client(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "storage.db"))
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("MQTT_ENABLED", "false")
    get_settings.cache_clear()
    with TestClient(create_app()) as value:
        yield value
    get_settings.cache_clear()


def component(**changes):
    result = {
        "id": "c1", "sku": "C70565", "name": "Test", "category": "IC",
        "package_name": "SOT23", "location": "A", "quantity": 10,
        "min_stock": 1, "updated_at": TIME, "deleted": False,
        "allocations": [{"location_id": "A", "quantity": 10}],
    }
    result.update(changes)
    return result


def push(client, item=None, movements=None, locations=None):
    return client.post("/sync/push", headers=HEADERS, json={
        "device_id": "test", "inventory_protocol": 1,
        "components": [] if item is None else [item],
        "stock_movements": movements or [],
        "storage_locations": locations if locations is not None else [
            {"id": code, "name": code, "updated_at": TIME, "deleted": False}
            for code in ("A", "B", "EMPTY")
        ],
    })


def snapshot(client, cursor=0):
    response = client.get(f"/sync/pull?cursor={cursor}", headers=HEADERS)
    assert response.status_code == 200, response.text
    return response.json()


def transfer(quantity=4):
    return {
        "id": "m1", "component_id": "c1", "movement_type": "transfer",
        "quantity": quantity, "reason": "Move", "happened_at": LATER,
        "updated_at": LATER, "location_id": "A", "destination_location_id": "B",
    }


def test_capabilities_and_empty_locations_roundtrip(client):
    assert client.get("/health").json()["inventory_protocol"] == 1
    assert client.post("/auth/ping", headers=HEADERS).json()["inventory_protocol"] == 1
    assert push(client, component()).status_code == 200
    data = snapshot(client)
    assert data["inventory_protocol"] == 1
    assert len(data["storage_locations"]) == 3
    assert data["components"][0]["allocations"] == [{"location_id": "A", "quantity": 10}]
    assert snapshot(client, data["sync_cursor"])["components"] == []
    assert len(snapshot(client, data["sync_cursor"])["storage_locations"]) == 3


def test_partial_transfer_is_atomic_conserves_total_and_retries(client):
    assert push(client, component()).status_code == 200
    moved = component(updated_at=LATER, base_updated_at=TIME, allocations=[
        {"location_id": "A", "quantity": 6}, {"location_id": "B", "quantity": 4},
    ])
    assert push(client, moved, [transfer()]).status_code == 200
    assert push(client, moved, [transfer()]).status_code == 200
    data = snapshot(client)
    assert data["components"][0]["quantity"] == 10
    assert [a["quantity"] for a in data["components"][0]["allocations"]] == [6, 4]
    assert len(data["stock_movements"]) == 1
    assert data["stock_movements"][0]["destination_location_id"] == "B"


def test_stale_offline_write_rejected_even_if_its_timestamp_is_later(client):
    assert push(client, component()).status_code == 200
    first = component(quantity=9, allocations=[{"location_id": "A", "quantity": 9}],
                      updated_at=LATER, base_updated_at=TIME)
    assert push(client, first).status_code == 200
    stale = deepcopy(first)
    stale.update(quantity=8, allocations=[{"location_id": "A", "quantity": 8}],
                 updated_at="2026-09-16T00:00:00Z")
    response = push(client, stale, locations=[{
        "id": "MUST-ROLLBACK", "name": "test", "updated_at": LATER,
    }])
    assert response.status_code == 409
    data = snapshot(client)
    assert data["components"][0]["quantity"] == 9
    assert "MUST-ROLLBACK" not in [x["id"] for x in data["storage_locations"]]


@pytest.mark.parametrize("allocations", [
    [], [{"location_id": "A", "quantity": 9}],
    [{"location_id": "A", "quantity": 5}, {"location_id": "A", "quantity": 5}],
    [{"location_id": "A", "quantity": -1}],
    [{"location_id": "A", "quantity": 10.5}],
    [{"location_id": "A", "quantity": True}],
])
def test_invalid_allocations_never_write(client, allocations):
    assert push(client, component(allocations=allocations)).status_code == 422
    assert snapshot(client)["components"] == []
    assert snapshot(client)["storage_locations"] == []


def test_orphan_locations_and_invalid_transfer_roll_back(client):
    assert push(client, component(allocations=[{"location_id": "NO", "quantity": 10}])).status_code == 409
    assert snapshot(client)["storage_locations"] == []
    assert push(client, component()).status_code == 200
    moved = component(updated_at=LATER, base_updated_at=TIME, allocations=[
        {"location_id": "A", "quantity": 1}, {"location_id": "B", "quantity": 9},
    ])
    invalid = transfer()
    invalid["destination_location_id"] = "UNKNOWN"
    assert push(client, moved, [invalid]).status_code == 409
    assert snapshot(client)["stock_movements"] == []
    assert snapshot(client)["components"][0]["updated_at"] == TIME


def test_coalesced_manual_stock_edit_and_transfer_is_valid_snapshot(client):
    assert push(client, component()).status_code == 200
    edited = component(quantity=15, updated_at=LATER, base_updated_at=TIME, allocations=[
        {"location_id": "A", "quantity": 11}, {"location_id": "B", "quantity": 4},
    ])
    assert push(client, edited, [transfer()]).status_code == 200
    assert snapshot(client)["components"][0]["quantity"] == 15


def test_legacy_client_cannot_overwrite_managed_stock(client):
    assert push(client, component()).status_code == 200
    legacy = component(quantity=500, updated_at=LATER)
    legacy.pop("allocations")
    response = client.post("/sync/push", headers=HEADERS, json={
        "device_id": "old", "components": [legacy],
    })
    assert response.status_code == 409
    assert snapshot(client)["components"][0]["quantity"] == 10


def test_assigned_location_cannot_be_deleted(client):
    assert push(client, component()).status_code == 200
    assert push(client, locations=[{
        "id": "A", "name": "A", "updated_at": LATER, "deleted": True,
    }]).status_code == 409
    assert not snapshot(client)["storage_locations"][0]["deleted"]
    assert push(client, locations=[{
        "id": "EMPTY", "name": "Empty", "updated_at": LATER, "deleted": True,
    }]).status_code == 200


def test_allocation_only_update_needs_new_timestamp(client):
    assert push(client, component()).status_code == 200
    same_version = component(base_updated_at=TIME, allocations=[
        {"location_id": "B", "quantity": 10},
    ])
    assert push(client, same_version).status_code == 409


def test_old_database_upgrade_keeps_snapshot_backup(tmp_path, monkeypatch):
    from app.database import init_db

    path = tmp_path / "old.db"
    with sqlite3.connect(path) as db:
        db.execute("""CREATE TABLE components (
            id TEXT PRIMARY KEY, sku TEXT, name TEXT, category TEXT,
            package_name TEXT, location TEXT, description TEXT,
            quantity INTEGER, min_stock INTEGER, updated_at TEXT, deleted INTEGER
        )""")
        db.execute("INSERT INTO components VALUES ('old','OLD','Old','IC','DIP','A',NULL,7,1,?,0)", (TIME,))
    monkeypatch.setenv("DATABASE_PATH", str(path))
    get_settings.cache_clear()
    init_db(get_settings())
    backup = path.with_name(path.name + ".pre-inventory-v1.bak")
    assert backup.exists()
    with sqlite3.connect(backup) as db:
        assert db.execute("SELECT quantity FROM components").fetchone()[0] == 7
        assert "inventory_managed" not in [row[1] for row in db.execute("PRAGMA table_info(components)")]
    init_db(get_settings())
    get_settings.cache_clear()


def test_location_allocation_and_cursor_share_read_snapshot(tmp_path, monkeypatch):
    from app.database import _connect, init_db
    from app.repositories import pull_sync_snapshot, save_sync_payload
    from app.schemas import PushRequest

    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "concurrent.db"))
    get_settings.cache_clear()
    settings = get_settings()
    init_db(settings)
    reader = _connect(settings.database_path)
    writer = _connect(settings.database_path)
    injected = False

    def write_after_cursor(statement):
        nonlocal injected
        if not injected and statement.startswith("SELECT * FROM components"):
            injected = True
            save_sync_payload(writer, PushRequest.model_validate({
                "device_id": "writer", "inventory_protocol": 1,
                "storage_locations": [{"id": "A", "name": "A", "updated_at": TIME}],
                "components": [component()],
            }))

    reader.set_trace_callback(write_after_cursor)
    first_locations = []
    next_locations = []
    try:
        cursor, first, _ = pull_sync_snapshot(reader, cursor=0, since=None, locations_out=first_locations)
        reader.set_trace_callback(None)
        _, second, _ = pull_sync_snapshot(reader, cursor=cursor, since=None, locations_out=next_locations)
    finally:
        reader.close()
        writer.close()
        get_settings.cache_clear()
    assert injected and not first and not first_locations
    assert second[0].allocations[0].quantity == 10
    assert next_locations[0].id == "A"
