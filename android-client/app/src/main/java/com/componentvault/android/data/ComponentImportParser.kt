package com.componentvault.android.data

import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType

internal object ComponentImportParser {
    fun parseJlcText(rawInput: String): ComponentImportCandidate = JlcImportParser.parseText(rawInput)

    fun parseScannedQr(rawInput: String): ComponentImportCandidate {
        return ComponentLabelCodec.parseScannedPayload(rawInput)
            ?: JlcImportParser.parseQr(rawInput)
    }

    fun parseSupplierText(rawInput: String): ComponentImportCandidate {
        val normalizedInput = rawInput.trim()
        require(normalizedInput.isNotBlank()) { "Scan or paste supplier text before parsing." }

        val lines = normalizedInput
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()

        val labeledValues = lines
            .mapNotNull(::parseKeyValueLine)
            .associate { it.first.lowercase() to it.second }

        val name = labeledValues.firstMatch(
            "\u540D\u79F0",
            "\u54C1\u540D",
            "name",
            "product",
            "part",
        ).ifBlank {
            lines.firstOrNull(::looksLikeHumanName).orEmpty()
        }

        val model = labeledValues.firstMatch(
            "\u578B\u53F7",
            "\u6599\u53F7",
            "model",
            "mpn",
            "p/n",
            "pn",
            "part no",
        ).ifBlank {
            lines.firstOrNull(::looksLikeModelCode).orEmpty()
        }.blankToNull()

        val brand = labeledValues.firstMatch(
            "\u54C1\u724C",
            "\u5382\u5546",
            "brand",
            "manufacturer",
            "mfg",
        ).blankToNull()

        val packageName = labeledValues.firstMatch(
            "\u5C01\u88C5",
            "\u89C4\u683C",
            "package",
            "pkg",
            "case",
        ).ifBlank {
            ComponentPackageInferencer.infer(*lines.toTypedArray()).orEmpty()
        }

        val sku = labeledValues.firstMatch(
            "sku",
            "\u7F16\u53F7",
            "\u7269\u6599\u7F16\u53F7",
            "\u8D27\u53F7",
        )

        val quantity = labeledValues.firstMatch(
            "\u6570\u91CF",
            "qty",
            "quantity",
        ).toIntOrNull()

        val displayName = name.ifBlank { model.orEmpty() }
        val inferredCategory = ComponentCategoryInferencer.infer(displayName, packageName, model, brand)

        return ComponentImportCandidate(
            sourceType = ComponentImportSourceType.SupplierOcr,
            rawPayload = normalizedInput,
            sourceLabel = "Supplier packaging OCR",
            sku = sku,
            name = displayName,
            packageName = packageName,
            category = inferredCategory,
            model = model,
            brand = brand,
            suggestedQuantity = quantity,
            notes = buildList {
                if (lines.isNotEmpty()) {
                    add("OCR lines: ${lines.size}")
                }
            },
        )
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

    private fun Map<String, String>.firstMatch(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            entries.firstOrNull { it.key.contains(key.lowercase()) }?.value
        }.orEmpty().trim()
    }

    private fun looksLikeHumanName(line: String): Boolean {
        if (line.length < 4 || line.contains(':') || line.contains('\uFF1A')) {
            return false
        }
        val hasLettersOrChinese = line.any { it.isLetter() || it.code in 0x4E00..0x9FFF }
        val hasSpacesOrSymbols = line.contains(' ') || line.contains('-') || line.contains('(')
        return hasLettersOrChinese && hasSpacesOrSymbols
    }

    private fun looksLikeModelCode(line: String): Boolean {
        if (line.length < 5 || line.contains(':') || line.contains('\uFF1A')) {
            return false
        }
        val alphaNumericCount = line.count { it.isLetterOrDigit() }
        return alphaNumericCount >= 5 && line.any { it == '-' || it.isDigit() }
    }

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
}
