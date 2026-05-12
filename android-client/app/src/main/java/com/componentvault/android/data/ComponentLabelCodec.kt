package com.componentvault.android.data

import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType
import com.componentvault.android.model.ComponentLabelSeed
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class WarehouseLabelPayload(
    val sku: String,
    val name: String,
    val category: String,
    val packageName: String,
    val location: String,
    val quantity: Int,
    val minStock: Int,
    val model: String? = null,
    val brand: String? = null,
)

internal data class ComponentQrPayload(
    val rawValue: String,
    val mode: ComponentLabelPayloadMode,
)

internal object ComponentLabelCodec {
    private const val WarehouseFormat = "component-vault-label"
    private const val WarehouseVersion = 1
    private const val CompactWarehousePrefix = "cvl2"

    fun buildQrPayload(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate,
    ): ComponentQrPayload? {
        if (!template.isQrLabel) {
            return null
        }

        val payload = WarehouseLabelPayload(
            sku = seed.sku,
            name = seed.name,
            category = seed.category,
            packageName = seed.packageName,
            location = seed.location,
            quantity = seed.quantity,
            minStock = seed.minStock,
            model = seed.model,
            brand = seed.brand,
        )

        return when {
            template.payloadMode == ComponentLabelPayloadMode.CompactOffline -> {
                ComponentQrPayload(
                    rawValue = encodeCompactWarehouseLabel(payload),
                    mode = ComponentLabelPayloadMode.CompactOffline,
                )
            }

            isJlcStyle(seed) -> {
                ComponentQrPayload(
                    rawValue = buildJlcCompatiblePayload(seed),
                    mode = ComponentLabelPayloadMode.JlcCompatible,
                )
            }

            else -> {
                ComponentQrPayload(
                    rawValue = encodeWarehouseLabel(payload),
                    mode = ComponentLabelPayloadMode.StandardWarehouse,
                )
            }
        }
    }

    fun parseScannedPayload(rawPayload: String): ComponentImportCandidate? {
        decodeCompactWarehouseLabel(rawPayload)?.let { payload ->
            return ComponentImportCandidate(
                sourceType = ComponentImportSourceType.WarehouseLabel,
                rawPayload = rawPayload.trim(),
                sourceLabel = "Warehouse compact label QR",
                sku = payload.sku,
                name = payload.name,
                packageName = payload.packageName,
                category = payload.category,
                model = payload.model,
                brand = payload.brand,
                suggestedQuantity = payload.quantity,
                notes = listOf("Compact offline warehouse label"),
            )
        }

        decodeWarehouseLabel(rawPayload)?.let { payload ->
            return ComponentImportCandidate(
                sourceType = ComponentImportSourceType.WarehouseLabel,
                rawPayload = rawPayload.trim(),
                sourceLabel = "Warehouse label QR",
                sku = payload.sku,
                name = payload.name,
                packageName = payload.packageName,
                category = payload.category,
                model = payload.model,
                brand = payload.brand,
                suggestedQuantity = payload.quantity,
                notes = buildList {
                    add("Warehouse location: ${payload.location}")
                    add("Minimum stock: ${payload.minStock}")
                },
            )
        }

        return null
    }

    private fun isJlcStyle(seed: ComponentLabelSeed): Boolean {
        return seed.sourceLabel?.contains("JLC", ignoreCase = true) == true ||
            seed.rawPayload?.trim()?.startsWith("{on:", ignoreCase = true) == true
    }

    private fun buildJlcCompatiblePayload(seed: ComponentLabelSeed): String {
        val payload = linkedMapOf(
            "on" to encodeJlcValue("local-${seed.sku}"),
            "pc" to encodeJlcValue(seed.sku),
            "pm" to encodeJlcValue(seed.model ?: seed.name),
            "qty" to seed.quantity.toString(),
            "mc" to "null",
            "cc" to "1",
            "pdi" to encodeJlcValue(seed.sku),
            "hp" to "null",
            "pkg" to encodeJlcValue(seed.packageName),
            "nm" to encodeJlcValue(seed.name),
            "br" to encodeJlcValue(seed.brand),
            "cat" to encodeJlcValue(seed.category),
            "loc" to encodeJlcValue(seed.location),
        )

        return payload.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { (key, value) -> "$key:${value.ifBlank { "null" }}" }
    }

    private fun encodeJlcValue(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return "null"
        }
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
    }

    private fun encodeWarehouseLabel(payload: WarehouseLabelPayload): String {
        val pairs = listOf(
            "\"fmt\":${jsonString(WarehouseFormat)}",
            "\"v\":$WarehouseVersion",
            "\"sku\":${jsonString(payload.sku)}",
            "\"name\":${jsonString(payload.name)}",
            "\"cat\":${jsonString(payload.category)}",
            "\"pkg\":${jsonString(payload.packageName)}",
            "\"loc\":${jsonString(payload.location)}",
            "\"qty\":${payload.quantity}",
            "\"min\":${payload.minStock}",
            "\"model\":${jsonNullableString(payload.model)}",
            "\"brand\":${jsonNullableString(payload.brand)}",
        )
        return pairs.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        )
    }

    private fun encodeCompactWarehouseLabel(payload: WarehouseLabelPayload): String {
        return listOf(
            CompactWarehousePrefix,
            encodeCompactValue(payload.sku),
            encodeCompactValue(payload.name),
            encodeCompactValue(payload.category),
            encodeCompactValue(payload.packageName),
            encodeCompactValue(payload.model),
            encodeCompactValue(payload.brand),
            payload.quantity.toString(),
        ).joinToString(separator = "|")
    }

    private fun encodeCompactValue(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return "-"
        }
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
    }

    private fun decodeWarehouseLabel(rawPayload: String): WarehouseLabelPayload? {
        val trimmed = rawPayload.trim()
        if (!trimmed.startsWith("{")) {
            return null
        }

        return parseFlatJsonObject(trimmed)
            ?.takeIf { it["fmt"] == WarehouseFormat && it["v"]?.toIntOrNull() == WarehouseVersion }
            ?.let { payload ->
                WarehouseLabelPayload(
                    sku = payload["sku"].orEmpty(),
                    name = payload["name"].orEmpty(),
                    category = payload["cat"].orEmpty(),
                    packageName = payload["pkg"].orEmpty(),
                    location = payload["loc"].orEmpty(),
                    quantity = payload["qty"]?.toIntOrNull() ?: 0,
                    minStock = payload["min"]?.toIntOrNull() ?: 0,
                    model = payload["model"]?.takeIf { it.isNotBlank() },
                    brand = payload["brand"]?.takeIf { it.isNotBlank() },
                )
            }
            ?.takeIf {
                it.sku.isNotBlank() && it.name.isNotBlank() && it.packageName.isNotBlank()
            }
    }

    private fun decodeCompactWarehouseLabel(rawPayload: String): WarehouseLabelPayload? {
        val trimmed = rawPayload.trim()
        if (!trimmed.startsWith("$CompactWarehousePrefix|")) {
            return null
        }

        val parts = trimmed.split('|')
        if (parts.size < 8) {
            return null
        }

        return WarehouseLabelPayload(
            sku = decodeCompactValue(parts.getOrNull(1)),
            name = decodeCompactValue(parts.getOrNull(2)),
            category = decodeCompactValue(parts.getOrNull(3)),
            packageName = decodeCompactValue(parts.getOrNull(4)),
            location = "",
            quantity = parts.getOrNull(7)?.toIntOrNull() ?: 0,
            minStock = 0,
            model = decodeCompactValue(parts.getOrNull(5)).ifBlank { null },
            brand = decodeCompactValue(parts.getOrNull(6)).ifBlank { null },
        ).takeIf {
            it.sku.isNotBlank() && it.name.isNotBlank() && it.packageName.isNotBlank()
        }
    }

    private fun decodeCompactValue(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank() || normalized == "-") {
            return ""
        }
        return URLDecoder.decode(normalized, StandardCharsets.UTF_8.toString())
    }

    private fun jsonString(value: String): String = "\"${escapeJson(value)}\""

    private fun jsonNullableString(value: String?): String {
        return value?.takeIf(String::isNotBlank)?.let(::jsonString) ?: "null"
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code < 0x20) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                    }
                }
            }
        }
    }

    private fun parseFlatJsonObject(raw: String): Map<String, String?>? {
        return runCatching {
            var index = 0

            fun skipWhitespace() {
                while (index < raw.length && raw[index].isWhitespace()) {
                    index++
                }
            }

            fun parseQuotedString(): String {
                require(index < raw.length && raw[index] == '"')
                index++
                val builder = StringBuilder()
                while (index < raw.length) {
                    val current = raw[index++]
                    when (current) {
                        '"' -> return builder.toString()
                        '\\' -> {
                            val escaped = raw.getOrNull(index++) ?: error("Invalid escape sequence")
                            builder.append(
                                when (escaped) {
                                    '"', '\\', '/' -> escaped
                                    'b' -> '\b'
                                    'f' -> '\u000C'
                                    'n' -> '\n'
                                    'r' -> '\r'
                                    't' -> '\t'
                                    'u' -> {
                                        val hex = raw.substring(index, index + 4)
                                        index += 4
                                        hex.toInt(16).toChar()
                                    }

                                    else -> error("Unsupported escape sequence")
                                },
                            )
                        }

                        else -> builder.append(current)
                    }
                }
                error("Unterminated string")
            }

            skipWhitespace()
            require(raw.getOrNull(index) == '{')
            index++

            val values = linkedMapOf<String, String?>()
            while (true) {
                skipWhitespace()
                if (raw.getOrNull(index) == '}') {
                    index++
                    break
                }

                val key = parseQuotedString()
                skipWhitespace()
                require(raw.getOrNull(index) == ':')
                index++
                skipWhitespace()

                val value = when (raw.getOrNull(index)) {
                    '"' -> parseQuotedString()
                    'n' -> {
                        require(raw.startsWith("null", index))
                        index += 4
                        null
                    }

                    else -> {
                        val start = index
                        while (index < raw.length && raw[index] != ',' && raw[index] != '}') {
                            index++
                        }
                        raw.substring(start, index).trim()
                    }
                }
                values[key] = value

                skipWhitespace()
                when (raw.getOrNull(index)) {
                    ',' -> index++
                    '}' -> {
                        index++
                        break
                    }

                    else -> error("Invalid JSON object")
                }
            }
            values
        }.getOrNull()
    }
}
