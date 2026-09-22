package com.componentvault.android.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun queryDemultiplexerRemovesAsyncFramesBeforeAndAfterModel() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append(asyncStatus(0x00) + "M1".toByteArray() + asyncStatus(0x01))

        val result = demultiplexer.finish()

        assertEquals("M1", result.reply.toString(Charsets.US_ASCII))
        assertEquals(listOf(0x00, 0x01), result.asyncStatusCodes)
        assertEquals(M1SppProtocol.ModelResult.Matched, M1SppProtocol.parseModelReply(result.reply))
    }

    @Test
    fun queryDemultiplexerRemovesAsyncFrameMixedBetweenModelBytes() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append("M".toByteArray() + asyncStatus(0x00) + "1".toByteArray())

        assertEquals("M1", demultiplexer.finish().reply.toString(Charsets.US_ASCII))
    }

    @Test
    fun queryDemultiplexerRetainsSplitFrameUntilComplete() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append("Mpooli_".toByteArray())
        assertTrue(demultiplexer.hasReply())
        demultiplexer.append("sta=".toByteArray())
        assertEquals("M", demultiplexer.snapshot().reply.toString(Charsets.US_ASCII))
        demultiplexer.append(byteArrayOf(0x10) + "1".toByteArray())

        val result = demultiplexer.finish()
        assertEquals("M1", result.reply.toString(Charsets.US_ASCII))
        assertEquals(listOf(0x10), result.asyncStatusCodes)
    }

    @Test
    fun queryDemultiplexerRetainsPartialFrameAcrossQueryReads() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append("M1pooli_".toByteArray())

        val modelRead = demultiplexer.take()
        assertEquals("M1", modelRead.reply.toString(Charsets.US_ASCII))
        assertEquals(emptyList(), modelRead.asyncStatusCodes)

        demultiplexer.append("sta=".toByteArray() + byteArrayOf(0x01) + byteArrayOf(0x00, 0x00, 0x7f))
        val statusRead = demultiplexer.take()
        assertEquals(listOf(0x01), statusRead.asyncStatusCodes)
        assertEquals(byteArrayOf(0x00, 0x00, 0x7f).toList(), statusRead.reply.toList())
        assertEquals(
            M1SppProtocol.StatusResult.Received(0x00, M1SppProtocol.StatusResult.Source.Query),
            statusRead.classifyStatus(),
        )
    }

    @Test
    fun statusOnlyDoesNotBecomeOrdinaryReplyAtTimeout() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append(asyncStatus(0x00))

        assertFalse(demultiplexer.hasReply())
        val result = demultiplexer.finish()
        assertEquals(0, result.reply.size)
        assertEquals(
            M1SppProtocol.StatusResult.Received(0x00, M1SppProtocol.StatusResult.Source.Async),
            result.classifyStatus(),
        )
    }

    @Test
    fun queryStatusTakesPriorityOverEarlierAsyncStatus() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append(asyncStatus(0x00) + byteArrayOf(0x01, 0x00, 0x7f))

        assertEquals(
            M1SppProtocol.StatusResult.Received(0x01, M1SppProtocol.StatusResult.Source.Query),
            demultiplexer.finish().classifyStatus(),
        )
    }

    @Test
    fun invalidOrdinaryStatusIsNotHiddenByAsyncReadyStatus() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        demultiplexer.append(asyncStatus(0x00) + "unknown".toByteArray())

        assertEquals(M1SppProtocol.StatusResult.Invalid, demultiplexer.take().classifyStatus())
    }

    @Test
    fun oversizedReplyFailsExplicitlyWithoutAcceptingTruncatedPrefix() {
        val demultiplexer = M1QueryReplyDemultiplexer(maxReplyBytes = 2)
        demultiplexer.append("M1extra".toByteArray())

        val result = demultiplexer.take()
        assertTrue(result.overflowed)
        assertEquals(M1SppProtocol.StatusResult.Invalid, result.classifyStatus())
    }

    @Test
    fun queryDemultiplexerPreservesUnknownAndBinaryBytesAndRejectsM11() {
        val demultiplexer = M1QueryReplyDemultiplexer()
        val unknown = byteArrayOf(0x01, 0x00, 0x7f) + "pooli_stxM11".toByteArray()
        demultiplexer.append(unknown)

        val result = demultiplexer.finish()
        assertEquals(unknown.toList(), result.reply.toList())
        assertEquals(emptyList(), result.asyncStatusCodes)
        assertEquals(M1SppProtocol.ModelResult.Mismatch, M1SppProtocol.parseModelReply("M11".toByteArray()))
    }

    @Test
    fun statusBitsExposeOnlyKnownFlags() {
        assertEquals(listOf("paper_out", "cover_open", "locate_failed"), M1SppProtocol.statusBits(0x51))
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun asyncStatus(code: Int): ByteArray =
    "pooli_sta=".toByteArray(Charsets.US_ASCII) + byteArrayOf(code.toByte())
