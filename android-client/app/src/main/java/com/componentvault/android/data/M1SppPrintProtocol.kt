package com.componentvault.android.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

internal data class M1PrintFrame(
    val rowBytes: Int,
    val rows: Int,
    val rawBytes: Int,
    val bytes: ByteArray,
)

internal enum class M1FeedMode { Label, None, Short }

internal data class M1PrintPart(
    val bytes: ByteArray,
    val isImageFrame: Boolean = false,
    val isFormFeed: Boolean = false,
    val isShortFeed: Boolean = false,
)

internal fun interruptedPrintResult(bytesAttempted: Int): M1TestPrintResult =
    if (bytesAttempted > 0) M1TestPrintResult.Partial else M1TestPrintResult.Interrupted

internal fun failedPrintResult(bytesAttempted: Int): M1TestPrintResult =
    if (bytesAttempted > 0) M1TestPrintResult.Partial else M1TestPrintResult.Rejected

internal object M1SppPrintProtocol {
    const val MAX_WIDTH_DOTS = 384
    // M1 / Hanma 3.3.4 runtime capture: 40-byte rows, 25 rows per frame.
    // Keep complete rows within its observed 1 KiB package budget.
    const val MAX_RAW_CHUNK_BYTES = 1024
    val alignRight = byteArrayOf(0x1b, 0x61, 0x02)
    val formFeed = byteArrayOf(0x1d, 0x66, 0xc0.toByte(), 0x03)
    val shortFeed = byteArrayOf(0x1b, 0x1b, 0x01, 0x5a, 0x00)

    fun printParts(frames: List<M1PrintFrame>, feedMode: M1FeedMode): List<M1PrintPart> = buildList {
        add(M1PrintPart(alignRight))
        frames.forEach { add(M1PrintPart(it.bytes, isImageFrame = true)) }
        when (feedMode) {
            M1FeedMode.Label -> add(M1PrintPart(formFeed, isFormFeed = true))
            M1FeedMode.Short -> add(M1PrintPart(shortFeed, isShortFeed = true))
            M1FeedMode.None -> Unit
        }
    }

    /** Copies pixels synchronously; callers retain ownership of [bitmap]. */
    fun frames(bitmap: Bitmap): List<M1PrintFrame> {
        require(bitmap.width in 1..MAX_WIDTH_DOTS) { "unsupported_width" }
        require(bitmap.height > 0) { "empty_bitmap" }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return frames(bitmap.width, bitmap.height, pixels)
    }

    fun frames(width: Int, height: Int, argb: IntArray): List<M1PrintFrame> {
        require(width in 1..MAX_WIDTH_DOTS) { "unsupported_width" }
        require(height > 0 && argb.size == width * height) { "invalid_pixels" }
        val rowBytes = (width + 7) / 8
        val rowsPerChunk = MAX_RAW_CHUNK_BYTES / rowBytes
        return buildList {
            var firstRow = 0
            while (firstRow < height) {
                val rows = minOf(rowsPerChunk, height - firstRow)
                val raw = packRows(width, firstRow, rows, argb)
                val payload = literalOnlyLzo1x(raw)
                val frame = ByteArrayOutputStream(12 + payload.size).apply {
                    write(byteArrayOf(0x1d, 0x76, 0x30, 0x30))
                    writeLe16(rowBytes)
                    writeLe16(rows)
                    writeLe32(payload.size)
                    write(payload)
                }.toByteArray()
                add(M1PrintFrame(rowBytes, rows, raw.size, frame))
                firstRow += rows
            }
        }
    }

    fun literalOnlyLzo1x(raw: ByteArray): ByteArray {
        require(raw.isNotEmpty()) { "empty_lzo_input" }
        return ByteArrayOutputStream(raw.size + 16).apply {
            if (raw.size <= 238) {
                write(17 + raw.size)
            } else {
                write(0)
                var remaining = raw.size - 18
                while (remaining > 255) {
                    write(0)
                    remaining -= 255
                }
                write(remaining)
            }
            write(raw)
            write(byteArrayOf(0x11, 0x00, 0x00))
        }.toByteArray()
    }

    private fun packRows(width: Int, firstRow: Int, rows: Int, argb: IntArray): ByteArray {
        val rowBytes = (width + 7) / 8
        val packed = ByteArray(rowBytes * rows)
        repeat(rows) { localY ->
            val pixelOffset = (firstRow + localY) * width
            repeat(width) { x ->
                if (isBlack(argb[pixelOffset + x])) {
                    val outputIndex = localY * rowBytes + x / 8
                    packed[outputIndex] = (packed[outputIndex].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
        }
        return packed
    }

    private fun isBlack(color: Int): Boolean {
        val alpha = color ushr 24 and 0xff
        if (alpha < 128) return false
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return red * 299 + green * 587 + blue * 114 < 128_000
    }
}

private fun ByteArrayOutputStream.writeLe16(value: Int) {
    write(value and 0xff)
    write(value ushr 8 and 0xff)
}

private fun ByteArrayOutputStream.writeLe32(value: Int) {
    write(value and 0xff)
    write(value ushr 8 and 0xff)
    write(value ushr 16 and 0xff)
    write(value ushr 24 and 0xff)
}
