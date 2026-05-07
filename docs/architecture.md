# Architecture Notes

## Topology

- `android-client/` is the Android Jetpack Compose client.
- `windows-client/` is the WinUI 3 Windows desktop client.
- `client/` remains as a legacy Flutter reference only.
- `server/` is a FastAPI service for single-user self-hosted sync with a
  NiceGUI admin console mounted in the same process.
- Android, Windows, and server all use SQLite in the current architecture, and
  each runtime is now implemented against live storage.

## Android Client

- Built with Jetpack Compose and Material 3.
- Current implementation is a stable-mode local-first client backed by
  on-device SQLite and shared preferences.
- User-facing copy is now routed through Android string resources, with
  Simplified Chinese (`zh-CN`) coverage for the primary UI.
- Navigation covers dashboard, components, movements, and sync settings.
- Components, movements, and settings screens now use stable-mode summary and
  detail layouts tuned for mobile-first Material 3 UI.
- The app writes locally first, queues changed entities, and optionally syncs
  to the FastAPI service.
- Android build verification completed successfully on `2026-05-07` on the
  current host machine after local SDK and JDK configuration.

## Windows Client

- Built with WinUI 3 and Windows App SDK.
- Current implementation is a native desktop client backed by local SQLite in
  the user's local app data directory.
- Release distribution is now oriented around a single-project MSIX package
  instead of shipping the raw unpackaged publish directory.
- Navigation uses `NavigationView` with dedicated dashboard, components,
  movements, and settings pages.
- Components, movements, and settings pages use desktop-oriented summary and
  detail panels rather than mobile-style stacked forms.
- The implemented behavior matches Android at the business level and follows
  Windows-native layout conventions.
- Windows build verification completed successfully on `2026-05-07`.

## Legacy Flutter Reference

- The old Flutter client preserves field naming, workflow intent, and prior UI
  exploration.
- It should be used only as a migration reference, not as the active client UI
  implementation target.

## Target Client Persistence

### components

- Primary inventory entity rendered by the dashboard and list views.
- Active records must have unique `sku`.
- `quantity` and `min_stock` are constrained to non-negative values.

### stock_movements

- Immutable-style movement log for inbound, outbound, and adjustment events.
- Each row references a component by `component_id`.
- Movement writes are also responsible for updating the current component
  quantity.

### sync_queue

- Local-only queue of entities pending upload.
- Stores `entity_type`, `entity_id`, `entity_updated_at`, and `created_at`.
- Cleared after a successful push or when a remote update supersedes a pending
  local queue item.

## Server

- FastAPI exposes a small token-protected sync API.
- NiceGUI is mounted in the same service at `/admin`.
- SQLite is used for a single-user self-hosted deployment.
- Incoming entities are merged with last-write-wins based on `updated_at`.
- `GET /health` is public.
- `POST /auth/ping`, `POST /sync/push`, and `GET /sync/pull` require the shared
  bearer token.
- Duplicate active `sku` values are rejected with a conflict response.

## Server Admin UI

- NiceGUI provides a stable-mode admin console for inventory administrators.
- Initial pages cover:
  - dashboard metrics
  - recent inventory records
  - recent stock movements
  - runtime and sync posture details
- inventory and sync pages emphasize low-stock watchlists, sync posture, and
  read-only operational checks for administrators.
- The admin UI focuses on monitoring, inventory posture, and runtime checks
  rather than replacing API-driven client editing flows.
- It reflects live server SQLite content.

## Sync Flow

1. The user changes inventory on the client.
2. The client commits the change to local SQLite first.
3. The client appends the changed entity to `sync_queue`.
4. Manual sync or auto sync pushes queued entities to `/sync/push`.
5. The client then pulls remote changes from `/sync/pull`.
6. Remote rows overwrite older local rows by comparing `updated_at`.
7. Matching or older queue entries are cleared after the remote snapshot lands.

## Conflict Model

- Conflict resolution is last-write-wins on UTC `updated_at`.
- Soft deletes are synchronized as normal entity updates with `deleted = true`.
- Local validation prevents negative stock and duplicate active `sku`.
- Server validation repeats the critical uniqueness and schema checks.

## Entities

### Component

- `id`
- `sku`
- `name`
- `category`
- `package_name`
- `location`
- `description`
- `quantity`
- `min_stock`
- `updated_at`
- `deleted`

### Stock Movement

- `id`
- `component_id`
- `movement_type`
- `quantity`
- `reason`
- `note`
- `happened_at`
- `updated_at`
- `deleted`

## Settings Surface

The native clients persist these sync settings locally:

- `device_id`
- `server_base_url`
- `api_token`
- `auto_sync_enabled`
- `last_synced_at`
- `last_sync_message`
