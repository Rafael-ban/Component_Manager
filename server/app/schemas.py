from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    server_time: datetime


class SyncTokenStatus(BaseModel):
    status: str
    server_time: datetime


class ComponentPayload(BaseModel):
    id: str = Field(min_length=1)
    sku: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=200)
    category: str = Field(min_length=1, max_length=120)
    package_name: str = Field(min_length=1, max_length=120)
    location: str = Field(min_length=1, max_length=120)
    description: str | None = None
    quantity: int = Field(ge=0)
    min_stock: int = Field(default=0, ge=0)
    updated_at: datetime
    deleted: bool = False


class StockMovementPayload(BaseModel):
    id: str = Field(min_length=1)
    component_id: str = Field(min_length=1)
    movement_type: Literal["inbound", "outbound", "adjustment"]
    quantity: int
    reason: str = Field(min_length=1, max_length=160)
    note: str | None = None
    happened_at: datetime
    updated_at: datetime
    deleted: bool = False


class PushRequest(BaseModel):
    device_id: str = Field(min_length=1)
    components: list[ComponentPayload] = Field(default_factory=list)
    stock_movements: list[StockMovementPayload] = Field(default_factory=list)


class PushResponse(BaseModel):
    accepted_components: int
    accepted_stock_movements: int
    server_time: datetime


class PullResponse(BaseModel):
    server_time: datetime
    components: list[ComponentPayload] = Field(default_factory=list)
    stock_movements: list[StockMovementPayload] = Field(default_factory=list)


class AdminMetricSnapshot(BaseModel):
    component_count: int
    total_units: int
    low_stock_count: int
    movement_count: int


class AdminComponentRecord(BaseModel):
    id: str
    sku: str
    name: str
    category: str
    location: str
    quantity: int
    min_stock: int
    updated_at: datetime
    status: str


class AdminLowStockRecord(BaseModel):
    id: str
    sku: str
    name: str
    location: str
    quantity: int
    min_stock: int
    updated_at: datetime


class AdminMovementRecord(BaseModel):
    id: str
    sku: str
    component_name: str
    movement_type: Literal["inbound", "outbound", "adjustment"]
    quantity: int
    reason: str
    note: str | None = None
    happened_at: datetime
    updated_at: datetime


class AdminKeyValueItem(BaseModel):
    label: str
    value: str


class AdminDashboardResponse(BaseModel):
    metrics: AdminMetricSnapshot
    recent_components: list[AdminComponentRecord] = Field(default_factory=list)
    sync_notes: list[str] = Field(default_factory=list)


class AdminInventoryResponse(BaseModel):
    metrics: AdminMetricSnapshot
    low_stock_components: list[AdminLowStockRecord] = Field(default_factory=list)
    recent_components: list[AdminComponentRecord] = Field(default_factory=list)
    inventory_rules: list[AdminKeyValueItem] = Field(default_factory=list)


class AdminSyncResponse(BaseModel):
    metrics: AdminMetricSnapshot
    recent_movements: list[AdminMovementRecord] = Field(default_factory=list)
    sync_assumptions: list[AdminKeyValueItem] = Field(default_factory=list)
    attention_items: list[str] = Field(default_factory=list)


class AdminSettingsResponse(BaseModel):
    runtime_configuration: list[AdminKeyValueItem] = Field(default_factory=list)
    access_posture: list[AdminKeyValueItem] = Field(default_factory=list)
    next_backend_additions: list[str] = Field(default_factory=list)


class LcscLookupResponse(BaseModel):
    found: bool
    source: str = "lcsc_openapi"
    sku: str | None = None
    name: str | None = None
    mpn: str | None = None
    package_name: str | None = None
    category: str | None = None
    category_path: str | None = None
    brand: str | None = None
    official_url: str | None = None
    matched_by: Literal["sku", "mpn", "name"] | None = None
    confidence: Literal["exact", "fallback", "none"] = "none"
    cache_hit: bool = False


class PartLookupResponse(BaseModel):
    found: bool
    source: str = "local_rules"
    sku: str | None = None
    name: str | None = None
    mpn: str | None = None
    package_name: str | None = None
    category: str | None = None
    category_path: str | None = None
    brand: str | None = None
    vendor: str | None = None
    model_family: str | None = None
    official_url: str | None = None
    matched_by: str | None = None
    confidence: str = "none"
    cache_hit: bool = False
    rule_version: str | None = None


class RecognitionRulesMetaResponse(BaseModel):
    version: str
    updated_at: str | None = None
    source: str
    active_path: str
    override_path: str
    remote_url: str | None = None
    web_fallback_enabled: bool = False
    refreshed: bool = False
