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
