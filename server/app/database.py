from __future__ import annotations

from collections.abc import Generator
from pathlib import Path
import sqlite3

from fastapi import Depends, HTTPException

from .config import Settings, get_settings
from .auth import require_token
from .accounts import Account


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
    """
    CREATE TABLE IF NOT EXISTS mqtt_outbox (
        event_id TEXT PRIMARY KEY,
        component_id TEXT NOT NULL,
        sync_revision INTEGER NOT NULL UNIQUE,
        topic TEXT NOT NULL,
        payload TEXT NOT NULL,
        created_at TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS mqtt_state (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        snapshot_seeded INTEGER NOT NULL DEFAULT 0,
        snapshot_key TEXT NOT NULL DEFAULT ''
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS mqtt_configuration (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        enabled INTEGER NOT NULL,
        host TEXT NOT NULL,
        port INTEGER NOT NULL,
        tls INTEGER NOT NULL,
        username TEXT NOT NULL,
        password TEXT NOT NULL,
        topic_prefix TEXT NOT NULL,
        client_id TEXT NOT NULL,
        updated_at TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS admin_operation_receipts (
        request_id TEXT PRIMARY KEY,
        operation TEXT NOT NULL,
        target_id TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        created_at TEXT NOT NULL
    )
    """,
)


def _connect(database_path: str, *, existing_only: bool = False) -> sqlite3.Connection:
    target = Path(database_path).resolve().as_uri() + "?mode=rw" if existing_only else database_path
    connection = sqlite3.connect(target, check_same_thread=False, uri=existing_only)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def init_db(settings: Settings, *, initialize_accounts: bool = True) -> None:
    database_path = Path(settings.database_path)
    database_path.parent.mkdir(parents=True, exist_ok=True)

    connection = _connect(str(database_path))
    try:
        connection.execute("PRAGMA journal_mode = WAL")
        columns = {row["name"] for row in connection.execute("PRAGMA table_info(components)")}
        backup_path = database_path.with_name(database_path.name + ".pre-inventory-v1.bak")
        if columns and "inventory_managed" not in columns and not backup_path.exists():
            backup = sqlite3.connect(str(backup_path))
            try:
                connection.backup(backup)
            finally:
                backup.close()
        connection.execute("BEGIN IMMEDIATE")
        for statement in SCHEMA_STATEMENTS:
            connection.execute(statement)
        _migrate_storage(connection)
        _migrate_sync_revisions(connection)
        connection.execute(
            "INSERT OR IGNORE INTO mqtt_state (id, snapshot_seeded) VALUES (1, 0)"
        )
        mqtt_state_columns = {
            row["name"]
            for row in connection.execute("PRAGMA table_info(mqtt_state)").fetchall()
        }
        if "snapshot_key" not in mqtt_state_columns:
            connection.execute(
                "ALTER TABLE mqtt_state ADD COLUMN snapshot_key TEXT NOT NULL DEFAULT ''"
            )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_mqtt_outbox_revision "
            "ON mqtt_outbox(sync_revision)"
        )
        for table in ("components", "stock_movements"):
            connection.execute(
                f"CREATE INDEX IF NOT EXISTS idx_{table}_sync_revision "
                f"ON {table}(sync_revision)"
            )
        connection.commit()
    finally:
        connection.close()
    if initialize_accounts:
        from dataclasses import replace
        from .accounts import init_registry, user_database_paths

        init_registry(settings)
        for user_path in user_database_paths(settings):
            if Path(user_path).is_file():
                init_db(replace(settings, database_path=user_path), initialize_accounts=False)


def _migrate_storage(connection: sqlite3.Connection) -> None:
    connection.execute("""
        CREATE TABLE IF NOT EXISTS storage_locations (
            id TEXT PRIMARY KEY, name TEXT NOT NULL,
            updated_at TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0
        )
    """)
    connection.execute("""
        CREATE TABLE IF NOT EXISTS component_allocations (
            component_id TEXT NOT NULL REFERENCES components(id),
            location_id TEXT NOT NULL REFERENCES storage_locations(id),
            quantity INTEGER NOT NULL CHECK(quantity >= 0),
            PRIMARY KEY(component_id, location_id)
        )
    """)
    for table, column, definition in (
        ("components", "inventory_managed", "INTEGER NOT NULL DEFAULT 0"),
        ("stock_movements", "location_id", "TEXT"),
        ("stock_movements", "destination_location_id", "TEXT"),
    ):
        columns = {row["name"] for row in connection.execute(
            f"PRAGMA table_info({table})"
        )}
        if column not in columns:
            connection.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


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
    account: Account = Depends(require_token),
) -> Generator[sqlite3.Connection, None, None]:
    try:
        connection = _connect(account.database_path, existing_only=account.role == "user")
    except sqlite3.OperationalError as error:
        if account.role != "user":
            raise
        raise HTTPException(
            status_code=503,
            detail="Account database is unavailable; restore it before syncing.",
        ) from error
    try:
        yield connection
    finally:
        connection.close()
