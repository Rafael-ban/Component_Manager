package com.componentvault.android.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class M1SppConnectionLifecycleTest {
    @Test
    fun reusesSameDeviceAndReplacingDeviceClosesPreviousConnection() {
        val closed = mutableListOf<String>()
        val slot = ReusableResourceSlot<String>(closed::add)
        slot.replace("printer-a", "connection-a")

        assertSame("connection-a", slot.get("printer-a"))
        assertEquals(null, slot.get("printer-b"))

        slot.replace("printer-b", "connection-b")
        assertEquals(listOf("connection-a"), closed)
        assertSame("connection-b", slot.get("printer-b"))
    }

    @Test
    fun staleSessionCannotCloseReplacementConnection() {
        val closed = mutableListOf<String>()
        val slot = ReusableResourceSlot<String>(closed::add)
        slot.replace("printer-a", "old")
        slot.replace("printer-a", "new")

        assertFalse(slot.closeIfOwned("old"))
        assertSame("new", slot.get("printer-a"))
        assertEquals(listOf("old"), closed)
    }

    @Test
    fun cancellationAfterConnectBeforePublishCannotInstallOldConnection() {
        val closed = mutableListOf<String>()
        val slot = ReusableResourceSlot<String>(closed::add)
        slot.replace("printer-a", "retained")
        var generationIsCurrent = true

        generationIsCurrent = false
        val installed = slot.installIfCurrent("printer-a", "late-connect") { generationIsCurrent }

        assertFalse(installed)
        assertSame("retained", slot.get("printer-a"))
        assertEquals(listOf("late-connect"), closed)
    }

    @Test
    fun staleReuseAttemptCannotCloseCurrentConnection() {
        val closed = mutableListOf<String>()
        val slot = ReusableResourceSlot<String>(closed::add)
        slot.replace("printer-a", "current")

        val reused = slot.getIfCurrent("printer-a") { false }

        assertEquals(null, reused)
        assertSame("current", slot.get("printer-a"))
        assertEquals(emptyList(), closed)
    }

    @Test
    fun cancelOrIdleCleanupClosesCurrentConnectionOnlyOnce() {
        val closed = mutableListOf<String>()
        val slot = ReusableResourceSlot<String>(closed::add)
        slot.replace("printer-a", "connection")

        assertTrue(slot.closeIfOwned("connection"))
        assertFalse(slot.closeIfOwned("connection"))
        slot.closeCurrent()

        assertEquals(listOf("connection"), closed)
    }

    @Test
    fun postPrintModelFailureNeverTurnsSentDataIntoRejectedPrint() {
        assertEquals(M1TestPrintResult.SentConnectionLost, postPrintModelFailureResult(bytesSent = 1))
        assertEquals(M1TestPrintResult.Rejected, postPrintModelFailureResult(bytesSent = 0))
    }
}
