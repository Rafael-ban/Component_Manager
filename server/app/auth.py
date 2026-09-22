from __future__ import annotations

from typing import Annotated
import secrets

from fastapi import Depends, Header, HTTPException, status

from .config import Settings, get_settings


def require_token(
    authorization: Annotated[str | None, Header()] = None,
    x_api_token: Annotated[str | None, Header()] = None,
    settings: Settings = Depends(get_settings),
) -> None:
    token = _extract_token(authorization=authorization, x_api_token=x_api_token)
    if (
        not settings.api_token
        or not token
        or not secrets.compare_digest(
            token.encode("utf-8"), settings.api_token.encode("utf-8")
        )
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API token",
        )


def _extract_token(
    authorization: str | None,
    x_api_token: str | None,
) -> str | None:
    if x_api_token:
        return x_api_token.strip()

    if authorization and authorization.startswith("Bearer "):
        return authorization.removeprefix("Bearer ").strip()

    return None
