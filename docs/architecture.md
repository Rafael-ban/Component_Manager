# Architecture Notes

## Topology

- `android-client/` is the Android Jetpack Compose client.
- `windows-client/` is the WinUI 3 Windows desktop client.
- `admin-web/` is a separated React + `shadcn/ui` operations console.
- `client/` remains as a legacy Flutter reference only.
- `server/` is a FastAPI service for single-user self-hosted sync and
  read-only admin APIs consumed by the web console.
- Android, Windows, and server all use SQLite in the current architecture, and
  each runtime is now implemented against live storage.

## Android Client

- Built with Jetpack Compose and Material 3.
- Current implementation is a stable-mode local-first client backed by
  on-device SQLite and shared preferences.
- User-facing copy is now routed through Android string resources, with a
  Chinese-first default UI and matching Simplified Chinese (`zh-CN`) resource
  coverage for the primary screens. Compose Preview uses injected
  `ComponentVaultStrings` sample bundles plus content-level preview composables
  so Preview rendering does not depend on Android Studio resolving the runtime
  `R.string` graph.
- Navigation now centers on four adaptive destinations:
  `Inventory`, `Movements`, `Overview`, and `Settings`.
- `Inventory` is the default high-frequency workflow and uses dense search,
  filter, and list-first layouts on phones, plus persistent list-detail panes
  on larger widths.
- `Movements` uses the same adaptive approach: compact history-first layouts on
  phones and split history/detail arrangements on larger widths.
- `Overview` is now a summary surface that routes users back into inventory or
  movement flows rather than acting as the primary editing page.
- `Settings` is organized as grouped sync forms and status blocks instead of
  large summary-card stacks.
- The app writes locally first, queues changed entities, and optionally syncs
  to the FastAPI service.
- Android build verification completed successfully on `2026-05-08` on the
  current host machine after local SDK and JDK configuration. Preview-focused
  `Phone`, `Tablet`, `Locale`, `Theme`, `Accessibility`, `Shell`, and `Dialogs`
  surfaces are now isolated under `ui/screen/preview/`.

## Versioning

- `docs/CHANGELOG.md` is the repository-wide version source.
- A local pre-commit hook consumes the `Unreleased` changelog section and
  synchronizes semantic versions to Android, admin-web, and Windows targets.
- Android uses semver for `versionName` plus a monotonically increasing
  `versionCode`.
- Windows package and assembly metadata use the same semver mapped to four-part
  versions as `major.minor.patch.0`.

## Windows Client

- Built with WinUI 3 and Windows App SDK.
- Current implementation is a native desktop client backed by local SQLite in
  the user's local app data directory.
- User-facing copy is now organized around Chinese-first WinUI pages while
  retaining Windows-native layout and interaction patterns.
- Release distribution now supports both a test-signed MSIX install flow and a
  portable unpackaged publish that can be zipped and launched directly.
- Navigation uses `NavigationView` with dedicated dashboard, components,
  movements, and settings pages.
- Components, movements, and settings pages use desktop-oriented summary and
  detail panels rather than mobile-style stacked forms.
- The implemented behavior matches Android at the business level and follows
  Windows-native layout conventions.
- Startup diagnostics now log fatal launch/runtime exceptions under
  `%LOCALAPPDATA%\ComponentVault\logs\startup.log` and surface the log path in
  a native Windows error dialog.
- Windows build verification completed successfully on `2026-05-08`, and the
  portable publish path was verified successfully on `2026-05-08`.

## Legacy Flutter Reference

- The old Flutter client preserves field naming, workflow intent, and prior UI
  exploration.
- It should be used only as a migration reference, not as the active client UI
  implementation target.

## Target Client Persistence

### components

- Primary inventory entity rendered by overview summaries and inventory lists.
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
- FastAPI also exposes token-protected read-only admin APIs at `/admin-api/*`.
- SQLite is used for a single-user self-hosted deployment.
- Incoming entities are merged with last-write-wins based on `updated_at`.
- `GET /health` is public.
- `POST /auth/ping`, `POST /sync/push`, and `GET /sync/pull` require the shared
  bearer token.
- Duplicate active `sku` values are rejected with a conflict response.
- CORS is enabled for the configured admin web origins.

## Server Admin Web

- `admin-web/` provides a separated `shadcn/ui` admin console for inventory
  administrators.
- Initial pages cover:
  - dashboard metrics
  - recent inventory records
  - recent stock movements
  - runtime and sync posture details
- inventory and sync pages emphasize low-stock watchlists, sync posture, and
  read-only operational checks for administrators.
- The admin web app authenticates with the same shared bearer token already
  used by sync clients.
- The admin surface focuses on monitoring, inventory posture, and runtime
  checks rather than replacing API-driven client editing flows.
- It reflects live server SQLite content through dedicated `/admin-api/*`
  snapshot endpoints.

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
