from __future__ import annotations

from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import threading
from typing import Annotated, Iterator
from urllib.parse import urlsplit
from uuid import uuid4

from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import FileResponse, RedirectResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator

from .application_logging import log_event, recent_log_lines
from .auth import _extract_token
from .config import (
    PERSISTED_ENVIRONMENT_FIELDS,
    config_path,
    environment_overrides,
    get_settings,
    log_path,
    normalize_admin_web_url,
    read_persisted_configuration,
)


router = APIRouter()
_configuration_lock = threading.Lock()


class DeploymentConfigurationUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    api_token: str = Field(default="", max_length=4096)
    admin_web_origins: list[str] = Field(default_factory=list, max_length=64)
    admin_web_url: str = Field(default="", max_length=2048)
    web_inventory_enabled: bool = False

    @field_validator("admin_web_url")
    @classmethod
    def validate_admin_web_url(cls, value: str) -> str:
        return normalize_admin_web_url(value)

    @field_validator("admin_web_origins")
    @classmethod
    def validate_origins(cls, values: list[str]) -> list[str]:
        normalized: list[str] = []
        for value in values:
            candidate = value.strip().rstrip("/")
            parsed = urlsplit(candidate)
            if (
                parsed.scheme not in {"http", "https"}
                or not parsed.netloc
                or parsed.path
                or parsed.query
                or parsed.fragment
            ):
                raise ValueError("Each admin origin must be an HTTP(S) origin.")
            if candidate not in normalized:
                normalized.append(candidate)
        return normalized


def _configured() -> bool:
    return bool(get_settings().api_token)


def _require_current_token(
    authorization: str | None,
    x_api_token: str | None,
) -> None:
    expected = get_settings().api_token
    supplied = _extract_token(authorization, x_api_token)
    if (
        not expected
        or not supplied
        or not secrets.compare_digest(
            supplied.encode("utf-8"), expected.encode("utf-8")
        )
    ):
        raise HTTPException(status_code=401, detail="Invalid API token")


def _validate_requested_token(token: str) -> None:
    if any(ord(character) < 0x21 or ord(character) > 0x7E for character in token):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="API tokens may contain visible ASCII characters only.",
        )


@contextmanager
def _exclusive_configuration_write() -> Iterator[None]:
    with _configuration_lock:
        config_path().parent.mkdir(parents=True, exist_ok=True)
        yield


def _write_configuration(payload: dict[str, object]) -> None:
    path = config_path()
    temporary = path.with_name(f".{path.name}.{uuid4().hex}.tmp")
    try:
        with temporary.open("x", encoding="utf-8") as handle:
            serialized = {
                PERSISTED_ENVIRONMENT_FIELDS[key]: value
                for key, value in payload.items()
            }
            json.dump(serialized, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        # Use the normal file-creation umask and mounted-directory ACLs so NAS
        # administrators can inspect the configuration alongside the database.
        os.replace(temporary, path)
    except OSError as error:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Could not save deployment configuration at {path}.",
        ) from error


@router.get("/", include_in_schema=False)
def root() -> RedirectResponse:
    return RedirectResponse(url="/setup", status_code=307)


@router.get("/setup", include_in_schema=False)
def setup_page() -> FileResponse:
    path = Path(__file__).with_name("setup.html")
    if not path.is_file():
        raise HTTPException(status_code=503, detail="Setup page is unavailable.")
    return FileResponse(path, media_type="text/html")


@router.get("/setup/status")
def setup_status() -> dict[str, bool]:
    return {"configured": _configured()}


@router.get("/setup/config")
def setup_config(
    authorization: Annotated[str | None, Header()] = None,
    x_api_token: Annotated[str | None, Header()] = None,
) -> dict[str, object]:
    _require_current_token(authorization, x_api_token)
    settings = get_settings()
    return {
        "configured": True,
        "config_path": str(config_path()),
        "log_path": str(log_path()),
        "environment_overrides": list(environment_overrides()),
        "admin_web_origins": list(settings.admin_web_origins),
        "admin_web_url": settings.admin_web_url,
        "web_inventory_enabled": settings.web_inventory_enabled,
    }


@router.post("/setup/config")
def update_setup_config(
    update: DeploymentConfigurationUpdate,
    authorization: Annotated[str | None, Header()] = None,
    x_api_token: Annotated[str | None, Header()] = None,
) -> dict[str, object]:
    was_configured = _configured()
    if was_configured:
        _require_current_token(authorization, x_api_token)
    with _exclusive_configuration_write():
        get_settings.cache_clear()
        current = get_settings()
        if was_configured:
            _require_current_token(authorization, x_api_token)
        if not was_configured and current.api_token:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Deployment was configured by another request; retry with its token.",
            )
        requested_token = update.api_token
        if requested_token:
            _validate_requested_token(requested_token)
        if not current.api_token and len(requested_token) < 16:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="The first API token must contain at least 16 characters.",
            )
        if (
            requested_token
            and requested_token != current.api_token
            and len(requested_token) < 16
        ):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="A replacement API token must contain at least 16 characters.",
            )
        saved = read_persisted_configuration()
        candidate = {
            "api_token": requested_token or current.api_token,
            "admin_web_origins": update.admin_web_origins,
            "admin_web_url": (
                update.admin_web_url if "admin_web_url" in update.model_fields_set
                else current.admin_web_url
            ),
            "web_inventory_enabled": update.web_inventory_enabled,
        }
        effective = {
            "api_token": current.api_token,
            "admin_web_origins": list(current.admin_web_origins),
            "admin_web_url": current.admin_web_url,
            "web_inventory_enabled": current.web_inventory_enabled,
        }
        for field, env_name in PERSISTED_ENVIRONMENT_FIELDS.items():
            if os.getenv(env_name, "").strip() and candidate[field] != effective[field]:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"{env_name} is set by the environment and cannot be changed here.",
                )
        for field in PERSISTED_ENVIRONMENT_FIELDS:
            if os.getenv(PERSISTED_ENVIRONMENT_FIELDS[field], "").strip():
                candidate[field] = saved.get(field, effective[field])
        _write_configuration(candidate)
        get_settings.cache_clear()
        settings = get_settings()
        log_event("configuration_saved", environment_overrides=list(environment_overrides()))
    return {
        "configured": True,
        "config_path": str(config_path()),
        "log_path": str(log_path()),
        "environment_overrides": list(environment_overrides()),
        "admin_web_origins": list(settings.admin_web_origins),
        "admin_web_url": settings.admin_web_url,
        "web_inventory_enabled": settings.web_inventory_enabled,
    }


@router.get("/setup/logs")
def setup_logs(
    authorization: Annotated[str | None, Header()] = None,
    x_api_token: Annotated[str | None, Header()] = None,
) -> dict[str, object]:
    _require_current_token(authorization, x_api_token)
    return {"lines": recent_log_lines(), "log_path": str(log_path())}
