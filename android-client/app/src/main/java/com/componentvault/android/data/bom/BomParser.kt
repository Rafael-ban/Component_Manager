package com.componentvault.android.data.bom

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

object BomParser {
    fun parseCsv(
        bytes: ByteArray,
        projectName: String,
        productionSets: Int,
    ): BomParseResult {
        validateRequest(bytes, projectName, productionSets)
        val text = decodeUtf8(bytes).removePrefix("\uFEFF")
        val rows = CsvTableReader.read(text)
        val sheet = BomSheet(name = "CSV", index = 0)
        val requirements = BomRowsMapper.map(
            rows = rows.mapIndexed { index, values -> TabularRow(index + 1, values) },
            sheetName = sheet.name,
            productionSets = productionSets,
        )
        return BomParseResult(
            projectName = projectName.trim(),
            productionSets = productionSets,
            availableSheets = listOf(sheet),
            selectedSheet = sheet,
            requirements = requirements,
            fileSha256 = ImportHash.sha256(bytes),
        )
    }

    fun listXlsxSheets(bytes: ByteArray): List<BomSheet> {
        checkFileSize(bytes)
        return XlsxWorkbookReader.read(bytes, loadWorksheetRows = false).sheets
    }

    fun parseXlsx(
        bytes: ByteArray,
        projectName: String,
        productionSets: Int,
        sheetName: String? = null,
    ): BomParseResult {
        validateRequest(bytes, projectName, productionSets)
        val workbook = XlsxWorkbookReader.read(
            bytes,
            loadWorksheetRows = true,
            selectedSheetName = sheetName,
        )
        val selectedSheet = if (sheetName == null) {
            workbook.sheets.firstOrNull { !it.hidden } ?: workbook.sheets.firstOrNull()
        } else {
            workbook.sheets.firstOrNull { it.name == sheetName }
        } ?: throw LocalImportException(
            LocalImportErrorCode.SHEET_NOT_FOUND,
            if (sheetName == null) "XLSX 中没有可用工作表。" else "找不到工作表：$sheetName",
            sheetName = sheetName,
        )
        val rows = workbook.rowsBySheetName[selectedSheet.name].orEmpty()
        return BomParseResult(
            projectName = projectName.trim(),
            productionSets = productionSets,
            availableSheets = workbook.sheets,
            selectedSheet = selectedSheet,
            requirements = BomRowsMapper.map(rows, selectedSheet.name, productionSets),
            fileSha256 = ImportHash.sha256(bytes),
        )
    }

    private fun validateRequest(bytes: ByteArray, projectName: String, productionSets: Int) {
        checkFileSize(bytes)
        if (projectName.isBlank()) {
            throw LocalImportException(
                LocalImportErrorCode.INVALID_PROJECT_NAME,
                "项目名不能为空。",
            )
        }
        if (productionSets <= 0) {
            throw LocalImportException(
                LocalImportErrorCode.INVALID_POSITIVE_INTEGER,
                "生产套数必须是正整数。",
            )
        }
    }

    internal fun checkFileSize(bytes: ByteArray) {
        if (bytes.size > LocalImportLimits.MAX_FILE_BYTES) {
            throw LocalImportException(
                LocalImportErrorCode.FILE_TOO_LARGE,
                "导入文件超过 10 MiB 限制。",
            )
        }
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
            "CSV 必须使用 UTF-8 编码。",
            cause = error,
        )
    }
}

internal data class TabularRow(
    val rowNumber: Int,
    val values: List<String>,
    val cellTypes: List<String> = emptyList(),
)

private object CsvTableReader {
    fun read(text: String): List<List<String>> {
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == delimiter -> {
                    row.add(field.toString())
                    field.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) {
            throw LocalImportException(LocalImportErrorCode.INVALID_CSV, "CSV 包含未闭合的引号。")
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun detectDelimiter(text: String): Char {
        val firstRecord = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return listOf(',', '\t', ';').maxByOrNull { candidate ->
            firstRecord.count { it == candidate }
        } ?: ','
    }
}

internal object BomRowsMapper {
    private val aliases = mapOf(
        ColumnKind.SKU to setOf(
            "sku", "料号", "立创编号", "物料编码", "物料编号", "产品编码", "productcode",
            "partnumber", "lcscpart", "supplierpart", "supplierpartnumber",
        ),
        ColumnKind.MODEL to setOf(
            "model", "mpn", "型号", "制造商料号", "manufacturerpart", "manufacturerpartnumber",
        ),
        ColumnKind.PACKAGE to setOf("package", "packagecase", "封装", "封装规格", "footprint", "encapstandard"),
        ColumnKind.QUANTITY to setOf("qty", "quantity", "数量", "用量", "单套用量", "需求数量"),
        ColumnKind.NAME to setOf("name", "名称", "品名", "元件名称", "description", "描述"),
    )

    fun map(rows: List<TabularRow>, sheetName: String, productionSets: Int): List<BomRequirement> {
        val headerIndex = rows.indexOfFirst { row -> row.values.any(String::isNotBlank) }
        if (headerIndex < 0) {
            throw LocalImportException(
                LocalImportErrorCode.MISSING_REQUIRED_COLUMN,
                "工作表没有表头。",
                sheetName,
            )
        }
        val headers = rows[headerIndex].values.map(String::trim)
        val columns = buildMap {
            headers.forEachIndexed { index, header ->
                val normalized = normalizeHeader(header)
                aliases.entries.firstOrNull { normalized in it.value }?.key?.let { kind ->
                    putIfAbsent(kind, index)
                }
            }
        }
        if (columns[ColumnKind.QUANTITY] == null) missingColumn(sheetName, "数量/qty")
        if (columns[ColumnKind.SKU] == null && columns[ColumnKind.MODEL] == null) {
            missingColumn(sheetName, "料号/SKU/product code 或 型号/MPN/model")
        }

        val dataRows = rows.drop(headerIndex + 1).filter { row -> row.values.any(String::isNotBlank) }
        if (dataRows.size > LocalImportLimits.MAX_DATA_ROWS) {
            throw LocalImportException(
                LocalImportErrorCode.TOO_MANY_ROWS,
                "BOM 数据行超过 5000 行限制。",
                sheetName,
            )
        }

        data class Accumulator(
            var sku: String?,
            var name: String?,
            var model: String?,
            var packageName: String?,
            var quantityPerSet: Int,
            val sourceRows: MutableList<BomSourceRow>,
        )

        val grouped = linkedMapOf<String, Accumulator>()
        dataRows.forEach { row ->
            fun value(kind: ColumnKind): String? = columns[kind]
                ?.let { row.values.getOrNull(it) }
                ?.trim()
                ?.takeIf(String::isNotBlank)

            val sku = value(ColumnKind.SKU)
            val model = value(ColumnKind.MODEL)
            val packageName = value(ColumnKind.PACKAGE)
            val name = value(ColumnKind.NAME)
            val quantityText = value(ColumnKind.QUANTITY).orEmpty()
            val quantity = parsePositiveInt(quantityText, sheetName, row.rowNumber, "数量")
            val identity = when {
                !sku.isNullOrBlank() -> "sku:${sku.normalizedIdentityPart()}"
                !model.isNullOrBlank() -> "model:${model.normalizedIdentityPart()}|package:${packageName.orEmpty().normalizedIdentityPart()}"
                else -> throw LocalImportException(
                    LocalImportErrorCode.MISSING_REQUIRED_COLUMN,
                    "第 ${row.rowNumber} 行缺少料号和型号，无法确定元件身份。",
                    sheetName,
                    row.rowNumber,
                )
            }
            val sourceFields = buildMap {
                headers.forEachIndexed { index, header ->
                    if (header.isNotBlank()) put(header, row.values.getOrNull(index).orEmpty().trim())
                }
            }
            val source = BomSourceRow(sheetName, row.rowNumber, quantity, sourceFields)
            val existing = grouped[identity]
            if (existing == null) {
                grouped[identity] = Accumulator(
                    sku,
                    name,
                    model,
                    packageName,
                    quantity,
                    mutableListOf(source),
                )
            } else {
                existing.quantityPerSet = addExact(
                    existing.quantityPerSet,
                    quantity,
                    sheetName,
                    row.rowNumber,
                )
                existing.sourceRows.add(source)
                existing.sku = existing.sku ?: sku
                existing.name = existing.name ?: name
                existing.model = existing.model ?: model
                existing.packageName = existing.packageName ?: packageName
            }
        }

        return grouped.map { (identity, item) ->
            BomRequirement(
                identity = BomRequirementIdentity(identity),
                sku = item.sku,
                name = item.name,
                model = item.model,
                packageName = item.packageName,
                quantityPerSet = item.quantityPerSet,
                requiredQuantity = multiplyExact(item.quantityPerSet, productionSets, sheetName),
                sourceRows = item.sourceRows.toList(),
            )
        }
    }

    private fun normalizeHeader(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_\\-/().#]+"), "")

    private fun missingColumn(sheetName: String, label: String): Nothing = throw LocalImportException(
        LocalImportErrorCode.MISSING_REQUIRED_COLUMN,
        "工作表缺少必需列：$label。",
        sheetName,
    )

    private fun parsePositiveInt(value: String, sheetName: String, rowNumber: Int, label: String): Int {
        if (!value.matches(Regex("[1-9]\\d*"))) {
            throw LocalImportException(
                LocalImportErrorCode.INVALID_POSITIVE_INTEGER,
                "第 $rowNumber 行${label}必须是正整数。",
                sheetName,
                rowNumber,
            )
        }
        return value.toIntOrNull() ?: throw LocalImportException(
            LocalImportErrorCode.QUANTITY_OVERFLOW,
            "第 $rowNumber 行${label}超过整数范围。",
            sheetName,
            rowNumber,
        )
    }

    private fun addExact(left: Int, right: Int, sheetName: String, rowNumber: Int): Int = try {
        Math.addExact(left, right)
    } catch (error: ArithmeticException) {
        throw LocalImportException(
            LocalImportErrorCode.QUANTITY_OVERFLOW,
            "第 $rowNumber 行合并后的单套需求数量超过整数范围。",
            sheetName,
            rowNumber,
            error,
        )
    }

    private fun multiplyExact(quantity: Int, sets: Int, sheetName: String): Int = try {
        Math.multiplyExact(quantity, sets)
    } catch (error: ArithmeticException) {
        throw LocalImportException(
            LocalImportErrorCode.QUANTITY_OVERFLOW,
            "BOM 需求数量乘以生产套数后超过整数范围。",
            sheetName,
            cause = error,
        )
    }

    private enum class ColumnKind { SKU, MODEL, PACKAGE, QUANTITY, NAME }
}
