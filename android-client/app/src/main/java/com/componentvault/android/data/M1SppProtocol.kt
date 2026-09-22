package com.componentvault.android.data

import java.io.ByteArrayOutputStream

internal class CancelableResourceSlot<T>(private val closeResource: (T) -> Unit) {
    private var cancelled = false
    private var resource: T? = null

    @Synchronized
    fun attach(value: T): Boolean {
        if (cancelled) {
            closeResource(value)
            return false
        }
        resource = value
        return true
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        resource?.let(closeResource)
        resource = null
    }

    @Synchronized
    fun closeCurrent() {
        resource?.let(closeResource)
        resource = null
    }
}

internal fun sessionMayDeliver(expectedGeneration: Int, currentGeneration: Int, isActiveSession: Boolean): Boolean =
    expectedGeneration == currentGeneration && isActiveSession

internal object M1SppProtocol {
    val sppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    val queryModel = byteArrayOf(0x1b, 0x1c) + "& V1 getval \"printer_name\"\r\n".toByteArray(Charsets.US_ASCII)
    val queryModelFallback = byteArrayOf(
        0x1b, 0x1c, 0x26, 0x20, 0x56, 0x31, 0x20, 0x67, 0x65, 0x74, 0x6b, 0x65, 0x79,
        0x0d, 0x0a, 0x01, 0x04, 0x00, 0x20,
    )
    val queryStatus = byteArrayOf(0x1b, 0x12, 0x73)

    internal enum class ModelResult { Matched, Mismatch, NoResponse }
    internal sealed interface StatusResult {
        data object NoResponse : StatusResult
        data object Invalid : StatusResult
        data class Received(val code: Int, val source: Source) : StatusResult
        enum class Source { Query, Async }
    }

    fun parseModelReply(reply: ByteArray): ModelResult {
        if (reply.isEmpty()) return ModelResult.NoResponse
        val model = reply.toString(Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' || it == '"' || it == '\'' }
        return if (model == "M1") ModelResult.Matched else ModelResult.Mismatch
    }

    /** The synchronous SDK reply is a little-endian value and is only valid with a trailing byte. */
    fun parseQueryStatus(reply: ByteArray): Int? = if (reply.size > 2) {
        (reply[0].toInt() and 0xff) or ((reply[1].toInt() and 0xff) shl 8)
    } else null

    fun parseStatusReply(reply: ByteArray): Int? {
        val marker = "pooli_sta=".toByteArray(Charsets.US_ASCII)
        val markerIndex = reply.indexOf(marker, 0)
        if (markerIndex >= 0) {
            val valueIndex = markerIndex + marker.size
            return reply.getOrNull(valueIndex)?.toInt()?.and(0xff)
        }
        // A split asynchronous frame must never be reinterpreted as the synchronous binary reply.
        if (longestMarkerPrefixSuffix(reply, marker) > 0 || marker.hasPrefix(reply)) return null
        return parseQueryStatus(reply)
    }

    fun classifyStatusReply(reply: ByteArray): StatusResult {
        if (reply.isEmpty()) return StatusResult.NoResponse
        val asyncCode = M1AsyncStatusParser().append(reply).lastOrNull()
        if (asyncCode != null) return StatusResult.Received(asyncCode, StatusResult.Source.Async)
        val parsed = parseStatusReply(reply)
        if (parsed == null) return StatusResult.Invalid
        val isPlainText = reply.take(3).all { byte ->
            val value = byte.toInt() and 0xff
            value in 0x20..0x7e
        }
        val hasAsyncFrame = reply.indexOf("pooli_sta=".toByteArray(Charsets.US_ASCII), 0) >= 0
        if (hasAsyncFrame) return StatusResult.Invalid
        val validQueryReply = !isPlainText && reply.size > 2 && (reply[1].toInt() and 0xff) == 0 && parsed <= 0x7f
        return if (validQueryReply) StatusResult.Received(parsed, StatusResult.Source.Query) else StatusResult.Invalid
    }

    fun statusBits(code: Int): List<String> = buildList {
        if (code and 0x01 != 0) add("paper_out")
        if (code and 0x02 != 0) add("temperature_high")
        if (code and 0x04 != 0) add("temperature_low")
        if (code and 0x08 != 0) add("battery_low")
        if (code and 0x10 != 0) add("cover_open")
        if (code and 0x20 != 0) add("voltage_low")
        if (code and 0x40 != 0) add("locate_failed")
    }
}

/** Stream parser for asynchronous `pooli_sta=` frames; it accepts split and coalesced reads. */
internal class M1AsyncStatusParser {
    private val pending = ByteArrayOutputStream()
    private val marker = "pooli_sta=".toByteArray(Charsets.US_ASCII)

    fun append(bytes: ByteArray, count: Int = bytes.size): List<Int> {
        if (count <= 0) return emptyList()
        pending.write(bytes, 0, count)
        val source = pending.toByteArray()
        val result = mutableListOf<Int>()
        var cursor = 0
        var retainFrom = source.size
        while (cursor <= source.size - marker.size) {
            val index = source.indexOf(marker, cursor)
            if (index < 0) break
            if (index + marker.size >= source.size) {
                retainFrom = index
                break
            }
            result += source[index + marker.size].toInt() and 0xff
            cursor = index + marker.size + 1
            retainFrom = cursor
        }
        if (retainFrom == source.size) {
            val suffix = longestMarkerPrefixSuffix(source, marker)
            retainFrom = source.size - suffix
        }
        pending.reset()
        if (retainFrom < source.size) pending.write(source, retainFrom, source.size - retainFrom)
        return result
    }
}

private fun ByteArray.indexOf(needle: ByteArray, start: Int): Int {
    outer@ for (index in start..size - needle.size) {
        for (offset in needle.indices) if (this[index + offset] != needle[offset]) continue@outer
        return index
    }
    return -1
}

private fun longestMarkerPrefixSuffix(source: ByteArray, marker: ByteArray): Int {
    val maximum = minOf(source.size, marker.size - 1)
    for (length in maximum downTo 1) {
        if ((0 until length).all { source[source.size - length + it] == marker[it] }) return length
    }
    return 0
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
    prefix.size <= size && prefix.indices.all { this[it] == prefix[it] }
