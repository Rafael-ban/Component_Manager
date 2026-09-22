package com.componentvault.android.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class M1SppProtocolTest {
    @Test
    fun cancelBeforeAttachRejectsAndClosesLateResource() {
        val closed = mutableListOf<String>()
        val slot = CancelableResourceSlot<String>(closed::add)
        slot.cancel()

        assertEquals(false, slot.attach("late"))
        assertEquals(listOf("late"), closed)
    }

    @Test
    fun cancelAfterAttachClosesCurrentResource() {
        val closed = mutableListOf<String>()
        val slot = CancelableResourceSlot<String>(closed::add)
        assertEquals(true, slot.attach("connected"))

        slot.cancel()

        assertEquals(listOf("connected"), closed)
    }

    @Test
    fun oldWorkerCompletionCannotDeliverIntoReplacementSession() {
        assertEquals(false, sessionMayDeliver(expectedGeneration = 4, currentGeneration = 5, isActiveSession = false))
        assertEquals(false, sessionMayDeliver(expectedGeneration = 5, currentGeneration = 5, isActiveSession = false))
        assertEquals(true, sessionMayDeliver(expectedGeneration = 5, currentGeneration = 5, isActiveSession = true))
    }

    @Test
    fun commandsMatchTheReadOnlySdkQueries() {
        assertEquals("1b1c262056312067657476616c20227072696e7465725f6e616d65220d0a", M1SppProtocol.queryModel.toHex())
        assertEquals("1b1c26205631206765746b65790d0a01040020", M1SppProtocol.queryModelFallback.toHex())
        assertEquals("1b1273", M1SppProtocol.queryStatus.toHex())
    }

    @Test
    fun modelReplyRequiresExactM1Token() {
        assertEquals(M1SppProtocol.ModelResult.Matched, M1SppProtocol.parseModelReply("\u0000 \"M1\"\r\n".toByteArray()))
        assertEquals(M1SppProtocol.ModelResult.Mismatch, M1SppProtocol.parseModelReply("device M1 ready".toByteArray()))
        assertEquals(M1SppProtocol.ModelResult.Mismatch, M1SppProtocol.parseModelReply("M11".toByteArray()))
        assertEquals(M1SppProtocol.ModelResult.NoResponse, M1SppProtocol.parseModelReply(byteArrayOf()))
    }

    @Test
    fun queryStatusNeedsTrailingByteAndUsesFirstTwoLittleEndianBytes() {
        assertNull(M1SppProtocol.parseQueryStatus(byteArrayOf(0x41, 0x00)))
        assertEquals(0x0141, M1SppProtocol.parseQueryStatus(byteArrayOf(0x41, 0x01, 0x7f)))
        assertEquals(
            M1SppProtocol.StatusResult.Received(0x41, M1SppProtocol.StatusResult.Source.Query),
            M1SppProtocol.classifyStatusReply(byteArrayOf(0x41, 0x00, 0x7f)),
        )
        assertEquals(M1SppProtocol.StatusResult.Invalid, M1SppProtocol.classifyStatusReply(byteArrayOf(0x41, 0x01, 0x7f)))
    }

    @Test
    fun incompleteAsyncStatusIsNotMisreadAsQueryStatus() {
        assertNull(M1SppProtocol.parseStatusReply("pooli_sta=".toByteArray()))
        assertNull(M1SppProtocol.parseStatusReply("pooli_".toByteArray()))
        assertEquals(0x41, M1SppProtocol.parseStatusReply("pooli_sta=".toByteArray() + byteArrayOf(0x41)))
    }

    @Test
    fun plainAsciiNoiseIsInvalidRatherThanAStatusCode() {
        assertEquals(
            M1SppProtocol.StatusResult.Invalid,
            M1SppProtocol.classifyStatusReply("OK noisy reply".toByteArray()),
        )
        assertEquals(M1SppProtocol.StatusResult.NoResponse, M1SppProtocol.classifyStatusReply(byteArrayOf()))
    }

    @Test
    fun asyncStatusParserAcceptsFragmentedFrame() {
        val parser = M1AsyncStatusParser()
        assertEquals(emptyList(), parser.append("pooli_".toByteArray()))
        assertEquals(listOf(0x11), parser.append("sta=".toByteArray() + byteArrayOf(0x11)))
    }

    @Test
    fun asyncStatusParserAcceptsMergedFramesAndNoise() {
        val parser = M1AsyncStatusParser()
        val merged = "noise pooli_sta=".toByteArray() + byteArrayOf(0x01) +
            "pooli_sta=".toByteArray() + byteArrayOf(0x50)
        assertEquals(listOf(0x01, 0x50), parser.append(merged))
    }

    @Test
    fun statusBitsExposeOnlyKnownFlags() {
        assertEquals(listOf("paper_out", "cover_open", "locate_failed"), M1SppProtocol.statusBits(0x51))
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
