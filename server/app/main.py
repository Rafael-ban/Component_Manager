from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timezone
import sqlite3
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware

from .admin.api import router as admin_router
from .auth import require_token
from .config import get_settings
from .database import get_db, init_db
from .repositories import (
    pull_components,
    pull_stock_movements,
    save_components,
    save_stock_movements,
)
from .schemas import (
    HealthResponse,
    PullResponse,
    PushRequest,
    PushResponse,
    SyncTokenStatus,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db(get_settings())
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="Component Vault Sync", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.admin_web_origins),
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-API-Token"],
    )
    app.include_router(admin_router)

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(status="ok", server_time=_utc_now())

    @app.post(
        "/auth/ping",
        response_model=SyncTokenStatus,
        dependencies=[Depends(require_token)],
    )
    def auth_ping() -> SyncTokenStatus:
        return SyncTokenStatus(status="ok", server_time=_utc_now())

    @app.post(
        "/sync/push",
        response_model=PushResponse,
        dependencies=[Depends(require_token)],
    )
    def sync_push(
        payload: PushRequest,
        connection: sqlite3.Connection = Depends(get_db),
    ) -> PushResponse:
        try:
            accepted_components = save_components(connection, payload.components)
            accepted_stock_movements = save_stock_movements(
                connection,
                payload.stock_movements,
            )
        except ValueError as error:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=str(error),
            ) from error
        return PushResponse(
            accepted_components=accepted_components,
            accepted_stock_movements=accepted_stock_movements,
            server_time=_utc_now(),
        )

    @app.get(
        "/sync/pull",
        response_model=PullResponse,
        dependencies=[Depends(require_token)],
    )
    def sync_pull(
        since: Annotated[datetime | None, Query()] = None,
        connection: sqlite3.Connection = Depends(get_db),
    ) -> PullResponse:
        return PullResponse(
            server_time=_utc_now(),
            components=pull_components(connection, since),
            stock_movements=pull_stock_movements(connection, since),
        )

    return app


app = create_app()


def _utc_now() -> datetime:
    return datetime.now(tz=timezone.utc)
