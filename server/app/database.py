from __future__ import annotations

from collections.abc import Generator
from pathlib import Path
import sqlite3

from fastapi import Depends

from .config import Settings, get_settings


SCHEMA_STATEMENTS = (
    """
    CREATE TABLE IF NOT EXISTS components (
        id TEXT PRIMARY KEY,
        sku TEXT NOT NULL,
        name TEXT NOT NULL,
        category TEXT NOT NULL,
        package_name TEXT NOT NULL,
        location TEXT NOT NULL,
        description TEXT,
        quantity INTEGER NOT NULL CHECK (quantity >= 0),
        min_stock INTEGER NOT NULL CHECK (min_stock >= 0),
        updated_at TEXT NOT NULL,
        deleted INTEGER NOT NULL DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_components_updated_at ON components(updated_at)",
    """
    CREATE UNIQUE INDEX IF NOT EXISTS idx_components_sku_active
    ON components(sku)
    WHERE deleted = 0
    """,
    """
    CREATE TABLE IF NOT EXISTS stock_movements (
        id TEXT PRIMARY KEY,
        component_id TEXT NOT NULL,
        movement_type TEXT NOT NULL,
        quantity INTEGER NOT NULL,
        reason TEXT NOT NULL,
        note TEXT,
        happened_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        deleted INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (component_id) REFERENCES components(id)
    )
    """,
    """
    CREATE INDEX IF NOT EXISTS idx_stock_movements_updated_at
    ON stock_movements(updated_at)
    """,
)


def _connect(database_path: str) -> sqlite3.Connection:
    connection = sqlite3.connect(database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    return connection


def init_db(settings: Settings) -> None:
    database_path = Path(settings.database_path)
    database_path.parent.mkdir(parents=True, exist_ok=True)

    connection = _connect(str(database_path))
    try:
        connection.execute("PRAGMA journal_mode = WAL")
        connection.execute("PRAGMA foreign_keys = ON")
        for statement in SCHEMA_STATEMENTS:
            connection.execute(statement)
        connection.commit()
    finally:
        connection.close()


def get_db(
    settings: Settings = Depends(get_settings),
) -> Generator[sqlite3.Connection, None, None]:
    connection = _connect(settings.database_path)
    try:
        yield connection
    finally:
        connection.close()
