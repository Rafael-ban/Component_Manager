package com.componentvault.android.data

import com.componentvault.android.model.JlcImportPayload
import com.componentvault.android.model.JlcImportSource
import java.util.Locale

internal object JlcImportParser {
    fun parseText(rawInput: String): JlcImportPayload {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Paste JLC text before parsing." }

        val values = normalizedInput
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull(::parseKeyValueLine)
            .associate { it.first to it.second }

        val name = values["名称"].orEmpty().ifBlank {
            values["name"].orEmpty()
        }
        val sku = values["编号"].orEmpty().ifBlank {
            values["sku"].orEmpty()
        }
        val packageName = values["封装"].orEmpty().ifBlank {
            values["package"].orEmpty()
        }
        val model = values["型号"].orEmpty().ifBlank {
            values["model"].orEmpty()
        }.ifBlank { null }
        val brand = values["品牌"].orEmpty().ifBlank {
            values["brand"].orEmpty()
        }.ifBlank { null }

        require(name.isNotBlank()) { "Unable to recognize the JLC component name." }
        require(sku.isNotBlank()) { "Unable to recognize the JLC component number." }
        require(packageName.isNotBlank()) { "Unable to recognize the JLC package field." }

        return JlcImportPayload(
            source = JlcImportSource.Text,
            rawPayload = normalizedInput,
            sourceLabel = "JLC paste text",
            sku = sku,
            name = name,
            packageName = packageName,
            category = inferCategory(name, packageName, model, brand),
            model = model,
            brand = brand,
            notes = buildList {
                add("JLC part number: $sku")
                model?.let { add("Supplier model: $it") }
                brand?.let { add("Supplier brand: $it") }
            },
        )
    }

    fun parseQr(rawInput: String): JlcImportPayload {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Scan a JLC code before importing." }

        val content = normalizedInput
            .removePrefix("{")
            .removeSuffix("}")

        val values = content
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val separatorIndex = token.indexOf(':')
                if (separatorIndex <= 0 || separatorIndex == token.lastIndex) {
                    null
                } else {
                    val key = token.substring(0, separatorIndex).trim()
                    val value = token.substring(separatorIndex + 1).trim()
                    key to value
                }
            }
            .associate { it.first to it.second }

        val sku = values["pc"].orEmpty()
        val model = values["pm"].cleanNullable()
        val manufacturerCode = values["mc"].cleanNullable()
        val quantity = values["qty"]?.toIntOrNull()
        val packageName = model ?: sku
        val displayName = manufacturerCode
            ?: model
            ?: "JLC Component $sku"

        require(sku.isNotBlank()) { "Unable to recognize the JLC part code from the QR payload." }

        return JlcImportPayload(
            source = JlcImportSource.Qr,
            rawPayload = normalizedInput,
            sourceLabel = "JLC package QR",
            sku = sku,
            name = displayName,
            packageName = packageName,
            category = inferCategory(displayName, packageName, model, manufacturerCode),
            model = model,
            brand = null,
            suggestedQuantity = quantity,
            notes = buildList {
                values["on"]?.cleanNullable()?.let { add("Order number: $it") }
                values["pdi"]?.cleanNullable()?.let { add("Package data id: $it") }
                values["cc"]?.cleanNullable()?.let { add("Package count: $it") }
                values["hp"]?.cleanNullable()?.let { add("Shelf hint: $it") }
            },
        )
    }

    private fun parseKeyValueLine(line: String): Pair<String, String>? {
        val separators = listOf('：', ':')
        val separatorIndex = separators
            .map { line.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
            ?: return null

        val key = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim()
        if (key.isBlank() || value.isBlank()) {
            return null
        }
        return key to value
    }

    private fun inferCategory(vararg values: String?): String {
        val haystack = values
            .filterNotNull()
            .joinToString(separator = " ")
            .lowercase(Locale.US)

        return when {
            haystack.contains("connector") || haystack.contains("usb") || haystack.contains("pico") ||
                haystack.contains("header") || haystack.contains("socket") || haystack.contains("端子") ->
                "Connector"

            haystack.contains("capacitor") || haystack.contains("cap") || haystack.contains("uf") ||
                haystack.contains("nf") || haystack.contains("pf") -> "Capacitor"

            haystack.contains("resistor") || haystack.contains("ohm") || haystack.contains("贴片电阻") ->
                "Resistor"

            haystack.contains("inductor") || haystack.contains("coil") -> "Inductor"

            haystack.contains("diode") || haystack.contains("tvs") -> "Diode"

            haystack.contains("mosfet") || haystack.contains("transistor") || haystack.contains("bjt") ->
                "Transistor"

            haystack.contains("mcu") || haystack.contains("stm32") || haystack.contains("ic") ||
                haystack.contains("controller") -> "IC"

            haystack.contains("led") -> "LED"

            else -> "General"
        }
    }

    private fun String?.cleanNullable(): String? {
        val value = this?.trim().orEmpty()
        return value
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}
