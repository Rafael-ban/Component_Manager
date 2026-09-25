from __future__ import annotations

from typing import Annotated
from pathlib import Path

from fastapi import Depends, Header, HTTPException, status

from .config import Settings, get_settings
from .accounts import Account, authenticate


def require_token(
    authorization: Annotated[str | None, Header()] = None,
    x_api_token: Annotated[str | None, Header()] = None,
    settings: Settings = Depends(get_settings),
) -> Account:
    token = _extract_token(authorization=authorization, x_api_token=x_api_token)
    account = authenticate(settings, token or "")
    if account is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API token",
        )
    if account.role == "user" and not Path(account.database_path).is_file():
        raise HTTPException(
            status_code=503,
            detail="Account database is missing; restore it from backup before syncing.",
        )
    return account


def require_admin(account: Account = Depends(require_token)) -> Account:
    if account.role != "admin":
        raise HTTPException(status_code=403, detail="Administrator account required.")
    return account


def require_sync_account(
    account: Account = Depends(require_token),
    x_component_vault_account_id: Annotated[str | None, Header()] = None,
) -> Account:
    if account.role == "user" and x_component_vault_account_id is None:
        raise HTTPException(status_code=409, detail="Upgrade client: account-aware sync is required.")
    if x_component_vault_account_id is not None and x_component_vault_account_id != account.account_id:
        raise HTTPException(status_code=403, detail="Sync account does not match API token.")
    return account


def _extract_token(
    authorization: str | None,
    x_api_token: str | None,
) -> str | None:
    if x_api_token:
        return x_api_token.strip()

    if authorization and authorization.startswith("Bearer "):
        return authorization.removeprefix("Bearer ").strip()

    return None
