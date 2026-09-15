from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import sqlite3

from ..config import Settings


@dataclass(frozen=True)
class AdminSnapshot:
    component_count: int
    total_units: int
    low_stock_count: int
    movement_count: int
    low_stock_components: list[dict[str, object]]
    recent_components: list[dict[str, object]]
    recent_movements: list[dict[str, object]]
    sync_notes: list[str]


def load_admin_snapshot(settings: Settings) -> AdminSnapshot:
    database_path = Path(settings.database_path)
    if not database_path.exists():
        return AdminSnapshot(
            component_count=0,
            total_units=0,
            low_stock_count=0,
            movement_count=0,
            low_stock_components=[],
            recent_components=[],
            recent_movements=[],
            sync_notes=[
                'Database file has not been created yet.',
                'Run the API once or push data from a client to populate records.',
            ],
        )

    connection = sqlite3.connect(str(database_path))
    connection.row_factory = sqlite3.Row
    try:
        aggregate = connection.execute(
            """
            SELECT
                COUNT(*) AS component_count,
                COALESCE(SUM(quantity), 0) AS total_units,
                COALESCE(SUM(CASE WHEN deleted = 0 AND quantity <= min_stock THEN 1 ELSE 0 END), 0)
                    AS low_stock_count
            FROM components
            WHERE deleted = 0
            """
        ).fetchone()
        movement_row = connection.execute(
            "SELECT COUNT(*) AS movement_count FROM stock_movements WHERE deleted = 0"
        ).fetchone()
        low_stock_rows = connection.execute(
            """
            SELECT
                id,
                sku,
                name,
                location,
                quantity,
                min_stock,
                updated_at
            FROM components
            WHERE deleted = 0 AND quantity <= min_stock
            ORDER BY updated_at DESC
            LIMIT 8
            """
        ).fetchall()
        component_rows = connection.execute(
            """
            SELECT
                id,
                sku,
                name,
                category,
                package_name,
                location,
                quantity,
                min_stock,
                updated_at,
                CASE
                    WHEN quantity <= min_stock THEN 'Low stock'
                    ELSE 'Healthy'
                END AS status
            FROM components
            WHERE deleted = 0
            ORDER BY updated_at DESC
            LIMIT 12
            """
        ).fetchall()
        movement_rows = connection.execute(
            """
            SELECT
                m.id,
                COALESCE(c.sku, m.component_id) AS sku,
                COALESCE(c.name, m.component_id) AS component_name,
                m.movement_type,
                m.quantity,
                m.reason,
                m.note,
                m.happened_at,
                m.updated_at
            FROM stock_movements m
            LEFT JOIN components c ON c.id = m.component_id
            WHERE m.deleted = 0
            ORDER BY m.happened_at DESC
            LIMIT 12
            """
        ).fetchall()
    finally:
        connection.close()

    sync_notes = [
        f'API health endpoint remains available at http://{settings.app_host}:{settings.app_port}/health.',
        'Managed inventory uses base_updated_at conflict checks; legacy snapshots use last-write-wins.',
        'Device registry is not implemented yet; sync visibility is derived from server-side inventory state.',
    ]
    if settings.api_token == 'change-me':
        sync_notes.insert(0, 'API token is still the default value. Replace it before deployment.')

    return AdminSnapshot(
        component_count=int(aggregate['component_count']),
        total_units=int(aggregate['total_units']),
        low_stock_count=int(aggregate['low_stock_count']),
        movement_count=int(movement_row['movement_count']),
        low_stock_components=[dict(row) for row in low_stock_rows],
        recent_components=[dict(row) for row in component_rows],
        recent_movements=[dict(row) for row in movement_rows],
        sync_notes=sync_notes,
    )
