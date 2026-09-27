"""Read-only deployment identity and explicit stable-release checks."""

from __future__ import annotations

import json
from pathlib import Path
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request as UrlRequest, urlopen

from fastapi import APIRouter, Depends, HTTPException

from ..auth import require_token
from ..build_info import BUILD_REVISION, BUILD_VERSION

router = APIRouter(prefix="/admin-api/about", dependencies=[Depends(require_token)])
REPOSITORY = "https://github.com/Rafael-ban/Component_Manager"
RELEASE_API = "https://api.github.com/repos/Rafael-ban/Component_Manager/releases/latest"


def version_key(value: str) -> tuple[int, int, int, int, int] | None:
    match = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)(?:-dev\.(\d+))?", value)
    if not match:
        return None
    major, minor, patch, dev = match.groups()
    return int(major), int(minor), int(patch), int(dev is None), int(dev or 0)


@router.get("")
def about() -> dict:
    return {
        "name": "Component Vault Server",
        "author": "Rafael-Ikaros",
        "version": BUILD_VERSION,
        "revision": BUILD_REVISION,
        "deployment": "container" if Path("/.dockerenv").exists() else "source_or_service",
        "repository_url": REPOSITORY,
        "deployment_guide_url": f"{REPOSITORY}/blob/master/docs/dockerhub.md",
    }


@router.post("/check-update")
def check_update() -> dict:
    request = UrlRequest(RELEASE_API, headers={
        "Accept": "application/vnd.github+json", "User-Agent": "Component-Vault-Server",
    })
    try:
        with urlopen(request, timeout=10) as response:
            release = json.load(response)
        latest = str(release["tag_name"])
        target = version_key(latest)
        if target is None or release.get("prerelease") or release.get("draft"):
            raise ValueError("Invalid stable release")
    except (HTTPError, URLError, TimeoutError, ValueError, KeyError, TypeError):
        raise HTTPException(502, "Release check unavailable. Try again later.") from None
    current = version_key(BUILD_VERSION)
    return {
        "current_version": BUILD_VERSION,
        "latest_version": latest,
        "update_available": target > current if current is not None else None,
        "release_url": f"{REPOSITORY}/releases/tag/{latest}",
        "automatic_update": False,
    }
