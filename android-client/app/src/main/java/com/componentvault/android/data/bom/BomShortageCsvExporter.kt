package com.componentvault.android.data.bom

import java.nio.charset.StandardCharsets

object BomShortageCsvExporter {
    fun export(preview: BomReleasePreview): ByteArray {
        val rows = buildList {
            add(listOf("SKU", "型号", "需求数量", "当前库存", "缺料数量", "匹配状态"))
            preview.matchingLines.forEach { line ->
                val shortage = (line.requirement.requiredQuantity - line.availableQuantity).coerceAtLeast(0)
                val status = when {
                    line.componentId == null -> "未匹配"
                    shortage > 0 -> "缺料"
                    else -> "库存充足"
                }
                add(listOf(
                    line.requirement.sku.orEmpty(), line.requirement.model.orEmpty(),
                    line.requirement.requiredQuantity.toString(), line.availableQuantity.toString(),
                    shortage.toString(), status,
                ))
            }
        }
        val csv = rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::cell) } + "\r\n"
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(StandardCharsets.UTF_8)
    }

    fun shortageCount(preview: BomReleasePreview): Int = preview.matchingLines.count {
        it.componentId == null || it.availableQuantity < it.requirement.requiredQuantity
    }

    private fun cell(raw: String): String {
        val safe = if (raw.dropWhile(Char::isWhitespace).firstOrNull() in setOf('=', '+', '-', '@')) "'$raw" else raw
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
