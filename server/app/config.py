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
    lcsc_openapi_key: str
    lcsc_openapi_secret: str
    lcsc_openapi_base_url: str
    lcsc_lookup_cache_ttl_seconds: int
    import_rules_remote_url: str
    import_rules_refresh_hours: int
    enable_web_fallback_resolvers: bool


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
        lcsc_openapi_key=os.getenv("LCSC_OPENAPI_KEY", "").strip(),
        lcsc_openapi_secret=os.getenv("LCSC_OPENAPI_SECRET", "").strip(),
        lcsc_openapi_base_url=os.getenv(
            "LCSC_OPENAPI_BASE_URL",
            "https://ips.lcsc.com",
        ).strip().rstrip("/"),
        lcsc_lookup_cache_ttl_seconds=int(
            os.getenv("LCSC_LOOKUP_CACHE_TTL_SECONDS", "43200"),
        ),
        import_rules_remote_url=os.getenv("IMPORT_RULES_REMOTE_URL", "").strip(),
        import_rules_refresh_hours=int(
            os.getenv("IMPORT_RULES_REFRESH_HOURS", "24"),
        ),
        enable_web_fallback_resolvers=os.getenv(
            "ENABLE_WEB_FALLBACK_RESOLVERS",
            "false",
        ).strip().lower() in {"1", "true", "yes", "on"},
    )
