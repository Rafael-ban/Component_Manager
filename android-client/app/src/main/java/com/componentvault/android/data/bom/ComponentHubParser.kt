package com.componentvault.android.data.bom

import com.componentvault.android.model.trustedProductImageUrl
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class ComponentHubDuplicatePolicy {
    BLOCK,
    SKIP,
}

data class ComponentHubSourceFields(
    val sourceId: String?,
    val productCode: String?,
    val name: String,
    val model: String?,
    val brand: String?,
    val encapStandard: String?,
    val category: String?,
    val subCategory: String?,
    val stock: Int,
    val threshold: Int,
    val location: String?,
    val notes: String?,
    val params: String?,
    val value: String?,
    val price: String?,
    val datasheet: String?,
    val image: String?,
    val trustedImageUrl: String?,
    val rawJson: String,
)

data class ComponentHubComponent(
    val sourceIndex: Int,
    val sku: String,
    val name: String,
    val model: String?,
    val brand: String?,
    val packageName: String,
    val category: String,
    val subCategory: String?,
    val quantity: Int,
    val minStock: Int,
    val location: String,
    val notes: List<String>,
    val sourceFields: ComponentHubSourceFields,
)

enum class ComponentHubConflictKind {
    DUPLICATE_IN_FILE,
    DUPLICATE_IN_INVENTORY,
}

data class ComponentHubSkuConflict(
    val sourceIndex: Int,
    val sku: String,
    val kind: ComponentHubConflictKind,
    val skipped: Boolean,
    val message: String,
)

data class ComponentHubRecordIssue(
    val sourceIndex: Int,
    val message: String,
)

data class ComponentHubParseResult(
    val fileSha256: String,
    val components: List<ComponentHubComponent>,
    val conflicts: List<ComponentHubSkuConflict>,
    val issues: List<ComponentHubRecordIssue>,
    val skippedDuplicateCount: Int,
) {
    val canConfirm: Boolean
        get() = components.isNotEmpty() && issues.isEmpty() && conflicts.none { !it.skipped }
}

object ComponentHubParser {
    fun parse(
        bytes: ByteArray,
        duplicatePolicy: ComponentHubDuplicatePolicy = ComponentHubDuplicatePolicy.BLOCK,
        existingSkus: Set<String> = emptySet(),
    ): ComponentHubParseResult {
        BomParser.checkFileSize(bytes)
        val text = decodeUtf8(bytes).removePrefix("\uFEFF")
        val root = runCatching { RawJsonParser(text).parse() }.getOrElse { error ->
            if (error is LocalImportException) throw error
            throw LocalImportException(
                LocalImportErrorCode.INVALID_COMPONENT_HUB_JSON,
                "Component Hub 备份不是有效 JSON。",
                cause = error,
            )
        } as? RawJsonObject ?: throw LocalImportException(
            LocalImportErrorCode.INVALID_COMPONENT_HUB_JSON,
            "Component Hub 备份根节点必须是对象。",
        )
        val componentsNode = root.property("components") as? RawJsonArray
            ?: throw LocalImportException(
                LocalImportErrorCode.INVALID_COMPONENT_HUB_JSON,
                "Component Hub 备份根对象必须包含 components 数组。",
            )
        if (componentsNode.items.size > LocalImportLimits.MAX_DATA_ROWS) {
            throw LocalImportException(
                LocalImportErrorCode.TOO_MANY_ROWS,
                "Component Hub 记录超过 5000 条限制。",
            )
        }

        val known = existingSkus.mapTo(mutableSetOf()) { it.normalizedIdentityPart() }
        val seen = mutableSetOf<String>()
        val accepted = mutableListOf<ComponentHubComponent>()
        val conflicts = mutableListOf<ComponentHubSkuConflict>()
        val issues = mutableListOf<ComponentHubRecordIssue>()

        componentsNode.items.forEachIndexed { zeroBasedIndex, node ->
            val sourceIndex = zeroBasedIndex + 1
            val item = node as? RawJsonObject
            if (item == null) {
                issues += ComponentHubRecordIssue(sourceIndex, "第 $sourceIndex 条记录不是对象。")
                return@forEachIndexed
            }
            val name = item.string("name").trim()
            val productCode = item.string("productCode").trim()
            val stock = item.int("stock")
            val threshold = item.int("threshold")
            if (name.isBlank()) {
                issues += ComponentHubRecordIssue(sourceIndex, "第 $sourceIndex 条记录缺少名称。")
                return@forEachIndexed
            }
            if (stock == null || stock < 0 || threshold == null || threshold < 0) {
                issues += ComponentHubRecordIssue(sourceIndex, "第 $sourceIndex 条记录的库存或阈值不是非负整数。")
                return@forEachIndexed
            }

            val sku = productCode.ifBlank { fallbackSku(item) }
            val normalizedSku = sku.normalizedIdentityPart()
            val conflictKind = when {
                normalizedSku in known -> ComponentHubConflictKind.DUPLICATE_IN_INVENTORY
                !seen.add(normalizedSku) -> ComponentHubConflictKind.DUPLICATE_IN_FILE
                else -> null
            }
            if (conflictKind != null) {
                val skipped = duplicatePolicy == ComponentHubDuplicatePolicy.SKIP
                conflicts += ComponentHubSkuConflict(
                    sourceIndex = sourceIndex,
                    sku = sku,
                    kind = conflictKind,
                    skipped = skipped,
                    message = "第 $sourceIndex 条记录的 SKU $sku 重复；请选择明确跳过策略后再导入。",
                )
                return@forEachIndexed
            }

            val model = item.string("model").trim().ifBlank { null }
            val brand = item.string("brand").trim().ifBlank { null }
            val encapStandard = item.string("encapStandard").trim().ifBlank { null }
            val categoryCandidates = listOf(
                item.string("parentCatalogName"),
                item.string("catalogName"),
                item.string("categoryName"),
                item.string("category"),
            ).map(String::trim).filter(String::isNotBlank)
            val originalCategory = categoryCandidates.firstOrNull()
            val category = normalizeCategory(
                categoryCandidates.firstOrNull(::containsChinese) ?: originalCategory.orEmpty(),
            )
            val subCategory = item.string("subCategory").trim().ifBlank { null }
            val location = item.string("location").trim().ifBlank { null }
            val sourceNotes = item.string("notes").trim().ifBlank { null }
            val params = item.readable("params")
            val value = item.readable("value")
            val price = item.readable("price")
            val datasheet = item.readable("datasheet")
            val originalImage = item.readable("image")
            val trustedImage = trustedProductImageUrl(item.string("image"))
            val notes = buildList {
                item.readable("id")?.let { add("原ID：$it") }
                productCode.takeIf(String::isNotBlank)?.let { add("原料号：$it") }
                model?.let { add("型号：$it") }
                brand?.let { add("品牌：$it") }
                originalCategory?.let { add("原分类：$it") }
                subCategory?.let { add("子分类：$it") }
                params?.let { add("参数：$it") }
                value?.let { add("值：$it") }
                price?.let { add("价格：$it") }
                datasheet?.let { add("数据手册：$it") }
                sourceNotes?.let { add("原备注：$it") }
                originalImage?.let { add("原图片：$it") }
                trustedImage?.let { add("商品图片：$it") }
                add("来源JSON：${boundedRawJson(item.raw)}")
            }
            accepted += ComponentHubComponent(
                sourceIndex = sourceIndex,
                sku = sku,
                name = name,
                model = model,
                brand = brand,
                packageName = encapStandard ?: "未知封装",
                category = category,
                subCategory = subCategory,
                quantity = stock,
                minStock = threshold,
                location = location ?: "待整理",
                notes = notes,
                sourceFields = ComponentHubSourceFields(
                    sourceId = item.string("id").trim().ifBlank { null },
                    productCode = productCode.ifBlank { null },
                    name = name,
                    model = model,
                    brand = brand,
                    encapStandard = encapStandard,
                    category = originalCategory,
                    subCategory = subCategory,
                    stock = stock,
                    threshold = threshold,
                    location = location,
                    notes = sourceNotes,
                    params = params,
                    value = value,
                    price = price,
                    datasheet = datasheet,
                    image = originalImage,
                    trustedImageUrl = trustedImage,
                    rawJson = item.raw,
                ),
            )
        }

        return ComponentHubParseResult(
            fileSha256 = ImportHash.sha256(bytes),
            components = accepted,
            conflicts = conflicts,
            issues = issues,
            skippedDuplicateCount = conflicts.count(ComponentHubSkuConflict::skipped),
        )
    }

    private fun containsChinese(value: String): Boolean = value.any { it in '\u3400'..'\u9FFF' }

    private fun normalizeCategory(value: String): String = when (value.trim().lowercase()) {
        "resistor", "resistors" -> "电阻"
        "capacitor", "capacitors" -> "电容"
        "inductor", "inductors" -> "电感"
        "diode", "diodes" -> "二极管"
        "transistor", "transistors" -> "晶体管"
        "ic", "integrated-circuit", "integrated-circuits" -> "集成电路"
        "connector", "connectors" -> "连接器"
        "sensor", "sensors" -> "传感器"
        "" -> "未分类"
        else -> value.trim()
    }

    private fun boundedRawJson(raw: String): String =
        if (raw.length <= 2_048) raw else raw.take(2_048) + "…（已截断）"

    private fun fallbackSku(item: RawJsonObject): String {
        val sourceId = when (val id = item.property("id")) {
            is RawJsonString -> id.value.trim()
            is RawJsonNumber -> id.raw.trim()
            else -> ""
        }
        val stableSource = if (sourceId.isNotEmpty()) {
            "id:$sourceId"
        } else {
            item.properties
                .asSequence()
                .filter { it.first != "createdAt" && it.first != "updatedAt" }
                .sortedBy { it.first }
                .joinToString("|") { (name, value) -> "$name:${value.raw}" }
        }
        return "CH-${ImportHash.sha256(stableSource.toByteArray(StandardCharsets.UTF_8)).take(12)}"
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw LocalImportException(
            LocalImportErrorCode.INVALID_TEXT_ENCODING,
            "Component Hub JSON 必须使用 UTF-8 编码。",
            cause = error,
        )
    }
}

private sealed class RawJsonValue(open val raw: String)
private data class RawJsonObject(
    val properties: List<Pair<String, RawJsonValue>>,
    override val raw: String,
) : RawJsonValue(raw) {
    fun property(name: String): RawJsonValue? = properties.firstOrNull { it.first == name }?.second
    fun string(name: String): String = (property(name) as? RawJsonString)?.value.orEmpty()
    fun int(name: String): Int? = (property(name) as? RawJsonNumber)?.raw?.toIntOrNull()
    fun readable(name: String): String? = when (val value = property(name)) {
        is RawJsonString -> value.value.trim().ifBlank { null }
        null -> null
        else -> value.raw.trim().ifBlank { null }
    }
}
private data class RawJsonArray(val items: List<RawJsonValue>, override val raw: String) : RawJsonValue(raw)
private data class RawJsonString(val value: String, override val raw: String) : RawJsonValue(raw)
private data class RawJsonNumber(override val raw: String) : RawJsonValue(raw)
private data class RawJsonLiteral(override val raw: String) : RawJsonValue(raw)

private class RawJsonParser(private val source: String) {
    private var position = 0

    fun parse(): RawJsonValue {
        skipWhitespace()
        val value = parseValue(depth = 0)
        skipWhitespace()
        requireJson(position == source.length, "JSON 根节点后存在多余内容。")
        return value
    }

    private fun parseValue(depth: Int): RawJsonValue {
        requireJson(depth <= 32, "JSON 嵌套层级超过 32 层限制。")
        skipWhitespace()
        requireJson(position < source.length, "JSON 意外结束。")
        return when (source[position]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> {
                val start = position
                RawJsonString(parseString(), source.substring(start, position))
            }
            't' -> parseLiteral("true")
            'f' -> parseLiteral("false")
            'n' -> parseLiteral("null")
            '-', in '0'..'9' -> parseNumber()
            else -> invalid("JSON 在位置 $position 包含无效值。")
        }
    }

    private fun parseObject(depth: Int): RawJsonObject {
        val start = position
        position++
        val properties = mutableListOf<Pair<String, RawJsonValue>>()
        skipWhitespace()
        if (consume('}')) return RawJsonObject(properties, source.substring(start, position))
        while (true) {
            skipWhitespace()
            requireJson(position < source.length && source[position] == '"', "JSON 对象属性名必须是字符串。")
            val name = parseString()
            skipWhitespace()
            requireJson(consume(':'), "JSON 对象属性后缺少冒号。")
            val value = parseValue(depth + 1)
            properties += name to value
            skipWhitespace()
            if (consume('}')) break
            requireJson(consume(','), "JSON 对象属性之间缺少逗号。")
        }
        return RawJsonObject(properties, source.substring(start, position))
    }

    private fun parseArray(depth: Int): RawJsonArray {
        val start = position
        position++
        val items = mutableListOf<RawJsonValue>()
        skipWhitespace()
        if (consume(']')) return RawJsonArray(items, source.substring(start, position))
        while (true) {
            items += parseValue(depth + 1)
            skipWhitespace()
            if (consume(']')) break
            requireJson(consume(','), "JSON 数组元素之间缺少逗号。")
        }
        return RawJsonArray(items, source.substring(start, position))
    }

    private fun parseString(): String {
        requireJson(consume('"'), "JSON 字符串缺少开始引号。")
        val result = StringBuilder()
        while (position < source.length) {
            val char = source[position++]
            when {
                char == '"' -> return result.toString()
                char == '\\' -> {
                    requireJson(position < source.length, "JSON 字符串转义不完整。")
                    when (val escaped = source[position++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            requireJson(position + 4 <= source.length, "JSON Unicode 转义不完整。")
                            val digits = source.substring(position, position + 4)
                            val codePoint = digits.toIntOrNull(16) ?: invalid("JSON Unicode 转义无效。")
                            result.append(codePoint.toChar())
                            position += 4
                        }
                        else -> invalid("JSON 字符串包含无效转义：\\$escaped")
                    }
                }
                char.code < 0x20 -> invalid("JSON 字符串包含控制字符。")
                else -> result.append(char)
            }
        }
        invalid("JSON 字符串缺少结束引号。")
    }

    private fun parseNumber(): RawJsonNumber {
        val start = position
        consume('-')
        if (consume('0')) {
            requireJson(position >= source.length || !source[position].isDigit(), "JSON 数字不能包含前导零。")
        } else {
            requireJson(position < source.length && source[position] in '1'..'9', "JSON 数字无效。")
            while (position < source.length && source[position].isDigit()) position++
        }
        if (consume('.')) {
            requireJson(position < source.length && source[position].isDigit(), "JSON 小数部分无效。")
            while (position < source.length && source[position].isDigit()) position++
        }
        if (position < source.length && source[position].lowercaseChar() == 'e') {
            position++
            if (position < source.length && source[position] in setOf('+', '-')) position++
            requireJson(position < source.length && source[position].isDigit(), "JSON 指数部分无效。")
            while (position < source.length && source[position].isDigit()) position++
        }
        return RawJsonNumber(source.substring(start, position))
    }

    private fun parseLiteral(expected: String): RawJsonLiteral {
        requireJson(source.regionMatches(position, expected, 0, expected.length), "JSON 字面量无效。")
        position += expected.length
        return RawJsonLiteral(expected)
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in setOf(' ', '\t', '\r', '\n')) position++
    }

    private fun consume(expected: Char): Boolean {
        if (position >= source.length || source[position] != expected) return false
        position++
        return true
    }

    private fun requireJson(condition: Boolean, message: String) {
        if (!condition) invalid(message)
    }

    private fun invalid(message: String): Nothing = throw LocalImportException(
        LocalImportErrorCode.INVALID_COMPONENT_HUB_JSON,
        message,
    )
}
