from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient
import pytest

from app.config import get_settings
from app.main import create_app


HEADERS = {"Authorization": "Bearer test-token"}


@pytest.fixture
def client(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("API_TOKEN", "test-token")
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "sync.db"))
    monkeypatch.setenv("MQTT_ENABLED", "false")
    get_settings.cache_clear()
    with TestClient(create_app()) as value:
        yield value
    get_settings.cache_clear()


def test_component_list_requires_authentication(client: TestClient) -> None:
    response = client.get("/admin-api/components")
    oversized = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"page_size": 101},
    )
    oversized_page = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"page": 1_000_001},
    )

    assert response.status_code == 401
    assert oversized.status_code == 422
    assert oversized_page.status_code == 422


def test_component_list_searches_and_paginates_more_than_recent_limit(
    client: TestClient,
) -> None:
    for index in range(15):
        _push_component(
            client,
            component_id=f"cmp-{index:02d}",
            sku=f"SKU-{index:02d}",
            name="Sensor" if index == 13 else f"Part {index:02d}",
            category="Sensors" if index == 13 else "Passives",
            location="Shelf Z" if index == 13 else "Shelf A",
            quantity=0 if index in {0, 13} else index,
            min_stock=1,
        )

    first = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"page": 1, "page_size": 6},
    )
    second = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"page": 2, "page_size": 6},
    )
    search = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"q": "shelf z", "low_stock": True},
    )
    literal_wildcard = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"q": "%_"},
    )

    assert first.status_code == 200
    assert first.json()["total"] == 15
    assert first.json()["page_count"] == 3
    assert len(first.json()["items"]) == 6
    assert {item["id"] for item in first.json()["items"]}.isdisjoint(
        {item["id"] for item in second.json()["items"]}
    )
    assert [item["id"] for item in search.json()["items"]] == ["cmp-13"]
    assert literal_wildcard.json()["total"] == 0


def test_component_detail_excludes_deleted_and_returns_missing(
    client: TestClient,
) -> None:
    _push_component(
        client,
        component_id="cmp-visible",
        sku="VISIBLE-1",
        name="Visible component",
        category="IC",
        location="B1",
        quantity=4,
        min_stock=2,
        description="Verified description",
    )
    detail = client.get("/admin-api/components/cmp-visible", headers=HEADERS)

    _push_component(
        client,
        component_id="cmp-visible",
        sku="VISIBLE-1",
        name="Visible component",
        category="IC",
        location="B1",
        quantity=4,
        min_stock=2,
        description="Verified description",
        deleted=True,
        updated_at="2026-09-16T00:01:00Z",
    )
    deleted_detail = client.get(
        "/admin-api/components/cmp-visible",
        headers=HEADERS,
    )
    missing_detail = client.get(
        "/admin-api/components/does-not-exist",
        headers=HEADERS,
    )
    listing = client.get("/admin-api/components", headers=HEADERS)

    assert detail.status_code == 200
    assert detail.json()["description"] == "Verified description"
    assert detail.json()["allocations"] == []
    assert deleted_detail.status_code == 404
    assert missing_detail.status_code == 404
    assert listing.json()["total"] == 0


def _push_component(
    client: TestClient,
    *,
    component_id: str,
    sku: str,
    name: str,
    category: str,
    location: str,
    quantity: int,
    min_stock: int,
    description: str = "",
    deleted: bool = False,
    updated_at: str = "2026-09-16T00:00:00Z",
) -> None:
    response = client.post(
        "/sync/push",
        headers=HEADERS,
        json={
            "device_id": "admin-test",
            "components": [
                {
                    "id": component_id,
                    "sku": sku,
                    "name": name,
                    "category": category,
                    "package_name": "TEST",
                    "location": location,
                    "description": description,
                    "quantity": quantity,
                    "min_stock": min_stock,
                    "updated_at": updated_at,
                    "deleted": deleted,
                }
            ],
        },
    )
    assert response.status_code == 200, response.text
