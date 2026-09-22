from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import sqlite3

from fastapi.testclient import TestClient

from app.admin.operations import record_stock_movement
from app.config import get_settings
from app.database import _connect, init_db
from app.main import create_app
from app.repositories import save_sync_payload
from app.schemas import AdminStockMovementCreate, PushRequest


HEADERS = {"Authorization": "Bearer test-token"}
LOCATION = {"request_id": "location-request-001", "id": "A", "name": "Shelf A"}
COMPONENT = {
    "request_id": "component-request-001",
    "sku": "WEB-001",
    "name": "Web component",
    "category": "Test",
    "package_name": "SMD",
    "description": "online",
    "min_stock": 2,
    "location_id": "A",
}


def _client(tmp_path: Path, monkeypatch, enabled: bool) -> TestClient:
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "sync.db"))
    monkeypatch.setenv("MQTT_ENABLED", "false")
    monkeypatch.setenv("WEB_INVENTORY_ENABLED", str(enabled).lower())
    get_settings.cache_clear()
    return TestClient(create_app())


def _create_location(client: TestClient, payload: dict | None = None):
    return client.post(
        "/admin-api/storage-locations",
        headers=HEADERS,
        json=payload or LOCATION,
    )


def _create_component(client: TestClient, payload: dict | None = None):
    return client.post(
        "/admin-api/components",
        headers=HEADERS,
        json=payload or COMPONENT,
    )


def _edit(component: dict, **changes) -> dict:
    payload = {
        key: value
        for key, value in COMPONENT.items()
        if key not in {"location_id", "request_id"}
    }
    payload.update(
        request_id="component-edit-001",
        expected_updated_at=component["updated_at"],
        **changes,
    )
    return payload


def _movement(component: dict, **changes) -> dict:
    payload = {
        "request_id": "movement-request-001",
        "expected_updated_at": component["updated_at"],
        "movement_type": "inbound",
        "quantity": 5,
        "reason": "delivery",
        "location_id": "A",
    }
    payload.update(changes)
    return payload


def test_writes_are_disabled_by_default_and_auth_is_preserved(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, False) as client:
        assert client.get("/admin-api/settings", headers=HEADERS).json()[
            "web_inventory_enabled"
        ] is False
        assert _create_location(client).status_code == 403
        assert client.post("/admin-api/storage-locations", json=LOCATION).status_code == 401


def test_empty_server_location_then_component_is_pull_visible(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        assert _create_location(client).status_code == 201
        created = _create_component(client)
        assert created.status_code == 201, created.text
        component = created.json()
        assert component["quantity"] == 0
        assert component["location"] == "A"
        assert component["inventory_managed"] is True
        assert component["allocations"] == [{"location_id": "A", "quantity": 0}]
        pulled = client.get("/sync/pull", headers=HEADERS).json()
        assert pulled["components"][0]["id"] == component["id"]
        assert pulled["components"][0]["allocations"] == component["allocations"]
        assert pulled["storage_locations"][0]["id"] == "A"


def test_long_location_name_uses_short_code_for_component_compatibility(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        location = {**LOCATION, "name": "仓" * 200}
        assert _create_location(client, location).status_code == 201
        created = _create_component(client)
        assert created.status_code == 201, created.text
        assert created.json()["location"] == "A"
        assert client.get(
            "/admin-api/storage-locations", headers=HEADERS
        ).json()[0]["name"] == "仓" * 200


def test_blank_or_padded_required_text_is_rejected_as_validation_error(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        for invalid in (
            {**LOCATION, "request_id": "blank-location-id", "id": "  "},
            {**LOCATION, "request_id": "padded-location-id", "id": " A "},
            {**LOCATION, "request_id": "blank-location-name", "name": "  "},
        ):
            assert _create_location(client, invalid).status_code == 422
        assert client.get(
            "/admin-api/storage-locations", headers=HEADERS
        ).json() == []

        assert _create_location(client).status_code == 201
        for field in ("sku", "name", "category", "package_name"):
            invalid = {
                **COMPONENT,
                "request_id": f"blank-component-{field}",
                field: "  ",
            }
            assert _create_component(client, invalid).status_code == 422
        component = _create_component(client).json()
        invalid_movement = _movement(
            component,
            request_id="blank-movement-reason",
            reason="  ",
        )
        assert client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=invalid_movement,
        ).status_code == 422


def test_location_and_component_idempotency_replays_current_state(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        first_location = _create_location(client)
        assert first_location.status_code == 201
        assert _create_location(client).status_code == 201
        assert _create_location(client, {**LOCATION, "name": "Different"}).status_code == 409

        first = _create_component(client)
        assert first.status_code == 201
        component = first.json()
        moved = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=_movement(component),
        )
        assert moved.status_code == 200, moved.text
        replay = _create_component(client)
        assert replay.status_code == 201
        assert replay.json()["quantity"] == 5
        assert _create_component(client, {**COMPONENT, "name": "Different"}).status_code == 409


def test_duplicate_location_creation_preserves_inventory(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        assert _create_location(client).status_code == 201
        component = _create_component(client).json()
        moved = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=_movement(component, quantity=15),
        )
        assert moved.status_code == 200, moved.text
        before = client.get("/sync/pull", headers=HEADERS).json()

        duplicate = _create_location(client, {
            **LOCATION, "request_id": "duplicate-location-new-request",
            "name": "Replacement shelf",
        })
        assert duplicate.status_code == 409
        after = client.get("/sync/pull", headers=HEADERS).json()
        for key in ("storage_locations", "components", "stock_movements"):
            assert after[key] == before[key]
        assert after["components"][0]["quantity"] == 15


def test_duplicate_location_creation_does_not_revive_deleted_location(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        assert _create_location(client).status_code == 201
        with _connect(tmp_path / "sync.db") as connection:
            connection.execute("UPDATE storage_locations SET deleted = 1 WHERE id = 'A'")
            before = dict(connection.execute(
                "SELECT * FROM storage_locations WHERE id = 'A'"
            ).fetchone())
        duplicate = _create_location(client, {
            **LOCATION, "request_id": "deleted-location-new-request",
            "name": "Replacement shelf",
        })
        assert duplicate.status_code == 409
        with _connect(tmp_path / "sync.db") as connection:
            after = dict(connection.execute(
                "SELECT * FROM storage_locations WHERE id = 'A'"
            ).fetchone())
        assert after == before


def test_edit_and_movement_retries_do_not_repeat_writes(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        _create_location(client)
        component = _create_component(client).json()
        edit_payload = _edit(component, name="Edited")
        edited = client.put(
            f"/admin-api/components/{component['id']}",
            headers=HEADERS,
            json=edit_payload,
        )
        assert edited.status_code == 200, edited.text
        movement_payload = _movement(edited.json())
        moved = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=movement_payload,
        )
        assert moved.status_code == 200, moved.text

        edit_replay = client.put(
            f"/admin-api/components/{component['id']}",
            headers=HEADERS,
            json=edit_payload,
        )
        movement_replay = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=movement_payload,
        )
        assert edit_replay.json()["quantity"] == 5
        assert movement_replay.json()["quantity"] == 5
        assert client.put(
            f"/admin-api/components/{component['id']}",
            headers=HEADERS,
            json={**edit_payload, "name": "Different"},
        ).status_code == 409
        assert client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json={**movement_payload, "quantity": 2},
        ).status_code == 409
        pulled = client.get("/sync/pull", headers=HEADERS).json()
        assert len(pulled["stock_movements"]) == 1


def test_versions_bounds_and_multiple_locations_are_atomic(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        _create_location(client)
        _create_location(
            client,
            {"request_id": "location-request-002", "id": "B", "name": "Shelf B"},
        )
        component = _create_component(client).json()
        assert client.put(
            f"/admin-api/components/{component['id']}",
            headers=HEADERS,
            json={**_edit(component), "quantity": 99},
        ).status_code == 422
        stale = component["updated_at"]
        first = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=_movement(component, quantity=4),
        )
        assert first.status_code == 200
        assert client.put(
            f"/admin-api/components/{component['id']}",
            headers=HEADERS,
            json={**_edit(first.json()), "expected_updated_at": stale},
        ).status_code == 409
        second_payload = _movement(
            first.json(),
            request_id="movement-request-002",
            quantity=3,
            location_id="B",
        )
        second = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=second_payload,
        )
        assert second.status_code == 200, second.text
        assert second.json()["allocations"] == [
            {"location_id": "A", "quantity": 4},
            {"location_id": "B", "quantity": 3},
        ]
        outbound = client.post(
            f"/admin-api/components/{component['id']}/movements",
            headers=HEADERS,
            json=_movement(
                second.json(),
                request_id="movement-request-003",
                movement_type="outbound",
                quantity=5,
                location_id="A",
            ),
        )
        assert outbound.status_code == 409
        current = client.get(
            f"/admin-api/components/{component['id']}", headers=HEADERS
        ).json()
        assert current["quantity"] == 7
        assert len(client.get("/sync/pull", headers=HEADERS).json()["stock_movements"]) == 2

        maximum = _create_component(
            client,
            {**COMPONENT, "request_id": "component-request-maximum", "sku": "MAX"},
        ).json()
        filled = client.post(
            f"/admin-api/components/{maximum['id']}/movements",
            headers=HEADERS,
            json=_movement(
                maximum,
                request_id="movement-request-maximum",
                quantity=2147483647,
            ),
        )
        assert filled.status_code == 200
        overflow = client.post(
            f"/admin-api/components/{maximum['id']}/movements",
            headers=HEADERS,
            json=_movement(
                filled.json(),
                request_id="movement-request-overflow",
                quantity=1,
            ),
        )
        assert overflow.status_code == 409


def test_legacy_edit_and_movements_preserve_original_location_model(
    tmp_path: Path, monkeypatch
) -> None:
    with _client(tmp_path, monkeypatch, True) as client:
        legacy = {
            "id": "legacy",
            "sku": "OLD",
            "name": "Old component",
            "category": "Legacy",
            "package_name": "DIP",
            "location": "old free text",
            "quantity": 7,
            "min_stock": 1,
            "updated_at": "2026-01-01T00:00:00Z",
        }
        assert client.post(
            "/sync/push",
            headers=HEADERS,
            json={"device_id": "old-client", "components": [legacy]},
        ).status_code == 200
        current = client.get("/admin-api/components/legacy", headers=HEADERS).json()
        assert current["inventory_managed"] is False
        edited = client.put(
            "/admin-api/components/legacy",
            headers=HEADERS,
            json={
                "request_id": "legacy-edit-001",
                "expected_updated_at": current["updated_at"],
                "sku": "OLD",
                "name": "Edited old component",
                "category": "Legacy",
                "package_name": "DIP",
                "description": None,
                "min_stock": 2,
            },
        )
        assert edited.status_code == 200, edited.text
        assert edited.json()["quantity"] == 7
        assert edited.json()["location"] == "old free text"
        assert edited.json()["allocations"] == []
        inbound_payload = {
            "request_id": "legacy-movement-001",
            "expected_updated_at": edited.json()["updated_at"],
            "movement_type": "inbound",
            "quantity": 2,
            "reason": "delivery",
        }
        inbound = client.post(
            "/admin-api/components/legacy/movements",
            headers=HEADERS,
            json=inbound_payload,
        )
        assert inbound.status_code == 200, inbound.text
        assert inbound.json()["quantity"] == 9
        assert inbound.json()["inventory_managed"] is False
        assert client.post(
            "/admin-api/components/legacy/movements",
            headers=HEADERS,
            json={
                **inbound_payload,
                "request_id": "legacy-movement-002",
                "expected_updated_at": inbound.json()["updated_at"],
                "location_id": "old free text",
            },
        ).status_code == 409
        pulled = client.get("/sync/pull", headers=HEADERS).json()
        assert pulled["components"][0]["allocations"] is None


def test_receipt_component_revision_movement_and_outbox_roll_back_together(
    tmp_path: Path, monkeypatch
) -> None:
    path = tmp_path / "atomic.db"
    monkeypatch.setenv("DATABASE_PATH", str(path))
    monkeypatch.setenv("API_TOKEN", "test-token")
    get_settings.cache_clear()
    settings = replace(
        get_settings(),
        mqtt_enabled=True,
        mqtt_topic_prefix="vault",
    )
    init_db(settings)
    connection = _connect(str(path))
    try:
        save_sync_payload(
            connection,
            PushRequest.model_validate(
                {
                    "device_id": "old",
                    "components": [
                        {
                            "id": "legacy",
                            "sku": "OLD",
                            "name": "Old",
                            "category": "Legacy",
                            "package_name": "DIP",
                            "location": "L",
                            "quantity": 2,
                            "min_stock": 0,
                            "updated_at": "2026-01-01T00:00:00Z",
                        }
                    ],
                }
            ),
        )
        before_revision = connection.execute(
            "SELECT current_revision FROM sync_state WHERE id=1"
        ).fetchone()[0]
        draft = AdminStockMovementCreate.model_validate(
            {
                "request_id": "rollback-movement-001",
                "expected_updated_at": "2026-01-01T00:00:00Z",
                "movement_type": "outbound",
                "quantity": 3,
                "reason": "too much",
            }
        )
        try:
            record_stock_movement(connection, settings, "legacy", draft)
        except ValueError:
            pass
        else:
            raise AssertionError("Expected movement failure")
        assert connection.execute(
            "SELECT quantity FROM components WHERE id='legacy'"
        ).fetchone()[0] == 2
        assert connection.execute("SELECT COUNT(*) FROM stock_movements").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM mqtt_outbox").fetchone()[0] == 0
        assert connection.execute(
            "SELECT COUNT(*) FROM admin_operation_receipts"
        ).fetchone()[0] == 0
        assert connection.execute(
            "SELECT current_revision FROM sync_state WHERE id=1"
        ).fetchone()[0] == before_revision

        success = AdminStockMovementCreate.model_validate(
            {
                "request_id": "success-movement-001",
                "expected_updated_at": "2026-01-01T00:00:00Z",
                "movement_type": "inbound",
                "quantity": 1,
                "reason": "delivery",
            }
        )
        first = record_stock_movement(connection, settings, "legacy", success)
        replay = record_stock_movement(connection, settings, "legacy", success)
        assert first["quantity"] == replay["quantity"] == 3
        assert connection.execute("SELECT COUNT(*) FROM stock_movements").fetchone()[0] == 1
        assert connection.execute("SELECT COUNT(*) FROM mqtt_outbox").fetchone()[0] == 1
        assert connection.execute(
            "SELECT COUNT(*) FROM admin_operation_receipts"
        ).fetchone()[0] == 1
    finally:
        connection.close()
        get_settings.cache_clear()


def test_failure_after_component_write_rolls_back_everything(
    tmp_path: Path, monkeypatch
) -> None:
    path = tmp_path / "late-failure.db"
    monkeypatch.setenv("DATABASE_PATH", str(path))
    get_settings.cache_clear()
    settings = replace(
        get_settings(),
        mqtt_enabled=True,
        mqtt_topic_prefix="vault",
    )
    init_db(settings)
    connection = _connect(str(path))
    try:
        save_sync_payload(
            connection,
            PushRequest.model_validate(
                {
                    "device_id": "old",
                    "components": [
                        {
                            "id": "legacy",
                            "sku": "OLD",
                            "name": "Old",
                            "category": "Legacy",
                            "package_name": "DIP",
                            "location": "L",
                            "quantity": 2,
                            "min_stock": 0,
                            "updated_at": "2026-01-01T00:00:00Z",
                        }
                    ],
                }
            ),
        )
        before_revision = connection.execute(
            "SELECT current_revision FROM sync_state WHERE id=1"
        ).fetchone()[0]

        def fail_enqueue(*_args, **_kwargs):
            raise RuntimeError("forced outbox failure")

        monkeypatch.setattr("app.mqtt.enqueue_component_state", fail_enqueue)
        draft = AdminStockMovementCreate.model_validate(
            {
                "request_id": "late-failure-movement",
                "expected_updated_at": "2026-01-01T00:00:00Z",
                "movement_type": "inbound",
                "quantity": 1,
                "reason": "delivery",
            }
        )
        try:
            record_stock_movement(connection, settings, "legacy", draft)
        except RuntimeError as error:
            assert "forced outbox failure" in str(error)
        else:
            raise AssertionError("Expected forced outbox failure")

        assert connection.execute(
            "SELECT quantity FROM components WHERE id='legacy'"
        ).fetchone()[0] == 2
        assert connection.execute("SELECT COUNT(*) FROM stock_movements").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM mqtt_outbox").fetchone()[0] == 0
        assert connection.execute(
            "SELECT COUNT(*) FROM admin_operation_receipts"
        ).fetchone()[0] == 0
        assert connection.execute(
            "SELECT current_revision FROM sync_state WHERE id=1"
        ).fetchone()[0] == before_revision
    finally:
        connection.close()
        get_settings.cache_clear()


def test_old_database_initialization_preserves_data_and_adds_receipts(
    tmp_path: Path, monkeypatch
) -> None:
    path = tmp_path / "old.db"
    with sqlite3.connect(path) as connection:
        connection.execute(
            """CREATE TABLE components (
                id TEXT PRIMARY KEY, sku TEXT, name TEXT, category TEXT,
                package_name TEXT, location TEXT, description TEXT,
                quantity INTEGER, min_stock INTEGER, updated_at TEXT, deleted INTEGER
            )"""
        )
        connection.execute(
            "INSERT INTO components VALUES "
            "('old','OLD','Old','Legacy','DIP','L',NULL,7,1,'2026-01-01T00:00:00Z',0)"
        )
    monkeypatch.setenv("DATABASE_PATH", str(path))
    get_settings.cache_clear()
    init_db(get_settings())
    with sqlite3.connect(path) as connection:
        assert connection.execute(
            "SELECT quantity, location, inventory_managed FROM components"
        ).fetchone() == (7, "L", 0)
        assert connection.execute(
            "SELECT COUNT(*) FROM admin_operation_receipts"
        ).fetchone()[0] == 0
    get_settings.cache_clear()
