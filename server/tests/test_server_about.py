import io
import json

import pytest
from fastapi import HTTPException

from app.admin import about


@pytest.mark.parametrize(("current", "latest", "expected"), [
    ("0.7.6", "v0.7.7", True), ("0.7.7-dev.5", "v0.7.7", True),
    ("0.7.8-dev.1", "v0.7.7", False), ("0.7.7", "v0.7.7", False),
    ("source", "v0.7.7", None),
])
def test_release_comparison(monkeypatch, current, latest, expected):
    monkeypatch.setattr(about, "BUILD_VERSION", current)
    monkeypatch.setattr(about, "urlopen", lambda *args, **kwargs: io.BytesIO(
        json.dumps({"tag_name": latest, "prerelease": False}).encode(),
    ))
    result = about.check_update()
    assert result["update_available"] is expected
    assert result["automatic_update"] is False


def test_invalid_release_is_not_reported_up_to_date(monkeypatch):
    monkeypatch.setattr(about, "urlopen", lambda *args, **kwargs: io.BytesIO(b"{}"))
    with pytest.raises(HTTPException) as error:
        about.check_update()
    assert error.value.status_code == 502


def test_about_contains_author_and_no_self_update():
    result = about.about()
    assert result["author"] == "Rafael-Ikaros"
    assert result["deployment"] in {"container", "source_or_service"}
