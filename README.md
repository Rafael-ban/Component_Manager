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

## Platform Status

- Windows native client: local SQLite, component editing, movement recording,
  sync settings, server sync wiring, Chinese-first WinUI pages, and MSIX
  packaging are implemented; XAML designer sample data is now wired for the
  main pages; `dotnet build` verified successfully on `2026-05-08`, and
  MSIX-oriented `dotnet publish` was verified successfully on `2026-05-07`.
- Android native client: local SQLite, component editing, movement recording,
  sync settings, server sync wiring, and Chinese-first Compose interface
  resources are implemented; Compose Preview sample states are now wired for
  the main screens; `help` and `assembleRelease` were verified successfully on
  `2026-05-07`, and `assembleDebug` verified successfully on `2026-05-08` on
  this host with the configured Android SDK and JDK paths.
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
```

The current desktop implementation uses a local SQLite database under the
user's local app data directory and supports:

- component create/edit/soft delete
- inventory movement entry
- sync settings save/test/sync-now
- push/pull against the FastAPI sync service

`dotnet publish` now emits an MSIX package under:

- `windows-client\ComponentVault.WinUI\bin\Release\net9.0-windows10.0.19041.0\win-x64\AppPackages\`

## Android Client

The Android client lives in `android-client/` as a Gradle/Compose app.
Before building, make sure the machine has:

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Gradle access to Google Maven
- or `android-client/local.properties` created from
  `android-client/local.properties.example`

Verified working setup on this host:

- Android SDK: `D:\Ide\sdk\Android\android-sdk`
- JDK: `D:\Ide\sdk\Android\openjdk\jdk-21.0.8`
- Gradle launcher: `D:\dev-tool\gradle\bin\gradle.bat`
- local SDK file: `android-client/local.properties`

Then run:

```powershell
$env:GRADLE_USER_HOME='D:\Project_Folder\Component_warehouse\.gradle-user-home'
$env:JAVA_HOME='D:\Ide\sdk\Android\openjdk\jdk-21.0.8'
$env:ANDROID_SDK_ROOT='D:\Ide\sdk\Android\android-sdk'
$env:ANDROID_HOME='D:\Ide\sdk\Android\android-sdk'
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client help
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleDebug
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleRelease
```

The current Android implementation uses on-device SQLite plus shared
preferences and supports:

- component create/edit/soft delete
- inventory movement entry
- sync settings save/test/sync-now
- push/pull against the FastAPI sync service
- Chinese-first UI resources for the primary screens, with a matching
  Simplified Chinese (`zh-CN`) resource set

Android visual editing is based on Compose Preview in Android Studio. Open
`android-client/app/src/main/java/com/componentvault/android/ui/screen/ComponentVaultApp.kt`
to use the built-in preview states. Visual Studio does not provide an
equivalent native Compose designer.

## UI Editing

- Android: use Android Studio Compose Preview on
  `android-client/app/src/main/java/com/componentvault/android/ui/screen/ComponentVaultApp.kt`.
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

The repository now includes two workflows under `.github/workflows/`:

- `ci.yml`: runs backend tests, separated admin-web build validation, Android
  debug build, and Windows build on push/pull request
- `release.yml`: builds release artifacts on `workflow_dispatch` and `v*` tags

Release artifacts produced by GitHub Actions:

- `component-vault-android-release.apk`
- `component-vault-admin-web.zip`
- `component-vault-windows-x64.msix`
- `component-vault-windows-test-certificate.cer`
- `Install-ComponentVault.ps1`

Required GitHub Secrets for Android release signing:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Windows release packaging uses a runner-generated self-signed certificate for
test distribution. The release workflow publishes both the `.msix` package and
the matching `.cer` certificate, plus an install script that imports the
certificate into the current user's `TrustedPeople` store before calling
`Add-AppxPackage`.

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
