"""Administrator account management and authenticated identity."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .accounts import (
    Account, create_account, deactivate_account_for_deletion,
    finish_account_deletion, list_accounts, rotate_key, update_account,
)
from .auth import require_admin, require_token
from .config import Settings, get_settings
from .web_sessions import (
    COOKIE_NAME, SESSION_SECONDS, authenticate_session, create_session,
    csrf_for_token, revoke_session, session_expiry,
)


router = APIRouter()


class AccountCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class AccountUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    active: bool | None = None


class SessionLogin(BaseModel):
    api_key: str = Field(min_length=1)


def _session_payload(account: Account, token: str, expires_at: str | None = None) -> dict[str, object]:
    return {
        "identity": {
            "server_id": account.server_id, "account_id": account.account_id,
            "name": account.name, "role": account.role,
        },
        "csrf_token": csrf_for_token(token),
        "expires_at": expires_at,
    }


@router.post("/auth/session")
def post_session(
    draft: SessionLogin, request: Request, response: Response,
    settings: Settings = Depends(get_settings),
) -> dict[str, object]:
    try:
        account, token, expires_at = create_session(settings, draft.api_key)
    except ValueError as error:
        raise HTTPException(status_code=401, detail=str(error)) from error
    response.set_cookie(
        COOKIE_NAME, token, max_age=SESSION_SECONDS, path="/",
        httponly=True, secure=request.url.scheme == "https", samesite="lax",
    )
    return _session_payload(account, token, expires_at)


@router.get("/auth/session")
def get_session(
    request: Request, settings: Settings = Depends(get_settings),
) -> dict[str, object]:
    token = request.cookies.get(COOKIE_NAME)
    account = authenticate_session(settings, token)
    if account is None:
        raise HTTPException(status_code=401, detail="Session expired or revoked.")
    return _session_payload(account, token or "", session_expiry(settings, token or ""))


@router.delete("/auth/session")
def delete_session(
    request: Request, response: Response,
    account: Account = Depends(require_token),
    settings: Settings = Depends(get_settings),
) -> dict[str, bool]:
    revoke_session(settings, request.cookies.get(COOKIE_NAME))
    response.delete_cookie(COOKIE_NAME, path="/")
    return {"logged_out": True}


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


@router.delete("/admin-api/accounts/{account_id}", dependencies=[Depends(require_admin)])
def delete_account(
    account_id: str, request: Request, settings: Settings = Depends(get_settings),
) -> dict[str, str]:
    try:
        path = deactivate_account_for_deletion(settings, account_id)
    except ValueError as error:
        raise HTTPException(status_code=403, detail=str(error)) from error
    if path is None:
        raise HTTPException(status_code=404, detail="Account not found.")
    request.app.state.spec_enrichment_jobs.cancel_account_and_wait(account_id, path)
    try:
        finish_account_deletion(settings, account_id)
    except OSError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    return {"account_id": account_id, "status": "deleted"}
