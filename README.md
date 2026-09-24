# Component Vault

Component Vault is a local-first electronic component inventory system for
Windows and Android. The current implementation direction is native UI on both
clients: `Jetpack Compose` for Android and `WinUI 3` for Windows. The service
stores sync data behind FastAPI, and a separate `shadcn/ui` web admin console
connects to it over HTTP.

## Workspace Layout

- `android-client/`: Android native client implemented with Jetpack Compose.
- `windows-client/`: Windows native desktop client implemented with WinUI 3.
- `admin-web/`: separated React + `shadcn/ui` admin console for server
  operations and inventory verification.
- `client/`: legacy Flutter reference kept for migration and field parity.
- `server/`: FastAPI sync service with project-local virtual environment,
  inventory admin APIs, optional authenticated Web inventory writes, and MQTT
  configuration writes.
- `.github/workflows/`: GitHub Actions CI and release artifact automation.
- `docs/`: architecture, integration, and operations notes.
- `docker-compose.yml`: self-hosted API deployment entrypoint.
- `docker-compose.hub.yml`: deploy published API and optional Web images without
  building locally, after those tags have actually been published.

## Key Behaviors

- Local-first storage with SQLite on the client.
- Android keeps a consistent private backup before a database schema upgrade.
  If startup cannot open existing inventory, it shows retry and diagnostic-copy
  actions without clearing data. Do not clear app storage to work around an
  upgrade failure; install the fixed update over the existing installation.
- Manual sync and optional auto sync.
- Android and Windows accept an optional external address for the same server;
  DNS, connection, or timeout failures on the preferred address trigger a
  pre-sync external probe. HTTP authentication, validation, and conflict
  responses do not trigger address switching. The selected endpoint remains
  fixed for the complete push/pull cycle and the next cycle retries primary.
- Catalog imports use the part model as the name and retain product descriptions
  separately. The server accepts legacy component names up to 4000 characters.
- Catalog lookup follows the app language: Chinese prefers domestic LCSC and
  English prefers international LCSC. Fallbacks display their source and reason;
  changing Android language invalidates catalog caches. Keyword search uses the
  domestic catalog with an explicit notice when the preferred site is international.
- BOM previews automatically select unambiguous SKU/model matches, allow users
  to map SKU/model/quantity columns, and support manual inventory selection.
  Missing demand can be exported as an Excel-friendly UTF-8 CSV without changing stock.
- Soft delete for synchronized entities.
- Inventory history recorded as stock movements.
- Self-hosted API secured by a shared API token.
- The API also serves a small setup and log page at `/setup` (port 8787),
  independently of the full inventory Web console. First-run configuration saves
  to `/data/config.json`; later access requires the current token. Non-empty
  deployment environment variables override the corresponding saved fields.
- Separate API and Web images support Docker Hub distribution for `linux/amd64`
  and `linux/arm64`; API uses `<version>`/`latest`, while Web uses
  `web-<version>`/`web-latest`. SQLite uses the persistent API `/data` mount.
  API `0.7.0` and Web `web-0.7.0` were verified published. Later tags still require
  successful publishing checks. Start with the [Docker / Synology quickstart](docs/docker-quickstart.md);
  maintainers can use the [Docker Hub release setup](docs/dockerhub.md).
  Historical 0.5.2 follow-up analysis is retained in
  [the 0.6.0 feature reconciliation](docs/remaining-features-0.6.md).
- Separated admin web console backed by token-protected `/admin-api/*`.
  Inventory and storage-location writes are available only when
  `WEB_INVENTORY_ENABLED=true`; the default remains read-only. Writes use
  optimistic `expected_updated_at` checks and persistent `request_id` receipts.
- JLC imports use local parsing and direct public product lookup without a
  server or API key. Official category paths take precedence over local guesses;
  product images appear on the right of inventory rows. User edits are preserved.
  Known official English categories use the same Chinese display mapping for
  current and historical rows, including filters; unknown and custom values remain unchanged.
- Android prioritizes QR scanning and direct C-number entry. Optional packaging
  OCR uses a CameraX photo with sensor rotation and bundled ML Kit; it no longer
  recognizes a screen-resolution preview or offers the unavailable Paddle engine.
- Windows and Android support project BOM CSV/XLSX preview, stock matching and
  configurable source-column mapping, missing-demand CSV export, transactional
  batch depletion, and component-hub JSON migration with conflict preview. See
  [BOM and migration guide](docs/bom-and-migration.md).
- Active components enforce unique `sku`.
- Component `quantity` and `min_stock` are non-negative.
- Independent storage locations support one component in multiple bins and
  transactional partial/full transfers. A component's quantity equals the sum
  of its location allocations; transfers do not count as consumption.
  Creating a location rejects an existing code, including deleted codes;
  renaming is an explicit edit and never changes allocations or stock quantities.
- Native clients share an Excel backup format and import LCSC_android_erp
  schema-version-1 workbooks through a conflict preview. Restore adds new
  records; it does not silently overwrite existing inventory. See
  [storage and backup guide](docs/storage-and-backup.md).
- Settings > Excel backup and restore also exports a separate label-printing
  workbook on Android and Windows. Select name, SKU, long/short QR text, model,
  package, category, location and quantity; the restore format is unchanged.
- Native layouts use a home summary, compact inventory cards with adjacent
  images and usage rings, local display dates, and a single Settings entry.
  About displays author `Rafael-Ikaros`, installed version, project/license
  links, and GitHub Release update checks. See the
  [native UI layout contract and references](docs/native-ui-redesign.md).
- Native Settings includes issue feedback with editable, opt-in diagnostic
  reports, text export and GitHub browser sign-in for final submission. Scan
  parsing and catalog network failures are diagnosed separately. See
  [feedback and scan diagnostics](docs/feedback-and-scan-diagnostics.md).
- JLC batch inbound collects packaging codes before lookup: Android supports
  continuous camera scanning, Windows supports scanner input and pasted lines.
  Private drafts survive restarts, failures have a separate pending page, and
  reviewed receipts add stock atomically with local replay protection. See
  [batch JLC inbound](docs/batch-jlc-inbound.md).
- Android batch scanning offers a saved 0.5–5 second capture interval (default
  1.5 seconds), independent success sound/vibration switches and visible capture
  feedback. Duplicate packages do not produce success feedback or add quantity.

## Sync reliability and direct LCSC lookup

- Updated native clients use the server-issued `sync_cursor` for incremental
  downloads. Managed inventory snapshots use `base_updated_at` optimistic
  conflict checks; legacy inventory and location metadata retain LWW.
  `server_time` is informational. The first upgraded sync is a full snapshot.
- Pending changes are acknowledged against the uploaded queue version, so edits
  made while a sync is running remain queued. Windows automatic sync now runs
  after startup and successful local edits, with a short debounce.
- Both native clients can look up an LCSC `C` number directly on official public
  pages and search the Chinese catalog by keyword, without a sync server or OpenAPI credentials. This is enabled by
  default on Android and can be disabled under import settings. Windows provides
  a lookup action in the editor. Only the entered SKU or search keyword is sent.
- Chinese catalog results or fallback Product JSON-LD must contain the exact scanned SKU. Product descriptions,
  manufacturer model, brand, and package fill the import confirmation form;
  supplier stock and prices never become local inventory quantities.
- Successful lookups are cached for seven days. Public-page changes, verification
  screens, and network failures leave manual entry and an open-product-page link
  available. This is best-effort enrichment, not a guaranteed catalog service.
- Chinese search reads the public `so.szlcsc.com` page's structured product
  records. Keyword candidates expose parameters and require selection. Exact
  C-number lookup falls back to `www.lcsc.com` when Chinese lookup is unavailable.
  Domestic product links use the returned `productId`, never the digits of the SKU.
- New native clients require server `inventory_protocol=1` before sync. A stale
  inventory base returns HTTP 409 without accepting any part of the push.
  Pending local changes remain available; upgrade the server before clients.
- See [component-hub comparison and BOM roadmap](docs/component-hub-comparison.md)
  and [implementation plan](docs/sync-and-catalog-plan.md).

## Versioning

- `docs/CHANGELOG.md` is the single source of truth for repository versions.
- Before committing, add notes under `## [Unreleased]` and set `bump:` to
  `major`, `minor`, or `patch`.
- Install the repository-managed Git hook once after cloning:
  `.\scripts\setup-git-hooks.ps1` on Windows or `sh ./scripts/setup-git-hooks.sh`
  on macOS/Linux.
- Each local `git commit` then auto-syncs:
  - Android `versionName` and `versionCode`
  - `admin-web/package.json` and `package-lock.json`
  - Windows assembly and MSIX package versions
- CI runs `python tools/versioning/sync_version.py --validate` so active
  branches can keep unreleased changelog notes without failing the pipeline.
- On the default branch, `.github/workflows/release-from-changelog.yml` also
  consumes `docs/CHANGELOG.md` and:
  - applies the version sync if unreleased notes were pushed without the local hook
  - creates and pushes the matching `vX.Y.Z` tag when the latest changelog
    release is not tagged yet
  - directly calls `.github/workflows/release.yml` in the same workflow run to
    build release artifacts and create or update the GitHub Release
- The default `GITHUB_TOKEN` is sufficient for this path as long as the
  workflow has `contents:write` permission. No separate
  `RELEASE_AUTOMATION_TOKEN` secret is required.
- Dev builds use `vX.Y.Z-dev.N` tags on CI-verified `master` commits and notes in
  `docs/releases/`. They publish as prereleases without advancing the stable
  changelog version or Docker Hub tags. See [release channels](docs/runbook.md#stable-and-dev-publishing).

## Platform Status

- Windows native client: local SQLite, component editing, movement recording,
  sync settings, server sync wiring, Chinese-first WinUI pages, and dual-mode
  packaging are implemented; the Windows UI follows a home-first
  `NavigationView` shell with `Home`, `Inventory`, `Records`, `Project BOM`, and
  `Settings` destinations, dense list/detail workspaces, and updated XAML
  designer sample data; `dotnet build` verified successfully on `2026-05-09`,
  MSIX-oriented `dotnet publish` was verified successfully on `2026-05-07`,
  and unpackaged portable `dotnet publish` was verified successfully on
  `2026-05-08`.
- Android native client: local SQLite, component editing, movement recording,
  JLC text and QR import, supplier packaging OCR import, physical label
  preview/export, expanded settings, server sync wiring, and Chinese-first
  Compose interface resources are implemented; the Android UI now follows an
  home-first adaptive Compose shell with four top-level destinations
  (`Home`, `Inventory`, `Records`, `Settings`),
  official Material 3 adaptive shell and pane primitives
  (`NavigationSuiteScaffold`, window size classes,
  `NavigableListDetailPaneScaffold`), a search-first Inventory header, denser
  inventory rows, compact phone detail drill-down, medium/expanded list-detail
  layouts, section-based settings, quantity-first import confirmation,
  generated JLC-compatible or warehouse QR labels, user-selectable
  `10x40mm QR`, `30x40mm QR`, and pure text strip templates, direct
  label-sized PNG/PDF export, larger on-label typography, collision-safe text
  layout with a horizontal `30x40mm` QR-left details layout, physical-aspect
  preview rendering, local import learning backed by a device-only SQLite
  mapping table, direct public product lookup for filling JLC fields,
  capture-first supplier packaging OCR with structured line extraction,
  user-selectable OCR engine preference (`Auto`,
  `ML Kit offline`), persisted in-app language
  switching with first-launch default `zh-CN`, stronger vendor-aware QR
  package/category inference, generated-label QR round-trip parsing for
  warehouse and JLC-compatible labels, scan-first stock movement entry with
  quick `Inbound`, `Outbound`, and `Adjustment` actions completed inside the
  matched scan sheet, movement-side small-label scan tuning with higher
  analysis resolution, potential-barcode detection, auto-zoom suggestions, and
  tap-to-focus, a shorter lookup-first `10x40mm` warehouse QR payload
  (`cvl3|sku|qty`) for narrow labels while preserving legacy `cvl2` and
  app-generated JLC-compatible label parsing, a unified inventory add-entry
  sheet for `Import` vs `Manual add`, import-name protection that keeps raw
  model codes out of the canonical component `name` field, and vendor-to-brand
  fallback during supplier parsing; the last full `assembleDebug` and
  `assembleRelease` verification on this host completed on `2026-05-16`.
  After the Android build-chain refresh to AGP `8.10.1`, Gradle `8.11.1`,
  SDK Build Tools `35.0.0`, and Lifecycle `2.9.2`,
  `.\android-client\gradlew.bat -p android-client help --no-daemon`
  completed successfully on `2026-05-17`; current local APK assembly on this
  host is blocked because the Android SDK directory is not writable, so AGP
  cannot auto-install `build-tools;35.0.0`.
- Server admin surface: now split into FastAPI `/admin-api/*` endpoints plus a
  separate `admin-web/` React application; backend `pytest` and `admin-web`
  production build were both verified successfully on `2026-05-08`.

## Data Model

Client and server both work with the same two synchronized entity families:

- `components`: `id`, `sku`, `name`, `category`, `package_name`, `location`,
  `description`, `quantity`, `min_stock`, `updated_at`, `deleted`
- `stock_movements`: `id`, `component_id`, `movement_type`, `quantity`,
  `reason`, `note`, `happened_at`, `updated_at`, `deleted`

The client also keeps a local-only `sync_queue` table that batches pending
pushes to the server.

## Windows Client

Build the WinUI 3 client with:

```powershell
dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release /p:PublishProfile=win-x64.pubxml
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release -r win-x64 -p:PublishProfile= -p:WindowsPackageType=None -p:GenerateAppxPackageOnBuild=false -p:AppxPackageSigningEnabled=false -p:WindowsAppSDKSelfContained=true -p:SelfContained=true -p:PublishSingleFile=false -o windows-client\artifacts\portable-local
```

The current desktop implementation uses a local SQLite database under the
user's local app data directory and supports:

- an inventory-first desktop shell:
  `Inventory`, `Movements`, `Overview`, `Settings`
- component create/edit/soft delete
- cached inventory navigation with a secondary Back path from batch inbound
- adaptive dashboard, movement and batch layouts for narrower windows
- connection draft testing without saving; explicit save required before syncing
  changed connection settings
- inventory movement entry
- sync settings save/test/sync-now
- push/pull against the FastAPI sync service

Windows releases use one self-contained, unpackaged ZIP. Extract the entire
`component-vault-windows-portable-x64.zip` archive and run
`ComponentVault.WinUI.exe`; keep its runtime files beside the executable.
No MSIX publisher certificate installation is required. User data remains under
`%LOCALAPPDATA%\ComponentVault`, separate from the extracted application.
CI publishes this layout and checks actual window startup before distributing it.

## Android Client

The Android client lives in `android-client/` as a Gradle/Compose app.
Before building, make sure the machine has:

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Gradle access to Google Maven
- or `android-client/local.properties` created from
  `android-client/local.properties.example`
- repository Gradle wrapper `8.11.1`, AGP `8.10.1`, Android SDK Platform `35`,
  Build Tools `35.0.0`, Kotlin `2.0.21`, and Lifecycle `2.9.2`

Verified working setup on this host:

- Android SDK: `C:\Users\gdblz\AppData\Local\Android\Sdk`
- JDK: `D:\android_studio\jbr`
- Gradle wrapper: `.\android-client\gradlew.bat`
- local SDK file: `android-client/local.properties`
- project-local Android user home: `D:\Project_Folder\Component_warehouse\.android-user`
- the repository does not pin `org.gradle.java.home`, so local builds and CI
  resolve Java from `JAVA_HOME` or the runner-provided toolchain

Then run:

```powershell
$env:GRADLE_USER_HOME='D:\Project_Folder\Component_warehouse\.gradle-user-home'
$env:JAVA_HOME='D:\android_studio\jbr'
$env:ANDROID_SDK_ROOT='C:\Users\gdblz\AppData\Local\Android\Sdk'
$env:ANDROID_HOME='C:\Users\gdblz\AppData\Local\Android\Sdk'
$env:ANDROID_USER_HOME='D:\Project_Folder\Component_warehouse\.android-user'
& '.\android-client\gradlew.bat' -p android-client help --no-daemon
& '.\android-client\gradlew.bat' -p android-client assembleDebug --no-daemon
& '.\android-client\gradlew.bat' -p android-client assembleRelease --no-daemon
```

On Windows, the repository also includes a helper that prefers Android Studio's
embedded JBR and then delegates to the repository wrapper, so host Java or
global Gradle drift does not change the build chain:

```powershell
.\scripts\android-gradle.ps1 assembleDebug
.\scripts\android-gradle.ps1 assembleRelease
```

The current Android implementation uses on-device SQLite plus shared
preferences and supports:

- an `Import and scan` bottom sheet with separate single-item, batch JLC,
  project BOM and data migration workflows, plus secondary manual entry
- Settings > Inventory and data for location management and Excel backup/restore;
  shared secondary-page chrome, scrollable location dialogs and staged BOM forms
- connection draft testing without saving credentials, preferences or sync cursor
- component create/edit/soft delete
- inventory movement entry
- generated warehouse label scan for quick component locate plus
  scan-first `Inbound`, `Outbound`, and `Adjustment` movement entry
- JLC copied-text import with automatic field mapping
- JLC package QR import through an in-app CameraX scanner backed by bundled
  ML Kit barcode scanning, with runtime camera permission handling
- movement-side label scanning uses a dedicated small-label mode with higher
  CameraX analysis resolution, ML Kit potential-barcode detection, zoom
  suggestions, and tap-to-focus for dense `10x40mm` tags
- supplier packaging OCR import through an in-app CameraX scanner backed by
  bundled ML Kit Chinese text recognition, now using a frozen-frame capture
  step plus structured line extraction before packaging-field parsing
- physical label preview plus PNG/PDF export, generating JLC-compatible QR
  payloads for JLC-sourced items and warehouse QR payloads for other items
- opt-in Bluetooth diagnostics from label preview: service discovery, M1 Classic
  SPP model/status queries and a separately confirmed single M1 test label using
  the existing QR template defaults or a local paper-size/rotation/offset profile.
  Consecutive tests reuse a confirmed foreground connection with a 60-second idle
  timeout. M1 uses the vendor profile's right alignment; an inconclusive post-print
  model query gets at most one read-only retry. Non-persistent test modes either omit
  the final feed or use the upstream short-feed command to investigate extra blank
  labels and incomplete bottom output. Omitting the feed stopped extra blank labels
  in two user tests, but bottom content was incomplete. Short feed produced complete
  content across two labels in earlier tests;
  neither diagnostic mode has passed normal-print acceptance.
  USB/HCI comparison with Hanma 3.3.4 now informs a 1 KiB raw-frame budget and
  separate parsing of `dithering_finish` notifications. Dev.6 normal label mode passed
  two consecutive 40×60 mm test prints on the tested M1 / Android 15 device, with
  complete content, correct placement and no extra blank labels confirmed by the user.
  Other sizes, firmware and long batches remain unverified; processing events are not proof of output.
  See [M1 device test steps](docs/printer-compatibility.md).
- user-selectable `10x40mm QR`, `30x40mm QR`, and pure text strip label
  templates, with linked QR dimensions, larger text treatment, direct
  label-sized export, physical-aspect preview rendering, and fixed per-size
  layouts including a horizontal `30x40mm` QR-left detail stack; the narrow
  `10x40mm` template now prefers a short lookup-first warehouse payload so the
  scanner resolves full metadata locally by `sku` instead of forcing a dense
  all-fields QR
- local-first JLC import enrichment through parser heuristics plus a device-only
  learned mapping table keyed by JLC SKU and fallback MPN reuse
- bundled offline recognition rules for package normalization, model-family
  matching, vendor normalization, and category inference even when no server
  is deployed
- direct LCSC lookup by scanned or manually entered C-number, official category
  priority, product-image caching and editable confirmation; the server-assisted
  recognition switch has been removed from the normal Android import flow
- project BOM preview and batch depletion, plus component-hub JSON migration,
  available from the inventory add/import actions; batch ledgers remain local
- sync settings save/test/sync-now
- separate sync-on-launch and sync-after-write behavior controls
- import defaults, local-learning controls, OCR engine preference, and an
  in-app language selector plus About section
- push/pull against the FastAPI sync service
- Chinese-first UI resources for the primary screens, with a matching
  Simplified Chinese (`zh-CN`) resource set and an in-app `中文` / `English`
  switch backed by Android per-app locales
- an adaptive shell with top-level `Home`, `Inventory`, `Records`, and `Settings`
- official adaptive Compose layout primitives for the shell and Inventory:
  `NavigationSuiteScaffold`, window size classes,
  `NavigableListDetailPaneScaffold`, and a `SearchBar`-first filter header
- compact phone flows centered on search, filters, dense lists, and full-screen
  detail, settings, or form routes
- tablet layouts that keep persistent list-detail panes for inventory and
  movement history while keeping settings as a sectioned secondary route

The [cross-platform UI audit](docs/ui-ux-audit-2026-09-16.md) records the initial
findings. The [UI consistency implementation report](docs/ui-consistency-implementation.md)
tracks completed Android, Windows and admin-web work and remaining validation.

Android visual editing is based on Compose Preview in Android Studio. Open
the files under
`android-client/app/src/main/java/com/componentvault/android/ui/screen/preview/`
to use the built-in preview states. Those preview entrypoints now render
content-level composables backed by static preview strings so Android Studio
Preview is not blocked by stale `R.string` state. The Android Studio `Layout
Editor` tutorial for View/XML layouts does not apply to this Compose client.
Visual Studio does not provide an equivalent native Compose designer.

## UI Editing

- Android: use Android Studio Compose Preview on
  `android-client/app/src/main/java/com/componentvault/android/ui/screen/preview/`.
- Windows: use Visual Studio XAML Designer and Hot Reload on the WinUI page
  files under `windows-client/ComponentVault.WinUI/Views/`.
- Detailed platform-specific notes live in `android-client/README.md` and
  `windows-client/README.md`.

## Admin Web

The separated web admin lives in `admin-web/` and uses `React`, `Vite`,
`Tailwind CSS`, and `shadcn/ui`.

Run it locally with:

```powershell
cd admin-web
cmd /c npm install
cmd /c npm run dev
```

The login screen validates the shared API token through `POST /auth/ping`,
stores the configured API base URL and token in browser local storage, and then
uses `/admin-api/components` and `/admin-api/components/{id}` for searched,
paginated inventory and details, plus `/admin-api/dashboard`,
`/admin-api/inventory`, `/admin-api/sync`, and
`/admin-api/settings` for monitoring. When the server reports
`web_inventory_enabled=true`, the inventory page can create storage locations
and components, edit component metadata, and record inbound/outbound movements.
The Android client also uses
`/admin-api/part-lookup` for optional supplier metadata enrichment during JLC
import flows, while `/admin-api/lcsc/lookup` remains available for direct
compatibility use.

Platform-specific notes live in:

- `admin-web/README.md`

## GitHub Actions

The repository now includes three workflows under `.github/workflows/`:

- `ci.yml`: runs backend tests, separated admin-web build validation, Android
  debug build, Windows build, and changelog/version validation on push/pull request
- `release-from-changelog.yml`: watches `docs/CHANGELOG.md` on the default
  branch, syncs version files if needed, creates the matching `v*` tag, and
  then invokes the reusable release workflow in the same orchestration chain
- `release.yml`: builds release artifacts on `workflow_dispatch`, `workflow_call`,
  and `v*` tags

Branch pushes explicitly trigger CI; version tags use the release workflow.
Reusable release calls resolve their supplied inputs before examining the
caller event, so a changelog-triggered branch push still publishes its requested
release tag. Build success alone does not prove that release assets were uploaded.

Release artifacts produced by GitHub Actions:

- `component-vault-android-release.apk`
- `component-vault-admin-web.zip`
- `component-vault-windows-portable-x64.zip`

Required GitHub Secrets for Android release signing:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

No additional `RELEASE_AUTOMATION_TOKEN` secret is required. The changelog
release workflow now uses the default `GITHUB_TOKEN` with
`permissions: contents: write`.

Windows releases contain a self-contained WinUI 3 ZIP. MSIX packages,
self-signed certificates and certificate-install scripts are no longer published.
The application update page selects the Windows portable ZIP.

GitHub Actions now pins Android builds to Java 21, Gradle `8.11.1`, and SDK
Build Tools `35.0.0`. The local `.\scripts\android-gradle.ps1` helper exists
only to keep Windows hosts on Android Studio's JBR 21 while still using the
same repository wrapper version.

When downloading from the GitHub Actions run page instead of a tagged GitHub
Release, first extract the outer workflow artifact archive, then use the inner
`component-vault-windows-portable-x64.zip`.

The release workflow also publishes a zipped `admin-web/dist` bundle for
static deployment of the separated web admin.

## Legacy Flutter Reference

`client/` remains in the repository as a migration reference for field names,
page inventory, and local-first workflow behavior. It is no longer the target
production client UI stack.

## Server Development

Use a project-local virtual environment so the host Python environment stays
clean:

```powershell
cd server
.\scripts\bootstrap.ps1
.\scripts\run-dev.ps1
```

If PowerShell blocks activation scripts, use:

```powershell
cd server
.\scripts\bootstrap.ps1
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8787
```

`.\scripts\bootstrap.ps1` now self-heals a broken Windows `py -3.12`
registration by falling back to a repo-local `uv`-managed Python 3.12 under
`.uv-python/` when `uv` is available.

Run tests with:

```powershell
cd server
.\.venv\Scripts\python.exe -m pytest
```

Latest healthy-host verification snapshot:

- `.\.venv\Scripts\python.exe -m pytest` -> backend suite passed on `2026-05-08`
- `GET /admin-api/dashboard` -> `200 OK` with bearer token on `2026-05-08`

## Docker Deployment

For a NAS or an existing Docker installation, use the
[Docker / Synology quickstart](docs/docker-quickstart.md). Mount the API's `/data`
directory to retain the database, `config.json`, and bounded `logs/server.log`.
New installations can leave `API_TOKEN` empty and open `http://server:8787/setup`
to configure access before using clients. The setup API closes anonymous writes
after initialization; existing environment-configured installations still require
their current token. No Docker Hub publishing credentials are needed to deploy.
The full inventory Web app remains optional on port 8081.

To build from source:

```powershell
docker compose up --build
```

The service listens on `http://localhost:8787` by default and persists data in
the Docker volume `component_vault_data`.

The separated admin web container is available at:

- `http://localhost:8081/`

### Server Environment Variables

- `API_TOKEN`: shared bearer token required by `/auth/ping`, `/sync/push`, and
  `/sync/pull`, and `/admin-api/*`
- `DATABASE_PATH`: SQLite file path used by the FastAPI service
- `CONFIG_PATH`: optional configuration file location; defaults beside the database
- `LOG_DIR`: optional application log directory; defaults to `logs` beside the database
- `APP_HOST`: bind host for local development
- `APP_PORT`: bind port for local development
- `ADMIN_WEB_ORIGINS`: comma-separated browser origins allowed to call the API
- `LCSC_OPENAPI_KEY`: optional LCSC OpenAPI key for official part lookup
- `LCSC_OPENAPI_SECRET`: optional LCSC OpenAPI secret for official part lookup
- `LCSC_OPENAPI_BASE_URL`: base URL for the LCSC OpenAPI, default
  `https://ips.lcsc.com`
- `LCSC_LOOKUP_CACHE_TTL_SECONDS`: server-side in-memory cache TTL for LCSC
  lookup responses, default `43200`
- `IMPORT_RULES_REMOTE_URL`: optional JSON URL used to refresh bundled part
  recognition rules on the server
- `IMPORT_RULES_REFRESH_HOURS`: refresh age threshold for the cached server
  recognition rules file, default `24`
- `ENABLE_WEB_FALLBACK_RESOLVERS`: enables public LCSC web-page fallback for
  `/admin-api/part-lookup` when OpenAPI credentials are unavailable, default
  `false`
- `WEB_INVENTORY_ENABLED`: enables authenticated admin-web inventory and
  storage-location writes, default `false`

## Sync Contract

The optional server MQTT publisher sends accepted inventory snapshots to your
own broker for Home Assistant or dashboard subscriptions. It is disabled by
default. Configure `MQTT_ENABLED`, `MQTT_HOST`, `MQTT_PORT`, `MQTT_TLS`,
`MQTT_USERNAME`, `MQTT_PASSWORD`, `MQTT_TOPIC_PREFIX`, and `MQTT_CLIENT_ID` in the
API environment; Compose forwards the same variables. A transactional SQLite
`mqtt_outbox` retries retained QoS 1 messages without waiting on the broker in
the sync request. See [MQTT setup and Home Assistant examples](docs/mqtt.md).
`GET /admin-api/mqtt/status` uses the existing bearer token.
The web Settings page shows live publisher status and saves MQTT configuration
for the next service restart. Saved values override MQTT environment defaults;
broker credentials are stored in the server SQLite database and never returned
by the configuration API. Protect database backups accordingly.

Native inventory rows now show an outbound donut. The statistical total is
current stock plus all valid recorded outbound quantities; adjustments and
deleted movements are excluded from the outbound total. This is not cumulative
purchases or proof of consumption. Component details show the counts and product
image; missing historical movements are never inferred.

Android and Windows Settings / About now show application identity, installed
version, the GPLv3 license, project and release links, and manual GitHub update
checks. A persisted update-channel selector defaults to Stable; Dev includes
public prereleases. Semantic version ordering prevents automatic downgrades.
Checks show notes and offer the matching APK or Windows portable ZIP.
Downloads open through the system browser; installation remains a user action.
See [About and application updates](docs/app-updates.md).

- `GET /health`: unauthenticated health probe
- `POST /auth/ping`: validate the configured token.
- `POST /sync/push`: upload the latest local entity state.
- `GET /sync/pull?since=<iso8601>`: download all remote changes after the
  provided timestamp.
- `GET /admin-api/*`: admin snapshots for the separated web console.
- `GET/POST /admin-api/storage-locations`: list active locations or create one;
  POST is disabled unless `WEB_INVENTORY_ENABLED=true`.
- `POST /admin-api/components`, `PUT /admin-api/components/{id}`, and
  `POST /admin-api/components/{id}/movements`: optional Web create, metadata
  edit, and inbound/outbound operations with request receipts and optimistic versions.
- `GET /admin-api/part-lookup?...`: token-protected hybrid recognition endpoint
  that applies bundled server rules first and then optional LCSC OpenAPI or
  public-web lookup.
- `GET /admin-api/recognition-rules/meta`: inspect the active bundled or
  refreshed server rule pack.
- `POST /admin-api/recognition-rules/refresh`: refresh the server rule pack from
  `IMPORT_RULES_REMOTE_URL` when configured.
- `GET /admin-api/lcsc/lookup?sku=<part>&mpn=<mpn>&name=<name>`: direct
  compatibility endpoint for the LCSC proxy lookup.

All synchronized entities use:

- global string `id`
- UTC `updated_at`
- soft delete flag `deleted`

For endpoint examples and payload details, see
[docs/integration-guide.md](/D:/Project_Folder/Component_warehouse/docs/integration-guide.md).
For deployment and troubleshooting, see
[docs/runbook.md](/D:/Project_Folder/Component_warehouse/docs/runbook.md).
