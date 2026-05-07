from __future__ import annotations

from datetime import datetime, timezone
import sqlite3

from .schemas import ComponentPayload, StockMovementPayload


def save_components(
    connection: sqlite3.Connection,
    components: list[ComponentPayload],
) -> int:
    accepted = 0
    for component in components:
        try:
            accepted += int(_upsert_component(connection, component))
        except sqlite3.IntegrityError as error:
            raise ValueError(
                'An active component with the same SKU already exists.',
            ) from error
    connection.commit()
    return accepted


def save_stock_movements(
    connection: sqlite3.Connection,
    stock_movements: list[StockMovementPayload],
) -> int:
    accepted = 0
    for stock_movement in stock_movements:
        accepted += int(_upsert_stock_movement(connection, stock_movement))
    connection.commit()
    return accepted


def pull_components(
    connection: sqlite3.Connection,
    since: datetime | None,
) -> list[ComponentPayload]:
    parameters: tuple[object, ...] = ()
    query = "SELECT * FROM components"
    if since is not None:
        query += " WHERE updated_at > ?"
        parameters = (_to_storage_time(since),)
    query += " ORDER BY updated_at ASC"

    rows = connection.execute(query, parameters).fetchall()
    return [_row_to_component(row) for row in rows]


def pull_stock_movements(
    connection: sqlite3.Connection,
    since: datetime | None,
) -> list[StockMovementPayload]:
    parameters: tuple[object, ...] = ()
    query = "SELECT * FROM stock_movements"
    if since is not None:
        query += " WHERE updated_at > ?"
        parameters = (_to_storage_time(since),)
    query += " ORDER BY updated_at ASC"

    rows = connection.execute(query, parameters).fetchall()
    return [_row_to_stock_movement(row) for row in rows]


def _upsert_component(
    connection: sqlite3.Connection,
    component: ComponentPayload,
) -> bool:
    new_updated_at = _to_storage_time(component.updated_at)
    existing = connection.execute(
        "SELECT updated_at FROM components WHERE id = ?",
        (component.id,),
    ).fetchone()
    if existing and existing["updated_at"] > new_updated_at:
        return False

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
            deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            deleted = excluded.deleted
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
    if existing and existing["updated_at"] > new_updated_at:
        return False

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
            deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            component_id = excluded.component_id,
            movement_type = excluded.movement_type,
            quantity = excluded.quantity,
            reason = excluded.reason,
            note = excluded.note,
            happened_at = excluded.happened_at,
            updated_at = excluded.updated_at,
            deleted = excluded.deleted
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
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
