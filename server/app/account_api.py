"""Administrator account management and authenticated identity."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .accounts import Account, create_account, list_accounts, rotate_key, update_account
from .auth import require_admin, require_token
from .config import Settings, get_settings


router = APIRouter()


class AccountCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class AccountUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    active: bool | None = None


@router.get("/auth/me")
def get_identity(account: Account = Depends(require_token)) -> dict[str, str]:
    return {
        "server_id": account.server_id,
        "account_id": account.account_id,
        "name": account.name,
        "role": account.role,
    }


@router.get("/admin-api/accounts", dependencies=[Depends(require_admin)])
def get_accounts(settings: Settings = Depends(get_settings)) -> list[dict[str, object]]:
    return list_accounts(settings)


@router.post("/admin-api/accounts", status_code=201, dependencies=[Depends(require_admin)])
def post_account(
    draft: AccountCreate, settings: Settings = Depends(get_settings),
) -> dict[str, object]:
    try:
        account, token = create_account(settings, draft.name)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    return account | {"api_token": token}


@router.patch("/admin-api/accounts/{account_id}", dependencies=[Depends(require_admin)])
def patch_account(
    account_id: str, draft: AccountUpdate, settings: Settings = Depends(get_settings),
) -> dict[str, object]:
    if draft.name is None and draft.active is None:
        raise HTTPException(status_code=422, detail="Specify name or active.")
    try:
        account = update_account(settings, account_id, name=draft.name, active=draft.active)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    if account is None:
        raise HTTPException(status_code=404, detail="Account not found.")
    return account


@router.post("/admin-api/accounts/{account_id}/rotate-key", dependencies=[Depends(require_admin)])
def post_rotate_key(
    account_id: str, settings: Settings = Depends(get_settings),
) -> dict[str, str]:
    token = rotate_key(settings, account_id)
    if token is None:
        raise HTTPException(status_code=404, detail="Account not found.")
    return {"account_id": account_id, "api_token": token}
