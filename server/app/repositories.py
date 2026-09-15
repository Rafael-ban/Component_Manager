from __future__ import annotations

from datetime import datetime, timezone
import sqlite3

from .schemas import ComponentPayload, PushRequest, StockMovementPayload


def save_components(
    connection: sqlite3.Connection,
    components: list[ComponentPayload],
    *,
    mqtt_topic_prefix: str | None = None,
) -> int:
    accepted = 0
    for component in components:
        try:
            was_accepted = _upsert_component(connection, component)
            accepted += int(was_accepted)
            if was_accepted and mqtt_topic_prefix is not None:
                from .mqtt import enqueue_component_state

                enqueue_component_state(connection, component.id, mqtt_topic_prefix)
        except sqlite3.IntegrityError as error:
            raise ValueError(
                'An active component with the same SKU already exists.',
            ) from error
    return accepted


def save_stock_movements(
    connection: sqlite3.Connection,
    stock_movements: list[StockMovementPayload],
) -> int:
    accepted = 0
    for stock_movement in stock_movements:
        accepted += int(_upsert_stock_movement(connection, stock_movement))
    return accepted


def save_sync_payload(
    connection: sqlite3.Connection,
    payload: PushRequest,
    *,
    mqtt_topic_prefix: str | None = None,
) -> tuple[int, int]:
    """Save an entire push in one transaction so retries see no partial state."""
    try:
        with connection:
            # Serialize the LWW read/check/write as well as revision allocation.
            connection.execute("BEGIN IMMEDIATE")
            accepted_components = save_components(
                connection,
                payload.components,
                mqtt_topic_prefix=mqtt_topic_prefix,
            )
            accepted_stock_movements = save_stock_movements(
                connection,
                payload.stock_movements,
            )
    except sqlite3.IntegrityError as error:
        raise ValueError(_integrity_error_message(error)) from error
    return accepted_components, accepted_stock_movements


def pull_sync_snapshot(
    connection: sqlite3.Connection,
    *,
    cursor: int | None,
    since: datetime | None,
) -> tuple[int, list[ComponentPayload], list[StockMovementPayload]]:
    """Read both entity types and the returned cursor from one DB snapshot."""
    connection.execute("BEGIN")
    try:
        sync_cursor = int(
            connection.execute(
                "SELECT current_revision FROM sync_state WHERE id = 1"
            ).fetchone()[0]
        )
        if cursor is not None:
            component_rows = connection.execute(
                "SELECT * FROM components "
                "WHERE sync_revision > ? AND sync_revision <= ? "
                "ORDER BY sync_revision ASC",
                (cursor, sync_cursor),
            ).fetchall()
            movement_rows = connection.execute(
                "SELECT * FROM stock_movements "
                "WHERE sync_revision > ? AND sync_revision <= ? "
                "ORDER BY sync_revision ASC",
                (cursor, sync_cursor),
            ).fetchall()
        else:
            component_rows = connection.execute("SELECT * FROM components").fetchall()
            movement_rows = connection.execute(
                "SELECT * FROM stock_movements"
            ).fetchall()
    finally:
        connection.rollback()

    components = [_row_to_component(row) for row in component_rows]
    movements = [_row_to_stock_movement(row) for row in movement_rows]
    if cursor is None and since is not None:
        since_utc = _as_utc(since)
        components = [
            item for item in components if _as_utc(item.updated_at) > since_utc
        ]
        movements = [
            item for item in movements if _as_utc(item.updated_at) > since_utc
        ]
    return sync_cursor, components, movements


def _upsert_component(
    connection: sqlite3.Connection,
    component: ComponentPayload,
) -> bool:
    new_updated_at = _to_storage_time(component.updated_at)
    existing = connection.execute(
        "SELECT updated_at FROM components WHERE id = ?",
        (component.id,),
    ).fetchone()
    if existing and _parse_storage_time(existing["updated_at"]) > _as_utc(
        component.updated_at
    ):
        return False

    sync_revision = _next_sync_revision(connection)

    connection.execute(
        """
        INSERT INTO components (
            id,
            sku,
            name,
            category,
            package_name,
            location,
            description,
            quantity,
            min_stock,
            updated_at,
            deleted,
            sync_revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            sku = excluded.sku,
            name = excluded.name,
            category = excluded.category,
            package_name = excluded.package_name,
            location = excluded.location,
            description = excluded.description,
            quantity = excluded.quantity,
            min_stock = excluded.min_stock,
            updated_at = excluded.updated_at,
            deleted = excluded.deleted,
            sync_revision = excluded.sync_revision
        """,
        (
            component.id,
            component.sku,
            component.name,
            component.category,
            component.package_name,
            component.location,
            component.description,
            component.quantity,
            component.min_stock,
            new_updated_at,
            int(component.deleted),
            sync_revision,
        ),
    )
    return True


def _upsert_stock_movement(
    connection: sqlite3.Connection,
    stock_movement: StockMovementPayload,
) -> bool:
    new_updated_at = _to_storage_time(stock_movement.updated_at)
    existing = connection.execute(
        "SELECT updated_at FROM stock_movements WHERE id = ?",
        (stock_movement.id,),
    ).fetchone()
    if existing and _parse_storage_time(existing["updated_at"]) > _as_utc(
        stock_movement.updated_at
    ):
        return False

    sync_revision = _next_sync_revision(connection)

    connection.execute(
        """
        INSERT INTO stock_movements (
            id,
            component_id,
            movement_type,
            quantity,
            reason,
            note,
            happened_at,
            updated_at,
            deleted,
            sync_revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            component_id = excluded.component_id,
            movement_type = excluded.movement_type,
            quantity = excluded.quantity,
            reason = excluded.reason,
            note = excluded.note,
            happened_at = excluded.happened_at,
            updated_at = excluded.updated_at,
            deleted = excluded.deleted,
            sync_revision = excluded.sync_revision
        """,
        (
            stock_movement.id,
            stock_movement.component_id,
            stock_movement.movement_type,
            stock_movement.quantity,
            stock_movement.reason,
            stock_movement.note,
            _to_storage_time(stock_movement.happened_at),
            new_updated_at,
            int(stock_movement.deleted),
            sync_revision,
        ),
    )
    return True


def _row_to_component(row: sqlite3.Row) -> ComponentPayload:
    return ComponentPayload.model_validate(
        {
            "id": row["id"],
            "sku": row["sku"],
            "name": row["name"],
            "category": row["category"],
            "package_name": row["package_name"],
            "location": row["location"],
            "description": row["description"],
            "quantity": row["quantity"],
            "min_stock": row["min_stock"],
            "updated_at": row["updated_at"],
            "deleted": bool(row["deleted"]),
        }
    )


def _row_to_stock_movement(row: sqlite3.Row) -> StockMovementPayload:
    return StockMovementPayload.model_validate(
        {
            "id": row["id"],
            "component_id": row["component_id"],
            "movement_type": row["movement_type"],
            "quantity": row["quantity"],
            "reason": row["reason"],
            "note": row["note"],
            "happened_at": row["happened_at"],
            "updated_at": row["updated_at"],
            "deleted": bool(row["deleted"]),
        }
    )


def _to_storage_time(value: datetime) -> str:
    return _as_utc(value).isoformat(timespec="microseconds").replace("+00:00", "Z")


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _parse_storage_time(value: str) -> datetime:
    return _as_utc(datetime.fromisoformat(value.replace("Z", "+00:00")))


def _next_sync_revision(connection: sqlite3.Connection) -> int:
    connection.execute(
        "UPDATE sync_state SET current_revision = current_revision + 1 WHERE id = 1"
    )
    return int(
        connection.execute(
            "SELECT current_revision FROM sync_state WHERE id = 1"
        ).fetchone()[0]
    )


def _integrity_error_message(error: sqlite3.IntegrityError) -> str:
    message = str(error).lower()
    if "foreign key" in message:
        return "A stock movement references a component that does not exist."
    if "sku" in message or "unique" in message:
        return "An active component with the same SKU already exists."
    return "The sync payload violates a database constraint."
