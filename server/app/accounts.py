"""Account registry beside the legacy administrator inventory."""

from __future__ import annotations

from dataclasses import dataclass
from contextlib import contextmanager
import hashlib
from pathlib import Path
import secrets
import sqlite3
from collections.abc import Iterator
from uuid import uuid4

from .config import Settings


ADMIN_ID = "admin"


@dataclass(frozen=True)
class Account:
    server_id: str
    account_id: str
    name: str
    role: str
    database_path: str


@contextmanager
def _connect(settings: Settings) -> Iterator[sqlite3.Connection]:
    connection = sqlite3.connect(settings.database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    try:
        with connection:
            yield connection
    finally:
        connection.close()


def init_registry(settings: Settings) -> None:
    with _connect(settings) as connection:
        connection.execute("""
            CREATE TABLE IF NOT EXISTS server_identity (
                id INTEGER PRIMARY KEY CHECK (id = 1), server_id TEXT NOT NULL
            )
        """)
        connection.execute(
            "INSERT OR IGNORE INTO server_identity (id, server_id) VALUES (1, ?)",
            (str(uuid4()),),
        )
        connection.execute("""
            CREATE TABLE IF NOT EXISTS accounts (
                account_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_key TEXT NOT NULL UNIQUE,
                token_digest TEXT NOT NULL UNIQUE,
                active INTEGER NOT NULL DEFAULT 1,
                database_name TEXT NOT NULL UNIQUE
            )
        """)


def _server_id(connection: sqlite3.Connection) -> str:
    return str(connection.execute(
        "SELECT server_id FROM server_identity WHERE id = 1"
    ).fetchone()[0])


def _user_path(settings: Settings, account_id: str) -> str:
    return str(Path(settings.database_path).parent / "users" / f"{account_id}.db")


def user_database_paths(settings: Settings) -> list[str]:
    with _connect(settings) as connection:
        rows = connection.execute("SELECT account_id FROM accounts").fetchall()
        return [_user_path(settings, str(row["account_id"])) for row in rows]


def _digest(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def authenticate(settings: Settings, token: str) -> Account | None:
    if settings.api_token and secrets.compare_digest(
        token.encode("utf-8"), settings.api_token.encode("utf-8")
    ):
        with _connect(settings) as connection:
            return Account(_server_id(connection), ADMIN_ID, "Administrator", "admin", settings.database_path)
    if not token:
        return None
    with _connect(settings) as connection:
        row = connection.execute(
            "SELECT account_id, name FROM accounts WHERE token_digest = ? AND active = 1",
            (_digest(token),),
        ).fetchone()
        if row is None:
            return None
        return Account(
            _server_id(connection), str(row["account_id"]), str(row["name"]), "user",
            _user_path(settings, str(row["account_id"])),
        )


def list_accounts(settings: Settings) -> list[dict[str, object]]:
    with _connect(settings) as connection:
        rows = connection.execute(
            "SELECT account_id, name, active FROM accounts ORDER BY name_key"
        ).fetchall()
        return [dict(row) | {"active": bool(row["active"]), "role": "user"} for row in rows]


def create_account(settings: Settings, name: str) -> tuple[dict[str, object], str]:
    from .database import init_db

    clean = name.strip()
    if not clean:
        raise ValueError("Account name is required.")
    account_id = str(uuid4())
    token = secrets.token_urlsafe(32)
    path = _user_path(settings, account_id)
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    from dataclasses import replace
    if Path(path).exists():
        raise RuntimeError("Generated account database path already exists.")
    try:
        # Initialize before publishing the account: a failed initialization stays invisible.
        init_db(replace(settings, database_path=path), initialize_accounts=False)
        with _connect(settings) as connection:
            connection.execute(
                "INSERT INTO accounts(account_id, name, name_key, token_digest, database_name) "
                "VALUES (?, ?, ?, ?, ?)",
                (account_id, clean, clean.casefold(), _digest(token), f"{account_id}.db"),
            )
    except Exception as error:
        for suffix in ("", "-wal", "-shm"):
            Path(path + suffix).unlink(missing_ok=True)
        if isinstance(error, sqlite3.IntegrityError):
            raise ValueError("Account name already exists.") from error
        raise
    return {"account_id": account_id, "name": clean, "active": True, "role": "user"}, token


def update_account(settings: Settings, account_id: str, *, name: str | None, active: bool | None) -> dict[str, object] | None:
    with _connect(settings) as connection:
        row = connection.execute("SELECT name, active FROM accounts WHERE account_id = ?", (account_id,)).fetchone()
        if row is None:
            return None
        clean = row["name"] if name is None else name.strip()
        if not clean:
            raise ValueError("Account name is required.")
        enabled = bool(row["active"]) if active is None else active
        try:
            connection.execute(
                "UPDATE accounts SET name = ?, name_key = ?, active = ? WHERE account_id = ?",
                (clean, clean.casefold(), int(enabled), account_id),
            )
        except sqlite3.IntegrityError as error:
            raise ValueError("Account name already exists.") from error
    return {"account_id": account_id, "name": clean, "active": enabled, "role": "user"}


def rotate_key(settings: Settings, account_id: str) -> str | None:
    token = secrets.token_urlsafe(32)
    with _connect(settings) as connection:
        result = connection.execute(
            "UPDATE accounts SET token_digest = ? WHERE account_id = ?",
            (_digest(token), account_id),
        )
        if result.rowcount == 0:
            return None
    return token
