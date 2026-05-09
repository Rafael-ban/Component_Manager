# Changelog

## [Unreleased]
bump: patch

<!-- Add unreleased notes below this line. -->
- Switched Android JLC package QR scanning from Google Code Scanner to an
  in-app CameraX scanner backed by bundled ML Kit barcode scanning, removing
  the runtime dependency on downloading the Barcode UI module before first use.

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
