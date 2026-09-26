from __future__ import annotations

from dataclasses import dataclass
import sqlite3
import unicodedata

from ..config import Settings


@dataclass(frozen=True)
class AdminSnapshot:
    component_count: int
    total_units: int
    low_stock_count: int
    movement_count: int
    low_stock_components: list[dict[str, object]]
    recent_components: list[dict[str, object]]
    recent_movements: list[dict[str, object]]
    sync_notes: list[str]


@dataclass(frozen=True)
class AdminComponentPage:
    items: list[dict[str, object]]
    page: int
    page_size: int
    total: int


def load_admin_components(
    connection: sqlite3.Connection,
    *,
    query: str | None,
    low_stock: bool | None,
    page: int,
    page_size: int,
) -> AdminComponentPage:
    clauses = ["deleted = 0"]
    parameters: list[object] = []
    if query and query.strip():
        terms = [_normalize_search_term(term) for term in query.split()]
        terms = [term for term in terms if term]
        if terms:
            connection.create_function(
                "component_search_text", 6, _component_search_text,
                deterministic=True,
            )
            search_text = (
                "component_search_text(sku, name, category, package_name, "
                "location, description)"
            )
            clauses.extend(f"instr({search_text}, ?) > 0" for _ in terms)
            parameters.extend(terms)
        else:
            clauses.append("0")
    if low_stock is not None:
        clauses.append("quantity <= min_stock" if low_stock else "quantity > min_stock")

    where = " AND ".join(clauses)
    total = int(
            connection.execute(
                f"SELECT COUNT(*) FROM components WHERE {where}",
                parameters,
            ).fetchone()[0]
        )
    rows = connection.execute(
            f"""
            SELECT id, sku, name, category, package_name, location,
                   quantity, min_stock, updated_at,
                   quantity <= min_stock AS low_stock
            FROM components
            WHERE {where}
            ORDER BY updated_at DESC, id ASC
            LIMIT ? OFFSET ?
            """,
            [*parameters, page_size, (page - 1) * page_size],
        ).fetchall()
    return AdminComponentPage(
        items=[dict(row) for row in rows],
        page=page,
        page_size=page_size,
        total=total,
    )


def load_admin_component(
    connection: sqlite3.Connection,
    component_id: str,
) -> dict[str, object] | None:
    row = connection.execute(
            """
            SELECT id, sku, name, category, package_name, location, description,
                   quantity, min_stock, updated_at, inventory_managed,
                   quantity <= min_stock AS low_stock
            FROM components
            WHERE id = ? AND deleted = 0
            """,
            (component_id,),
        ).fetchone()
    if row is None:
        return None
    result = dict(row)
    result["inventory_managed"] = bool(result["inventory_managed"])
    result["allocations"] = [
            dict(allocation)
            for allocation in connection.execute(
                """
                SELECT location_id, quantity
                FROM component_allocations
                WHERE component_id = ?
                ORDER BY location_id ASC
                """,
                (component_id,),
            ).fetchall()
        ]
    return result


def _normalize_search_term(value: str) -> str:
    normalized = (
        unicodedata.normalize("NFKC", value)
        .casefold()
        .replace("μ", "u")
        .replace("ω", "ohm")
    )
    result = []
    for index, char in enumerate(normalized):
        if char == ".":
            if (
                0 < index < len(normalized) - 1
                and normalized[index - 1].isdigit()
                and normalized[index + 1].isdigit()
            ):
                result.append(char)
        elif not char.isspace() and char not in "-_ /\\":
            result.append(char)
    return "".join(result)


def _component_search_text(*fields: str | None) -> str:
    # NUL boundaries prevent a term from matching across two unrelated fields.
    return "\x00".join(_normalize_search_term(field or "") for field in fields)


def load_admin_snapshot(
    connection: sqlite3.Connection, settings: Settings, *, role: str = "admin"
) -> AdminSnapshot:
    aggregate = connection.execute(
            """
            SELECT
                COUNT(*) AS component_count,
                COALESCE(SUM(quantity), 0) AS total_units,
                COALESCE(SUM(CASE WHEN deleted = 0 AND quantity <= min_stock THEN 1 ELSE 0 END), 0)
                    AS low_stock_count
            FROM components
            WHERE deleted = 0
            """
        ).fetchone()
    movement_row = connection.execute(
            "SELECT COUNT(*) AS movement_count FROM stock_movements WHERE deleted = 0"
        ).fetchone()
    low_stock_rows = connection.execute(
            """
            SELECT
                id,
                sku,
                name,
                location,
                quantity,
                min_stock,
                updated_at
            FROM components
            WHERE deleted = 0 AND quantity <= min_stock
            ORDER BY updated_at DESC
            LIMIT 8
            """
        ).fetchall()
    component_rows = connection.execute(
            """
            SELECT
                id,
                sku,
                name,
                category,
                package_name,
                location,
                quantity,
                min_stock,
                updated_at,
                CASE
                    WHEN quantity <= min_stock THEN 'Low stock'
                    ELSE 'Healthy'
                END AS status
            FROM components
            WHERE deleted = 0
            ORDER BY updated_at DESC
            LIMIT 12
            """
        ).fetchall()
    movement_rows = connection.execute(
            """
            SELECT
                m.id,
                COALESCE(c.sku, m.component_id) AS sku,
                COALESCE(c.name, m.component_id) AS component_name,
                m.movement_type,
                m.quantity,
                m.reason,
                m.note,
                m.happened_at,
                m.updated_at
            FROM stock_movements m
            LEFT JOIN components c ON c.id = m.component_id
            WHERE m.deleted = 0
            ORDER BY m.happened_at DESC
            LIMIT 12
            """
        ).fetchall()

    sync_notes = [
        f'API health endpoint remains available at http://{settings.app_host}:{settings.app_port}/health.',
        'Managed inventory uses base_updated_at conflict checks; legacy snapshots use last-write-wins.',
        'Device registry is not implemented yet; sync visibility is derived from server-side inventory state.',
    ]
    if role == "admin" and settings.api_token == 'change-me':
        sync_notes.insert(0, 'API token is still the default value. Replace it before deployment.')

    return AdminSnapshot(
        component_count=int(aggregate['component_count']),
        total_units=int(aggregate['total_units']),
        low_stock_count=int(aggregate['low_stock_count']),
        movement_count=int(movement_row['movement_count']),
        low_stock_components=[dict(row) for row in low_stock_rows],
        recent_components=[dict(row) for row in component_rows],
        recent_movements=[dict(row) for row in movement_rows],
        sync_notes=sync_notes,
    )
