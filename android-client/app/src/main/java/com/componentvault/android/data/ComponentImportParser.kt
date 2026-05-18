package com.componentvault.android.data

import com.componentvault.android.data.ocr.OcrResult
import com.componentvault.android.data.ocr.normalizedText
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType

internal object ComponentImportParser {
    private val lcscSkuRegex = Regex("""\bC\d{5,10}\b""", RegexOption.IGNORE_CASE)
    private val quantityRegexes = listOf(
        Regex("""(?i)\b(?:qty|quantity)\b\s*[:：]?\s*(\d{1,7})"""),
        Regex("""\u6570\u91CF\s*[:：]?\s*(\d{1,7})"""),
    )
    private val traceabilityKeywords = listOf(
        "lot",
        "date",
        "datecode",
        "d/c",
        "\u6279\u6B21",
        "\u65E5\u671F",
        "\u751F\u4EA7",
    )
    private val nameLabelKeys = arrayOf(
        "\u540D\u79F0",
        "\u54C1\u540D",
        "name",
        "product",
        "description",
        "item",
        "part",
    )
    private val skuLabelKeys = arrayOf(
        "sku",
        "\u7F16\u53F7",
        "\u7269\u6599\u7F16\u53F7",
        "\u6599\u53F7",
        "item no",
        "code",
    )
    private val brandLabelKeys = arrayOf(
        "\u54C1\u724C",
        "\u5382\u5546",
        "\u5236\u9020\u5546",
        "manufacturer",
        "brand",
        "mfg",
        "vendor",
    )
    private val modelLabelKeys = arrayOf(
        "\u578B\u53F7",
        "\u7269\u6599\u578B\u53F7",
        "\u6599\u53F7",
        "model",
        "mpn",
        "p/n",
        "pn",
        "part no",
    )
    private val packageLabelKeys = arrayOf(
        "\u5C01\u88C5",
        "\u5C3A\u5BF8",
        "package",
        "pkg",
        "case",
        "footprint",
    )
    private val quantityLabelKeys = arrayOf(
        "\u6570\u91CF",
        "qty",
        "quantity",
    )

    fun parseJlcText(rawInput: String): ComponentImportCandidate = JlcImportParser.parseText(rawInput)

    fun parseScannedQr(rawInput: String): ComponentImportCandidate {
        return ComponentLabelCodec.parseScannedPayload(rawInput)
            ?: JlcImportParser.parseQr(rawInput)
    }

    fun parseSupplierText(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "请先扫描或粘贴供应商包装文字后再解析。" }

        return parseSupplierPayload(
            lines = normalizeLines(normalizedInput.lineSequence()),
            rawPayload = normalizedInput,
            sourceLabel = "供应商包装文字",
            ocrResult = null,
        )
    }

    fun parseSupplierOcr(result: OcrResult): ComponentImportCandidate {
        val normalizedInput = result.normalizedText()
        require(normalizedInput.isNotBlank()) { "请先拍摄并识别可读的供应商包装文字后再解析。" }

        return parseSupplierPayload(
            lines = normalizeLines(result.lines.asSequence().map { it.text }),
            rawPayload = normalizedInput,
            sourceLabel = "供应商包装 OCR（${result.engineLabel}）",
            ocrResult = result,
        )
    }

    private fun parseSupplierPayload(
        lines: List<String>,
        rawPayload: String,
        sourceLabel: String,
        ocrResult: OcrResult?,
    ): ComponentImportCandidate {
        val labeledValues = lines.flatMap(::parseStructuredFragments)
        val sku = labeledValues.firstMatch(*skuLabelKeys).ifBlank {
            findStandaloneSku(lines).orEmpty()
        }
        val brand = labeledValues.firstMatch(*brandLabelKeys).blankToNull()
        val name = resolveSupplierName(
            labeledValues = labeledValues,
            lines = lines,
            sku = sku,
            brand = brand,
        )
        val model = labeledValues.firstMatch(*modelLabelKeys).ifBlank {
            lines.firstOrNull { line ->
                looksLikeModelCode(line) &&
                    !line.equals(name, ignoreCase = true) &&
                    !line.equals(sku, ignoreCase = true) &&
                    !line.equals(brand, ignoreCase = true)
            }.orEmpty()
        }.blankToNull()
        val packageName = labeledValues.firstMatch(*packageLabelKeys).ifBlank {
            ComponentPackageInferencer.infer(
                name,
                model,
                sku,
                *lines.toTypedArray(),
            ).orEmpty()
        }
        val quantity = labeledValues.firstMatch(*quantityLabelKeys).extractFirstInt()
            ?: findQuantity(lines)

        val classificationHint = name.ifBlank {
            model ?: sku
        }.orEmpty()
        val inferredCategory = ComponentCategoryInferencer.infer(
            classificationHint,
            packageName,
            model,
            brand,
        )

        val notes = buildList {
            if (ocrResult != null) {
                add("OCR 引擎：${ocrResult.engineLabel}")
                add("OCR 文本块：${ocrResult.blocks.size}")
            }
            if (lines.isNotEmpty()) {
                add("识别行数：${lines.size}")
            }
            collectTraceabilityNotes(
                lines = lines,
                labeledValues = labeledValues,
            ).forEach { note ->
                addIfMissing(note)
            }
        }

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.SupplierOcr,
            rawPayload = rawPayload,
            sourceLabel = sourceLabel,
            sku = sku,
            name = name,
            packageName = packageName,
            category = inferredCategory,
            model = model,
            brand = brand,
            suggestedQuantity = quantity,
            notes = notes,
        )
    }

    private fun resolveSupplierName(
        labeledValues: List<Pair<String, String>>,
        lines: List<String>,
        sku: String,
        brand: String?,
    ): String {
        val labeledName = labeledValues.firstMatch(*nameLabelKeys)
        if (labeledName.isNotBlank()) {
            return labeledName
        }

        return lines.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val score = scoreNameCandidate(
                    line = line,
                    sku = sku,
                    brand = brand,
                ) ?: return@mapNotNull null
                score to line
            }
            .sortedByDescending { (score, line) ->
                score * 1000 + line.length
            }
            .map { (_, line) -> line }
            .firstOrNull()
            .orEmpty()
    }

    private fun scoreNameCandidate(
        line: String,
        sku: String,
        brand: String?,
    ): Int? {
        if (!looksLikeNameCandidate(line)) {
            return null
        }
        if (line.equals(sku, ignoreCase = true) || line.equals(brand, ignoreCase = true)) {
            return null
        }
        if (looksLikeSkuCode(line) || looksLikeModelCode(line) || looksLikeTraceabilityLine(line)) {
            return null
        }
        if (quantityRegexes.any { it.containsMatchIn(line) }) {
            return null
        }

        var score = 0
        if (line.any { it.code in 0x4E00..0x9FFF }) {
            score += 5
        }
        if (line.contains(' ')) {
            score += 3
        }
        if (line.contains('(') || line.contains(')')) {
            score += 2
        }
        if (line.length in 8..56) {
            score += 2
        }
        if (line.any(Char::isLowerCase)) {
            score += 1
        }
        return score.takeIf { it > 0 }
    }

    private fun normalizeLines(lines: Sequence<String>): List<String> {
        return lines
            .map { line -> line.replace('\u3000', ' ').trim() }
            .filter(String::isNotBlank)
            .toList()
    }

    private fun parseStructuredFragments(line: String): List<Pair<String, String>> {
        return buildList {
            parseKeyValueLine(line)?.let(::add)
        }
    }

    private fun parseKeyValueLine(line: String): Pair<String, String>? {
        val separatorIndex = listOf(':', '\uFF1A')
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

    private fun List<Pair<String, String>>.firstMatch(vararg keys: String): String {
        return firstNotNullOfOrNull { (key, value) ->
            value.takeIf { candidate ->
                candidate.isNotBlank() && keys.any { key.contains(it, ignoreCase = true) }
            }
        }.orEmpty().trim()
    }

    private fun List<Pair<String, String>>.matchingValues(vararg keys: String): List<String> {
        return mapNotNull { (key, value) ->
            value.takeIf { candidate ->
                candidate.isNotBlank() && keys.any { key.contains(it, ignoreCase = true) }
            }?.trim()
        }
    }

    private fun collectTraceabilityNotes(
        lines: List<String>,
        labeledValues: List<Pair<String, String>>,
    ): List<String> {
        return buildList {
            labeledValues.matchingValues(
                "lot",
                "date",
                "datecode",
                "\u6279\u6B21",
                "\u65E5\u671F",
                "\u751F\u4EA7",
            ).forEach { value ->
                addIfMissing("包装标记：$value")
            }

            lines.filter(::looksLikeTraceabilityLine)
                .take(3)
                .forEach { line ->
                    addIfMissing("包装标记：$line")
                }
        }
    }

    private fun findStandaloneSku(lines: List<String>): String? {
        return lines.firstNotNullOfOrNull { line ->
            lcscSkuRegex.find(line)?.value
                ?: line.takeIf(::looksLikeSkuCode)
        }
    }

    private fun findQuantity(lines: List<String>): Int? {
        return lines.firstNotNullOfOrNull { line ->
            quantityRegexes.firstNotNullOfOrNull { regex ->
                regex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }
    }

    private fun looksLikeNameCandidate(line: String): Boolean {
        if (line.length < 4 || line.length > 80) {
            return false
        }
        if (line.contains(':') || line.contains('\uFF1A')) {
            return false
        }
        val hasLettersOrChinese = line.any { it.isLetter() || it.code in 0x4E00..0x9FFF }
        if (!hasLettersOrChinese) {
            return false
        }
        return line.any { it.code in 0x4E00..0x9FFF } ||
            line.contains(' ') ||
            line.contains('(') ||
            line.contains(')')
    }

    private fun looksLikeModelCode(line: String): Boolean {
        if (line.length < 5 || line.contains(':') || line.contains('\uFF1A')) {
            return false
        }
        if (line.any { it.code in 0x4E00..0x9FFF } || line.contains(' ')) {
            return false
        }
        val alphaNumericCount = line.count { it.isLetterOrDigit() }
        return alphaNumericCount >= 5 && line.any(Char::isDigit)
    }

    private fun looksLikeSkuCode(line: String): Boolean {
        if (line.length !in 6..24 || line.contains(' ')) {
            return false
        }
        val alphaNumericCount = line.count { it.isLetterOrDigit() }
        return alphaNumericCount >= 5 && line.any(Char::isDigit)
    }

    private fun looksLikeTraceabilityLine(line: String): Boolean {
        return traceabilityKeywords.any { keyword ->
            line.contains(keyword, ignoreCase = true)
        }
    }

    private fun String.extractFirstInt(): Int? {
        return Regex("""\d{1,7}""").find(this)?.value?.toIntOrNull()
    }

    private fun MutableList<String>.addIfMissing(value: String) {
        if (none { it.equals(value, ignoreCase = true) }) {
            add(value)
        }
    }

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
}
