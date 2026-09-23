package com.componentvault.android.data

import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class M1SppPrintProtocolTest {
    @Test
    fun packsBlackPixelsMsbFirstAndPadsRowTailWhite() {
        val pixels = intArrayOf(
            0xff000000.toInt(), 0xffffffff.toInt(), 0xff000000.toInt(), 0xffffffff.toInt(),
            0xffffffff.toInt(), 0xffffffff.toInt(), 0xffffffff.toInt(), 0xff000000.toInt(),
            0xff000000.toInt(),
        )
        val frame = M1SppPrintProtocol.frames(9, 1, pixels).single()
        assertEquals(2, frame.rowBytes)
        assertEquals(2, frame.rawBytes)
        assertContentEquals(byteArrayOf(19, 0xa1.toByte(), 0x80.toByte(), 0x11, 0, 0), frame.bytes.copyOfRange(12, frame.bytes.size))
    }

    @Test
    fun splitsOnlyOnRowsWithRawChunksAtMost3072Bytes() {
        val frames = M1SppPrintProtocol.frames(384, 65, IntArray(384 * 65) { 0xffffffff.toInt() })
        assertEquals(listOf(64, 1), frames.map(M1PrintFrame::rows))
        assertEquals(listOf(3072, 48), frames.map(M1PrintFrame::rawBytes))
        assertTrue(frames.all { it.rawBytes <= M1SppPrintProtocol.MAX_RAW_CHUNK_BYTES })
    }

    @Test
    fun frameHeaderContainsDimensionsAndCompressedPayloadLength() {
        val frame = M1SppPrintProtocol.frames(16, 2, IntArray(32) { 0xffffffff.toInt() }).single()
        assertContentEquals(byteArrayOf(0x1d, 0x76, 0x30, 0x30), frame.bytes.copyOfRange(0, 4))
        assertContentEquals(byteArrayOf(2, 0, 2, 0), frame.bytes.copyOfRange(4, 8))
        val payloadSize = frame.bytes.size - 12
        assertContentEquals(byteArrayOf(payloadSize.toByte(), 0, 0, 0), frame.bytes.copyOfRange(8, 12))
    }

    @Test
    fun literalOnlyLzoUsesShortAndExtendedPrefixesAndEos() {
        assertEquals(18, M1SppPrintProtocol.literalOnlyLzo1x(ByteArray(1)).first().toInt())
        assertEquals(255, M1SppPrintProtocol.literalOnlyLzo1x(ByteArray(238)).first().toInt() and 0xff)
        assertContentEquals(byteArrayOf(0, 0xdd.toByte()), M1SppPrintProtocol.literalOnlyLzo1x(ByteArray(239)).copyOfRange(0, 2))
        assertContentEquals(byteArrayOf(0, 0, 1), M1SppPrintProtocol.literalOnlyLzo1x(ByteArray(274)).copyOfRange(0, 3))
        assertContentEquals(byteArrayOf(0x11, 0, 0), M1SppPrintProtocol.literalOnlyLzo1x(ByteArray(274)).takeLast(3).toByteArray())
    }

    @Test
    fun literalOnlyLzoMatchesIndependentlyDecodedGoldenStreams() {
        val expected = mapOf(
            239 to "ec6494873ce1361e490d2b42242269deba11135ee515ff7bcbf4d1815747464b",
            274 to "295e76471d8cd02dbe63fcfd9bf56debf38c0d1d452fe9e54574f7fc716697c5",
            3072 to "c537f1a8fb2dac12b762cc2ad2703d8c1b356b02c373d82e5ffba504ec5f24c2",
        )
        expected.forEach { (length, digest) ->
            val raw = ByteArray(length) { index -> (index * 37 + 11).toByte() }
            assertEquals(digest, M1SppPrintProtocol.literalOnlyLzo1x(raw).sha256())
        }
    }

    @Test
    fun printCommandsAreRightAlignedAndSingleFormFeedLiterals() {
        assertContentEquals(byteArrayOf(0x1b, 0x61, 0x02), M1SppPrintProtocol.alignRight)
        assertContentEquals(byteArrayOf(0x1d, 0x66, 0xc0.toByte(), 0x03), M1SppPrintProtocol.formFeed)
        assertContentEquals(byteArrayOf(0x1b, 0x1b, 0x01, 0x5a, 0x00), M1SppPrintProtocol.shortFeed)
    }

    @Test
    fun feedModesShareSevenImageFramesAndDifferOnlyByGoldenTail() {
        val frames = M1SppPrintProtocol.frames(320, 480, IntArray(320 * 480) { 0xffffffff.toInt() })
        assertEquals(7, frames.size)
        val labelParts = M1SppPrintProtocol.printParts(frames, M1FeedMode.Label)
        val imageOnlyParts = M1SppPrintProtocol.printParts(frames, M1FeedMode.None)
        val shortParts = M1SppPrintProtocol.printParts(frames, M1FeedMode.Short)

        assertEquals(imageOnlyParts.size + 1, labelParts.size)
        assertEquals(imageOnlyParts.size + 1, shortParts.size)
        imageOnlyParts.indices.forEach { index ->
            assertContentEquals(imageOnlyParts[index].bytes, labelParts[index].bytes)
            assertContentEquals(imageOnlyParts[index].bytes, shortParts[index].bytes)
        }
        assertEquals(7, labelParts.count(M1PrintPart::isImageFrame))
        assertEquals(7, imageOnlyParts.count(M1PrintPart::isImageFrame))
        assertEquals(7, shortParts.count(M1PrintPart::isImageFrame))
        assertEquals(1, labelParts.count(M1PrintPart::isFormFeed))
        assertEquals(0, imageOnlyParts.count(M1PrintPart::isFormFeed))
        assertEquals(0, shortParts.count(M1PrintPart::isFormFeed))
        assertEquals(0, labelParts.count(M1PrintPart::isShortFeed))
        assertEquals(0, imageOnlyParts.count(M1PrintPart::isShortFeed))
        assertEquals(1, shortParts.count(M1PrintPart::isShortFeed))
        assertContentEquals(M1SppPrintProtocol.formFeed, labelParts.last().bytes)
        assertContentEquals(M1SppPrintProtocol.shortFeed, shortParts.last().bytes)
    }

    @Test
    fun cancellationAndFailurePreservePartialWriteMeaning() {
        assertEquals(M1TestPrintResult.Interrupted, interruptedPrintResult(0))
        assertEquals(M1TestPrintResult.Partial, interruptedPrintResult(1))
        assertEquals(M1TestPrintResult.Rejected, failedPrintResult(0))
        assertEquals(M1TestPrintResult.Partial, failedPrintResult(1))
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
