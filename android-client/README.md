# Android Native Client

This directory contains the Android-native client built with Jetpack Compose
and Material 3.

## Current State

- Native navigation shell implemented
- Dashboard, components, movements, and settings screens implemented
- Local SQLite persistence and sync settings persistence implemented
- Component create/edit/soft delete workflow implemented
- Movement entry workflow implemented
- Push/pull sync wiring implemented against the FastAPI service
- Chinese-first Material 3 UI implemented for the primary screens, with a
  matching Simplified Chinese (`zh-CN`) resource set
- Compose Preview sample states added for dashboard, components, movements,
  and settings so the main screens can be inspected without booting an emulator
- `gradle -p android-client help` verified successfully on `2026-05-07`
- `assembleDebug` verified successfully on `2026-05-07` on this host
- `assembleRelease` verified successfully on `2026-05-07` on this host

## Build Preconditions

- Android SDK installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured
- Gradle can resolve Android plugins and dependencies from Google Maven
- Alternatively, copy `local.properties.example` to `local.properties` and set
  `sdk.dir=<absolute-sdk-path>`

## This Host Setup

Verified working paths on this Windows machine:

- Android SDK: `D:\Ide\sdk\Android\android-sdk`
- JDK: `D:\Ide\sdk\Android\openjdk\jdk-21.0.8`
- Gradle launcher: `D:\dev-tool\gradle\bin\gradle.bat`
- Project-local Gradle cache: `D:\Project_Folder\Component_warehouse\.gradle-user-home`

`android-client/local.properties` should contain:

```properties
sdk.dir=D:\\Ide\\sdk\\Android\\android-sdk
```

## Quick Check

```powershell
$env:GRADLE_USER_HOME='D:\Project_Folder\Component_warehouse\.gradle-user-home'
$env:JAVA_HOME='D:\Ide\sdk\Android\openjdk\jdk-21.0.8'
$env:ANDROID_SDK_ROOT='D:\Ide\sdk\Android\android-sdk'
$env:ANDROID_HOME='D:\Ide\sdk\Android\android-sdk'
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client help
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleDebug
& 'D:\dev-tool\gradle\bin\gradle.bat' -p android-client assembleRelease
```

## Visual Editing

Jetpack Compose does not use the old XML layout designer. For this client,
visual editing means Compose Preview and interactive preview rendering inside
Android Studio.

Use this workflow:

1. Open `android-client/` in Android Studio.
2. Open
   `app/src/main/java/com/componentvault/android/ui/screen/ComponentVaultApp.kt`.
3. In the editor, switch to `Split` or `Design`.
4. Use the preview functions at the bottom of the file:
   `DashboardScreenPreview`, `ComponentsScreenPreview`,
   `ComponentsScreenEmptyPreview`, `MovementsScreenPreview`,
   `SettingsScreenPreview`, and `SettingsScreenBusyPreview`.
5. If the preview does not refresh, click `Build & Refresh`.

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
