#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

git rev-parse --show-toplevel >/dev/null 2>&1
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit

printf '%s\n' "Configured core.hooksPath to .githooks"
printf '%s\n' "Future commits will sync versions from docs/CHANGELOG.md automatically."
