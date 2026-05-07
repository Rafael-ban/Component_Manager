from __future__ import annotations

from nicegui import ui

from ..config import get_settings
from .data import load_admin_snapshot


def register_admin_ui(fastapi_app) -> None:
    @ui.page('/')
    def admin_dashboard() -> None:
        _build_shell('Dashboard', _render_dashboard)

    @ui.page('/inventory')
    def admin_inventory() -> None:
        _build_shell('Inventory', _render_inventory)

    @ui.page('/sync')
    def admin_sync() -> None:
        _build_shell('Sync', _render_sync)

    @ui.page('/settings')
    def admin_settings() -> None:
        _build_shell('Settings', _render_settings)

    ui.run_with(
        fastapi_app,
        mount_path='/admin',
        title='Component Vault Admin',
        dark=False,
    )


def _build_shell(title: str, renderer) -> None:
    _inject_theme_styles()
    settings = get_settings()
    snapshot = load_admin_snapshot(settings)

    with ui.left_drawer(top_corner=True, bottom_corner=True).classes(
        'bg-slate-900 text-white'
    ):
        ui.label('Component Vault').classes('text-lg font-semibold mt-4')
        ui.label('Admin Console').classes('text-sm text-slate-300 mb-6')
        _nav_link('Dashboard', '/admin/')
        _nav_link('Inventory', '/admin/inventory')
        _nav_link('Sync', '/admin/sync')
        _nav_link('Settings', '/admin/settings')

    with ui.header(elevated=False).classes(
        'items-center justify-between bg-white/80 backdrop-blur px-6 py-4'
    ):
        with ui.column().classes('gap-0'):
            ui.label(title).classes('text-2xl font-semibold text-slate-950')
            ui.label(
                'Single-user inventory admin for the self-hosted sync service.'
            ).classes('text-sm text-slate-500')
        with ui.row().classes('items-center gap-3'):
            ui.badge('FastAPI API online', color='emerald')
            ui.badge('NiceGUI admin', color='primary')

    with ui.column().classes('cv-shell w-full gap-6 p-6'):
        renderer(settings, snapshot)


def _render_dashboard(settings, snapshot) -> None:
    with ui.row().classes('w-full gap-4 max-[900px]:flex-col'):
        _stat_card('Active components', snapshot.component_count, 'Tracked SKUs on the server')
        _stat_card('Units on hand', snapshot.total_units, 'Sum of server-side quantities')
        _stat_card('Low stock', snapshot.low_stock_count, 'Components at or below min_stock')
        _stat_card('Movements', snapshot.movement_count, 'Recorded inventory transactions')

    with ui.row().classes('w-full gap-6 max-[1100px]:flex-col'):
        with ui.card().classes('cv-card flex-1 p-5'):
            ui.label('Recent components').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'Latest component records visible to the sync service.'
            ).classes('cv-label mb-4')
            _components_table(snapshot.recent_components)

        with ui.card().classes('cv-card w-[360px] max-[1100px]:w-full p-5'):
            ui.label('Operational notes').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'Deployment and sync behaviors surfaced for administrators.'
            ).classes('cv-label mb-4')
            for note in snapshot.sync_notes:
                with ui.row().classes('items-start gap-3 mb-3'):
                    ui.icon('info').classes('text-sky-600 mt-0.5')
                    ui.label(note).classes('text-sm text-slate-700')


def _render_inventory(settings, snapshot) -> None:
    with ui.row().classes('w-full gap-4 max-[900px]:flex-col'):
        _stat_card('Active components', snapshot.component_count, 'Tracked server-side component records')
        _stat_card('Low stock', snapshot.low_stock_count, 'Rows at or below min_stock')
        _stat_card(
            'Healthy stock',
            max(snapshot.component_count - snapshot.low_stock_count, 0),
            'Active rows above the minimum threshold',
        )
        _stat_card('Units on hand', snapshot.total_units, 'Current summed quantity on the server')

    with ui.row().classes('w-full gap-6 max-[1100px]:flex-col'):
        with ui.card().classes('cv-card flex-1 p-5'):
            ui.label('Low-stock watchlist').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'Use this view to quickly spot components that need replenishment.'
            ).classes('cv-label mb-4')
            _low_stock_list(snapshot.low_stock_components)

        with ui.card().classes('cv-card w-[360px] max-[1100px]:w-full p-5'):
            ui.label('Inventory posture').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'The server reflects the latest accepted state after last-write-wins sync.'
            ).classes('cv-label mb-4')
            _settings_grid(
                [
                    ('Conflict mode', 'Last write wins on updated_at'),
                    ('Soft delete', 'Enabled for synchronized rows'),
                    ('Low-stock trigger', 'quantity <= min_stock'),
                    ('Inventory browser', 'Server-side read verification only'),
                ]
            )

    with ui.card().classes('cv-card p-5'):
        ui.label('Recently updated inventory').classes('text-lg font-semibold text-slate-950')
        ui.label(
            'Stable-mode inventory listing for server-side verification.'
        ).classes('cv-label mb-4')
        _components_table(snapshot.recent_components, include_updated=True)


def _render_sync(settings, snapshot) -> None:
    with ui.row().classes('w-full gap-4 max-[900px]:flex-col'):
        _stat_card('Movements', snapshot.movement_count, 'Stored stock movement events')
        _stat_card('Components', snapshot.component_count, 'Active synchronized component rows')
        _stat_card('Low stock', snapshot.low_stock_count, 'Components at or below min_stock')
        _stat_card('Units on hand', snapshot.total_units, 'Current summed quantity on the server')

    with ui.row().classes('w-full gap-6 max-[1100px]:flex-col'):
        with ui.card().classes('cv-card flex-1 p-5'):
            ui.label('Recent stock movements').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'Latest movement activity received by the sync API.'
            ).classes('cv-label mb-4')
            _movements_table(snapshot.recent_movements)

        with ui.card().classes('cv-card w-[360px] max-[1100px]:w-full p-5'):
            ui.label('Sync posture').classes('text-lg font-semibold text-slate-950')
            ui.label('Current server sync assumptions.').classes('cv-label mb-4')
            _settings_grid(
                [
                    ('Conflict mode', 'Last write wins'),
                    ('Soft delete', 'Enabled'),
                    ('Device registry', 'Not implemented'),
                    ('Authenticated routes', '/auth/ping, /sync/push, /sync/pull'),
                ]
            )

            ui.separator().classes('my-4')
            ui.label('Attention items').classes('text-sm font-semibold text-slate-900')
            for item in (
                'Replace the default API token before exposing the service outside local development.'
                if settings.api_token == 'change-me'
                else 'Custom API token is configured for authenticated sync routes.',
                'Clients still own all inventory writes; this admin UI is read-only.',
                'Sync visibility is derived from the latest accepted server-side rows.',
            ):
                with ui.row().classes('items-start gap-3 mb-3'):
                    ui.icon('priority_high').classes('text-amber-600 mt-0.5')
                    ui.label(item).classes('text-sm text-slate-700')


def _render_settings(settings, snapshot) -> None:
    with ui.row().classes('w-full gap-6 max-[1100px]:flex-col'):
        with ui.card().classes('cv-card flex-1 p-5'):
            ui.label('Runtime configuration').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'The admin UI reads the same process settings as the API service.'
            ).classes('cv-label mb-4')
            _settings_grid(
                [
                    ('App name', settings.app_name),
                    ('Host', settings.app_host),
                    ('Port', str(settings.app_port)),
                    ('Database path', settings.database_path),
                    ('API token status', 'Default token in use' if settings.api_token == 'change-me' else 'Custom token configured'),
                ]
            )

        with ui.card().classes('cv-card w-[360px] max-[1100px]:w-full p-5'):
            ui.label('Access posture').classes('text-lg font-semibold text-slate-950')
            ui.label(
                'Operational endpoints and deployment-sensitive settings.'
            ).classes('cv-label mb-4')
            _settings_grid(
                [
                    ('Health URL', f"http://{settings.app_host}:{settings.app_port}/health"),
                    ('Admin URL', f"http://{settings.app_host}:{settings.app_port}/admin/"),
                    ('Sync API', f"http://{settings.app_host}:{settings.app_port}/sync/pull"),
                    ('Token risk', 'Replace before deployment' if settings.api_token == 'change-me' else 'Custom token present'),
                ]
            )

    with ui.card().classes('cv-card p-5'):
        ui.label('Next backend additions').classes('text-lg font-semibold text-slate-950')
        ui.label(
            'These areas are intentionally left as admin-phase follow-ups.'
        ).classes('cv-label mb-4')
        for item in (
            'Per-device sync audit log',
            'Conflict history and resolution records',
            'Admin authentication beyond infrastructure-level access control',
        ):
            with ui.row().classes('items-start gap-3 mb-3'):
                ui.icon('schedule').classes('text-amber-600 mt-0.5')
                ui.label(item).classes('text-sm text-slate-700')


def _stat_card(title: str, value: int, subtitle: str) -> None:
    with ui.card().classes('cv-card min-w-[220px] flex-1 p-5'):
        ui.label(title).classes('cv-label')
        ui.label(str(value)).classes('cv-value')
        ui.label(subtitle).classes('text-sm text-slate-500')


def _components_table(
    rows: list[dict[str, object]],
    *,
    include_updated: bool = False,
) -> None:
    columns = [
        {'name': 'sku', 'label': 'SKU', 'field': 'sku', 'align': 'left'},
        {'name': 'name', 'label': 'Name', 'field': 'name', 'align': 'left'},
        {'name': 'category', 'label': 'Category', 'field': 'category', 'align': 'left'},
        {'name': 'location', 'label': 'Location', 'field': 'location', 'align': 'left'},
        {'name': 'quantity', 'label': 'Qty', 'field': 'quantity', 'align': 'right'},
        {'name': 'status', 'label': 'Status', 'field': 'status', 'align': 'left'},
    ]
    if include_updated:
        columns.append(
            {
                'name': 'updated_at',
                'label': 'Updated',
                'field': 'updated_at',
                'align': 'left',
            }
        )

    if not rows:
        ui.label('No component records are available yet.').classes('text-sm text-slate-500')
        return

    ui.table(columns=columns, rows=rows, row_key='id').props(
        'flat bordered wrap-cells'
    ).classes('w-full')


def _movements_table(rows: list[dict[str, object]]) -> None:
    columns = [
        {'name': 'sku', 'label': 'SKU', 'field': 'sku', 'align': 'left'},
        {'name': 'component_name', 'label': 'Component', 'field': 'component_name', 'align': 'left'},
        {'name': 'movement_type', 'label': 'Type', 'field': 'movement_type', 'align': 'left'},
        {'name': 'quantity', 'label': 'Qty', 'field': 'quantity', 'align': 'right'},
        {'name': 'reason', 'label': 'Reason', 'field': 'reason', 'align': 'left'},
        {'name': 'happened_at', 'label': 'Happened', 'field': 'happened_at', 'align': 'left'},
    ]

    if not rows:
        ui.label('No stock movement activity is available yet.').classes('text-sm text-slate-500')
        return

    ui.table(columns=columns, rows=rows, row_key='id').props(
        'flat bordered wrap-cells'
    ).classes('w-full')


def _low_stock_list(rows: list[dict[str, object]]) -> None:
    if not rows:
        ui.label('No low-stock components are currently flagged on the server.').classes(
            'text-sm text-slate-500'
        )
        return

    with ui.column().classes('w-full gap-0'):
        for row in rows:
            with ui.row().classes(
                'w-full items-start justify-between gap-4 border-b border-slate-200 py-3'
            ):
                with ui.column().classes('gap-0'):
                    ui.label(f"{row['name']} ({row['sku']})").classes(
                        'text-sm font-semibold text-slate-900'
                    )
                    ui.label(f"{row['location']} | Updated {row['updated_at']}").classes(
                        'text-sm text-slate-500'
                    )
                with ui.column().classes('items-end gap-0'):
                    ui.label(f"{row['quantity']} / {row['min_stock']}").classes(
                        'text-sm font-semibold text-amber-700'
                    )
                    ui.label('On hand / Min').classes('text-xs text-slate-500')


def _settings_grid(rows: list[tuple[str, str]]) -> None:
    with ui.column().classes('w-full gap-0'):
        for label, value in rows:
            with ui.row().classes(
                'w-full items-start justify-between gap-6 border-b border-slate-200 py-3'
            ):
                ui.label(label).classes('text-sm font-medium text-slate-600')
                ui.label(value).classes('text-sm text-slate-900 text-right')


def _nav_link(label: str, href: str) -> None:
    ui.link(label, href).classes(
        'block rounded-xl px-3 py-2 text-sm text-slate-100 no-underline hover:bg-slate-800'
    )


def _inject_theme_styles() -> None:
    ui.add_head_html(
        '''
        <style>
          body {
            background: #f5f7fb;
          }
          .cv-shell {
            background: linear-gradient(180deg, #f5f7fb 0%, #edf3f7 100%);
          }
          .cv-card {
            border-radius: 22px;
            border: 1px solid rgba(148, 163, 184, 0.2);
            box-shadow: 0 20px 40px rgba(15, 23, 42, 0.06);
          }
          .cv-label {
            color: #475569;
            font-size: 0.92rem;
            letter-spacing: 0.01em;
          }
          .cv-value {
            color: #0f172a;
            font-size: 2rem;
            font-weight: 700;
          }
        </style>
        '''
    )
