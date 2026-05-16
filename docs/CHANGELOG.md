# Changelog

## [Unreleased]
bump: patch

<!-- Add unreleased notes below this line. -->

## [0.3.8] - 2026-05-16

- Fixed the Android matched-component bottom sheet so the matched scan flow now
  uses a scrollable `LazyColumn` layout with IME and bottom inset handling,
  keeping all fields and actions reachable on compact screens and with large
  text.
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
