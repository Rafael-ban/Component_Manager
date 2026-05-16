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

`.\scripts\bootstrap.ps1` now repairs a broken or missing Windows
`py -3.12` launcher registration by falling back to a repo-local
`uv`-managed Python 3.12 under `.uv-python/` when `uv` is installed.

Verification snapshot on a healthy local toolchain (`2026-05-08`):

- `.\.venv\Scripts\python.exe -m pytest` -> backend suite passed
- `/admin-api/dashboard` smoke request with token -> `200 OK`

## Local Admin Web Development

The separated admin web console lives in `admin-web/`.

```powershell
cd admin-web
cmd /c npm install
cmd /c npm run dev
```

Default local addresses:

- FastAPI API: `http://localhost:8787`
- Admin web: `http://localhost:5173`

## Docker Deployment

Start the bundled single-user stack with:

```powershell
docker compose up --build
```

The compose file publishes `8787` and stores the SQLite database in the Docker
volume `component_vault_data`.

Admin web is available at:

- `http://localhost:8081/`

## Environment Variables

### Server

- `API_TOKEN`: required shared token for all authenticated endpoints
- `DATABASE_PATH`: SQLite file path used by the FastAPI service
- `APP_HOST`: host binding for direct local development
- `APP_PORT`: port binding for direct local development
- `ADMIN_WEB_ORIGINS`: comma-separated origins allowed to call the API from the
  separated admin web app
- `LCSC_OPENAPI_KEY`: optional LCSC OpenAPI key used by
  `/admin-api/lcsc/lookup`
- `LCSC_OPENAPI_SECRET`: optional LCSC OpenAPI secret used by
  `/admin-api/lcsc/lookup`
- `LCSC_OPENAPI_BASE_URL`: optional LCSC OpenAPI base URL, default
  `https://ips.lcsc.com`
- `LCSC_LOOKUP_CACHE_TTL_SECONDS`: server-side in-memory cache TTL for official
  lookup responses, default `43200`
- `IMPORT_RULES_REMOTE_URL`: optional JSON URL used to refresh the server-side
  recognition rule pack consumed by `/admin-api/part-lookup`
- `IMPORT_RULES_REFRESH_HOURS`: refresh age threshold for the cached rule pack,
  default `24`
- `ENABLE_WEB_FALLBACK_RESOLVERS`: enables public LCSC product-page fallback
  for `/admin-api/part-lookup` when OpenAPI credentials are unavailable,
  default `false`

## Versioning Workflow

Repository versions are driven by `docs/CHANGELOG.md`.

Install the Git hook once after cloning:

```powershell
.\scripts\setup-git-hooks.ps1
```

On macOS/Linux:

```sh
sh ./scripts/setup-git-hooks.sh
```

Daily flow:

1. Add release notes under `## [Unreleased]` in `docs/CHANGELOG.md`
2. Set `bump:` to `major`, `minor`, or `patch`
3. Run `git commit`

The pre-commit hook then:

- computes the next version from the latest released changelog entry
- updates Android, admin-web, and Windows version files
- converts `Unreleased` into a concrete release section
- creates a fresh empty `Unreleased` template

CI/default-branch flow:

- `ci.yml` runs `python tools/versioning/sync_version.py --validate`
- `release-from-changelog.yml` watches `docs/CHANGELOG.md` on the repository
  default branch
- if unreleased notes arrive without the local hook, the workflow applies the
  same sync server-side and pushes `chore(release): sync version to X.Y.Z`
- whether the release was synced locally or by GitHub Actions, the workflow
  pushes the matching `vX.Y.Z` tag if it does not already exist
- the same workflow run then calls `release.yml` directly to build Android,
  admin-web, and Windows artifacts and create or update the GitHub Release

Manual checks:

Active branch validation:

```powershell
python tools/versioning/sync_version.py --validate
```

Strict released-state validation:

```powershell
python tools/versioning/sync_version.py --check
```

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

Verified working configuration on this host (`2026-05-08`):

- Android SDK: `C:\Users\gdblz\AppData\Local\Android\Sdk`
- JDK: `D:\android_studio\jbr`
- Gradle: `D:\dev-tool\gradle\bin\gradle.bat`
- project-local Gradle cache: `D:\Project_Folder\Component_warehouse\.gradle-user-home`
- project-local Android user home: `D:\Project_Folder\Component_warehouse\.android-user`
- the repository does not hardcode `org.gradle.java.home`, so Gradle now uses
  `JAVA_HOME` or the toolchain configured by the host/runner

Create or confirm `android-client/local.properties`:

```properties
sdk.dir=C:\\Users\\gdblz\\AppData\\Local\\Android\\Sdk
```

Build verification:

```powershell
$env:GRADLE_USER_HOME='D:\Project_Folder\Component_warehouse\.gradle-user-home'
$env:JAVA_HOME='D:\android_studio\jbr'
$env:ANDROID_SDK_ROOT='C:\Users\gdblz\AppData\Local\Android\Sdk'
$env:ANDROID_HOME='C:\Users\gdblz\AppData\Local\Android\Sdk'
$env:ANDROID_USER_HOME='D:\Project_Folder\Component_warehouse\.android-user'
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client help
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleDebug
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client testDebugUnitTest
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleRelease
```

Simpler Windows helper:

```powershell
.\scripts\android-gradle.ps1 assembleDebug
.\scripts\android-gradle.ps1 assembleRelease
```

That helper prefers Android Studio's embedded JBR if the current machine
default `java` is newer than the Android lint toolchain supports.

Current host compatibility note:

- system `java -version` returns `25.0.1`
- Android `assembleRelease` can fail under that JDK during `lintVitalAnalyzeRelease`
  with `IllegalArgumentException: 25.0.1`
- using Android Studio's bundled `D:\android_studio\jbr` (`OpenJDK 21`) avoids
  the lint failure on this host

Verification result on this host:

- `help` -> success
- `assembleDebug` -> success
- `testDebugUnitTest` -> success
- `assembleRelease` -> success
- Android metrics warnings and Kotlin daemon fallback messages may appear on
  this host, but the builds still complete successfully

Android Studio note:

- The official `Layout Editor` tutorial applies to View/XML layouts.
- This project uses Jetpack Compose, so visual editing should use Compose
  Preview, Interactive Preview, Run Preview, and Live Edit inside Android
  Studio.

Implemented client behaviors:

- local SQLite persistence for components, stock movements, and sync queue
- shared-preference sync settings
- component create/edit/soft delete
- movement entry and quantity recalculation
- manual sync, connection test, and optional auto sync
- JLC text/QR import with bundled offline recognition rules, device-only import
  learning, and optional server-assisted enrichment through
  `GET /admin-api/part-lookup`, while keeping unresolved canonical names blank
  until the user or server confirms them
- supplier packaging OCR import plus generated JLC-compatible or warehouse QR
  labels

### Windows

The Windows client is a WinUI 3 project in `windows-client/`.

Basic check:

```powershell
dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release /p:PublishProfile=win-x64.pubxml
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release -r win-x64 -p:PublishProfile= -p:WindowsPackageType=None -p:GenerateAppxPackageOnBuild=false -p:AppxPackageSigningEnabled=false -p:WindowsAppSDKSelfContained=true -p:SelfContained=true -p:PublishSingleFile=false -o windows-client\artifacts\portable-local
```

Windows output paths after publish:

- `windows-client\ComponentVault.WinUI\bin\Release\net9.0-windows10.0.19041.0\win-x64\AppPackages\`
- `windows-client\artifacts\portable-local\`

Implemented client behaviors:

- local SQLite persistence for components, stock movements, and sync queue
- component create/edit/soft delete
- movement entry and quantity recalculation
- manual sync, connection test, and optional auto sync
- WinUI XAML Designer previews are available in Visual Studio 2022 by opening
  the page files under `windows-client\ComponentVault.WinUI\Views\`

## GitHub Actions

The repository includes three GitHub Actions workflows:

- `.github/workflows/ci.yml`
  Runs server tests, admin-web build validation, Android debug compilation,
  Windows build validation, and changelog/version validation.
- `.github/workflows/release-from-changelog.yml`
  Watches `docs/CHANGELOG.md` on the default branch, applies version sync if
  needed, pushes the release tag, and then invokes the reusable release
  workflow in the same orchestration chain.
- `.github/workflows/release.yml`
  Runs Android release packaging, admin-web static bundle packaging, and
  Windows dual-mode packaging on manual trigger, reusable workflow calls, and
  `v*` tag pushes.

### Android Release Secrets

Configure these GitHub repository secrets before running `release.yml`:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

No additional `RELEASE_AUTOMATION_TOKEN` secret is required. Changelog-driven
release automation now runs with the default `GITHUB_TOKEN` plus
`permissions: contents: write`.

The workflow decodes the keystore into a runner-local temp file and exports:

- `ANDROID_KEYSTORE_PATH`

### Release Outputs

`release.yml` produces:

- `component-vault-android-release.apk`
- `component-vault-admin-web.zip`
- `component-vault-windows-portable-x64.zip`
- `component-vault-windows-x64.msix`
- `component-vault-windows-test-certificate.cer`
- `Install-ComponentVault.ps1`
- `README-Windows-Release.txt`

On changelog-driven releases, reusable workflow calls, and `v*` tag runs that
publish a release, the workflow also attaches the Windows portable zip, the
MSIX install set, the Android APK, and the `admin-web` static bundle to the
GitHub Release.

### Installing The Windows Release

GitHub Release now ships two Windows distribution modes.

Portable zip:

1. If the file came from the GitHub Actions artifact page, extract the outer
   workflow artifact archive first
2. Download or locate `component-vault-windows-portable-x64.zip`
3. Extract it
4. Open the extracted `component-vault-windows-portable-x64` folder
5. Run `ComponentVault.WinUI.exe`

If the executable fails during startup, check:

- `%LOCALAPPDATA%\ComponentVault\logs\startup.log`

MSIX package:

1. Download `component-vault-windows-x64.msix`
2. Download `component-vault-windows-test-certificate.cer`
3. Open PowerShell as Administrator
4. Run `Install-ComponentVault.ps1`

The script imports the certificate into `Cert:\LocalMachine\TrustedPeople` and
then runs `Add-AppxPackage` for the MSIX package.

### Legacy Flutter

`client/` remains in the repo only as a migration reference and should not be
treated as the primary production UI target.

## Smoke Checks

### Server Health

```powershell
curl http://localhost:8787/health
```

### Admin API Check

```powershell
curl http://localhost:8787/admin-api/dashboard `
  -H "Authorization: Bearer change-me"
```

### Auth Check

```powershell
curl -X POST http://localhost:8787/auth/ping `
  -H "Authorization: Bearer change-me"
```

## Common Failure Modes

### `py` exists but Python is broken

- Symptom: `py -3.12` cannot launch the registered interpreter.
- Fix: re-run `.\scripts\bootstrap.ps1`; it will recreate `server/.venv` and
  fall back to a repo-local `uv`-managed Python 3.12 when `uv` is available.
  If `uv` is not installed, repair or reinstall Python 3.12 first.

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

### Gradle is pinned to a local Java path

- Symptom: GitHub Actions or another machine fails with
  `Value '.../jbr' given for org.gradle.java.home Gradle property is invalid`.
- Cause: `android-client/gradle.properties` contains a machine-specific
  `org.gradle.java.home` override.
- Fix: keep that property out of the repository and provide Java through
  `JAVA_HOME`, Android Studio, or the CI runner toolchain instead.

### Android theme resource missing

- Symptom: AAPT fails with `resource style/Theme.Material3.DayNight.NoActionBar
  not found`.
- Cause: the app theme inherits from the Android Material theme, but the
  `com.google.android.material` dependency is missing.
- Fix: keep `com.google.android.material:material` in
  `android-client/app/build.gradle.kts`.

### Admin web cannot reach the API

- Symptom: login succeeds locally in one environment but browser requests fail
  with CORS errors.
- Cause: the browser origin is missing from `ADMIN_WEB_ORIGINS`.
- Fix: add the origin to `ADMIN_WEB_ORIGINS`, restart the FastAPI service, and
  retry from the admin web console.

### Android release workflow fails immediately

- Symptom: `release.yml` stops at signing secret validation.
- Cause: one or more Android signing secrets are missing from the GitHub
  repository settings.
- Fix: add all four required Android signing secrets before rerunning the
  release workflow.

### Android `assembleRelease` fails in `lintVitalAnalyzeRelease` on Java 25

- Symptom: local Windows `gradle -p android-client assembleRelease` fails in
  `:app:lintVitalAnalyzeRelease` with `IllegalArgumentException: 25.0.1` or a
  follow-up `org.jetbrains.uast.UastFacade` initialization error.
- Cause: the Android lint/UAST stack in the current toolchain is not compatible
  with the system Java 25 runtime.
- Fix: run builds with Android Studio's embedded JBR 21, either by setting
  `JAVA_HOME=D:\android_studio\jbr` before invoking Gradle or by using
  `.\scripts\android-gradle.ps1 assembleRelease`.

### Changelog release automation cannot push commit or tag

- Symptom: `release-from-changelog.yml` reaches `git push` and fails with a
  permission or protection error.
- Cause: the workflow token does not have `contents:write`, the repository is
  configured with read-only workflow permissions, the default branch blocks
  workflow pushes, or tag creation is restricted.
- Fix: grant GitHub Actions read/write repository permissions, keep
  `permissions: contents: write` on the workflow, and allow the workflow bot
  to push the synchronized release commit and `v*` tags.

### Changelog release automation does not create a GitHub Release

- Symptom: `release-from-changelog.yml` pushes or finds the tag, but the
  release creation job is skipped or fails afterward.
- Cause: the reusable `release.yml` workflow was not callable, a required
  Android signing secret is missing, or artifact packaging failed in one of the
  platform jobs.
- Fix: verify that `.github/workflows/release.yml` supports `workflow_call`,
  confirm all Android signing secrets are configured, and rerun the workflow
  after inspecting the failing build job.

### Windows MSIX install is blocked by certificate trust

- Symptom: Windows refuses to install the MSIX package or says the publisher is
  untrusted.
- Cause: the test signing certificate from the release has not been imported
  into `Cert:\LocalMachine\TrustedPeople`, or the install script was not run
  from an elevated PowerShell session.
- Fix: download `component-vault-windows-test-certificate.cer` and run
  `Install-ComponentVault.ps1` as Administrator, or manually import the
  certificate into `Cert:\LocalMachine\TrustedPeople` before installing the
  MSIX package.

### Windows artifact is downloaded and extracted but nothing obvious runs

- Symptom: the GitHub Actions Windows artifact is unpacked, but there is no
  direct executable at the root or the user expects the MSIX file itself to
  behave like a portable app.
- Cause: the Windows release now contains both a packaged MSIX flow and a
  separate portable zip; the artifact root is only a bundle of release files.
- Fix: either extract `component-vault-windows-portable-x64.zip` and run
  `ComponentVault.WinUI.exe`, or use `Install-ComponentVault.ps1` for the MSIX
  package flow.

### Windows client exits during startup

- Symptom: `ComponentVault.WinUI.exe` appears briefly, or the app shows a
  startup failure dialog.
- Cause: an unhandled startup or UI exception occurred on the target machine.
- Fix: open `%LOCALAPPDATA%\ComponentVault\logs\startup.log`, keep the dialog
  text, and use that exception message for the next debugging pass.

### Windows app or designer cannot resolve `TextFillColorSecondaryBrush`

- Symptom: startup log or XAML Designer reports
  `Cannot find a Resource with the Name/Key TextFillColorSecondaryBrush`.
- Cause: `windows-client\ComponentVault.WinUI\App.xaml` is missing the merged
  `XamlControlsResources` dictionary, so WinUI theme brushes and styles such as
  `TextFillColorSecondaryBrush`, `ControlFillColorSecondaryBrush`, and
  `AccentButtonStyle` are not available.
- Fix: restore `XamlControlsResources` in `App.xaml`, then rebuild the WinUI
  project and reopen the XAML page in Visual Studio.

### Duplicate SKU push rejected

- Symptom: `/sync/push` returns `409 Conflict`.
- Cause: two active components share the same `sku`.
- Fix: rename or soft-delete the conflicting component locally, then sync again.

### Negative stock blocked

- Symptom: recording a movement fails locally.
- Cause: an outbound or adjustment movement would drive stock below zero.
- Fix: correct the quantity or record an inbound adjustment first.
