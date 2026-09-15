package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncProtocolTest {
    @Test
    fun inventorySnapshotDoesNotOverwriteEditsMadeDuringUploadEvenAfterClockRollback() {
        val pushed = "2026-09-15T10:00:00Z"
        assertFalse(SyncProtocol.canApplyInventorySnapshot("2026-09-15T10:01:00Z", pushed))
        assertFalse(SyncProtocol.canApplyInventorySnapshot("2026-09-15T09:00:00Z", pushed))
        assertFalse(SyncProtocol.canApplyInventorySnapshot(pushed, null))
        assertTrue(SyncProtocol.canApplyInventorySnapshot("2026-09-15T10:00:00.000000Z", pushed))
        assertTrue(SyncProtocol.canApplyInventorySnapshot(null, pushed))
    }

    @Test
    fun timestampsCompareByInstantAcrossFractionalPrecisionAndOffsets() {
        assertTrue(
            SyncProtocol.isRemoteAtLeastAsNew(
                localUpdatedAt = "2026-01-01T00:00:00Z",
                remoteUpdatedAt = "2026-01-01T00:00:00.500000Z",
            ),
        )
        assertTrue(
            SyncProtocol.isRemoteAtLeastAsNew(
                localUpdatedAt = "2026-01-01T08:00:00+08:00",
                remoteUpdatedAt = "2026-01-01T00:00:00Z",
            ),
        )
        assertFalse(
            SyncProtocol.isRemoteAtLeastAsNew(
                localUpdatedAt = "2026-01-01T00:00:00.5Z",
                remoteUpdatedAt = "2026-01-01T00:00:00Z",
            ),
        )
    }

    @Test
    fun queueAckKeepsAConcurrentNewerVersion() {
        assertTrue(
            SyncProtocol.shouldRemoveQueued(
                queuedUpdatedAt = "2026-01-01T00:00:00Z",
                acknowledgedUpdatedAt = "2026-01-01T00:00:00.5Z",
            ),
        )
        assertFalse(
            SyncProtocol.shouldRemoveQueued(
                queuedUpdatedAt = "2026-01-01T00:00:00.500Z",
                acknowledgedUpdatedAt = "2026-01-01T00:00:00Z",
            ),
        )
    }

    @Test
    fun cursorStartsAtZeroAndStoresAValidServerCursor() {
        assertEquals("/sync/pull?cursor=0", SyncProtocol.pullPath(null))
        assertEquals("/sync/pull?cursor=42", SyncProtocol.pullPath(42L))
        assertEquals(
            CursorDecision.Store(42L),
            SyncProtocol.cursorFromResponse(hasCursor = true, cursor = 42L),
        )
    }

    @Test
    fun legacyResponseClearsCursor() {
        assertIs<CursorDecision.Clear>(
            SyncProtocol.cursorFromResponse(hasCursor = false, cursor = null),
        )
    }
}
