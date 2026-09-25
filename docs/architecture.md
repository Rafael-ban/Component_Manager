# Architecture Notes

## Topology

- `android-client/` is the Android Jetpack Compose client.
- `windows-client/` is the WinUI 3 Windows desktop client.
- `admin-web/` is a separated React + `shadcn/ui` operations console.
- `client/` remains as a legacy Flutter reference only.
- `server/` is a FastAPI service for account-isolated self-hosted sync and
  inventory admin APIs, optional authenticated Web inventory writes, and MQTT
  configuration writes.
- Android, Windows, and server all use SQLite in the current architecture, and
  each runtime is now implemented against live storage.

The API and admin Web containers are built independently. `server-image.yml`
always builds and starts amd64 validation images, checks API/auth/database
startup, then fetches the Web index and its emitted JS/CSS assets. When Docker
Hub variables and token are configured, releases publish API tags `<version>`
and `latest`, plus Web tags `web-<version>` and `web-latest`, for amd64/arm64.
The 0.7.0 API and Web tags were verified published. Each subsequent release must
still verify its own publication. SQLite persists under `/data`.
`docker-compose.hub.yml` retains the source Compose data volume and exposes the
Web image through the optional `web` profile. Its default API/Web images use
`latest` / `web-latest`; updating requires pulling and recreating the containers.
See [Docker Hub operations](dockerhub.md).

The API provides a separate, small `/setup` page on its own origin for first-run
configuration and authenticated operational logs. It does not replace the React
inventory console. An explicit `admin_web_url` / `ADMIN_WEB_URL` identifies the
actual browser console entry, independently of the CORS origin list. Setup offers
an enter-console action after saving, leaving time to copy the token; credentials
are never appended to the navigation URL. The authenticated Web Settings form
uses the same `/setup/config` endpoints, respects environment-controlled fields,
and updates its browser session after a token change. Without a configured console
URL, `/setup` remains a complete configuration entry.
Initialization persists Token, allowed origins and the Web
write switch to `config.json` beside SQLite. Non-empty environment overrides
remain authoritative. Configuration writes are atomic; initializing twice cannot
replace a configured server without its current token. Logs under `logs/` are
bounded and contain operational events, not request bodies or credentials.

## Account ownership

The original `DATABASE_PATH` remains the administrator inventory. Its account
registry and stable server identity resolve high-entropy user keys to separate
`users/<account-id>.db` files beside it. Authentication, not request input,
chooses a database. Components, active SKU uniqueness, locations, allocations,
movements, tombstones, revision cursors and operation receipts are therefore
isolated together. User key digests are stored; full keys are returned only on
creation or reset. Disabling an account retains its database.

`/auth/me` identifies the server, account and role. Setup, logs, account creation
and global MQTT settings require the administrator. The first iteration keeps
MQTT publishing for administrator inventory only; ordinary writes never enter
that publisher's outbox. Shared public catalog caches contain no private stock.
Authenticated Web CRUD uses the account database and the existing global Web
write switch. The Web shell uses identity to show role-appropriate settings.

Each native installation currently has one local inventory workspace. Before
sync, it verifies the remote identity against a persistent local binding. A
mismatch stops both push and pull without modifying stock or adopting the new
identity. Legacy previously-synced workspaces initially bind only to admin;
ordinary sync requires the matching `X-Component-Vault-Account-Id` header so old
clients cannot accidentally upload an existing workspace to a new user key.
This header is a client compatibility check, not an authentication credential.
See [implementation plan and recovery boundaries](account-isolation-plan.md).

Web language preference uses browser-local `component-vault.language` with
`system`, `zh-CN` or `en`. It is a personal display choice, independent of server
configuration and stock values. Separate Web/API origins store it independently.

## Android Client

Batch scanning acknowledges a capture only after the draft queue accepts it.
`ContinuousCaptureGate` uses monotonic time for a configurable 0.5–5 second
interval, while full packaging payloads remain deduplicated for the session and
the persisted draft. `BatchScannerSettingsStore` persists the interval and
independent sound/vibration preferences outside the inventory schema. Success
copy remains visible for at least 800ms; optional feedback hardware failures
cannot cancel an accepted receipt.

Both clients export label-printing XLSX independently of full inventory backup.
The export contains selected columns and active components only. QR cells carry
the established standard/JLC-compatible long payload and `cvl3` short payload as
Excel text, so leading zeros and delimiters survive third-party label import.

Catalog routing prefers domestic LCSC for Chinese and international LCSC for
English. Android reads its saved app language; Windows uses its current UI
culture. A successful primary result is not overwritten by a secondary lookup.
Blocked/unavailable/unmatched/invalid responses retain explicit fallback reasons.
International keyword search is unavailable in the current public-page adapter,
so that path explicitly identifies its use of domestic search. No server or
OpenAPI key is introduced by this routing change.

Domestic lookup first parses valid product data before checking verification-page
markers. An app-session gate pauses further domestic attempts after a verification
block (120 seconds), a network failure (30 seconds), or a bounded Retry-After rate
limit. Different SKUs can still use the international fallback; explicit retry
clears the pause. Browser sessions are independent and browser verification does
not guarantee that native requests will be accepted. No challenge bypass is used.

Android label preview includes an opt-in Bluetooth diagnostic dialog with service
discovery and M1 connection modes. It lists nearby BLE and system-paired devices.
M1 mode uses a paired Classic/dual-mode device and an RFCOMM SPP socket to query
the model, then status only after a recognised M1 reply. Socket I/O runs off the UI
thread; cancellation, timeout and leaving the foreground close the socket and
discard obsolete session callbacks. The read-only query action sends no print
commands. A separate, explicitly confirmed test-label action rechecks the model
and status, then sends one fixed 203-dpi raster in complete-row blocks of at most
1024 raw bytes (20 complete-row frames for 320×480, matching the observed Hanma
3.3.4 M1 runtime geometry). Blocks use independently implemented literal-only LZO1X and the
M1 POLI header, preceded by right alignment and followed by a single label feed.
Explicit, non-persistent diagnostic choices omit only that final feed or replace
it with the upstream short-feed command; neither changes the raster or is the
default printing mode. Reports distinguish the requested feed mode from the numbers
of label-feed and short-feed commands successfully written. No vendor binary or JNI is used.
The existing 40 × 10 / 40 × 30 mm templates supply defaults for a device-local
test paper profile with dimensions, rotation and image offsets. It changes the
raster only; no firmware paper-type or calibration commands are sent. Normal
Android component-label printing has a separate foreground print center, reached
from label preview or inventory batch printing. `M1ComponentLabelRenderer` uses
the existing QR payload codec and text field rules with integer QR modules at
203 dpi; this paper raster is used for both preview and transmission. Paper
dimensions, rotation and offsets are local rendering options, not firmware writes.
Actual labels have no outer frame. Offsets translate the raster on the paper axes
without resizing QR modules; positive horizontal values move ink right. Physical
validation on one M1 / Android 15 / 40×60 mm gap stock confirmed scannable labels
and two automatic copies with consistent placement and no extra paper after a
device-specific +0.5 mm horizontal adjustment. Default offsets remain zero.
`LabelPrintController` serializes jobs through the same M1 single-label transport
with normal label feed. Each item stores a component snapshot and copy number;
printing does not change inventory. `LabelPrintQueueStore` uses an app-private
AtomicFile JSON checkpoint before sending; interrupted Sending items recover as
Uncertain. Failed or uncertain items require explicit review/retry/skip before
continuing, and clearing a job does not clear stock. The current queue can contain
at most 500 labels, with up to 99 copies per selected component. Only one current
job is retained. Queues do not sync to the server or appear in shared diagnostics.
Automatic advancement requires a processing notification and a clean model reply
without reported printer errors. This proves protocol readiness, not physical
label completion; missing readiness pauses for review. Leaving the foreground
stops the queue and closes the transport. Query reads separate known asynchronous
status frames and exact `dithering_finish\0` processing notifications from ordinary
model/status replies, preserving split frames across
query boundaries. Processing-event counts do not certify paper placement. Socket write
completion means sent/unconfirmed, not physical print completion. Interrupted
jobs are never automatically resent. M1 test printing retains a successful SPP
connection for the next explicit print in the foreground dialog, releasing it
on cancellation, error, leaving the screen or 60 seconds idle. Each print still
checks model/status; after sending it also queries the model before retaining
the connection. An inconclusive model reply is drained for 250 ms and retried once,
without replaying any print data. Continued uncertainty after completed writes is
sent/unconfirmed, not proof of disconnection; it releases the connection. Actual
I/O failures remain distinct. Classic SPP writes are split into at most 1024 bytes per write.
Reports retain partial stages and status codes, but exclude arbitrary device
names, addresses, serial numbers and raw replies; a model match is recorded only
as the fixed value M1. See [device test steps](printer-compatibility.md) and
[the APK-derived protocol evidence](m1-apk-analysis.md).

Android catalog cache keys include the preferred source. Only a primary-source
success is persisted; fallback results never suppress a subsequent attempt at
the preferred site. Metadata cache round-trips description, parameters and
datasheet links. Changing app language clears only lookup entries; startup
removes legacy unscoped, malformed and expired entries from that same prefix.

Database initialization failures are displayed before normal inventory controls
are available. Initial inventory reload errors use the same recovery screen.
Retry keeps the original database; diagnostic copy includes exception classes
and relevant class/method/line references, excluding exception messages and file
paths. Automatic startup sync begins only after the initial inventory load.
Pre-upgrade snapshots copy the SQLite database and WAL/SHM under a write lock;
failure stops the upgrade. Result-returning PRAGMAs use `rawQuery`, not `execSQL`.

Both native clients expose a Settings feedback surface. Reports are authored
locally; diagnostic inclusion is opt-in and editable. A bounded process-local
event log accepts fixed event types and numeric fields, never raw barcode
payloads, credentials, inventory or arbitrary exception text. Reports can be
copied/exported; the system browser opens a fixed GitHub new-issue URL for login
and final submission. Long bodies use an explicit copy/paste path rather than
silent truncation. No GitHub token, OAuth secret or feedback server is required.
See [feedback and scan diagnostics](feedback-and-scan-diagnostics.md).

- Built with Jetpack Compose and Material 3.
- Current implementation is a stable-mode local-first client backed by
  on-device SQLite and shared preferences.
- User-facing copy is now routed through Android string resources, with a
  Chinese-first default UI, persisted app-language switching (`zh-CN` and
  `en`), and matching Simplified Chinese (`zh-CN`) resource coverage for the
  primary screens. Compose Preview uses injected `ComponentVaultStrings`
  sample bundles plus content-level preview composables so Preview rendering
  does not depend on Android Studio resolving the runtime `R.string` graph.
- Navigation centers on four adaptive top-level destinations: `Home`,
  `Inventory`, `Records`, and `Settings`. Home presents the summary; settings
  has one main entry with section drill-down and direct sync-section routing.
  Inventory's Import and scan bottom sheet groups single-item lookup,
  batch JLC inbound, separate Project BOM and data migration routes, and manual entry. Settings > Inventory
  and data owns location management and Excel backup/restore. These secondary
  routes consume system Back and return to their parent. Batch review uses a
  bounded LazyColumn with collapsible summaries and fixed confirmation actions;
  editor fields scroll independently inside their dialog. See the
  [native UI layout contract](native-ui-redesign.md) and
  [cross-platform UI audit and phased plan](ui-ux-audit-2026-09-16.md).
- `SecondaryPageScaffold` provides shared app bars, insets and busy-aware Back
  handling for location management, backup, BOM and batch pages. The import
  chooser overlays the existing inventory route and dismisses back to it.
  Locations use a bounded list and separate create/edit dialogs. BOM configuration
  scrolls separately from its compact-summary, bounded-list preview stage.
- Testing a connection uses a transient URL/token and does not save preferences
  or reset the sync cursor. Sync actions require connection changes to be saved
  explicitly. The same rule applies to the Windows settings page.
- `Inventory` is the high-frequency workflow and uses dense search,
  filter, and list-first layouts on phones, plus persistent list-detail panes
  on larger widths. The current shell is built on official Material 3 adaptive
  primitives: `NavigationSuiteScaffold` for top-level navigation,
  window size classes for width handling, and
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
  the user to align packaging text, capture a photo with ImageCapture, build a
  structured OCR result with ordered text lines, and only then parse packaging
  fields into the quantity-first import confirmation form.
- Android OCR now goes through a dedicated engine abstraction with local
  preference storage. `Auto` currently resolves to the bundled ML Kit engine,
  `ML Kit` forces the same offline recognizer explicitly. The unavailable
  `Paddle experimental` choice is hidden and old preferences fall back to Auto.
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
  resolves the label locally by parsed `sku`. A successful match closes the
  scanner and opens a dedicated batch review page for `Inbound`, `Outbound`,
  or `Adjustment`, quantities and notes. Adding another scanned item requires
  an explicit action. Returning to history preserves the pending review;
  inventory changes only when the reviewed batch is confirmed. The scanner runs in a dedicated
  small-label mode with higher analysis resolution, ML Kit
  potential-barcode detection, zoom suggestions, and tap-to-focus to improve
  read rates on narrow printed labels.
- `Inventory` now routes primary create flows through a unified add-entry sheet
  so the main action consistently branches into `Import` or `Manual add`.
  Component create/import forms stay open on save failure, surface repository
  errors inline, and reselect the saved component after a successful local
  reload so compact detail flows do not lose context.
- `Home` is the summary landing surface and routes users into inventory or
  movement workflows. Transient action feedback uses a snackbar.
- `Settings` is organized as grouped sync forms and status blocks instead of
  large summary-card stacks, with local-only import defaults and an About
  section. On compact widths it uses a summary/list home plus full-screen
  section drill-down, and on larger widths it uses a persistent section list
  plus detail pane.
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
  optional direct LCSC public product metadata. The local rule
  pack now also recognizes more vendor numbering schemes so package and
  category inference can come from model families instead of only raw
  keywords. Canonical component names now stay blank until explicit source
  text, learned mappings, or public product metadata confirms them, so raw model codes
  are no longer written into the saved `name` field by fallback. Local parsing
  now also treats supplier `vendor` fields as a `brand` fallback and rejects
  model-like tokens when persisting learned names. User edits in the import
  confirmation form remain authoritative.
- Android keeps a local cache for repeated public lookups. Server lookup routes
  remain compatibility endpoints for older clients when credentials are available;
  they are no longer part of the normal Android import flow.
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
  Preview-focused `Phone`, `Medium`, `Tablet`, `Locale`, `Theme`,
  `Accessibility`, `Shell`, and `Dialogs` surfaces are now isolated under
  `ui/screen/preview/`.

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
- Dev tags (`vX.Y.Z-dev.N`) build the same `master` code as prereleases, injecting
  artifact version names and monotonically ordered Android version codes. They
  do not advance the stable changelog or publish Docker Hub images. Windows
  displays the informational version, including the dev suffix, while keeping
  numeric assembly metadata. See [release channels](runbook.md#stable-and-dev-publishing).
- CI explicitly includes branch pushes alongside pull requests. The reusable
  release workflow prioritizes supplied inputs because its event context comes
  from the caller; it must not require an event name of `workflow_call`.

## Windows Client

- Built with WinUI 3 and Windows App SDK.
- Current implementation is a native desktop client backed by local SQLite in
  the user's local app data directory.
- User-facing copy is now organized around Chinese-first WinUI pages while
  retaining Windows-native layout and interaction patterns.
- Release distribution uses a self-contained unpackaged WinUI 3 ZIP; no MSIX
  signing or certificate installation is required. CI checks actual window
  startup as well as compilation, including the archived release layout.
- Navigation uses `NavigationView` with `Home`, `Inventory`, `Records`, and
  `Project BOM` in the main list and one `Settings` footer destination.
- `Home` is the summary landing page. `Inventory` uses a dense desktop workspace
  with search, fixed filters, a continuous list, and a persistent inspector.
- `Movements` is organized as a history-first ledger with compact metrics and a
  right-side audit inspector.
- `Overview` is a summary surface with KPI blocks, low-stock watch entries,
  recent activity, and sync posture instead of acting as the primary edit page.
- `Settings` is organized as grouped sync forms plus diagnostics rather than
  mirrored summary cards. Native About pages identify the author as
  `Rafael-Ikaros`, show installed versions and GPLv3, and retain GitHub Release
  checks and platform download/fallback actions.
- The implemented behavior matches Android at the business level and follows
  Windows-native layout conventions.
- Windows Frame history retains the cached inventory page when entering batch
  inbound. Busy batch operations block Back and sidebar navigation; leaving an
  idle batch persists edits. Dashboard, movements and batch controls reflow at
  narrower window widths. Sync progress and result dialogs have an action guard.
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

Native batch JLC inbound uses private atomic JSON drafts with stable package-row
IDs and exact trimmed-payload deduplication. Capture does not perform network
lookup. Existing active SKUs reuse local metadata; new SKUs use bounded serial
catalog lookup. A separate pending screen retains failed or incomplete rows.
Reviewed rows commit component totals, allocations, inbound movements, sync
queue entries and local row receipts in one SQLite transaction. Android uses
`batch_jlc_receipts` (database v5), Windows uses `batch_inbound_receipts`.
Receipts reconcile drafts after interrupted commits and prevent local replay;
they do not synchronize between devices. Draft payloads and receipts are absent
from server/MQTT payloads and inventory Excel exports. See
[batch inbound contract](batch-jlc-inbound.md).

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
- Acknowledged only against the uploaded entity version, or when a remote
  update supersedes that version. New local edits made during network requests
  remain queued.

## Server

- FastAPI exposes a small token-protected sync API.
- FastAPI exposes token-protected inventory snapshots at `/admin-api/*` and
  MQTT configuration GET/POST at `/admin-api/mqtt/config`.
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
- Legacy incoming entities and location metadata use last-write-wins on `updated_at`;
  managed inventory uses a checked `base_updated_at` and a complete allocation snapshot.
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
  - searched and paginated active inventory
  - component details and optional inventory operations
  - recent stock movements
  - runtime and sync posture details
- Inventory reads `GET /admin-api/components` with bounded page/page-size,
  optional low-stock filtering, literal substring matching across SKU, name,
  category and default location, and deterministic
  `updated_at DESC, id ASC` ordering. `GET /admin-api/components/{id}` returns
  reliable component fields and location allocations. Both exclude deleted
  components; the original `/admin-api/inventory` overview remains unchanged.
- Inventory and sync pages emphasize low-stock watchlists and sync posture.
  With `WEB_INVENTORY_ENABLED=false` inventory stays read-only. When enabled,
  authenticated users can create locations and zero-stock components, edit
  metadata, and record inbound/outbound movements.
- The admin web app authenticates with the same shared bearer token already
  used by sync clients.
- Admin writes use `expected_updated_at` compare-and-swap and a persistent
  `admin_operation_receipts` row keyed by `request_id`. Receipt, component,
  allocation, movement, sync revision, and MQTT outbox changes share one short
  transaction. Exact retries return current entity state; reusing a request ID
  for different operation, target, or payload returns HTTP 409.
- It reflects live server SQLite content through dedicated `/admin-api/*`
  snapshot endpoints.
- Inventory query and page state live in the URL. A 401 clears the invalid
  local session, explains why login is required, and restores only an
  allowlisted internal Dashboard, Inventory, Sync or Settings path after token
  validation; inventory query parameters are retained.
- Desktop and narrow/mobile layouts expose the same operations with responsive
  forms, explicit pending/error states, and refreshed component versions after writes.

## Sync Flow

1. The user changes inventory on the client.
2. The client commits the change to local SQLite first.
3. The client appends the changed entity to `sync_queue`.
4. Manual sync or auto sync pushes queued entities to `/sync/push`.
5. The client then pulls remote changes from `/sync/pull`.
6. Remote rows overwrite older local rows by comparing `updated_at`.
7. Matching or older queue entries are cleared after the remote snapshot lands.

## Conflict Model

- Managed inventory uses optimistic `base_updated_at` checks; mismatches reject
  the entire push. Legacy records and location metadata retain UTC timestamp LWW.
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
- `fallback_server_base_url` (Windows; optional)
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

## Cursor sync and direct catalog enrichment (2026-09-15)

The server adds `sync_revision` to each synchronized entity table and maintains
one global counter in `sync_state`. Startup migrates and backfills existing
rows in a transaction without deleting data. Existing orphan movement rows are
retained during migration; all newly opened connections enforce foreign keys.
Push acquires a write transaction before the LWW read/check/write and commits
both entity families and revision allocation atomically. Pull reads the counter
and both families within a single SQLite snapshot. Timestamps use parsed UTC
instants; client modification time is not the replication cursor.

Windows stores nullable `last_sync_cursor` in `sync_settings` in the same
transaction as applied data. Android stores `sync_cursor` in preferences after
its data transaction: interruption before cursor persistence may repeat a pull
but cannot skip unapplied data. Both clients start with a full pull, discard
cursors on server changes, and use full pulls with old servers lacking cursors.
A server switch while a request is pending prevents the old response from
applying. Restoring or replacing a database is outside the cursor's identity
boundary; reset clients to a full pull after such maintenance.

Windows automatic sync debounces startup/local edits and serializes execution
with manual sync. New edits can request one later run; failed requests do not
schedule an unbounded retry loop.

Android `LcscPublicCatalog` extracts exact-SKU Product JSON-LD from a fixed
HTTPS LCSC product URL; it does not execute page JavaScript. `LcscPublicLookup`
provides bounded per-importer caching/backoff, and `InventoryRepository` retains
successful metadata for seven days in its existing local cache. Local parser,
rules, and learned mappings remain usable offline. The public lookup preserves
user overrides and never copies offers,
prices, or supplier stock into the local stock model. Direct query preference
is independent of synchronization and defaults to enabled.

## Project BOM and component-hub migration (2026-09-15)

Both native clients provide bounded CSV/XLSX readers, exact-SKU and
model/package matching, preview, explicit confirmation and transactional stock
depletion. Duplicate demands are aggregated by final component ID. Each commit
rechecks the previewed version and available quantity before writing any rows;
stock movements, components, pending sync queue entries and the release marker
commit together. Windows uses `bom_consumption_batches`; Android schema version
3 adds `bom_releases`. Both add `component_hub_imports` for local file-fingerprint
idempotency. Android SQLiteOpenHelper upgrades these tables without replacing
existing components or history.

Release and migration markers are device-local. Only the existing component
and movement entities synchronize; no project/BOM object was added to the wire
protocol. Managed snapshots now reject conflicting concurrent writes rather than
merging offline decrements. A production batch should be released
on one device and synchronized before using another device for the same stock.

Migration accepts the upstream `{components: [...]}` backup envelope, maps
`productCode`, `stock`, `threshold`, `encapStandard`, category and location, and
preserves source metadata in descriptions. It generates fresh local IDs;
positive starting stock also receives an inbound movement. Duplicates block by
default or are explicitly skipped. Input files and existing components are not
overwritten. See [BOM and migration guide](bom-and-migration.md) for supported
columns, file limits, unavailable XLS support and migration scope.

Both clients use Chinese public catalog records, exact-SKU fallback Product JSON-LD,
and trusted image hosts for catalog
enrichment. Official classifications precede heuristic guesses; recognized
English paths have Chinese display mappings and the original category path is
retained. Images use the portable `商品图片：URL` description convention. Lazy
image lookup changes presentation caches only, never component stock snapshots.
Normal Android imports no longer call the server recognition endpoint; old
server routes remain for older clients. OCR captures ImageCapture photos with
sensor orientation; the unavailable Paddle choice is no longer exposed.

Movement quantities remain stored as magnitudes for inbound/outbound and signed
deltas for adjustments. UI `quantityChange` / `QuantityChange` derives the sign
from the movement type, fixing outbound history without rewriting stored data.
Transfers carry source and destination location IDs and have zero quantity change.

## Multi-location inventory and Excel exchange

The native databases add `storage_locations`, `component_allocations`, and a
component `base_updated_at`. Android upgrades to schema version 4; Windows uses
column/table migration guards. An upgrade backup is retained before migration.
Existing location strings become stable location IDs. Active SKU uniqueness is
unchanged. Allocation quantities are non-negative and sum to component quantity;
all writers (forms, movements, BOM, migration and pull) maintain this invariant.

Native location creation and editing are distinct operations: creation rejects
an existing primary key (including tombstones), while editing only renames an
active location. Each check and write shares a transaction; neither operation
replaces a location row or modifies allocations. Server admin creation already
rejects duplicate IDs. Sync continues to apply location metadata using LWW;
this local creation guard does not change the sync protocol or database schema.

The server stores the same allocation relation and an `inventory_managed` flag.
The wire protocol carries full allocations inside each component snapshot and
complete location metadata on pull. This prevents independent per-bin LWW writes
from violating component totals. New clients require `inventory_protocol=1`,
and server CAS checks reject stale inventory bases atomically. New local edits
during a sync retain their queue entry and use only the acknowledged base version.

Both clients share the five-sheet `component-vault` Excel schemaVersion=1 format,
plus a reader for LCSC_android_erp's distinct schemaVersion=1. Preview validates
data before a transaction imports new records; existing IDs/SKUs are not overwritten.
Own history is retained; foreign workbooks lacking history produce explicit initial
inventory movements. Local images can be embedded without network access during export.
See [storage and backup guide](storage-and-backup.md) and the exact shared columns
in [implementation record](inventory-expansion-plan.md).

## Inventory statistics and MQTT publication

Each native client runs a full-history SQLite aggregation of non-deleted
`outbound` magnitudes, grouped by component ID once per refresh. The recent
movement list's 200-row limit does not affect these totals. Donuts and detail
statistics use current quantity plus recorded outbound quantity as the
denominator, with zero-safe ratios and 64-bit totals. Adjustments change current
quantity but are not counted as outbound; missing pre-migration history is not
reconstructed. Android detail reuses the trusted product image cache at a larger
display size; Windows detail retains its existing product image.

The FastAPI lifespan owns an optional single MQTT publisher. Accepted component
upserts and their exact state snapshots enter `mqtt_outbox` within the same
`BEGIN IMMEDIATE` transaction. Stale LWW writes and rolled-back pushes produce
no messages. A background worker reads the oldest revision, publishes retained
QoS 1, then deletes only after PUBACK. Retries preserve event IDs; consumers
must tolerate duplicates. Broker I/O happens outside request handling and uses
separate SQLite connections. Client writes and sync payloads are unchanged.

`mqtt_state` tracks snapshot initialization and the destination identity
(host/port/TLS/prefix, excluding credentials). First enable, re-enable after a
disabled startup, or changing destination seeds current components including
tombstones. A destination change replaces pending old-destination events with
current snapshots. Existing broker retained messages are not removed.
The publisher supports one API process, not multiple Uvicorn workers.
The authenticated `/admin-api/mqtt/status` reports connection and queue state;
it does not expose credentials. The existing admin-web Settings page adds
publisher status and a connection form, without adding a route. GET/POST
`/admin-api/mqtt/config` read/save a single `mqtt_configuration` SQLite row.
Saved MQTT settings override environment defaults at the next process startup;
saving does not hot-swap the publisher. Configuration responses omit passwords
and report whether a restart is required. Optional inventory writes are
independently gated by `WEB_INVENTORY_ENABLED` and do not change MQTT settings.
See [MQTT](mqtt.md).

Native Settings / About use standalone GitHub Release parsers and anonymous
HTTP clients, independent of inventory sync and its bearer token. A device-local
update-channel preference defaults to Stable; Dev also considers prereleases.
Semantic version comparisons, channel filtering, bounded responses, timeouts and
repository-specific asset URL validation determine the update UI. Installers
open only after a user click through the system browser. No background updater,
self-replacement, auto-install or new database tables are needed for this flow.
See [application updates](app-updates.md) for platform-specific installation.

## Catalog-name compatibility and native connection routing

Catalog metadata keeps the model and official description separately. New imports
prefer the model for the editable component name; user-customized names are not
rewritten. `ComponentPayload.name` accepts 1–4000 characters so earlier imports
containing long descriptions can synchronize without truncation. SQLite stores
names as TEXT, so this API compatibility change needs no database rebuild.

Android stores an optional external URL in its existing preferences. Each sync
first probes the configured primary address with `/auth/ping`; when a distinct
fallback is present the primary connect/read timeout is 3 seconds. Only transport
failures trigger the external probe. HTTP authentication/validation and malformed
response errors remain visible. Once selected, the endpoint is fixed for that
push/pull cycle; a failure preserves the local queue. The next cycle retries the
primary. Routing does not modify the saved primary URL or reset the sync cursor.
Both addresses must refer to the same service/database. Windows now follows the
same boundary: it probes primary first, falls back only for DNS, connection, or
timeout failures, fixes the endpoint for the complete push/pull run, and never
switches and replays a failed write. Its optional address is added to older
settings databases with an empty default, and sync status shows the endpoint used.

BOM automatic matching requires exact SKU when provided, otherwise exact model
and optional package. Ambiguous matches require selection. Android preserves raw
matching rows separately from aggregated commit rows; Windows retains selection
groups for original rows. Editing a match remains possible after several rows
have been assigned to the same inventory item. Stock validation and deductions
use aggregated quantities, preserving the existing transactional commit path.
Both clients let the user map SKU, model, package, quantity, name, and reference
source columns before preview. Missing results can be exported as UTF-8 CSV with
an Excel UTF-8 BOM; export is read-only and contains SKU, model, required,
available, missing quantity, and match status.
