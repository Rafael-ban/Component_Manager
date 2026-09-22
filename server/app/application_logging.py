from __future__ import annotations

import json
import logging
from logging.handlers import RotatingFileHandler
from datetime import datetime, timezone
from pathlib import Path
import sys
from typing import Any

from .config import log_path


LOGGER_NAME = "component_vault.application"


def configure_application_logger() -> logging.Logger:
    logger = logging.getLogger(LOGGER_NAME)
    logger.setLevel(logging.INFO)
    logger.propagate = False
    close_application_logger()
    path = log_path()
    formatter = logging.Formatter("%(message)s")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        file_handler = RotatingFileHandler(
            path, maxBytes=1024 * 1024, backupCount=3, encoding="utf-8"
        )
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)
    except OSError as error:
        # Keep the service diagnosable on a read-only or incorrectly mounted volume.
        print(
            json.dumps({
                "event": "logging_unavailable",
                "log_path": str(path),
                "error": type(error).__name__,
            }, separators=(",", ":")),
            file=sys.stderr,
        )
    stream_handler = logging.StreamHandler(sys.stdout)
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)
    return logger


def close_application_logger() -> None:
    logger = logging.getLogger(LOGGER_NAME)
    for handler in list(logger.handlers):
        logger.removeHandler(handler)
        handler.close()


def log_event(event: str, **fields: Any) -> None:
    payload = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "event": event,
        **fields,
    }
    logging.getLogger(LOGGER_NAME).info(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    )


def recent_log_lines(max_lines: int = 200, max_bytes: int = 256 * 1024) -> list[str]:
    path: Path = log_path()
    if not path.is_file():
        return []
    with path.open("rb") as handle:
        handle.seek(0, 2)
        size = handle.tell()
        handle.seek(max(0, size - max_bytes))
        data = handle.read(max_bytes)
    text = data.decode("utf-8", errors="replace")
    lines = text.splitlines()
    if size > max_bytes and lines:
        lines = lines[1:]
    return lines[-max_lines:]
