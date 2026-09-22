#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


TAG_RE = re.compile(
    r"^v(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)"
    r"(?:-dev\.(?P<dev>[1-9]\d*))?$",
)
MAX_ANDROID_VERSION_CODE = 2_100_000_000


@dataclass(frozen=True)
class ReleaseChannelVersion:
    version: str
    prerelease: bool
    android_version_code: int


def parse_release_tag(tag: str) -> ReleaseChannelVersion:
    match = TAG_RE.fullmatch(tag)
    if match is None:
        raise ValueError("release tag must be vX.Y.Z or vX.Y.Z-dev.N")
    major, minor, patch = (int(match[name]) for name in ("major", "minor", "patch"))
    if minor >= 100 or patch >= 100:
        raise ValueError("minor and patch versions must be between 0 and 99")
    dev_text = match["dev"]
    if dev_text is not None and int(dev_text) >= 999:
        raise ValueError("dev build number must be between 1 and 998")
    channel_build = int(dev_text) if dev_text is not None else 999
    version_code = major * 10_000_000 + minor * 100_000 + patch * 1_000 + channel_build
    if version_code > MAX_ANDROID_VERSION_CODE:
        raise ValueError("release tag exceeds Android's maximum versionCode")
    return ReleaseChannelVersion(tag[1:], dev_text is not None, version_code)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: release_channel.py TAG GITHUB_OUTPUT", file=sys.stderr)
        return 2
    try:
        result = parse_release_tag(sys.argv[1])
    except ValueError as exc:
        print(f"release channel error: {exc}", file=sys.stderr)
        return 1
    output = Path(sys.argv[2])
    with output.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(f"prerelease={'true' if result.prerelease else 'false'}\n")
        handle.write(f"component_version={result.version}\n")
        handle.write(f"android_version_code={result.android_version_code}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
