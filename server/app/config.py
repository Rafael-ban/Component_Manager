from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
import os
from pathlib import Path
import sqlite3
from urllib.parse import quote
import re


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
    mqtt_enabled: bool = False
    mqtt_host: str = ""
    mqtt_port: int = 1883
    mqtt_tls: bool = False
    mqtt_username: str = ""
    mqtt_password: str = ""
    mqtt_topic_prefix: str = "component-vault"
    mqtt_client_id: str = "component-vault-server"


def _parse_csv_env(name: str, default: str) -> tuple[str, ...]:
    value = os.getenv(name, default)
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    return default if value is None else value.strip().lower() in {"1", "true", "yes", "on"}


def validate_mqtt_settings(values: dict[str, object]) -> dict[str, object]:
    enabled = bool(values["mqtt_enabled"])
    host = str(values["mqtt_host"]).strip()
    port = int(values["mqtt_port"])
    prefix = str(values["mqtt_topic_prefix"]).strip().strip("/")
    client_id = str(values["mqtt_client_id"]).strip()
    if not 1 <= port <= 65535:
        raise ValueError("MQTT_PORT must be between 1 and 65535.")
    if enabled and not host:
        raise ValueError("MQTT_HOST is required when MQTT is enabled.")
    if host and (
        re.search(r"[\x00-\x20\x7f]", host)
        or "://" in host
        or any(character in host for character in "@/\\?#")
    ):
        raise ValueError("MQTT_HOST must be a domain, IP address, or IPv6 address without a URL scheme or path.")
    if not prefix or "+" in prefix or "#" in prefix:
        raise ValueError("MQTT_TOPIC_PREFIX must be non-empty and contain no wildcards.")
    if re.search(r"[\x00-\x1f\x7f]", prefix):
        raise ValueError("MQTT_TOPIC_PREFIX must contain no control characters.")
    if not client_id:
        raise ValueError("MQTT_CLIENT_ID must be non-empty.")
    if re.search(r"[\x00-\x1f\x7f]", client_id):
        raise ValueError("MQTT_CLIENT_ID must contain no control characters.")
    return {
        "mqtt_enabled": enabled,
        "mqtt_host": host,
        "mqtt_port": port,
        "mqtt_tls": bool(values["mqtt_tls"]),
        "mqtt_username": str(values["mqtt_username"]).strip(),
        "mqtt_password": str(values["mqtt_password"]),
        "mqtt_topic_prefix": prefix,
        "mqtt_client_id": client_id,
    }


def _mqtt_env_settings() -> dict[str, object]:
    return validate_mqtt_settings({
        "mqtt_enabled": _bool_env("MQTT_ENABLED"),
        "mqtt_host": os.getenv("MQTT_HOST", ""),
        "mqtt_port": int(os.getenv("MQTT_PORT", "1883")),
        "mqtt_tls": _bool_env("MQTT_TLS"),
        "mqtt_username": os.getenv("MQTT_USERNAME", ""),
        "mqtt_password": os.getenv("MQTT_PASSWORD", ""),
        "mqtt_topic_prefix": os.getenv("MQTT_TOPIC_PREFIX", "component-vault"),
        "mqtt_client_id": os.getenv("MQTT_CLIENT_ID", "component-vault-server"),
    })


def _saved_mqtt_settings(database_path: str) -> dict[str, object] | None:
    path = Path(database_path)
    if not path.is_file():
        return None
    uri = f"file:{quote(path.resolve().as_posix(), safe='/:')}?mode=ro"
    try:
        connection = sqlite3.connect(uri, uri=True)
        connection.row_factory = sqlite3.Row
        try:
            exists = connection.execute(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='mqtt_configuration'"
            ).fetchone()
            if not exists:
                return None
            row = connection.execute(
                "SELECT * FROM mqtt_configuration WHERE id = 1"
            ).fetchone()
        finally:
            connection.close()
    except sqlite3.Error:
        return None
    if row is None:
        return None
    return validate_mqtt_settings({
        "mqtt_enabled": bool(row["enabled"]),
        "mqtt_host": row["host"],
        "mqtt_port": row["port"],
        "mqtt_tls": bool(row["tls"]),
        "mqtt_username": row["username"],
        "mqtt_password": row["password"],
        "mqtt_topic_prefix": row["topic_prefix"],
        "mqtt_client_id": row["client_id"],
    })


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    database_path = os.getenv("DATABASE_PATH", "./data/component_vault.db")
    mqtt_settings = _saved_mqtt_settings(database_path) or _mqtt_env_settings()
    return Settings(
        app_name="Component Vault Sync",
        api_token=os.getenv("API_TOKEN", "change-me"),
        database_path=database_path,
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
        **mqtt_settings,
    )
