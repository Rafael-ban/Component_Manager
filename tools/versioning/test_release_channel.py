from __future__ import annotations

import unittest

from tools.versioning.release_channel import parse_release_tag


class ReleaseChannelVersionTests(unittest.TestCase):
    def test_android_codes_preserve_upgrade_order(self) -> None:
        old_stable = parse_release_tag("v0.7.3")
        dev_one = parse_release_tag("v0.7.4-dev.1")
        dev_two = parse_release_tag("v0.7.4-dev.2")
        stable = parse_release_tag("v0.7.4")
        next_patch_dev = parse_release_tag("v0.7.5-dev.1")
        self.assertLess(26, dev_one.android_version_code)
        self.assertLess(old_stable.android_version_code, dev_one.android_version_code)
        self.assertLess(dev_one.android_version_code, dev_two.android_version_code)
        self.assertLess(dev_two.android_version_code, stable.android_version_code)
        self.assertLess(stable.android_version_code, next_patch_dev.android_version_code)

    def test_channel_metadata(self) -> None:
        self.assertTrue(parse_release_tag("v0.7.4-dev.1").prerelease)
        self.assertFalse(parse_release_tag("v0.7.4").prerelease)
        self.assertEqual("0.7.4-dev.1", parse_release_tag("v0.7.4-dev.1").version)

    def test_rejects_ambiguous_or_out_of_range_tags(self) -> None:
        for tag in ("v0.07.4", "v0.100.0", "v0.7.100", "v0.7.4-dev.0", "v0.7.4-dev.999"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                parse_release_tag(tag)


if __name__ == "__main__":
    unittest.main()
