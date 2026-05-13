# Android Native Client

This directory contains the Android-native client built with Jetpack Compose
and Material 3.

## Current State

- Native navigation shell implemented
- Inventory-first adaptive shell implemented with `Inventory`, `Movements`,
  `Overview`, and `Settings` destinations
- Local SQLite persistence and sync settings persistence implemented
- Component create/edit/soft delete workflow implemented
- Movement entry workflow implemented
- JLC copied-text import and package QR import implemented through an in-app
  CameraX scanner backed by bundled ML Kit barcode scanning
- Supplier packaging OCR import implemented through an in-app CameraX scanner
  backed by bundled ML Kit Chinese text recognition
- Quantity-first import confirmation implemented with optional full editor
  handoff for JLC text, JLC-compatible QR payloads, warehouse labels, and
  supplier packaging text
- Compact label preview plus PNG/PDF export implemented for inventory labels,
  including `10x40mm QR`, horizontal `30x40mm QR`, and pure text strip
  templates
- Expanded local-only settings implemented for sync behavior, import defaults,
  and About
- Optional hybrid server-side part enrichment implemented for JLC text and QR
  imports through `/admin-api/part-lookup`, with local cache reuse and an
  in-app toggle, while `/admin-api/lcsc/lookup` remains a server compatibility
  endpoint
- Push/pull sync wiring implemented against the FastAPI service
- Chinese-first Material 3 UI implemented for the primary screens, with a
  matching Simplified Chinese (`zh-CN`) resource set
- Compose Preview sample states added for inventory, overview, movements,
  settings, shell, and form flows so the main screens can be inspected without
  booting an emulator
- Preview rendering now uses injected static `ComponentVaultStrings` sample
  bundles plus content-level preview composables so Android Studio does not
  have to resolve the runtime `R.string` graph for preview-only rendering
- `gradle -p android-client help` verified successfully on `2026-05-08`
- `assembleDebug` verified successfully on `2026-05-10` on this host
- `assembleRelease` verified successfully on `2026-05-10` on this host
- On this host, non-blocking Android metrics warnings and Kotlin daemon
  fallback messages can appear during verification

## Build Preconditions

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Java provided through `JAVA_HOME`, Android Studio, or the CI runner toolchain
- Gradle can resolve Android plugins and dependencies from Google Maven
- Alternatively, copy `local.properties.example` to `local.properties` and set
  `sdk.dir=<absolute-sdk-path>`
- The repository intentionally does not commit `org.gradle.java.home`, so
  machine-specific JDK paths do not break other environments
- For official JLC/LCSC enrichment during imports, the configured sync server
  must also expose `/admin-api/lcsc/lookup` with valid LCSC OpenAPI credentials

## This Host Setup

Verified working paths on this Windows machine:

- Android SDK: `C:\Users\gdblz\AppData\Local\Android\Sdk`
- JDK: `D:\android_studio\jbr`
- Gradle launcher: `D:\dev-tool\gradle\bin\gradle.bat`
- Project-local Gradle cache: `D:\Project_Folder\Component_warehouse\.gradle-user-home`
- Project-local Android user home: `D:\Project_Folder\Component_warehouse\.android-user`

`android-client/local.properties` should contain:

```properties
sdk.dir=C:\\Users\\gdblz\\AppData\\Local\\Android\\Sdk
```

## Quick Check

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

For this Windows host, you can also use the repository helper script:

```powershell
.\scripts\android-gradle.ps1 assembleDebug
.\scripts\android-gradle.ps1 assembleRelease
```

That helper prefers Android Studio's embedded JBR when the current machine
default `java` is newer than the Android lint toolchain supports.

If this host logs Kotlin daemon access warnings under
`C:\Users\gdblz\AppData\Local\kotlin\daemon\...`, Gradle may fall back to
non-daemon compilation and still finish successfully.

If local `assembleRelease` fails in `lintVitalAnalyzeRelease` with
`IllegalArgumentException: 25.0.1` or a follow-up `org.jetbrains.uast.UastFacade`
initialization error, the machine is likely using an unsupported Java 25
runtime. Use `.\scripts\android-gradle.ps1 assembleRelease` or set
`JAVA_HOME=D:\android_studio\jbr` before invoking Gradle.

## Visual Editing

Jetpack Compose does not use the old XML layout designer. For this client,
visual editing means Compose Preview, Interactive Preview, Run Preview, and
Live Edit inside Android Studio.

The Android Studio tutorial for `Layout Editor` applies to View/XML layouts,
not to this Compose client. For this project, use the Compose tooling flow
instead of the XML drag-and-drop editor.

Use this workflow:

1. Open `android-client/` in Android Studio.
2. Open one of the dedicated preview files under
   `app/src/main/java/com/componentvault/android/ui/screen/preview/`.
3. Start with:
   `OverviewScreenPreview.kt`, `InventoryScreenPreview.kt`,
   `MovementsScreenPreview.kt`, `SettingsScreenPreview.kt`,
   `QrScannerPreview.kt`,
   `AppShellPreviews.kt`, or `DialogsPreview.kt`.
4. In the editor, switch to `Split` or `Design`.
5. Use the Preview panel group filter to start with `Phone` and `Tablet`.
6. Use `Locale`, `Theme`, `Accessibility`, `Shell`, and `Dialogs` only for
   targeted follow-up checks after the baseline previews render cleanly.
7. If the preview does not refresh, click `Build & Refresh`.
8. If Preview still shows stale `R.string` / `NoSuchFieldError` render
   problems after code changes, run `Build > Rebuild Project` once and reopen
   the preview file.
9. For lightweight taps, text entry, and dialog-state checks, use
   `Interactive Preview`.
10. For real device context, permissions, and runtime behavior, use
   `Run Preview` or a normal emulator/device run.
11. Once the app is running, use `Live Edit` for rapid spacing, color, and
    typography adjustments.

The current preview catalog is baseline-first:

- baseline light previews for inventory phone and tablet states, overview,
  movements, settings, shell, and form surfaces
- targeted secondary previews for `zh-CN`, dark theme, and large-font checks
  on selected high-value states instead of multiplying every screen by every
  variant
- dialog and shell previews kept to single light variants to reduce Compose
  Preview rendering load inside Android Studio

This lighter matrix keeps Preview more reliable while still covering the
important density, locale, theme, and accessibility checks before running an
emulator.

## UI Structure

The Android UI is now split so Preview-friendly composables are isolated from
the `ViewModel` entrypoint:

- `ui/screen/ComponentVaultApp.kt`
  Route/container that connects `InventoryViewModel` to the UI
- `ui/screen/ComponentVaultStrings.kt`
  Runtime string bundle assembly plus preview-safe composition locals for
  shared UI copy
- `ui/screen/ComponentVaultShell.kt`
  Adaptive app shell and destination routing
- `ui/screen/OverviewScreen.kt`
  Summary-first overview surface
- `ui/screen/InventoryScreen.kt`
  Search/filter-driven inventory list, detail flows, and JLC import entrypoint
- `ui/screen/MovementsScreen.kt`
  Movement history and detail flows
- `ui/screen/SettingsScreen.kt`
  Grouped sync, import, scanner, and About layout
- `ui/screen/JlcImportScreen.kt`
  Quantity-first import surface for JLC text, JLC-compatible QR payloads,
  supplier packaging OCR, warehouse labels, and background official metadata
  enrichment
- `ui/screen/JlcQrScannerScreen.kt`
  App-internal CameraX + bundled ML Kit QR scanner surface with permission handling
- `ui/screen/ImportTextScannerScreen.kt`
  App-internal CameraX + bundled ML Kit Chinese text recognition surface for
  supplier packaging OCR
- `ui/screen/ComponentLabelPreviewScreen.kt`
  Compact inventory label preview and PNG/PDF export flow with physical-size
  templates and preview-safe aspect ratios
- `ui/screen/InventoryForms.kt`
  Adaptive full-screen and dialog-based editing forms
- `ui/screen/InventoryUiParts.kt`
  Shared dense list rows, badges, and section containers
- `data/InventoryRepository.kt`
  Local SQLite, sync wiring, and cached `/admin-api/part-lookup` integration
- `ui/screen/preview/`
  Preview annotations, preview host, static preview string bundles, sample
  states, and dedicated preview files

This split keeps the Preview targets parameter-driven so Android Studio can
render them without booting the full runtime graph, and lets preview-only
surfaces bypass runtime resource lookups that can go stale inside the IDE.

Important limitation:

- Visual Studio can edit and build this Kotlin project when the Android toolchain
  is configured, but it does not provide the Google-native Compose Preview
  experience. For Android UI visual editing, use Android Studio.

## GitHub Release Build

`release.yml` expects Android signing values from environment variables. In
GitHub Actions, these are supplied from repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
