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
  Chinese-first default UI, persisted app-language switching (`zh-CN` and
  `en`), and matching Simplified Chinese (`zh-CN`) resource coverage for the
  primary screens. Compose Preview uses injected `ComponentVaultStrings`
  sample bundles plus content-level preview composables so Preview rendering
  does not depend on Android Studio resolving the runtime `R.string` graph.
- Navigation now centers on four adaptive destinations:
  `Inventory`, `Movements`, `Overview`, and `Settings`.
- `Inventory` is the default high-frequency workflow and uses dense search,
  filter, and list-first layouts on phones, plus persistent list-detail panes
  on larger widths. The current shell is built on official Material 3 adaptive
  primitives: `NavigationSuiteScaffold` for top-level navigation,
  `currentWindowAdaptiveInfo` for width class handling, and
  `NavigableListDetailPaneScaffold` for Inventory list-detail behavior. The
  Inventory header now treats search as the primary action through
  `SearchBar`, with fixed horizontal filters and denser inventory rows instead
  of the older stacked filter-card layout. It now also includes quantity-first
  import flows for JLC copied mobile product text, package QR payloads,
  supplier packaging OCR, and generated warehouse labels.
- Package QR scanning now runs through an in-app CameraX surface backed by the
  bundled ML Kit Barcode Scanning API, so first use does not depend on Google
  Play services downloading an external scanner module.
- Supplier text recognition runs through an in-app CameraX surface backed by
  bundled ML Kit Chinese text recognition. The current Android flow now asks
  the user to align packaging text, capture one preview frame, build a
  structured OCR result with ordered text lines, and only then parse packaging
  fields into the quantity-first import confirmation form.
- Android OCR now goes through a dedicated engine abstraction with local
  preference storage. `Auto` currently resolves to the bundled ML Kit engine,
  `ML Kit` forces the same offline recognizer explicitly, and
  `Paddle experimental` remains an honest compile-safe placeholder for a
  future native model bundle rather than pretending to be active in the
  current codebase.
- Label generation is client-owned and stays schema-compatible: JLC-sourced
  items generate JLC-compatible QR payloads with app extension fields, while
  non-JLC items generate an app-specific warehouse QR payload. Both paths can
  be exported as PNG or PDF for physical bag, bin, or drawer labels. The
  current Android implementation uses three client-side label templates:
  `10x40mm QR`, `30x40mm QR`, and a pure text strip. The narrow label renders
  horizontally with the QR code on the right and now emits a shorter
  lookup-first warehouse payload (`cvl3|sku|qty`) so dense small labels stay
  scan-friendly; legacy compact `cvl2` labels and app-generated
  JLC-compatible labels still round-trip. The `30x40mm` label now uses a
  horizontal physical `40x30mm` page with the QR on the left, a centered
  package-plus-name stack on the right, and bottom-aligned metadata rows, and
  the pure text strip uses single-line fit-to-fill typography. Preview now
  respects each template's physical aspect ratio, exports use the resolved
  label dimensions directly, and a bounded two-zone renderer keeps dynamic
  field text out of the QR safe area. Movement import and label scan flows
  resolve the compact `10x40mm` payload locally by `sku` so no sync API or
  schema change is required for the shorter code path.
- `Movements` uses the same adaptive approach: compact history-first layouts on
  phones and split history/detail arrangements on larger widths. It now also
  supports a scan-first workflow for already-generated warehouse labels:
  CameraX + bundled ML Kit barcode scanning returns raw QR content, the client
  resolves the label locally by parsed `sku`, and the user then chooses a
  quick `Inbound`, `Outbound`, or `Adjustment` action and completes the final
  movement form inside the same matched bottom sheet instead of navigating to a
  separate movement page. The movement scanner now runs in a dedicated
  small-label mode with higher analysis resolution, ML Kit
  potential-barcode detection, zoom suggestions, and tap-to-focus to improve
  read rates on narrow printed labels.
- `Inventory` now routes primary create flows through a unified add-entry sheet
  so the main action consistently branches into `Import` or `Manual add`.
  Component create/import forms stay open on save failure, surface repository
  errors inline, and reselect the saved component after a successful local
  reload so compact detail flows do not lose context.
- `Overview` is now a summary surface that routes users back into inventory or
  movement flows rather than acting as the primary editing page.
- `Settings` is organized as grouped sync forms and status blocks instead of
  large summary-card stacks, with local-only import defaults and an About
  section.
- The app writes locally first, queues changed entities, and optionally syncs
  to the FastAPI service.
- JLC import metadata, OCR-derived metadata, and label round-trip hints are
  mapped into existing component fields and appended to `description` so these
  workflows do not require sync API or schema changes.
- Android local storage now also includes an `import_learning_mappings` SQLite
  table for device-only JLC import learning keyed by source SKU with MPN
  fallback reuse.
- JLC text and QR imports are seeded by parser output, then enriched by
  bundled offline recognition rules, device-only learned mappings, and finally
  optional server-side `GET /admin-api/part-lookup` metadata. The local rule
  pack now also recognizes more vendor numbering schemes so package and
  category inference can come from model families instead of only raw
  keywords. Canonical component names now stay blank until explicit source
  text, learned mappings, or server metadata confirms them, so raw model codes
  are no longer written into the saved `name` field by fallback. Local parsing
  now also treats supplier `vendor` fields as a `brand` fallback and rejects
  model-like tokens when persisting learned names. User edits in the import
  confirmation form remain authoritative.
- Android still keeps a local cache for repeated server-assisted lookups, while
  `/admin-api/lcsc/lookup` now sits behind the newer hybrid recognition flow as
  a direct compatibility endpoint when official LCSC credentials are available.
- Android build verification completed successfully on `2026-05-08` on the
  current host machine after local SDK and JDK configuration. The current
  Android build baseline is AGP `8.10.1`, Gradle wrapper `8.11.1`, Java 17
  bytecode, SDK Build Tools `35.0.0`, Kotlin `2.0.21`, and Lifecycle `2.9.2`.
  The last full Android `assembleDebug` and `assembleRelease` verification on
  this host completed on `2026-05-16`. After the build-chain refresh,
  `.\android-client\gradlew.bat -p android-client help --no-daemon`
  completed successfully on `2026-05-17`; current local APK assembly is
  blocked on this host because the Android SDK directory is not writable, so
  AGP cannot auto-install `build-tools;35.0.0`. Kotlin and Compose source
  compilation are not the blocker in the current state.
  Preview-focused
  `Phone`, `Tablet`, `Locale`, `Theme`, `Accessibility`, `Shell`, and `Dialogs`
  surfaces are now isolated under `ui/screen/preview/`.

## Versioning

- `docs/CHANGELOG.md` is the repository-wide version source.
- A local pre-commit hook consumes the `Unreleased` changelog section and
  synchronizes semantic versions to Android, admin-web, and Windows targets.
- The default-branch GitHub workflow `release-from-changelog.yml` also uses the
  changelog as input. It applies the same sync server-side when needed, pushes
  the matching `vX.Y.Z` tag, and then directly invokes the reusable release
  workflow in the same orchestration chain.
- Android uses semver for `versionName` plus a monotonically increasing
  `versionCode`.
- Windows package and assembly metadata use the same semver mapped to four-part
  versions as `major.minor.patch.0`.
- The release path is:
  `docs/CHANGELOG.md -> tools/versioning/sync_version.py -> release commit if needed -> vX.Y.Z tag -> reusable .github/workflows/release.yml build and GitHub Release publish`

## Windows Client

- Built with WinUI 3 and Windows App SDK.
- Current implementation is a native desktop client backed by local SQLite in
  the user's local app data directory.
- User-facing copy is now organized around Chinese-first WinUI pages while
  retaining Windows-native layout and interaction patterns.
- Release distribution now supports both a test-signed MSIX install flow and a
  portable unpackaged publish that can be zipped and launched directly.
- Navigation uses `NavigationView` with four top-level destinations:
  `Inventory`, `Movements`, `Overview`, and `Settings`.
- `Inventory` is the default landing page and uses a dense desktop workspace
  with search, fixed filters, a continuous list, and a persistent inspector.
- `Movements` is organized as a history-first ledger with compact metrics and a
  right-side audit inspector.
- `Overview` is a summary surface with KPI blocks, low-stock watch entries,
  recent activity, and sync posture instead of acting as the primary edit page.
- `Settings` is organized as grouped sync forms plus diagnostics rather than
  mirrored summary cards.
- The implemented behavior matches Android at the business level and follows
  Windows-native layout conventions.
- Startup diagnostics now log fatal launch/runtime exceptions under
  `%LOCALAPPDATA%\ComponentVault\logs\startup.log` and surface the log path in
  a native Windows error dialog.
- Windows build verification completed successfully on `2026-05-09`, and the
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
- The server now exposes `GET /admin-api/part-lookup` as a token-protected
  hybrid recognition endpoint that applies bundled or refreshed rule packs
  first and then optionally merges official LCSC metadata when credentials are
  configured or public LCSC product-page metadata when
  `ENABLE_WEB_FALLBACK_RESOLVERS=true`.
- The server also exposes `GET /admin-api/recognition-rules/meta` and
  `POST /admin-api/recognition-rules/refresh` so operators can inspect or
  refresh the active rule pack without changing the sync schema.
- `GET /admin-api/lcsc/lookup` remains available as a token-protected direct
  compatibility proxy to the LCSC OpenAPI, with short in-memory response
  caching and configurable credentials through environment variables.
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

The Android client also persists local-only app behavior settings:

- `default_import_location`
- `last_import_location`
- `default_import_min_stock`
- `remember_last_import_location`
- `sync_after_local_changes`
- `enable_local_auto_recognition`
- `prefer_aggressive_auto_recognition`
- `enable_local_import_learning`
- `enable_server_jlc_lookup`
- `ocr_engine_mode`
- `app_language`
