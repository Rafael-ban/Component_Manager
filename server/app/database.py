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
        deleted INTEGER NOT NULL DEFAULT 0,
        sync_revision INTEGER NOT NULL DEFAULT 0
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
        sync_revision INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (component_id) REFERENCES components(id)
    )
    """,
    """
    CREATE INDEX IF NOT EXISTS idx_stock_movements_updated_at
    ON stock_movements(updated_at)
    """,
    """
    CREATE TABLE IF NOT EXISTS sync_state (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        current_revision INTEGER NOT NULL CHECK (current_revision >= 0)
    )
    """,
)


def _connect(database_path: str) -> sqlite3.Connection:
    connection = sqlite3.connect(database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def init_db(settings: Settings) -> None:
    database_path = Path(settings.database_path)
    database_path.parent.mkdir(parents=True, exist_ok=True)

    connection = _connect(str(database_path))
    try:
        connection.execute("PRAGMA journal_mode = WAL")
        connection.execute("BEGIN IMMEDIATE")
        for statement in SCHEMA_STATEMENTS:
            connection.execute(statement)
        _migrate_sync_revisions(connection)
        for table in ("components", "stock_movements"):
            connection.execute(
                f"CREATE INDEX IF NOT EXISTS idx_{table}_sync_revision "
                f"ON {table}(sync_revision)"
            )
        connection.commit()
    finally:
        connection.close()


def _migrate_sync_revisions(connection: sqlite3.Connection) -> None:
    """Add and populate cursor metadata without rebuilding existing tables."""
    for table in ("components", "stock_movements"):
        columns = {
            row["name"]
            for row in connection.execute(f"PRAGMA table_info({table})").fetchall()
        }
        if "sync_revision" not in columns:
            connection.execute(
                f"ALTER TABLE {table} "
                "ADD COLUMN sync_revision INTEGER NOT NULL DEFAULT 0"
            )

    connection.execute(
        "INSERT OR IGNORE INTO sync_state (id, current_revision) VALUES (1, 0)"
    )
    current_revision = int(
        connection.execute(
            "SELECT current_revision FROM sync_state WHERE id = 1"
        ).fetchone()[0]
    )
    current_revision = max(
        current_revision,
        *(
            int(
                connection.execute(
                    f"SELECT COALESCE(MAX(sync_revision), 0) FROM {table}"
                ).fetchone()[0]
            )
            for table in ("components", "stock_movements")
        ),
    )
    for table in ("components", "stock_movements"):
        rows = connection.execute(
            f"SELECT rowid FROM {table} WHERE sync_revision = 0 ORDER BY rowid"
        ).fetchall()
        for row in rows:
            current_revision += 1
            connection.execute(
                f"UPDATE {table} SET sync_revision = ? WHERE rowid = ?",
                (current_revision, row["rowid"]),
            )
    connection.execute(
        "UPDATE sync_state SET current_revision = ? WHERE id = 1",
        (current_revision,),
    )


def get_db(
    settings: Settings = Depends(get_settings),
) -> Generator[sqlite3.Connection, None, None]:
    connection = _connect(settings.database_path)
    try:
        yield connection
    finally:
        connection.close()
