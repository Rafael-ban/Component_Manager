package com.componentvault.android.ui.screen

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortLocalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Formats persisted ISO timestamps for display without changing stored values. */
internal fun formatShortLocalTimestamp(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (value.isBlank()) return value
    val localDateTime = runCatching {
        Instant.parse(value).atZone(zoneId).toLocalDateTime()
    }.recoverCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zoneId).toLocalDateTime()
    }.recoverCatching {
        LocalDateTime.parse(value)
    }.getOrNull() ?: return value
    return shortLocalDateTimeFormatter.format(localDateTime)
}
