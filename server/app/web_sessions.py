"""Short-lived browser sessions derived from existing account keys."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import hashlib
from pathlib import Path
import secrets

from .accounts import ADMIN_ID, Account, _connect, _digest, _server_id, _user_path, authenticate
from .config import Settings


COOKIE_NAME = "cv_session"
SESSION_SECONDS = 12 * 60 * 60


def _now() -> datetime:
    return datetime.now(timezone.utc)


def csrf_for_token(token: str) -> str:
    return hashlib.sha256(("csrf:" + token).encode()).hexdigest()


def create_session(settings: Settings, api_key: str) -> tuple[Account, str, str]:
    account = authenticate(settings, api_key)
    if account is None:
        raise ValueError("Invalid API key.")
    if account.role == "user" and not Path(account.database_path).is_file():
        raise ValueError("Account database is unavailable.")
    token = secrets.token_urlsafe(32)
    expiry = (_now() + timedelta(seconds=SESSION_SECONDS)).isoformat()
    with _connect(settings) as connection:
        connection.execute("DELETE FROM web_sessions WHERE expires_at <= ?", (_now().isoformat(),))
        connection.execute(
            "INSERT INTO web_sessions(token_digest,account_id,key_digest,expires_at) "
            "VALUES(?,?,?,?)",
            (_digest(token), account.account_id, _digest(api_key), expiry),
        )
    return account, token, expiry


def authenticate_session(settings: Settings, token: str | None) -> Account | None:
    if not token:
        return None
    with _connect(settings) as connection:
        row = connection.execute(
            "SELECT account_id,key_digest,expires_at FROM web_sessions WHERE token_digest = ?",
            (_digest(token),),
        ).fetchone()
        if row is None or datetime.fromisoformat(row["expires_at"]) <= _now():
            return None
        account_id = str(row["account_id"])
        if account_id == ADMIN_ID:
            current_digest = _digest(settings.api_token)
            account = Account(_server_id(connection), ADMIN_ID, "Administrator", "admin", settings.database_path)
        else:
            user = connection.execute(
                "SELECT name,token_digest FROM accounts WHERE account_id = ? AND active = 1",
                (account_id,),
            ).fetchone()
            if user is None:
                return None
            current_digest = str(user["token_digest"])
            account = Account(
                _server_id(connection), account_id, str(user["name"]), "user",
                _user_path(settings, account_id),
            )
            if not Path(account.database_path).is_file():
                return None
        return account if secrets.compare_digest(row["key_digest"], current_digest) else None


def session_expiry(settings: Settings, token: str) -> str | None:
    with _connect(settings) as connection:
        row = connection.execute(
            "SELECT expires_at FROM web_sessions WHERE token_digest = ?", (_digest(token),),
        ).fetchone()
        return str(row["expires_at"]) if row is not None else None


def revoke_session(settings: Settings, token: str | None) -> None:
    if not token:
        return
    with _connect(settings) as connection:
        connection.execute("DELETE FROM web_sessions WHERE token_digest = ?", (_digest(token),))
