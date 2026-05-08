# Changelog

## [Unreleased]
bump: patch

<!-- Add unreleased notes below this line. -->

- Removed the repository-pinned Android JDK path from `android-client/gradle.properties`
  so GitHub Actions and other non-Windows environments can use their own
  configured Java runtime.
- Added default-branch changelog release automation that syncs version files,
  pushes a release commit when needed, and creates the matching `v*` tag for
  `release.yml`.
- Stabilized Android Compose Preview by moving preview rendering onto static
  string bundles and content-level preview composables instead of direct
  preview-time `R.string` resolution.

## [0.2.0] - 2026-05-08

- Added changelog-driven automatic version syncing for Android, admin-web, and Windows.
- Added repository-managed Git hook bootstrap plus CI version consistency checks.

## [0.1.0]

- Initial released baseline before changelog-driven version automation.
