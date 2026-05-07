from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
import os


@dataclass(frozen=True)
class Settings:
    app_name: str
    api_token: str
    database_path: str
    app_host: str
    app_port: int
    admin_web_origins: tuple[str, ...]


def _parse_csv_env(name: str, default: str) -> tuple[str, ...]:
    value = os.getenv(name, default)
    return tuple(item.strip() for item in value.split(",") if item.strip())


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings(
        app_name="Component Vault Sync",
        api_token=os.getenv("API_TOKEN", "change-me"),
        database_path=os.getenv("DATABASE_PATH", "./data/component_vault.db"),
        app_host=os.getenv("APP_HOST", "0.0.0.0"),
        app_port=int(os.getenv("APP_PORT", "8787")),
        admin_web_origins=_parse_csv_env(
            "ADMIN_WEB_ORIGINS",
            ",".join(
                (
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:8081",
                    "http://127.0.0.1:8081",
                )
            ),
        ),
    )
