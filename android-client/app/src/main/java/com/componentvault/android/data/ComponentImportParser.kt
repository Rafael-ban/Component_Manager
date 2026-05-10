package com.componentvault.android.data

import com.componentvault.android.data.ocr.OcrResult
import com.componentvault.android.data.ocr.normalizedText
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType

internal object ComponentImportParser {
    private val lcscSkuRegex = Regex("""\bC\d{5,10}\b""", RegexOption.IGNORE_CASE)
    private val quantityRegexes = listOf(
        Regex("""(?i)\b(?:qty|quantity)\b\s*[:：]?\s*(\d{1,7})"""),
        Regex("""数量\s*[:：]?\s*(\d{1,7})"""),
    )
    private val traceabilityKeywords = listOf(
        "lot",
        "date",
        "datecode",
        "d/c",
        "批次",
        "批号",
        "日期",
        "生产日期",
    )

    fun parseJlcText(rawInput: String): ComponentImportCandidate = JlcImportParser.parseText(rawInput)

    fun parseScannedQr(rawInput: String): ComponentImportCandidate {
        return ComponentLabelCodec.parseScannedPayload(rawInput)
            ?: JlcImportParser.parseQr(rawInput)
    }

    fun parseSupplierText(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Scan or paste supplier text before parsing." }

        return parseSupplierPayload(
            lines = normalizeLines(normalizedInput.lineSequence()),
            rawPayload = normalizedInput,
            sourceLabel = "Supplier packaging text",
            ocrResult = null,
        )
    }

    fun parseSupplierOcr(result: OcrResult): ComponentImportCandidate {
        val normalizedInput = result.normalizedText()
        require(normalizedInput.isNotBlank()) { "Capture readable supplier text before parsing." }

        return parseSupplierPayload(
            lines = normalizeLines(result.lines.asSequence().map { it.text }),
            rawPayload = normalizedInput,
            sourceLabel = "Supplier packaging OCR (${result.engineLabel})",
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

        val name = labeledValues.firstMatch(
            "名称",
            "品名",
            "name",
            "product",
            "part",
        ).ifBlank {
            lines.firstOrNull(::looksLikeHumanName).orEmpty()
        }

        val sku = labeledValues.firstMatch(
            "sku",
            "编号",
            "物料编号",
            "货号",
            "item no",
            "code",
        ).ifBlank {
            findStandaloneSku(lines).orEmpty()
        }

        val brand = labeledValues.firstMatch(
            "品牌",
            "厂商",
            "manufacturer",
            "brand",
            "mfg",
        ).blankToNull()

        val model = labeledValues.firstMatch(
            "型号",
            "料号",
            "规格型号",
            "model",
            "mpn",
            "p/n",
            "pn",
            "part no",
        ).ifBlank {
            lines.firstOrNull { line ->
                looksLikeModelCode(line) &&
                    !line.equals(name, ignoreCase = true) &&
                    !line.equals(sku, ignoreCase = true) &&
                    !line.equals(brand, ignoreCase = true)
            }.orEmpty()
        }.blankToNull()

        val packageName = labeledValues.firstMatch(
            "封装",
            "规格",
            "package",
            "pkg",
            "case",
            "footprint",
        ).ifBlank {
            ComponentPackageInferencer.infer(
                name,
                model,
                sku,
                *lines.toTypedArray(),
            ).orEmpty()
        }

        val quantity = labeledValues.firstMatch(
            "数量",
            "qty",
            "quantity",
        ).extractFirstInt()
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
                add("OCR engine: ${ocrResult.engineLabel}")
                add("OCR blocks: ${ocrResult.blocks.size}")
            }
            if (lines.isNotEmpty()) {
                add("Recognized lines: ${lines.size}")
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

    private fun normalizeLines(lines: Sequence<String>): List<String> {
        return lines
            .map { line -> line.replace('\u3000', ' ').trim() }
            .filter(String::isNotBlank)
            .toList()
    }

    private fun parseStructuredFragments(line: String): List<Pair<String, String>> {
        val normalized = line.replace('：', ':')
        return buildList {
            parseKeyValueLine(normalized)?.let(::add)
        }
    }

    private fun parseKeyValueLine(line: String): Pair<String, String>? {
        val separatorIndex = line.indexOf(':').takeIf { it > 0 } ?: return null
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
                "批次",
                "批号",
                "日期",
                "生产日期",
            ).forEach { value ->
                addIfMissing("Packaging mark: $value")
            }

            lines.filter(::looksLikeTraceabilityLine)
                .take(3)
                .forEach { line ->
                    addIfMissing("Packaging mark: $line")
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

    private fun looksLikeHumanName(line: String): Boolean {
        if (line.length < 4 || line.contains(':') || line.contains('：')) {
            return false
        }
        val hasLettersOrChinese = line.any { it.isLetter() || it.code in 0x4E00..0x9FFF }
        val hasSpacesOrSymbols = line.contains(' ') || line.contains('-') || line.contains('(')
        return hasLettersOrChinese && hasSpacesOrSymbols
    }

    private fun looksLikeModelCode(line: String): Boolean {
        if (line.length < 5 || line.contains(':') || line.contains('：')) {
            return false
        }
        val alphaNumericCount = line.count { it.isLetterOrDigit() }
        return alphaNumericCount >= 5 && line.any { it == '-' || it.isDigit() }
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
