from __future__ import annotations

from datetime import datetime, timezone
import sqlite3

from .config import Settings, validate_mqtt_settings
from .schemas import MqttConfigurationResponse, MqttConfigurationUpdate


def read_saved_mqtt(connection: sqlite3.Connection) -> sqlite3.Row | None:
    return connection.execute(
        "SELECT * FROM mqtt_configuration WHERE id = 1"
    ).fetchone()


def configuration_response(
    connection: sqlite3.Connection,
    runtime: Settings,
) -> MqttConfigurationResponse:
    row = read_saved_mqtt(connection)
    if row is None:
        values = _runtime_values(runtime)
        source = "environment"
        restart_required = False
    else:
        values = _row_values(row)
        source = "saved"
        restart_required = values != _runtime_values(runtime)
    return MqttConfigurationResponse(
        enabled=bool(values["mqtt_enabled"]),
        host=str(values["mqtt_host"]),
        port=int(values["mqtt_port"]),
        tls=bool(values["mqtt_tls"]),
        username=str(values["mqtt_username"]),
        topic_prefix=str(values["mqtt_topic_prefix"]),
        client_id=str(values["mqtt_client_id"]),
        password_configured=bool(values["mqtt_password"]),
        source=source,
        restart_required=restart_required,
        message=(
            "配置已保存，重启服务后生效。"
            if restart_required
            else "当前显示配置已由本次运行加载。"
        ),
    )


def save_mqtt_configuration(
    connection: sqlite3.Connection,
    runtime: Settings,
    update: MqttConfigurationUpdate,
) -> MqttConfigurationResponse:
    existing = read_saved_mqtt(connection)
    inherited_password = (
        existing["password"] if existing is not None else runtime.mqtt_password
    )
    supplied = update.password.get_secret_value() if update.password is not None else ""
    password = "" if update.clear_password else (supplied or inherited_password)
    values = validate_mqtt_settings({
        "mqtt_enabled": update.enabled,
        "mqtt_host": update.host,
        "mqtt_port": update.port,
        "mqtt_tls": update.tls,
        "mqtt_username": update.username,
        "mqtt_password": password,
        "mqtt_topic_prefix": update.topic_prefix,
        "mqtt_client_id": update.client_id,
    })
    with connection:
        connection.execute(
            """
            INSERT INTO mqtt_configuration (
                id, enabled, host, port, tls, username, password,
                topic_prefix, client_id, updated_at
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                enabled=excluded.enabled, host=excluded.host, port=excluded.port,
                tls=excluded.tls, username=excluded.username,
                password=excluded.password, topic_prefix=excluded.topic_prefix,
                client_id=excluded.client_id, updated_at=excluded.updated_at
            """,
            (
                int(values["mqtt_enabled"]), values["mqtt_host"],
                values["mqtt_port"], int(values["mqtt_tls"]),
                values["mqtt_username"], values["mqtt_password"],
                values["mqtt_topic_prefix"], values["mqtt_client_id"],
                datetime.now(timezone.utc).isoformat(),
            ),
        )
    return configuration_response(connection, runtime)


def _runtime_values(settings: Settings) -> dict[str, object]:
    return {
        "mqtt_enabled": settings.mqtt_enabled, "mqtt_host": settings.mqtt_host,
        "mqtt_port": settings.mqtt_port, "mqtt_tls": settings.mqtt_tls,
        "mqtt_username": settings.mqtt_username, "mqtt_password": settings.mqtt_password,
        "mqtt_topic_prefix": settings.mqtt_topic_prefix,
        "mqtt_client_id": settings.mqtt_client_id,
    }


def _row_values(row: sqlite3.Row) -> dict[str, object]:
    return {
        "mqtt_enabled": bool(row["enabled"]), "mqtt_host": row["host"],
        "mqtt_port": row["port"], "mqtt_tls": bool(row["tls"]),
        "mqtt_username": row["username"], "mqtt_password": row["password"],
        "mqtt_topic_prefix": row["topic_prefix"], "mqtt_client_id": row["client_id"],
    }
