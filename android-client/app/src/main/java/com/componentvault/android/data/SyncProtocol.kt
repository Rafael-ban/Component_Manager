package com.componentvault.android.data

import java.time.Instant

internal object SyncProtocol {
    fun pullPath(cursor: Long?): String = "/sync/pull?cursor=${cursor ?: 0L}"

    fun isRemoteAtLeastAsNew(localUpdatedAt: String, remoteUpdatedAt: String): Boolean =
        parseInstant(remoteUpdatedAt) >= parseInstant(localUpdatedAt)

    fun shouldRemoveQueued(
        queuedUpdatedAt: String,
        acknowledgedUpdatedAt: String,
    ): Boolean = parseInstant(queuedUpdatedAt) <= parseInstant(acknowledgedUpdatedAt)

    fun timestampsEqual(first: String, second: String): Boolean =
        parseInstant(first) == parseInstant(second)

    fun canApplyInventorySnapshot(queuedVersion: String?, pushedVersion: String?): Boolean =
        queuedVersion == null || (pushedVersion != null && timestampsEqual(queuedVersion, pushedVersion))

    fun cursorFromResponse(hasCursor: Boolean, cursor: Long?): CursorDecision = when {
        !hasCursor -> CursorDecision.Clear
        cursor != null && cursor >= 0L -> CursorDecision.Store(cursor)
        else -> throw IllegalArgumentException("Invalid sync_cursor in pull response")
    }

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (exception: Exception) {
        throw IllegalArgumentException("Invalid sync timestamp: $value", exception)
    }
}

internal sealed interface CursorDecision {
    data class Store(val value: Long) : CursorDecision

    data object Clear : CursorDecision
}
