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
- `server/`: FastAPI sync service with project-local virtual environment and
  read-only admin APIs for the web console.
- `.github/workflows/`: GitHub Actions CI and release artifact automation.
- `docs/`: architecture, integration, and operations notes.
- `docker-compose.yml`: self-hosted API deployment entrypoint.

## Key Behaviors

- Local-first storage with SQLite on the client.
- Manual sync and optional auto sync.
- Soft delete for synchronized entities.
- Inventory history recorded as stock movements.
- Self-hosted API secured by a shared API token.
- Separated admin web console backed by token-protected `/admin-api/*`.
- Active components enforce unique `sku`.
- Component `quantity` and `min_stock` are non-negative.

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
- Configure the GitHub repository secret `RELEASE_AUTOMATION_TOKEN` with
  `contents:write` permission for that workflow. A separate token is required
  because tag and commit pushes performed with the default `GITHUB_TOKEN` do
  not reliably trigger downstream workflows.

## Platform Status

- Windows native client: local SQLite, component editing, movement recording,
  sync settings, server sync wiring, Chinese-first WinUI pages, and dual-mode
  packaging are implemented; the Windows UI now follows an inventory-first
  `NavigationView` shell with `Inventory`, `Movements`, `Overview`, and
  `Settings` destinations, dense list/detail workspaces, and updated XAML
  designer sample data; `dotnet build` verified successfully on `2026-05-09`,
  MSIX-oriented `dotnet publish` was verified successfully on `2026-05-07`,
  and unpackaged portable `dotnet publish` was verified successfully on
  `2026-05-08`.
- Android native client: local SQLite, component editing, movement recording,
  JLC text and QR import, supplier packaging OCR import, compact label
  preview/export, expanded settings, server sync wiring, and Chinese-first
  Compose interface resources are implemented; the Android UI now follows an
  inventory-first adaptive Compose shell with `Inventory`, `Movements`,
  `Overview`, and `Settings` destinations, compact phone detail drill-down,
  tablet list-detail layouts, quantity-first import confirmation, generated
  JLC-compatible or warehouse QR labels, and scroll-safe `Scaffold` inset
  handling; `assembleDebug` and `assembleRelease` were verified successfully
  on `2026-05-10` on this host with the configured Android SDK and JDK paths.
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
- inventory movement entry
- sync settings save/test/sync-now
- push/pull against the FastAPI sync service

`dotnet publish` now supports two Windows release shapes:

- `windows-client\ComponentVault.WinUI\bin\Release\net9.0-windows10.0.19041.0\win-x64\AppPackages\`
- `windows-client\artifacts\portable-local\`

The first path is the test-signed MSIX package output. The second path is an
unpackaged portable folder that can be zipped and run directly via
`ComponentVault.WinUI.exe`.

## Android Client

The Android client lives in `android-client/` as a Gradle/Compose app.
Before building, make sure the machine has:

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Gradle access to Google Maven
- or `android-client/local.properties` created from
  `android-client/local.properties.example`

Verified working setup on this host:

- Android SDK: `C:\Users\gdblz\AppData\Local\Android\Sdk`
- JDK: `D:\android_studio\jbr`
- Gradle launcher: `D:\dev-tool\gradle\bin\gradle.bat`
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
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client help
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleDebug
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleRelease
```

The current Android implementation uses on-device SQLite plus shared
preferences and supports:

- component create/edit/soft delete
- inventory movement entry
- JLC copied-text import with automatic field mapping
- JLC package QR import through an in-app CameraX scanner backed by bundled
  ML Kit barcode scanning, with runtime camera permission handling
- supplier packaging OCR import through an in-app CameraX scanner backed by
  bundled ML Kit Chinese text recognition
- compact label preview plus PNG/PDF export, generating JLC-compatible QR
  payloads for JLC-sourced items and warehouse QR payloads for other items
- sync settings save/test/sync-now
- separate sync-on-launch and sync-after-write behavior controls
- import defaults and an in-app About section
- push/pull against the FastAPI sync service
- Chinese-first UI resources for the primary screens, with a matching
  Simplified Chinese (`zh-CN`) resource set
- an inventory-first adaptive shell:
  `Inventory`, `Movements`, `Overview`, `Settings`
- compact phone flows centered on search, filters, dense lists, and full-screen
  detail or form routes
- tablet layouts that keep persistent list-detail panes for inventory and
  movement history

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
uses `/admin-api/dashboard`, `/admin-api/inventory`, `/admin-api/sync`, and
`/admin-api/settings` for read-only monitoring.

Platform-specific notes live in:

- `admin-web/README.md`

## GitHub Actions

The repository now includes three workflows under `.github/workflows/`:

- `ci.yml`: runs backend tests, separated admin-web build validation, Android
  debug build, Windows build, and changelog/version validation on push/pull request
- `release-from-changelog.yml`: watches `docs/CHANGELOG.md` on the default
  branch, syncs version files if needed, and creates the matching `v*` tag
- `release.yml`: builds release artifacts on `workflow_dispatch` and `v*` tags

Release artifacts produced by GitHub Actions:

- `component-vault-android-release.apk`
- `component-vault-admin-web.zip`
- `component-vault-windows-portable-x64.zip`
- `component-vault-windows-x64.msix`
- `component-vault-windows-test-certificate.cer`
- `Install-ComponentVault.ps1`
- `README-Windows-Release.txt`

Required GitHub Secrets for Android release signing:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Required GitHub Secret for changelog-triggered release automation:

- `RELEASE_AUTOMATION_TOKEN`

Windows release packaging uses a runner-generated self-signed certificate for
test distribution. The release workflow publishes both a portable zip and an
MSIX package. The portable zip can be extracted and launched directly with
`ComponentVault.WinUI.exe`. The MSIX path still ships with the matching `.cer`
certificate plus an install script that must be run from an elevated
PowerShell window. The script imports the certificate into
`Cert:\LocalMachine\TrustedPeople` before calling `Add-AppxPackage`.

`release-from-changelog.yml` should use a personal access token or fine-grained
token with `contents:write` permission. That token allows the workflow to push
the synchronized release commit and `vX.Y.Z` tag so `release.yml` can run from
the tag event.

When downloading from the GitHub Actions run page instead of a tagged GitHub
Release, first extract the outer workflow artifact archive, then use the inner
`component-vault-windows-portable-x64.zip` or the MSIX install set.

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

Run tests with:

```powershell
cd server
.\.venv\Scripts\python.exe -m pytest
```

Current local verification:

- `.\.venv\Scripts\python.exe -m pytest` -> `9 passed`
- `GET /admin-api/dashboard` -> `200 OK` with bearer token

## Docker Deployment

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
- `APP_HOST`: bind host for local development
- `APP_PORT`: bind port for local development
- `ADMIN_WEB_ORIGINS`: comma-separated browser origins allowed to call the API

## Sync Contract

- `GET /health`: unauthenticated health probe
- `POST /auth/ping`: validate the configured token.
- `POST /sync/push`: upload the latest local entity state.
- `GET /sync/pull?since=<iso8601>`: download all remote changes after the
  provided timestamp.
- `GET /admin-api/*`: read-only admin snapshots for the separated web console.

All synchronized entities use:

- global string `id`
- UTC `updated_at`
- soft delete flag `deleted`

For endpoint examples and payload details, see
[docs/integration-guide.md](/D:/Project_Folder/Component_warehouse/docs/integration-guide.md).
For deployment and troubleshooting, see
[docs/runbook.md](/D:/Project_Folder/Component_warehouse/docs/runbook.md).
