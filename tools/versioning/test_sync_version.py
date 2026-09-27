from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.versioning import sync_version


class ChangelogVersionBaselineTests(unittest.TestCase):
    def test_dev_heading_keeps_latest_stable_as_metadata_baseline(self) -> None:
        changelog = (
            "## [Unreleased]\nbump: patch\n\n"
            "## [0.7.7-dev.1]\n- Preview.\n\n"
            "## [0.7.6]\n- Stable.\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "CHANGELOG.md"
            path.write_text(changelog, encoding="utf-8")
            with patch.object(sync_version, "CHANGELOG_PATH", path):
                state = sync_version.parse_changelog()

        self.assertEqual("0.7.7-dev.1", state.latest_heading)
        self.assertEqual("0.7.6", str(state.latest_release))
        self.assertFalse(state.unreleased_has_entries)

    def test_explicit_stable_heading_syncs_without_consuming_unreleased(self) -> None:
        state = sync_version.ChangelogState(
            text="## [Unreleased]\n- Draft.\n## [0.7.7]\n- Release.\n",
            latest_heading="0.7.7",
            latest_release=sync_version.SemVer(0, 7, 7),
            unreleased_has_entries=True,
            bump_kind="patch",
            next_version=sync_version.SemVer(0, 7, 8),
        )
        with (
            patch.object(sync_version, "parse_changelog", return_value=state),
            patch.object(
                sync_version,
                "read_current_versions",
                return_value={"android.versionName": "0.7.6"},
            ),
            patch.object(sync_version, "update_android_build") as android,
            patch.object(sync_version, "update_admin_web") as web,
            patch.object(sync_version, "update_windows") as windows,
            patch.object(sync_version, "stage_version_files") as stage,
            patch.object(sync_version, "write_text") as write,
        ):
            sync_version.sync_latest_stable_metadata()

        android.assert_called_once_with(sync_version.SemVer(0, 7, 7), increment_code=True)
        web.assert_called_once_with(sync_version.SemVer(0, 7, 7))
        windows.assert_called_once_with(sync_version.SemVer(0, 7, 7))
        stage.assert_called_once()
        write.assert_not_called()

    def test_dev_heading_cannot_sync_stable_metadata(self) -> None:
        state = sync_version.ChangelogState(
            text="",
            latest_heading="0.7.7-dev.1",
            latest_release=sync_version.SemVer(0, 7, 6),
            unreleased_has_entries=False,
            bump_kind="patch",
            next_version=None,
        )
        with patch.object(sync_version, "parse_changelog", return_value=state):
            with self.assertRaises(sync_version.VersionSyncError):
                sync_version.sync_latest_stable_metadata()

    def test_partial_stable_metadata_repair_does_not_bump_android_code(self) -> None:
        state = sync_version.ChangelogState(
            text="", latest_heading="0.7.7",
            latest_release=sync_version.SemVer(0, 7, 7),
            unreleased_has_entries=False, bump_kind="patch", next_version=None,
        )
        with (
            patch.object(sync_version, "parse_changelog", return_value=state),
            patch.object(
                sync_version, "read_current_versions",
                return_value={"android.versionName": "0.7.7"},
            ),
            patch.object(
                sync_version, "validate_synced_versions",
                side_effect=sync_version.VersionSyncError("web version differs"),
            ),
            patch.object(sync_version, "update_android_build") as android,
            patch.object(sync_version, "update_admin_web"),
            patch.object(sync_version, "update_windows"),
            patch.object(sync_version, "stage_version_files"),
        ):
            sync_version.sync_latest_stable_metadata()

        android.assert_called_once_with(sync_version.SemVer(0, 7, 7), increment_code=False)


if __name__ == "__main__":
    unittest.main()
