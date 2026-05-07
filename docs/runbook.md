# Runbook

## Local Backend Development

Use the project-local virtual environment inside `server/.venv`.

```powershell
cd server
.\scripts\bootstrap.ps1
.\scripts\run-dev.ps1
```

Run tests with:

```powershell
cd server
.\.venv\Scripts\python.exe -m pytest
```

Verified on `2026-05-07`:

- `.\.venv\Scripts\python.exe -m pytest` -> `7 passed`
- `/admin/` smoke request -> `200 OK`

## Docker Deployment

Start the bundled single-user stack with:

```powershell
docker compose up --build
```

The compose file publishes `8787` and stores the SQLite database in the Docker
volume `component_vault_data`.

Admin UI is available at:

- `http://localhost:8787/admin/`

## Environment Variables

### Server

- `API_TOKEN`: required shared token for all authenticated endpoints
- `DATABASE_PATH`: SQLite file path used by the FastAPI service
- `APP_HOST`: host binding for direct local development
- `APP_PORT`: port binding for direct local development

## Client Bootstrap

### Android

The Android client is a Gradle/Compose app in `android-client/`.

Known machine requirements:

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Access to Google Maven repositories

Basic check:

```powershell
gradle -p android-client help
```

Verified working configuration on this host (`2026-05-07`):

- Android SDK: `D:\Ide\sdk\Android\android-sdk`
- JDK: `D:\Ide\sdk\Android\openjdk\jdk-21.0.8`
- Gradle: `D:\dev-tool\gradle\bin\gradle.bat`
- project-local Gradle cache: `D:\Project_Folder\Component_warehouse\.gradle-user-home`

Create or confirm `android-client/local.properties`:

```properties
sdk.dir=D:\\Ide\\sdk\\Android\\android-sdk
```

Build verification:

```powershell
$env:GRADLE_USER_HOME='D:\Project_Folder\Component_warehouse\.gradle-user-home'
$env:JAVA_HOME='D:\Ide\sdk\Android\openjdk\jdk-21.0.8'
$env:ANDROID_SDK_ROOT='D:\Ide\sdk\Android\android-sdk'
$env:ANDROID_HOME='D:\Ide\sdk\Android\android-sdk'
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client help
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleDebug
```

Verification result on this host:

- `help` -> success
- `assembleDebug` -> success
- `assembleRelease` -> success

Implemented client behaviors:

- local SQLite persistence for components, stock movements, and sync queue
- shared-preference sync settings
- component create/edit/soft delete
- movement entry and quantity recalculation
- manual sync, connection test, and optional auto sync

### Windows

The Windows client is a WinUI 3 project in `windows-client/`.

Basic check:

```powershell
dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release /p:PublishProfile=win-x64.pubxml
```

Implemented client behaviors:

- local SQLite persistence for components, stock movements, and sync queue
- component create/edit/soft delete
- movement entry and quantity recalculation
- manual sync, connection test, and optional auto sync

## GitHub Actions

The repository includes two GitHub Actions workflows:

- `.github/workflows/ci.yml`
  Runs server tests, Android debug compilation, and Windows build validation.
- `.github/workflows/release.yml`
  Runs Android release packaging and Windows publish packaging on manual trigger
  and `v*` tag pushes.

### Android Release Secrets

Configure these GitHub repository secrets before running `release.yml`:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow decodes the keystore into a runner-local temp file and exports:

- `ANDROID_KEYSTORE_PATH`

### Release Outputs

`release.yml` produces:

- `component-vault-android-release.apk`
- `component-vault-windows-win-x64.zip`

On `v*` tags, the workflow also attaches both files to the GitHub Release.

### Legacy Flutter

`client/` remains in the repo only as a migration reference and should not be
treated as the primary production UI target.

## Smoke Checks

### Server Health

```powershell
curl http://localhost:8787/health
```

### Auth Check

```powershell
curl -X POST http://localhost:8787/auth/ping `
  -H "Authorization: Bearer change-me"
```

## Common Failure Modes

### `py` exists but Python is broken

- Symptom: `py -3.12` cannot launch the registered interpreter.
- Fix: repair or reinstall Python 3.12, then re-run `.\scripts\bootstrap.ps1`.

### Flutter not found

- Symptom: `flutter` is not recognized.
- Impact: only the legacy reference client is affected.
- Fix: install Flutter only if you need to inspect or compare the old prototype.

### Android SDK not configured

- Symptom: `assembleDebug` fails with `SDK location not found`.
- Impact: Android client compilation and emulator/device workflows cannot
  start.
- Fix: install the Android SDK, set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or
  create `android-client/local.properties` with `sdk.dir=<absolute-sdk-path>`.

### Android theme resource missing

- Symptom: AAPT fails with `resource style/Theme.Material3.DayNight.NoActionBar
  not found`.
- Cause: the app theme inherits from the Android Material theme, but the
  `com.google.android.material` dependency is missing.
- Fix: keep `com.google.android.material:material` in
  `android-client/app/build.gradle.kts`.

### Android release workflow fails immediately

- Symptom: `release.yml` stops at signing secret validation.
- Cause: one or more Android signing secrets are missing from the GitHub
  repository settings.
- Fix: add all four required Android signing secrets before rerunning the
  release workflow.

### Duplicate SKU push rejected

- Symptom: `/sync/push` returns `409 Conflict`.
- Cause: two active components share the same `sku`.
- Fix: rename or soft-delete the conflicting component locally, then sync again.

### Negative stock blocked

- Symptom: recording a movement fails locally.
- Cause: an outbound or adjustment movement would drive stock below zero.
- Fix: correct the quantity or record an inbound adjustment first.
