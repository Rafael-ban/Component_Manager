package com.componentvault.android.data

import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object JlcImportParser {
    private val qrKeyValuePattern = Regex("""([A-Za-z0-9_]+):(.*?)(?=,[A-Za-z0-9_]+:|$)""")
    private val modelTokenPattern = Regex("""\b[A-Z0-9][A-Z0-9._/\-]{4,}\b""", RegexOption.IGNORE_CASE)

    fun parseText(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Paste JLC text before parsing." }

        val values = normalizedInput
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseKeyValueLine)
            .associate { it.first.lowercase() to it.second }

        val name = values.valueOf("\u540D\u79F0", "name")
        val sku = values.valueOf("\u7F16\u53F7", "sku")
        val model = values.valueOf("\u578B\u53F7", "model").blankToNull()
        val brand = values.valueOf("\u54C1\u724C", "brand").blankToNull()
        val packageName = values.valueOf("\u5C01\u88C5", "package").ifBlank {
            ComponentPackageInferencer.infer(name, model, brand, normalizedInput).orEmpty()
        }

        require(sku.isNotBlank()) { "Unable to recognize the JLC component number." }

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText,
            rawPayload = normalizedInput,
            sourceLabel = "JLC paste text",
            sku = sku,
            name = name,
            packageName = packageName,
            category = ComponentCategoryInferencer.infer(name, packageName, model, brand),
            model = model,
            brand = brand,
            notes = buildList {
                add("JLC part number: $sku")
                model?.let { add("Supplier model: $it") }
                brand?.let { add("Supplier brand: $it") }
            },
        )
    }

    fun parseQr(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Scan a JLC code before importing." }

        val values = parseQrKeyValues(normalizedInput)

        val sku = values["pc"].orEmpty()
        val manufacturerCode = values["mc"].cleanNullable()
        val explicitName = values["nm"].cleanNullable()
        val brand = values["br"].cleanNullable()
        val model = values["pm"].cleanNullable()
            ?: inferModelCandidate(
                manufacturerCode,
                explicitName,
                brand,
                sku,
            )
        val packageName = values["pkg"].cleanNullable()
            ?: ComponentPackageInferencer.infer(
                explicitName,
                model,
                manufacturerCode,
                brand,
                sku,
                normalizedInput,
            )
            .orEmpty()
        val explicitCategory = values["cat"].cleanNullable()
        val explicitLocation = values["loc"].cleanNullable()
        val quantity = values["qty"]?.toIntOrNull()
        val categoryHint = explicitName
            ?: model
            ?: manufacturerCode
            ?: sku

        require(sku.isNotBlank()) { "Unable to recognize the JLC part code from the QR payload." }

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = normalizedInput,
            sourceLabel = "JLC package QR",
            sku = sku,
            name = explicitName.orEmpty(),
            packageName = packageName,
            category = explicitCategory ?: ComponentCategoryInferencer.infer(
                categoryHint,
                packageName,
                model,
                manufacturerCode,
                brand,
                sku,
            ),
            model = model,
            brand = brand,
            suggestedQuantity = quantity,
            notes = buildList {
                values["on"]?.cleanNullable()?.let { add("Order number: $it") }
                values["pdi"]?.cleanNullable()?.let { add("Package data id: $it") }
                values["cc"]?.cleanNullable()?.let { add("Package count: $it") }
                values["hp"]?.cleanNullable()?.let { add("Shelf hint: $it") }
                manufacturerCode?.let { add("Manufacturer code: $it") }
                explicitLocation?.let { add("Warehouse location: $it") }
            },
        )
    }

    private fun parseQrKeyValues(rawInput: String): Map<String, String> {
        val body = rawInput.removePrefix("{").removeSuffix("}")
        return buildMap {
            qrKeyValuePattern.findAll(body).forEach { match ->
                val key = match.groupValues[1].trim()
                val value = decodeQrValue(match.groupValues[2].trim())
                if (key.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }

    private fun inferModelCandidate(vararg values: String?): String? {
        return values.asSequence()
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
            .flatMap { value -> modelTokenPattern.findAll(value).map { it.value.trim() } }
            .firstOrNull { token ->
                token.any(Char::isLetter) &&
                    !token.matches(Regex("""C\d{5,}""", RegexOption.IGNORE_CASE))
            }
    }

    private fun parseKeyValueLine(line: String): Pair<String, String>? {
        val separators = listOf(':', '\uFF1A')
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

    private fun Map<String, String>.valueOf(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            entries.firstOrNull { it.key.contains(key.lowercase()) }?.value
        }.orEmpty().trim()
    }

    private fun String?.cleanNullable(): String? {
        val value = this?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }

    private fun decodeQrValue(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        }.getOrDefault(value)
    }
}
