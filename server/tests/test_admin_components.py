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


def test_component_search_normalizes_models_and_matches_all_fields(
    client: TestClient,
) -> None:
    _push_component(
        client,
        component_id="resistor",
        sku="RC0603FR-0710KL",
        name="Precision resistor",
        category="Passives",
        package_name="0603-SMD",
        location="Shelf Z",
        description="品牌：FOJAN(富捷)\n10kΩ ±1% 0.1W",
        quantity=0,
        min_stock=1,
    )
    _push_component(
        client,
        component_id="capacitor",
        sku="CAP-10.5-V",
        name="Capacitor",
        category="Passives",
        package_name="0805",
        location="Shelf A",
        description="10.5μF 20%",
        quantity=8,
        min_stock=1,
    )
    _push_component(
        client,
        component_id="other",
        sku="CAP-105-V",
        name="Capacitor",
        category="Passives",
        package_name="0805",
        location="Shelf B",
        description="105μF 20%",
        quantity=8,
        min_stock=1,
    )

    for query, expected in (
        ("ｒｃ０６０３ｆｒ０７１０ｋｌ", ["resistor"]),
        ("RC0603FR / 0710KL", ["resistor"]),
        ("precision 0603 z 0.1w", ["resistor"]),
        ("10kΩ 1%", ["resistor"]),
        ("10kohm 1%", ["resistor"]),
        ("fojan 0603", ["resistor"]),
        ("富捷 10k", ["resistor"]),
        ("10.5uF", ["capacitor"]),
        ("10.5", ["capacitor"]),
        ("105", ["other"]),
        ("resistor 0805", []),
    ):
        response = client.get(
            "/admin-api/components", headers=HEADERS, params={"q": query}
        )
        assert response.status_code == 200, response.text
        assert [item["id"] for item in response.json()["items"]] == expected
        assert response.json()["total"] == len(expected)

    low_stock = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"q": "passives", "low_stock": True, "page_size": 1},
    )
    assert [item["id"] for item in low_stock.json()["items"]] == ["resistor"]
    assert low_stock.json()["total"] == 1

    second_page = client.get(
        "/admin-api/components",
        headers=HEADERS,
        params={"q": "capacitor", "page": 2, "page_size": 1},
    )
    assert second_page.json()["total"] == 2
    assert second_page.json()["page_count"] == 2
    assert len(second_page.json()["items"]) == 1


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
    package_name: str = "TEST",
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
                    "package_name": package_name,
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
