# Changelog

## [Unreleased]
bump: minor

<!-- Add unreleased notes below this line. -->
- Switched Android JLC package QR scanning from Google Code Scanner to an
  in-app CameraX scanner backed by bundled ML Kit barcode scanning, removing
  the runtime dependency on downloading the Barcode UI module before first use.
- Added Android supplier packaging OCR import through bundled ML Kit Chinese
  text recognition, with quantity-first confirmation and optional full-editor
  refinement before saving inventory.
- Added Android inventory label preview plus PNG/PDF export, generating
  JLC-compatible QR payloads for JLC-sourced parts and warehouse QR payloads
  for non-JLC parts so labels can round-trip back into import flows.
- Added server-side `GET /admin-api/lcsc/lookup` plus Android-side optional
  LCSC official metadata enrichment for JLC text and QR imports, with local
  cache reuse and a settings toggle.
- Reworked Android JLC import enrichment into a local-first flow with
  device-only learned mappings stored in SQLite, SKU-first and MPN-fallback
  reuse, field-origin review in the import form, and separate settings for
  local learning vs optional server lookup.
- Rebuilt Android runtime string resources and preview string bundles after the
  import-enrichment changes, and re-verified `assembleDebug` and
  `assembleRelease` on `2026-05-10`.

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
