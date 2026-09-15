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
        require(normalizedInput.isNotBlank()) { "请先粘贴 JLC 文本后再解析。" }

        val values = normalizedInput
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseKeyValueLine)
            .associate { it.first.lowercase() to it.second }

        val name = values.valueOf("\u540D\u79F0", "name")
        val sku = values.valueOf("\u7F16\u53F7", "sku")
        val model = values.valueOf("\u578B\u53F7", "model").blankToNull()
        val brand = values.valueOf("\u54C1\u724C", "\u5382\u5546", "brand").blankToNull()
        val packageName = values.valueOf("\u5C01\u88C5", "package").ifBlank {
            ComponentPackageInferencer.infer(name, model, brand, normalizedInput).orEmpty()
        }

        require(sku.isNotBlank()) { "无法识别 JLC 元件编号。" }

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText,
            rawPayload = normalizedInput,
            sourceLabel = "JLC 粘贴文本",
            sku = sku,
            name = name,
            packageName = packageName,
            category = ComponentCategoryInferencer.infer(name, packageName, model, brand),
            model = model,
            brand = brand,
            notes = buildList {
                add("JLC 编号：$sku")
                model?.let { add("供应商型号：$it") }
                brand?.let { add("供应商品牌：$it") }
            },
        )
    }

    fun parseQr(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "请先扫描 JLC 二维码后再导入。" }

        val values = parseQrKeyValues(normalizedInput)

        val explicitSku = values["pc"].orEmpty()
        val sku = if (explicitSku.isNotBlank()) {
            LcscPublicCatalog.normalizeSku(explicitSku) ?: explicitSku
        } else {
            // Accept a bare SKU or a URL containing one unambiguous SKU; never infer
            // a C-number from the unrelated numeric product ID of a domestic URL.
            Regex("""(?i)(?<![A-Z0-9])C\d{1,10}(?![A-Z0-9])""")
                .findAll(normalizedInput).map { it.value.uppercase(java.util.Locale.ROOT) }
                .distinct().toList().singleOrNull().orEmpty()
        }
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

        require(sku.isNotBlank()) { "无法从二维码载荷中识别 JLC 料号。" }

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = normalizedInput,
            sourceLabel = "JLC 包装二维码",
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
                values["on"]?.cleanNullable()?.let { add("订单号：$it") }
                values["pdi"]?.cleanNullable()?.let { add("包装数据 ID：$it") }
                values["cc"]?.cleanNullable()?.let { add("包装数量：$it") }
                values["hp"]?.cleanNullable()?.let { add("货架提示：$it") }
                manufacturerCode?.let { add("厂商编码：$it") }
                explicitLocation?.let { add("仓位：$it") }
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
                    put(key.lowercase(java.util.Locale.ROOT), value)
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
