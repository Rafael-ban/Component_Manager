#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHANGELOG_PATH = ROOT / "docs" / "CHANGELOG.md"
ANDROID_BUILD_PATH = ROOT / "android-client" / "app" / "build.gradle.kts"
ADMIN_PACKAGE_PATH = ROOT / "admin-web" / "package.json"
ADMIN_LOCK_PATH = ROOT / "admin-web" / "package-lock.json"
WINDOWS_CSPROJ_PATH = ROOT / "windows-client" / "ComponentVault.WinUI" / "ComponentVault.WinUI.csproj"
WINDOWS_MSIX_PATH = ROOT / "windows-client" / "ComponentVault.WinUI" / "Package.appxmanifest"
WINDOWS_APP_MANIFEST_PATH = ROOT / "windows-client" / "ComponentVault.WinUI" / "app.manifest"

CHANGELOG_HEADER_RE = re.compile(
    r"^## \[(?P<version>[^\]]+)\](?: - (?P<release_date>\d{4}-\d{2}-\d{2}))?\s*$",
)
BUMP_LINE_RE = re.compile(r"^bump:\s*(major|minor|patch)\s*$", re.IGNORECASE)
SEMVER_RE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")


class VersionSyncError(RuntimeError):
    pass


@dataclass(frozen=True)
class SemVer:
    major: int
    minor: int
    patch: int

    @classmethod
    def parse(cls, value: str) -> "SemVer":
        match = SEMVER_RE.fullmatch(value.strip())
        if match is None:
            raise VersionSyncError(f"Invalid semantic version: {value}")
        return cls(int(match.group(1)), int(match.group(2)), int(match.group(3)))

    def bump(self, bump_kind: str) -> "SemVer":
        if bump_kind == "major":
            return SemVer(self.major + 1, 0, 0)
        if bump_kind == "minor":
            return SemVer(self.major, self.minor + 1, 0)
        if bump_kind == "patch":
            return SemVer(self.major, self.minor, self.patch + 1)
        raise VersionSyncError(f"Unsupported bump kind: {bump_kind}")

    def to_windows(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}.0"

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


@dataclass(frozen=True)
class ChangelogState:
    text: str
    latest_release: SemVer
    unreleased_has_entries: bool
    bump_kind: str
    next_version: SemVer | None


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise VersionSyncError(f"Required file not found: {path}") from exc


def write_text(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")


def normalize_json_file(path: Path, payload: dict) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def trim_blank_lines(lines: list[str]) -> list[str]:
    start = 0
    end = len(lines)

    while start < end and lines[start].strip() == "":
        start += 1
    while end > start and lines[end - 1].strip() == "":
        end -= 1

    return lines[start:end]


def parse_changelog() -> ChangelogState:
    text = read_text(CHANGELOG_PATH)
    lines = text.splitlines(keepends=True)

    unreleased_start = None
    unreleased_end = None
    latest_release = None

    header_positions: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        match = CHANGELOG_HEADER_RE.match(line.strip())
        if match is None:
            continue
        header_positions.append((index, match.group("version")))

    if not header_positions or header_positions[0][1] != "Unreleased":
        raise VersionSyncError("docs/CHANGELOG.md must start with '## [Unreleased]'.")

    unreleased_start = header_positions[0][0]
    unreleased_end = header_positions[1][0] if len(header_positions) > 1 else len(lines)

    for _, header_name in header_positions[1:]:
        if header_name == "Unreleased":
            raise VersionSyncError("docs/CHANGELOG.md may only contain one 'Unreleased' section.")
        latest_release = SemVer.parse(header_name)
        break

    if latest_release is None:
        raise VersionSyncError("docs/CHANGELOG.md must contain at least one released version section.")

    unreleased_body = lines[unreleased_start + 1:unreleased_end]
    bump_kind = None
    release_note_lines: list[str] = []

    for line in unreleased_body:
        stripped = line.strip()
        bump_match = BUMP_LINE_RE.match(stripped)
        if bump_match is not None:
            if bump_kind is not None:
                raise VersionSyncError("docs/CHANGELOG.md may only contain one 'bump:' line in Unreleased.")
            bump_kind = bump_match.group(1).lower()
            continue
        release_note_lines.append(line)

    if bump_kind is None:
        raise VersionSyncError("docs/CHANGELOG.md Unreleased section must include 'bump: major|minor|patch'.")

    normalized_notes = trim_blank_lines(release_note_lines)
    bullet_lines = [
        line for line in normalized_notes
        if line.lstrip().startswith("- ") or line.lstrip().startswith("* ")
    ]
    unreleased_has_entries = len(bullet_lines) > 0
    next_version = latest_release.bump(bump_kind) if unreleased_has_entries else None

    return ChangelogState(
        text=text,
        latest_release=latest_release,
        unreleased_has_entries=unreleased_has_entries,
        bump_kind=bump_kind,
        next_version=next_version,
    )


def render_released_changelog(next_version: SemVer) -> str:
    text = read_text(CHANGELOG_PATH)
    lines = text.splitlines(keepends=True)

    header_indices = [
        index for index, line in enumerate(lines)
        if CHANGELOG_HEADER_RE.match(line.strip()) is not None
    ]

    unreleased_start = header_indices[0]
    unreleased_end = header_indices[1] if len(header_indices) > 1 else len(lines)
    unreleased_body = lines[unreleased_start + 1:unreleased_end]

    release_note_lines: list[str] = []
    for line in unreleased_body:
        if BUMP_LINE_RE.match(line.strip()) is not None:
            continue
        release_note_lines.append(line)

    release_note_lines = trim_blank_lines(release_note_lines)
    if not release_note_lines:
        raise VersionSyncError("Cannot release changelog without at least one bullet entry in Unreleased.")

    new_section = [
        "## [Unreleased]\n",
        "bump: patch\n",
        "\n",
        "<!-- Add unreleased notes below this line. -->\n",
        "\n",
        f"## [{next_version}] - {date.today().isoformat()}\n",
        "\n",
    ]

    new_section.extend(release_note_lines)
    if new_section[-1].strip() != "":
        new_section.append("\n")
    new_section.append("\n")

    rewritten_lines = lines[:unreleased_start] + new_section + lines[unreleased_end:]
    return "".join(rewritten_lines)


def replace_pattern(text: str, pattern: str, replacement: str, description: str) -> str:
    updated_text, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise VersionSyncError(f"Unable to update {description}.")
    return updated_text


def update_android_build(version: SemVer, increment_code: bool) -> None:
    text = read_text(ANDROID_BUILD_PATH)

    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    if code_match is None:
        raise VersionSyncError("Unable to find Android versionCode.")
    version_code = int(code_match.group(1))
    next_code = version_code + 1 if increment_code else version_code

    text = replace_pattern(
        text,
        r'versionCode\s*=\s*\d+',
        f"versionCode = {next_code}",
        "Android versionCode",
    )
    text = replace_pattern(
        text,
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{version}"',
        "Android versionName",
    )
    write_text(ANDROID_BUILD_PATH, text)


def update_admin_web(version: SemVer) -> None:
    version_text = str(version)

    package_payload = json.loads(read_text(ADMIN_PACKAGE_PATH))
    package_payload["version"] = version_text
    normalize_json_file(ADMIN_PACKAGE_PATH, package_payload)

    lock_payload = json.loads(read_text(ADMIN_LOCK_PATH))
    lock_payload["version"] = version_text
    root_package = lock_payload.get("packages", {}).get("")
    if not isinstance(root_package, dict):
        raise VersionSyncError("admin-web/package-lock.json is missing the root package entry.")
    root_package["version"] = version_text
    normalize_json_file(ADMIN_LOCK_PATH, lock_payload)


def replace_or_insert_tag(text: str, tag_name: str, value: str) -> str:
    tag_pattern = re.compile(rf"<{tag_name}>[^<]*</{tag_name}>")
    if tag_pattern.search(text):
        return tag_pattern.sub(f"<{tag_name}>{value}</{tag_name}>", text, count=1)

    property_group_end = text.find("</PropertyGroup>")
    if property_group_end == -1:
        raise VersionSyncError(f"Unable to locate insertion point for <{tag_name}>.")

    insertion = f"    <{tag_name}>{value}</{tag_name}>\n"
    return text[:property_group_end] + insertion + text[property_group_end:]


def update_windows(version: SemVer) -> None:
    version_text = str(version)
    windows_version = version.to_windows()

    csproj_text = read_text(WINDOWS_CSPROJ_PATH)
    csproj_text = replace_or_insert_tag(csproj_text, "Version", version_text)
    csproj_text = replace_or_insert_tag(csproj_text, "AssemblyVersion", windows_version)
    csproj_text = replace_or_insert_tag(csproj_text, "FileVersion", windows_version)
    csproj_text = replace_or_insert_tag(csproj_text, "InformationalVersion", version_text)
    write_text(WINDOWS_CSPROJ_PATH, csproj_text)

    msix_text = read_text(WINDOWS_MSIX_PATH)
    msix_text = replace_pattern(
        msix_text,
        r'Version="[^"]+"',
        f'Version="{windows_version}"',
        "Windows MSIX version",
    )
    write_text(WINDOWS_MSIX_PATH, msix_text)

    app_manifest_text = read_text(WINDOWS_APP_MANIFEST_PATH)
    app_manifest_text, count = re.subn(
        r'(<assemblyIdentity[^>]*\bversion=")[^"]+(")',
        rf'\g<1>{windows_version}\2',
        app_manifest_text,
        count=1,
    )
    if count != 1:
        raise VersionSyncError("Unable to update Windows app manifest version.")
    write_text(WINDOWS_APP_MANIFEST_PATH, app_manifest_text)


def read_current_versions() -> dict[str, str]:
    android_text = read_text(ANDROID_BUILD_PATH)
    android_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', android_text)
    android_code_match = re.search(r"versionCode\s*=\s*(\d+)", android_text)
    if android_name_match is None or android_code_match is None:
        raise VersionSyncError("Unable to read Android version fields.")

    package_payload = json.loads(read_text(ADMIN_PACKAGE_PATH))
    lock_payload = json.loads(read_text(ADMIN_LOCK_PATH))
    lock_root = lock_payload.get("packages", {}).get("")
    if not isinstance(lock_root, dict):
        raise VersionSyncError("admin-web/package-lock.json is missing the root package entry.")

    csproj_text = read_text(WINDOWS_CSPROJ_PATH)
    msix_text = read_text(WINDOWS_MSIX_PATH)
    app_manifest_text = read_text(WINDOWS_APP_MANIFEST_PATH)

    versions = {
        "android.versionName": android_name_match.group(1),
        "android.versionCode": android_code_match.group(1),
        "admin-web.package": str(package_payload.get("version", "")),
        "admin-web.lock": str(lock_payload.get("version", "")),
        "admin-web.lockRoot": str(lock_root.get("version", "")),
        "windows.csproj.version": extract_xml_tag(csproj_text, "Version"),
        "windows.csproj.informational": extract_xml_tag(csproj_text, "InformationalVersion"),
        "windows.csproj.assembly": extract_xml_tag(csproj_text, "AssemblyVersion"),
        "windows.csproj.file": extract_xml_tag(csproj_text, "FileVersion"),
        "windows.msix": extract_attribute(msix_text, "Version"),
        "windows.appManifest": extract_windows_app_manifest_version(app_manifest_text),
    }
    return versions


def extract_xml_tag(text: str, tag_name: str) -> str:
    match = re.search(rf"<{tag_name}>([^<]+)</{tag_name}>", text)
    if match is None:
        raise VersionSyncError(f"Missing <{tag_name}> in Windows project file.")
    return match.group(1)


def extract_attribute(text: str, attribute_name: str) -> str:
    match = re.search(rf'{attribute_name}="([^"]+)"', text)
    if match is None:
        raise VersionSyncError(f"Missing attribute '{attribute_name}'.")
    return match.group(1)


def extract_windows_app_manifest_version(text: str) -> str:
    match = re.search(r'<assemblyIdentity[^>]*\bversion="([^"]+)"', text)
    if match is None:
        raise VersionSyncError("Missing assemblyIdentity version in Windows app manifest.")
    return match.group(1)


def validate_synced_versions(expected_version: SemVer) -> None:
    versions = read_current_versions()
    expected = str(expected_version)
    expected_windows = expected_version.to_windows()

    checks = {
        "android.versionName": expected,
        "admin-web.package": expected,
        "admin-web.lock": expected,
        "admin-web.lockRoot": expected,
        "windows.csproj.version": expected,
        "windows.csproj.informational": expected,
        "windows.csproj.assembly": expected_windows,
        "windows.csproj.file": expected_windows,
        "windows.msix": expected_windows,
        "windows.appManifest": expected_windows,
    }

    mismatches = [
        f"{key}: expected {expected_value}, found {versions[key]}"
        for key, expected_value in checks.items()
        if versions[key] != expected_value
    ]

    if mismatches:
        raise VersionSyncError(
            "Version files are not synchronized:\n" + "\n".join(mismatches),
        )


def stage_version_files() -> None:
    git_root = find_git_root()
    if git_root is None:
        print("No Git working tree detected; skipping automatic git add.", file=sys.stderr)
        return

    relative_paths = [
        CHANGELOG_PATH.relative_to(ROOT),
        ANDROID_BUILD_PATH.relative_to(ROOT),
        ADMIN_PACKAGE_PATH.relative_to(ROOT),
        ADMIN_LOCK_PATH.relative_to(ROOT),
        WINDOWS_CSPROJ_PATH.relative_to(ROOT),
        WINDOWS_MSIX_PATH.relative_to(ROOT),
        WINDOWS_APP_MANIFEST_PATH.relative_to(ROOT),
    ]

    command = ["git", "add", *[str(path).replace("\\", "/") for path in relative_paths]]
    run_subprocess(command, cwd=git_root)


def write_github_outputs(
    output_path: Path | None,
    *,
    released: bool,
    version: SemVer,
) -> None:
    if output_path is None:
        return

    lines = [
        f"released={'true' if released else 'false'}\n",
        f"version={version}\n",
        f"tag=v{version}\n",
    ]
    with output_path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.writelines(lines)


def find_git_root() -> Path | None:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None

    root_path = completed.stdout.strip()
    return Path(root_path) if root_path else None


def run_subprocess(command: list[str], cwd: Path) -> None:
    try:
        subprocess.run(command, cwd=cwd, check=True)
    except FileNotFoundError as exc:
        raise VersionSyncError(f"Required command not found: {command[0]}") from exc
    except subprocess.CalledProcessError as exc:
        raise VersionSyncError(
            f"Command failed with exit code {exc.returncode}: {' '.join(command)}",
        ) from exc


def apply_version_sync(output_path: Path | None = None) -> None:
    changelog_state = parse_changelog()
    if not changelog_state.unreleased_has_entries:
        write_github_outputs(
            output_path,
            released=False,
            version=changelog_state.latest_release,
        )
        print("No unreleased changelog entries found; version sync skipped.")
        return

    next_version = changelog_state.next_version
    if next_version is None:
        raise VersionSyncError("Unable to determine the next version.")

    write_text(CHANGELOG_PATH, render_released_changelog(next_version))
    update_android_build(next_version, increment_code=True)
    update_admin_web(next_version)
    update_windows(next_version)
    stage_version_files()
    write_github_outputs(
        output_path,
        released=True,
        version=next_version,
    )

    print(f"Synchronized repository version to {next_version}.")


def check_version_sync() -> None:
    changelog_state = parse_changelog()
    if changelog_state.unreleased_has_entries:
        raise VersionSyncError(
            "docs/CHANGELOG.md still has unreleased entries. Run the version sync before pushing or merge the commit created by the pre-commit hook.",
        )

    validate_synced_versions(changelog_state.latest_release)
    print(f"Version files are synchronized at {changelog_state.latest_release}.")


def validate_version_state() -> None:
    changelog_state = parse_changelog()
    if changelog_state.unreleased_has_entries:
        next_version = changelog_state.next_version
        if next_version is None:
            raise VersionSyncError("Unable to determine the unreleased target version.")
        print(f"Unreleased changelog entries target {next_version}.")
        return

    validate_synced_versions(changelog_state.latest_release)
    print(f"Version files are synchronized at {changelog_state.latest_release}.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Synchronize repository versions from docs/CHANGELOG.md.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply version updates from the Unreleased changelog section.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate that all version files match the latest released changelog entry.",
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        help="Validate changelog structure and accept unreleased entries on active branches.",
    )
    parser.add_argument(
        "--github-output",
        help="Append release metadata to the provided GitHub Actions output file.",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    selected_modes = sum(bool(flag) for flag in (args.apply, args.check, args.validate))
    if selected_modes > 1:
        parser.error("Use only one of --apply, --check, or --validate.")
    if args.github_output and (args.check or args.validate):
        parser.error("--github-output can only be used with apply mode.")

    output_path = Path(args.github_output) if args.github_output else None

    try:
        if args.check:
            check_version_sync()
        elif args.validate:
            validate_version_state()
        else:
            apply_version_sync(output_path)
    except VersionSyncError as exc:
        print(f"Version sync error: {exc}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
