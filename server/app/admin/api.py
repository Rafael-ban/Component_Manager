from __future__ import annotations

import sqlite3

from fastapi import APIRouter, Depends, HTTPException, Query, status

from ..auth import require_token
from ..config import Settings, get_settings
from ..database import get_db
from ..lcsc import LookupConfigurationError, LookupRequestError, lookup_lcsc_product
from ..part_lookup import (
    RecognitionRulesRefreshError,
    get_recognition_rules_meta,
    lookup_part_metadata,
    refresh_recognition_rules,
)
from ..schemas import (
    AdminComponentDetail,
    AdminComponentCreate,
    AdminComponentUpdate,
    AdminComponentListResponse,
    AdminDashboardResponse,
    AdminInventoryResponse,
    AdminKeyValueItem,
    AdminMetricSnapshot,
    AdminSettingsResponse,
    AdminStockMovementCreate,
    AdminStorageLocation,
    AdminStorageLocationCreate,
    AdminSyncResponse,
    LcscLookupResponse,
    PartLookupResponse,
    RecognitionRulesMetaResponse,
)
from .data import (
    AdminSnapshot,
    load_admin_component,
    load_admin_components,
    load_admin_snapshot,
)
from .operations import (
    AdminOperationConflict,
    AdminOperationNotFound,
    create_component,
    create_storage_location,
    list_storage_locations,
    record_stock_movement,
    update_component,
)

router = APIRouter(
    prefix="/admin-api",
    tags=["admin"],
    dependencies=[Depends(require_token)],
)


@router.get("/dashboard", response_model=AdminDashboardResponse)
def get_dashboard(
    settings: Settings = Depends(get_settings),
) -> AdminDashboardResponse:
    snapshot = load_admin_snapshot(settings)
    return AdminDashboardResponse(
        metrics=_metrics(snapshot),
        recent_components=snapshot.recent_components,
        sync_notes=snapshot.sync_notes,
    )


@router.get("/inventory", response_model=AdminInventoryResponse)
def get_inventory(
    settings: Settings = Depends(get_settings),
) -> AdminInventoryResponse:
    snapshot = load_admin_snapshot(settings)
    return AdminInventoryResponse(
        metrics=_metrics(snapshot),
        low_stock_components=snapshot.low_stock_components,
        recent_components=snapshot.recent_components,
        inventory_rules=[
            AdminKeyValueItem(
                label="Conflict mode",
                value="Last write wins on updated_at",
            ),
            AdminKeyValueItem(
                label="Soft delete",
                value="Enabled for synchronized rows",
            ),
            AdminKeyValueItem(
                label="Low-stock trigger",
                value="quantity <= min_stock",
            ),
            AdminKeyValueItem(
                label="Inventory browser",
                value="Server-side read verification only",
            ),
        ],
    )


@router.get("/components", response_model=AdminComponentListResponse)
def get_components(
    q: str | None = Query(default=None, max_length=200),
    low_stock: bool | None = Query(default=None),
    page: int = Query(default=1, ge=1, le=1_000_000),
    page_size: int = Query(default=25, ge=1, le=100),
    settings: Settings = Depends(get_settings),
) -> AdminComponentListResponse:
    result = load_admin_components(
        settings,
        query=q,
        low_stock=low_stock,
        page=page,
        page_size=page_size,
    )
    page_count = (result.total + result.page_size - 1) // result.page_size
    return AdminComponentListResponse(
        items=result.items,
        page=result.page,
        page_size=result.page_size,
        total=result.total,
        page_count=page_count,
    )


@router.get("/components/{component_id}", response_model=AdminComponentDetail)
def get_component_detail(
    component_id: str,
    settings: Settings = Depends(get_settings),
) -> AdminComponentDetail:
    component = load_admin_component(settings, component_id)
    if component is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Component not found.",
        )
    return AdminComponentDetail.model_validate(component)


@router.get(
    "/storage-locations",
    response_model=list[AdminStorageLocation],
)
def get_storage_locations(
    connection: sqlite3.Connection = Depends(get_db),
) -> list[AdminStorageLocation]:
    return [
        AdminStorageLocation.model_validate(item)
        for item in list_storage_locations(connection)
    ]


@router.post(
    "/storage-locations",
    response_model=AdminStorageLocation,
    status_code=status.HTTP_201_CREATED,
)
def post_storage_location(
    draft: AdminStorageLocationCreate,
    settings: Settings = Depends(get_settings),
    connection: sqlite3.Connection = Depends(get_db),
) -> AdminStorageLocation:
    _require_web_inventory(settings)
    return _run_admin_operation(
        AdminStorageLocation,
        lambda: create_storage_location(connection, draft),
    )


@router.post(
    "/components",
    response_model=AdminComponentDetail,
    status_code=status.HTTP_201_CREATED,
)
def post_component(
    draft: AdminComponentCreate,
    settings: Settings = Depends(get_settings),
    connection: sqlite3.Connection = Depends(get_db),
) -> AdminComponentDetail:
    _require_web_inventory(settings)
    return _run_admin_operation(
        AdminComponentDetail,
        lambda: create_component(connection, settings, draft),
    )


@router.put(
    "/components/{component_id}",
    response_model=AdminComponentDetail,
)
def put_component(
    component_id: str,
    draft: AdminComponentUpdate,
    settings: Settings = Depends(get_settings),
    connection: sqlite3.Connection = Depends(get_db),
) -> AdminComponentDetail:
    _require_web_inventory(settings)
    return _run_admin_operation(
        AdminComponentDetail,
        lambda: update_component(connection, settings, component_id, draft),
    )


@router.post(
    "/components/{component_id}/movements",
    response_model=AdminComponentDetail,
)
def post_component_movement(
    component_id: str,
    draft: AdminStockMovementCreate,
    settings: Settings = Depends(get_settings),
    connection: sqlite3.Connection = Depends(get_db),
) -> AdminComponentDetail:
    _require_web_inventory(settings)
    return _run_admin_operation(
        AdminComponentDetail,
        lambda: record_stock_movement(connection, settings, component_id, draft),
    )


@router.get("/sync", response_model=AdminSyncResponse)
def get_sync(
    settings: Settings = Depends(get_settings),
) -> AdminSyncResponse:
    snapshot = load_admin_snapshot(settings)
    return AdminSyncResponse(
        metrics=_metrics(snapshot),
        recent_movements=snapshot.recent_movements,
        sync_assumptions=[
            AdminKeyValueItem(label="Conflict mode", value="Last write wins"),
            AdminKeyValueItem(label="Soft delete", value="Enabled"),
            AdminKeyValueItem(label="Device registry", value="Not implemented"),
            AdminKeyValueItem(
                label="Authenticated routes",
                value="/auth/ping, /sync/push, /sync/pull, /admin-api/*",
            ),
        ],
        attention_items=_attention_items(settings),
    )


@router.get("/settings", response_model=AdminSettingsResponse)
def get_settings_overview(
    settings: Settings = Depends(get_settings),
) -> AdminSettingsResponse:
    return AdminSettingsResponse(
        web_inventory_enabled=settings.web_inventory_enabled,
        runtime_configuration=[
            AdminKeyValueItem(label="App name", value=settings.app_name),
            AdminKeyValueItem(label="Host", value=settings.app_host),
            AdminKeyValueItem(label="Port", value=str(settings.app_port)),
            AdminKeyValueItem(label="Database path", value=settings.database_path),
            AdminKeyValueItem(
                label="Admin web origins",
                value=", ".join(settings.admin_web_origins),
            ),
            AdminKeyValueItem(
                label="API token status",
                value=(
                    "Default token in use"
                    if settings.api_token == "change-me"
                    else "Custom token configured"
                ),
            ),
            AdminKeyValueItem(
                label="LCSC lookup status",
                value=(
                    "Configured"
                    if settings.lcsc_openapi_key and settings.lcsc_openapi_secret
                    else "Not configured"
                ),
            ),
            AdminKeyValueItem(
                label="Recognition rules remote URL",
                value=settings.import_rules_remote_url or "Not configured",
            ),
            AdminKeyValueItem(
                label="Web fallback resolvers",
                value="Enabled" if settings.enable_web_fallback_resolvers else "Disabled",
            ),
        ],
        access_posture=[
            AdminKeyValueItem(
                label="Health URL",
                value=f"http://{settings.app_host}:{settings.app_port}/health",
            ),
            AdminKeyValueItem(
                label="Auth URL",
                value=f"http://{settings.app_host}:{settings.app_port}/auth/ping",
            ),
            AdminKeyValueItem(
                label="Admin API",
                value=f"http://{settings.app_host}:{settings.app_port}/admin-api/dashboard",
            ),
            AdminKeyValueItem(
                label="Sync API",
                value=f"http://{settings.app_host}:{settings.app_port}/sync/pull",
            ),
            AdminKeyValueItem(
                label="Token risk",
                value=(
                    "Replace before deployment"
                    if settings.api_token == "change-me"
                    else "Custom token present"
                ),
            ),
        ],
        next_backend_additions=[
            "Per-device sync audit log",
            "Conflict history and resolution records",
            "Backend session auth for the web admin",
        ],
    )


@router.get("/part-lookup", response_model=PartLookupResponse)
def get_part_lookup(
    sku: str | None = Query(default=None, max_length=120),
    mpn: str | None = Query(default=None, max_length=200),
    name: str | None = Query(default=None, max_length=300),
    brand: str | None = Query(default=None, max_length=200),
    package_hint: str | None = Query(default=None, max_length=120),
    source_type: str | None = Query(default=None, max_length=80),
    settings: Settings = Depends(get_settings),
) -> PartLookupResponse:
    if not any(
        value and value.strip()
        for value in (sku, mpn, name, brand, package_hint)
    ):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail="At least one lookup field is required.",
        )

    return lookup_part_metadata(
        settings=settings,
        sku=sku,
        mpn=mpn,
        name=name,
        brand=brand,
        package_hint=package_hint,
        source_type=source_type,
    )


@router.get(
    "/recognition-rules/meta",
    response_model=RecognitionRulesMetaResponse,
)
def get_rules_meta(
    settings: Settings = Depends(get_settings),
) -> RecognitionRulesMetaResponse:
    return get_recognition_rules_meta(settings)


@router.post(
    "/recognition-rules/refresh",
    response_model=RecognitionRulesMetaResponse,
)
def post_rules_refresh(
    settings: Settings = Depends(get_settings),
) -> RecognitionRulesMetaResponse:
    try:
        return refresh_recognition_rules(settings)
    except RecognitionRulesRefreshError as error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(error),
        ) from error


@router.get("/lcsc/lookup", response_model=LcscLookupResponse)
def get_lcsc_lookup(
    sku: str | None = Query(default=None, max_length=120),
    mpn: str | None = Query(default=None, max_length=200),
    name: str | None = Query(default=None, max_length=300),
    settings: Settings = Depends(get_settings),
) -> LcscLookupResponse:
    if not any(value and value.strip() for value in (sku, mpn, name)):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail="At least one of sku, mpn, or name is required.",
        )

    try:
        return lookup_lcsc_product(
            settings=settings,
            sku=sku,
            mpn=mpn,
            name=name,
        )
    except LookupConfigurationError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error
    except LookupRequestError as error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(error),
        ) from error


def _metrics(snapshot: AdminSnapshot) -> AdminMetricSnapshot:
    return AdminMetricSnapshot(
        component_count=snapshot.component_count,
        total_units=snapshot.total_units,
        low_stock_count=snapshot.low_stock_count,
        movement_count=snapshot.movement_count,
    )


def _attention_items(settings: Settings) -> list[str]:
    if settings.api_token == "change-me":
        token_item = (
            "API token is still the default value. Replace it before deployment."
        )
    else:
        token_item = "Custom API token is configured for authenticated routes."

    return [
        token_item,
        (
            "Authenticated web inventory writes are enabled."
            if settings.web_inventory_enabled
            else "Web inventory writes are disabled; clients own inventory writes."
        ),
        "Sync visibility is derived from the latest accepted server-side rows.",
    ]


def _require_web_inventory(settings: Settings) -> None:
    if not settings.web_inventory_enabled:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Web inventory writes are disabled.",
        )


def _run_admin_operation(model, operation):
    try:
        return model.model_validate(operation())
    except AdminOperationNotFound as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(error),
        ) from error
    except AdminOperationConflict as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(error),
        ) from error
