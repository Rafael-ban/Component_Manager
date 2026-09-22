# Changelog

## [Unreleased]
bump: patch

<!-- Add unreleased notes below this line. -->

## [0.7.2] - 2026-09-22

<!-- Add unreleased notes below this line. -->

- Fix duplicate storage-location creation on Android and Windows: creating an
  existing code now reports an error instead of renaming the original location
  or reviving a deleted one. Trim surrounding whitespace before checking.
- Separate location creation from explicit editing. Editing requires an active
  existing code and preserves inventory allocations; failed saves remain in the
  editor. Existing data and database schemas are unchanged. Previously overwritten
  names must be corrected manually using their original location codes.
- Add SQLite regressions for duplicate creation, deleted-code protection and
  inventory preservation in native clients; verify the server's existing HTTP 409
  behavior preserves locations, component quantities, allocations and movements.


## [0.7.1] - 2026-09-22

<!-- Add unreleased notes below this line. -->

- Fix domestic LCSC lookup false positives by parsing valid product data before
  verification-page markers. Distinguish verification blocks, rate limits and
  network failures; pause repeated domestic requests across batch SKUs while
  retaining international fallback and an explicit domestic retry action.
- Add a built-in server setup page at port 8787, independent of the optional
  inventory Web image. First-run configuration saves API Token, allowed Web
  origins and the inventory-write switch atomically to config.json. Later
  changes require the current token, and non-empty environment overrides remain
  authoritative. Preserve old inventory and legacy token behavior on upgrade.
- Add authenticated viewing of bounded, timestamped application logs, persisted
  beside the database and also sent to container output. Logs exclude credentials,
  request bodies, query strings and barcode contents.
- Improve API Token discovery with show/copy actions in native settings and Web
  login, plus direct links from the Web console to server configuration and logs.
- Reconcile implementation and deployment documentation. Add a Synology / Docker
  quickstart covering image selection, first-run setup, File Station mappings,
  config/database/log locations, effective Token recovery, environment overrides
  and upgrades. Explain the public Python-image GPG_KEY fingerprint and separate
  ordinary deployment from Docker Hub publishing credentials.
- Complete the first comparison of NIIMBOT, Phomemo and official HPRT M1 resources.
  Add opt-in Android Bluetooth service diagnostics in label preview to collect
  device-test evidence. This does not send print commands or claim M1 printing
  support; direct printing still requires a verified protocol and device tests.
- Extend CI with Compose profile/variable validation and container first-run,
  authentication, mounted configuration/logs and restart-persistence checks.


## [0.7.0] - 2026-09-22

<!-- Add unreleased notes below this line. -->

- Expand Chinese display of official LCSC categories, including current LDO
  category names. Known English categories in existing Android and Windows
  inventory now share localized display, search and filter behavior with new
  imports. Preserve original official paths and unknown/custom category values.
- Add Windows primary/external server addresses with transport-only failover,
  safe migration of old settings and visible endpoint results. Each sync keeps
  one endpoint; authentication errors and interrupted writes never switch sites.
- Add Android and Windows BOM column mapping for SKU, model, quantity, package,
  name and reference designators, plus an Excel-compatible UTF-8 shortage CSV
  export. Mapping and export do not modify inventory; existing confirmed BOM
  deductions retain their transaction and duplicate-submission protection.
- Add optional Web inventory writes, disabled by default with
  WEB_INVENTORY_ENABLED=false. Authenticated browsers can create locations and
  components, edit component details, and record inbound/outbound stock with
  location selection, version-conflict checks and persisted retry receipts.
- Adapt Web inventory operations to desktop and mobile with compact expandable
  forms, stock previews and persistent success/error feedback. Recover uncertain
  movements after a lost response or page reload without duplicating stock.
  Support LAN HTTP browsers without crypto.randomUUID and Chinese location codes.
- Extend Docker CI with a separate Web image and startup/asset checks. API and
  Web images use version/latest and web-version/web-latest tags in the same
  Docker Hub repository; Compose offers an optional web profile. The 0.7.0
  release published both API and Web images for linux/amd64 and linux/arm64.
- Reconcile implementation/deployment documentation, including the detailed
  Docker Hub setup guide. Bluetooth printing remains deferred; its next stage
  will compare open-source protocols and multi-brand adapters before device work.


## [0.6.0] - 2026-09-22

<!-- Add unreleased notes below this line. -->

- Added Android batch-scan settings: a persistent 0.5–5 second capture interval
  (default 1.5 seconds), independent success sound/vibration switches and visible
  success/duplicate/cooldown feedback. Only newly accepted packages trigger
  feedback; a code remaining in view cannot repeatedly add stock.
- Added a separate label-printing XLSX export to Android and Windows backup
  settings, with selectable name, SKU, model, package, category, location,
  quantity and long/short QR text columns. Existing backup/restore is unchanged.
- Route catalog lookup by UI language: Chinese prefers domestic LCSC and English
  prefers international LCSC, with visible fallback reasons. Keyword search
  explicitly identifies its domestic source. Android clears lookup caches on
  language changes and does not persist fallback results over the preferred site.
- Preserve descriptions, parameters and datasheet links in catalog caches;
  remove expired, malformed and obsolete unscoped entries without affecting inventory.
- Added a reusable Docker image workflow with API startup, authentication and
  mounted-database checks, plus amd64/arm64 Docker Hub publishing support for
  rafaelikaros/component_manager. Publishing remains explicitly skipped until
  DOCKERHUB_TOKEN is configured; existing releases can be published manually later.
- Added docker-compose.hub.yml and deployment instructions for pulling the API
  image with persistent /data storage. The admin web app remains separate.


## [0.5.4] - 2026-09-17

<!-- Add unreleased notes below this line. -->

- Fixed Windows WinUI 3 startup failing on a missing theme color resource; use
  the Windows App SDK background brush, including system high-contrast support.
  Apply localized window titles after initialization and remove the obsolete
  localized Content override from the composite sync button.
  Include compiled window/page XBF resources and the merged application PRI in
  portable publish output; attach navigation handlers after initialization.
- Publish Windows as a self-contained x64 ZIP only. Removed MSIX, temporary
  signing certificates and certificate-install scripts from release artifacts;
  CI now starts the real app and checks the extracted release ZIP before upload.
- Prefer the manufacturer part model for new JLC/LCSC imports and retain long
  official product descriptions separately, preserving custom component names.
- Raised the server component-name limit from 200 to 4000 characters so earlier
  long-name imports can synchronize without truncation or clearing local data.
  Android now summarizes HTTP 422 validation errors without dumping input data.
- Added optional Android external server routing: probe the preferred address
  first, fall back on transport failures, and keep one endpoint for each sync.
- Clarified deployment API_TOKEN configuration and added show/copy controls for
  the web session token. Docker Compose now reads API_TOKEN from the root .env.
  Web release builds suggest the server host instead of a baked-in localhost.
- Completed BOM exact SKU/model matching and manual inventory search on Android
  and Windows, including editing matches after quantity aggregation.
- Deferred optional Web inventory writes and Windows LAN/WAN routing to the next
  version; the web console remains read-only for inventory in this release.


## [0.5.3] - 2026-09-16

<!-- Add unreleased notes below this line. -->

- Preserved Android's Import and scan bottom sheet over the inventory page;
  separated Project BOM and data migration choices without replacing the entry
  workflow with a new page.
- Reorganized BOM and migration into scrollable file/configuration, preview and
  confirmation stages, with concise task titles and predictable Back behavior.
- Unified Android location, backup, BOM and batch page chrome; location editing
  now uses scrollable dialogs with visible validation and retryable save errors.
- Testing Android and Windows connection drafts no longer silently saves them
  or resets sync state; changed connection settings must be saved before syncing.
- Fixed Windows secondary navigation and inventory state retention, prevented
  duplicate sync dialogs, and added narrow-window layouts for core workflows.
- Added authenticated read-only inventory search, pagination and component
  details to the admin console and server, including expired-login recovery.
- Added UI interaction regressions, rendered Android screenshots and browser
  verification records; documented remaining real-device and high-DPI checks.


## [0.5.2] - 2026-09-16

<!-- Add unreleased notes below this line. -->

- Simplified Android inventory actions into an Import and scan menu, with batch
  JLC inbound grouped there and location management / Excel backup moved to Settings.
- Fixed long batch inbound review and edit layouts with scrollable content,
  collapsible summaries and fixed confirmation actions.
- Added visible lookup/search progress and restored system Back behavior on
  backup, location and BOM secondary pages without exiting the app.
- Added Compose interaction regressions for import entry grouping, secondary-page
  Back and editing the last entry in a 100-package batch draft.
- Documented a full Android, Windows and admin-web UI audit with prioritized
  follow-up work; Windows/admin-web redesign recommendations remain planned.


## [0.5.1] - 2026-09-15

<!-- Add unreleased notes below this line. -->

- Fixed Android upgrades from existing databases failing before startup because
  the pre-upgrade backup executed a result-returning PRAGMA with execSQL.
- Added a data-preserving startup failure page with retry and redacted diagnostics;
  inventory is never silently reset after an initialization failure.
- Added SQLite/WAL migration coverage that verifies retained inventory and a
  readable pre-upgrade backup, plus startup diagnostic privacy regression tests.


## [0.5.0] - 2026-09-15

<!-- Add unreleased notes below this line. -->

- Added visible import error dialogs and explicit duplicate-SKU inbound
  confirmation showing current quantity, added quantity and resulting total,
  while preserving existing component metadata and ordinary edit semantics.

- Added Android continuous JLC QR capture and Windows scanner/text batch
  collection, private resumable drafts, package-level deduplication, reviewed
  inbound totals and a separate pending page for failures/manual correction.
- Added atomic batch inbound with location allocation updates, movement history,
  sync queue writes and local receipts preventing repeated submissions.
- Fixed repeated parsing of the same QR resetting enriched fields without
  restarting lookup; retained edits, debounced retries and rejected stale results.
- Corrected international lookup diagnostics to distinguish matched products,
  empty results and cache hits instead of reporting every parsed response as success.


## [0.4.2] - 2026-09-15

<!-- Add unreleased notes below this line. -->

- Unified JLC text/QR parsing and improved Android multi-code selection so
  decoded packaging payloads are not rejected by the text-only entry point.
- Added Android and Windows feedback forms with editable, opt-in diagnostic
  reports, copy/text export and GitHub browser login for final Issue submission.
- Added bounded process-local scan/catalog diagnostic events without raw
  packaging payloads, order data, credentials or arbitrary exception messages.
- Bluetooth printer integration remains deferred while scan reliability and
  issue reporting are addressed.


## [0.4.1] - 2026-09-15

<!-- Add unreleased notes below this line. -->

- Unified Android and Windows inventory workbook size/row limits, separating
  full inventory backups from the smaller BOM import limit. Export validates its
  result before publishing the file so it can be read back by the clients.
- Improved Excel numeric-cell interoperability for integral decimals and
  scientific notation while retaining strict inventory integer validation.


## [0.4.0] - 2026-09-15

<!-- Add unreleased notes below this line. -->

- Added Chinese LCSC public catalog search, keyword candidates, structured
  parameter details and exact C-number fallback to the international storefront.
- Added independent storage locations, multiple locations per component, explicit
  location movements, partial/full transfers and visible BOM allocation plans
  on Android and Windows. Transfers do not increase consumption statistics.
- Added a shared Excel backup/restore format and LCSC_android_erp schemaVersion=1
  migration with preview, new-record merging, strict inventory validation and
  persistent embedded product images. Export does not query or change inventory.
- Added inventory sync protocol 1 with atomic allocation snapshots, checked
  base versions, capability negotiation, legacy-client write protection and
  pre-upgrade database backups. Concurrent local edits remain queued.
- Extended MQTT component state with location allocations and the admin console
  movement display with transfers. Bluetooth printer integration remains deferred.


## [0.3.13] - 2026-09-15

- Fixed the Android settings section list's missing localized string binding
  found by GitHub CI. Includes the native UI redesign from 0.3.12, whose
  Android release build did not complete.


## [0.3.12] - 2026-09-15

- Redesigned Android and Windows native navigation, home summaries, inventory
  and movement workspaces, settings, and secondary editing/import surfaces.
- Moved inventory usage rings into compact quantity/image columns, removed
  duplicate settings headings and actions, and formatted display dates locally.
- Reorganized native About pages with author Rafael-Ikaros, installed version,
  project/license links, and the existing GitHub Release update workflow.
- Documented the native layout contract and official reference projects.
- Added CI coverage for local timestamp formatting and explicitly scoped
  release publication to the current GitHub repository.


## [0.3.11] - 2026-09-15

- Added native inventory donut indicators and detail statistics using current
  stock plus all valid recorded outbound movements, including history beyond
  the recent 200-row list. Added a larger Android component-detail product image.
- Added an optional server-only MQTT inventory-state publisher for Home
  Assistant and dashboards, with transactional SQLite outbox, retained QoS 1
  messages, deletion tombstones, retry after PUBACK failure, destination-aware
  initial snapshots and authenticated publisher status.
- Added MQTT environment/Compose configuration, Home Assistant setup examples,
  and regression coverage for outbound totals and MQTT delivery boundaries.
- Completed native Settings / About with installed versions, GPLv3 and project
  links, manual GitHub stable-release checks, release notes, and validated
  platform download links with explicit no-update, missing-asset and error states.
- Added authenticated MQTT configuration in web Settings, masked credentials,
  persistent settings applied on server restart, and connection/queue status.
- Fixed CI branch-push triggers and reusable release input resolution so
  changelog-driven builds publish their requested GitHub Release assets.


## [0.3.10] - 2026-09-15

- Added Android and Windows project BOM CSV/XLSX preview, exact inventory matching,
  shortage checks and transactional batch depletion with local retry idempotency.
- Added component-hub JSON migration with source metadata, Chinese category
  mapping, duplicate preview/explicit skip and atomic initial-stock movements.
- Added trusted product images on native inventory rows, Windows direct C-number
  lookup, official-category priority and Chinese domestic-store links.
- Fixed outbound movement signs in native lists/details without changing stored
  magnitudes; Android now captures OCR photos with rotation instead of preview
  screenshots, prioritizes direct C-number entry, and hides server recognition
  and the unavailable Paddle option.

- Added service-issued sync cursors and legacy database migration to deliver
  late offline changes; fixed timestamp comparisons, foreign-key enforcement,
  and atomic push rollback.
- Preserved native-client queue edits made during sync, reset cursors on server
  changes, and implemented debounced Windows automatic synchronization.
- Added Android direct LCSC public product lookup from scanned C-numbers without
  server/API-key configuration, with exact SKU checks, local caching, import
  preference, and manual/product-page fallback.
- Added synchronization, public-catalog, BOM and migration regression tests,
  plus a sourced component-hub comparison and migration guide.
- CI now runs Android inventory regressions (sync, catalog, BOM, migration,
  movement signs and OCR preferences) and Windows core tests.
- Excluded local screenshot attachments and temporary Gradle verification files
  from version control.


## [0.3.9] - 2026-05-19

- Reworked Android stock movements into a scan-then-review batch workflow:
  successful warehouse-label scans now stay in a continuous session, merge
  duplicate scans into one queue row, and let each queued component choose its
  own inbound, outbound, or adjustment details before one local commit.
- Re-armed the Android in-app label scanner between successful movement scans
  without dropping the existing auto-zoom and tap-to-focus path, and changed
  the scanner exit flow to return to the pending review queue instead of
  forcing a one-scan-one-entry sheet.
- Added Android movement batch validation, localized batch-review copy, and
  refreshed movement previews so the new queue editing states render in both
  runtime and Compose Preview.

## [0.3.8] - 2026-05-16

<!-- Add unreleased notes below this line. -->

- Tightened Android small-label warehouse scanning with a dedicated movement
  scan mode that raises CameraX analysis resolution, enables bundled ML Kit
  potential-barcode detection plus zoom suggestions, and keeps narrow printed
  labels on a pure auto-zoom plus tap-to-focus path instead of manual zoom
  controls.
- Switched the Android `10x40mm QR` warehouse label to a shorter
  lookup-first payload (`cvl3|sku|qty`) so the app resolves full component
  metadata locally by `sku`, while preserving backward compatibility for older
  compact `cvl2` labels and app-generated JLC-compatible labels.
- Hardened Android import parsing so supplier `vendor` values can populate
  `brand`, model-like tokens are no longer learned or saved as canonical
  component names, and explicit or server-resolved names take precedence over
  raw model codes during JLC and OCR import refinement.
- Refactored Android inventory and movement create flows so the main inventory
  add action opens a unified `Import` / `Manual add` chooser, matched label
  scans complete `Inbound` / `Outbound` / `Adjustment` edits inside the same
  bottom sheet, save failures remain inline, and successful saves reselect the
  affected component before optional label preview.
- Reworked the Android shell and Inventory workspace onto official Material 3
  adaptive primitives, including `NavigationSuiteScaffold`,
  `currentWindowAdaptiveInfo`, `NavigableListDetailPaneScaffold`, and a
  search-first `SearchBar` header, while tightening inventory row density and
  shifting the primary flow away from stacked filter cards.
- Upgraded the Android release build chain to a newer AGP and Lifecycle
  combination, aligned CI and release workflows to the matching Gradle
  8.11.1 plus SDK Build Tools 35.0.0 baseline, migrated off the deprecated
  `kotlinOptions` DSL, and removed empty proxy properties to address the
  GitHub Actions `lintVitalAnalyzeRelease` crash path triggered by Lifecycle
  lint binary incompatibility.


## [0.3.7] - 2026-05-13

- Added Android scan-first stock movement entry for generated warehouse and
  app-generated JLC-compatible labels, including local label parsing by `sku`,
  match-status feedback (`invalid`, `not found`, `ambiguous`, `matched`), and
  quick `Inbound`, `Outbound`, and `Adjustment` actions that open a
  component-locked movement form without requiring server lookup.

## [0.3.6] - 2026-05-13

- Reworked Android inventory labels into collision-safe physical templates
  with user-selectable `10x40mm QR`, `30x40mm QR`, and pure text strip modes,
  compact offline warehouse QR payloads for the narrow label, optional
  companion text-label export, direct label-sized export, size-specific QR
  placement rules, larger label typography, fixed physical preview ratios,
  a horizontal `30x40mm` layout with QR-left and centered package/name
  stacking, and refreshed Compose preview states for long-text and Chinese
  label cases.
- Fixed Android import semantics so unresolved supplier parts no longer save
  raw `model` or `sku` values into the canonical component `name`; the import
  form now shows them as reference labels and allows manual adoption or full
  editor refinement.
- Enabled real server-side public LCSC web fallback for
  `GET /admin-api/part-lookup` behind `ENABLE_WEB_FALLBACK_RESOLVERS`, so
  self-hosted deployments without OpenAPI credentials can still enrich JLC QR
  imports by SKU.
- Reworded the Android local deletion status copy to clarify that a component
  was marked deleted on the current device.
- Reworked changelog-driven GitHub release automation into a single orchestration
  chain that uses the default `GITHUB_TOKEN`, pushes the matching release tag,
  and then directly calls the reusable release workflow instead of relying on a
  second workflow being triggered by tag pushes.


## [0.3.5] - 2026-05-11

- Switched Android JLC package QR scanning from Google Code Scanner to an
  in-app CameraX scanner backed by bundled ML Kit barcode scanning, removing
  the runtime dependency on downloading the Barcode UI module before first use.
- Added Android supplier packaging OCR import through bundled ML Kit Chinese
  text recognition, with quantity-first confirmation and optional full-editor
  refinement before saving inventory.
- Added Android inventory label preview plus PNG/PDF export, generating
  JLC-compatible QR payloads for JLC-sourced parts and warehouse QR payloads
  for non-JLC parts so labels can round-trip back into import flows.
- Added server-side hybrid recognition endpoints:
  `GET /admin-api/part-lookup`,
  `GET /admin-api/recognition-rules/meta`, and
  `POST /admin-api/recognition-rules/refresh`, while keeping
  `GET /admin-api/lcsc/lookup` as a compatibility proxy for direct official
  supplier metadata requests.
- Reworked Android JLC import enrichment into a local-first flow with
  bundled offline recognition rules, device-only learned mappings stored in
  SQLite, SKU-first and MPN-fallback reuse, field-origin review in the import
  form, and separate settings for local recognition, learning, and optional
  server lookup.
- Rebuilt Android runtime string resources and preview string bundles after the
  import-enrichment changes, and re-verified `assembleDebug` and
  `assembleRelease` on `2026-05-10`.
- Added a Windows-side Android Gradle helper script that prefers Android
  Studio's embedded JBR so local `assembleRelease` remains stable when the
  system default Java runtime is newer than the Android lint toolchain
  supports.
- Refactored Android supplier packaging OCR into a capture-first flow with a
  unified OCR contract, structured line extraction, packaging-field parsing,
  and richer import notes instead of flattening every live frame directly into
  raw text.
- Added Android OCR engine preference storage and settings UI with `Auto`,
  `ML Kit offline`, and `Paddle experimental` modes, while keeping the
  current build honest by treating Paddle as an unavailable future native
  integration instead of a fake fallback.
- Renamed the Android app surface to `元件仓库`, tightened the inventory home
  top bar from the previous medium/two-row app bar to a single-row layout,
  and repaired the broken `values-zh-rCN` resource file so Preview and runtime
  Chinese resources resolve again.
- Added persisted Android in-app language switching with first-launch default
  `zh-CN`, backed by `AppCompatDelegate.setApplicationLocales`, a settings
  selector for `中文` / `English`, and manifest locale metadata for Android
  per-app language support.
- Improved Android JLC QR parsing and local recognition so vendor numbering
  schemes can infer package, model-family, and category more reliably, while
  avoiding the old fallback that incorrectly copied raw model codes into the
  package field when no package was actually recognized.

## [0.3.0] - 2026-05-09

- Removed the repository-pinned Android JDK path from `android-client/gradle.properties`
  so GitHub Actions and other non-Windows environments can use their own
  configured Java runtime.
- Added default-branch changelog release automation that syncs version files,
  pushes a release commit when needed, and creates the matching `v*` tag for
  `release.yml`.
- Fixed Windows MSIX install guidance and release packaging so the generated
  installer imports the test signing certificate into
  `Cert:\LocalMachine\TrustedPeople` instead of the current-user store.
- Restored the WinUI application resource merge so Windows startup and Visual
  Studio XAML Designer previews can resolve theme resources such as
  `TextFillColorSecondaryBrush`.
- Stabilized Android Compose Preview by moving preview rendering onto static
  string bundles and content-level preview composables instead of direct
  preview-time `R.string` resolution.
- Reworked the Android inventory flow around scroll-safe `Scaffold` inset
  handling, pinned JLC import actions, and quantity-first import confirmation
  so phone previews and runtime scrolling behave like a native list-first
  inventory app instead of a clipped card stack.
- Added Android JLC/LCSC-style text import and package QR import through
  Google Code Scanner, mapping JLC item numbers into local `sku`, pre-filling
  most fields, and storing extra import metadata in the existing
  `description` field without changing sync APIs or the SQLite schema.
- Expanded Android settings with separate sync-on-launch vs sync-after-write
  controls, import defaults, scanner preferences, and an in-app About section
  for `0.3.0`.
- Rebuilt the Windows WinUI shell around an inventory-first desktop workflow
  with `Inventory`, `Movements`, `Overview`, and `Settings`, plus denser
  list/detail pages, grouped sync settings, and refreshed XAML designer sample
  data.

## [0.2.0] - 2026-05-08

- Added changelog-driven automatic version syncing for Android, admin-web, and Windows.
- Added repository-managed Git hook bootstrap plus CI version consistency checks.

## [0.1.0]

- Initial released baseline before changelog-driven version automation.
