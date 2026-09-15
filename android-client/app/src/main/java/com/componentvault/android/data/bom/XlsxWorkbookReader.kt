package com.componentvault.android.data.bom

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.SortedMap
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal data class XlsxWorkbook(
    val sheets: List<BomSheet>,
    val rowsBySheetName: Map<String, List<TabularRow>>,
)

internal object XlsxWorkbookReader {
    fun read(
        bytes: ByteArray,
        loadWorksheetRows: Boolean,
        selectedSheetName: String? = null,
        loadAllWorksheets: Boolean = false,
    ): XlsxWorkbook {
        val entries = unzipBounded(bytes)
        val workbookXml = entries["xl/workbook.xml"] ?: invalid("XLSX 缺少 xl/workbook.xml。")
        val relationshipsXml = entries["xl/_rels/workbook.xml.rels"]
            ?: invalid("XLSX 缺少 workbook relationships。")
        val sheetRefs = parseWorkbook(workbookXml)
        val relationships = parseRelationships(relationshipsXml)
        val sheets = sheetRefs.mapIndexed { index, ref -> BomSheet(ref.name, index, ref.hidden) }
        if (!loadWorksheetRows) return XlsxWorkbook(sheets, emptyMap())

        val selected = if (selectedSheetName == null) {
            sheetRefs.firstOrNull { !it.hidden } ?: sheetRefs.firstOrNull()
        } else {
            sheetRefs.firstOrNull { it.name == selectedSheetName }
        } ?: return XlsxWorkbook(sheets, emptyMap())
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val requested = if (loadAllWorksheets) sheetRefs else listOf(selected)
        val rowsByName = requested.associate { sheet ->
            val target = relationships[sheet.relationshipId]
                ?: invalid("工作表 ${sheet.name} 缺少 relationship。")
            val worksheetXml = entries[target]
                ?: invalid("XLSX 缺少工作表内容：${sheet.name}。")
            sheet.name to parseWorksheet(worksheetXml, sharedStrings, sheet.name)
        }
        return XlsxWorkbook(sheets, rowsByName)
    }

    private fun unzipBounded(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var entryCount = 0
        var totalBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > LocalImportLimits.MAX_ZIP_ENTRIES) zipLimit("XLSX ZIP entry 数超过限制。")
                    val normalizedName = normalizeEntryName(entry.name)
                    if (entry.size > LocalImportLimits.MAX_ZIP_ENTRY_BYTES) {
                        zipLimit("XLSX ZIP entry 过大：${entry.name}")
                    }
                    if (normalizedName in result) invalid("XLSX 包含重复 ZIP entry：$normalizedName")
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        if (entryBytes > LocalImportLimits.MAX_ZIP_ENTRY_BYTES) {
                            zipLimit("XLSX ZIP entry 解压后过大：${entry.name}")
                        }
                        if (totalBytes > LocalImportLimits.MAX_ZIP_TOTAL_BYTES) {
                            zipLimit("XLSX ZIP 总解压量超过限制。")
                        }
                        if (!entry.isDirectory) output.write(buffer, 0, count)
                    }
                    if (!entry.isDirectory) result[normalizedName] = output.toByteArray()
                    zip.closeEntry()
                }
            }
        } catch (error: LocalImportException) {
            throw error
        } catch (error: ZipException) {
            throw LocalImportException(
                LocalImportErrorCode.INVALID_XLSX,
                "XLSX ZIP 结构无效。",
                cause = error,
            )
        }
        return result
    }

    private fun normalizeEntryName(name: String): String {
        val replaced = name.replace('\\', '/')
        if (replaced.startsWith('/') || replaced.split('/').any { it == ".." }) {
            invalid("XLSX ZIP entry 路径无效。")
        }
        return replaced.removePrefix("./")
    }

    private fun parseWorkbook(xml: ByteArray): List<SheetRef> {
        val sheets = mutableListOf<SheetRef>()
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (elementName(localName, qName) != "sheet") return
                val name = attributes.valueByName("name").orEmpty()
                val relationshipId = attributes.valueByName("id").orEmpty()
                if (name.isBlank() || relationshipId.isBlank()) invalid("XLSX 工作表元数据无效。")
                sheets += SheetRef(
                    name = name,
                    relationshipId = relationshipId,
                    hidden = !attributes.valueByName("state").equals("visible", ignoreCase = true) &&
                        attributes.valueByName("state") != null,
                )
            }
        })
        if (sheets.isEmpty()) invalid("XLSX 不包含工作表。")
        if (sheets.map { it.name }.distinct().size != sheets.size) invalid("XLSX 包含重名工作表。")
        return sheets
    }

    private fun parseRelationships(xml: ByteArray): Map<String, String> {
        val relationships = mutableMapOf<String, String>()
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (elementName(localName, qName) != "Relationship") return
                val id = attributes.valueByName("Id").orEmpty()
                val target = attributes.valueByName("Target").orEmpty()
                val type = attributes.valueByName("Type").orEmpty()
                if (id.isBlank() || target.isBlank() || !type.endsWith("/worksheet")) return
                relationships[id] = resolveWorkbookTarget(target)
            }
        })
        return relationships
    }

    private fun resolveWorkbookTarget(target: String): String {
        val resolved = if (target.startsWith('/')) {
            target.removePrefix("/")
        } else {
            Paths.get("xl").resolve(target).normalize().toString().replace('\\', '/')
        }
        if (!resolved.startsWith("xl/") || resolved.split('/').any { it == ".." }) {
            invalid("XLSX worksheet relationship 路径越界。")
        }
        return resolved
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        var inStringItem = false
        var inText = false
        val current = StringBuilder()
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (elementName(localName, qName)) {
                    "si" -> {
                        inStringItem = true
                        current.clear()
                    }
                    "t" -> if (inStringItem) inText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inStringItem && inText) current.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (elementName(localName, qName)) {
                    "t" -> inText = false
                    "si" -> {
                        strings += current.toString()
                        if (strings.size > 100_000) zipLimit("XLSX shared strings 数量超过限制。")
                        inStringItem = false
                    }
                }
            }
        })
        return strings
    }

    private fun parseWorksheet(
        xml: ByteArray,
        sharedStrings: List<String>,
        sheetName: String,
    ): List<TabularRow> {
        val rows = mutableListOf<TabularRow>()
        var fallbackRowNumber = 0
        var currentRowNumber = 0
        var currentCells = sortedMapOf<Int, String>()
        var currentCellTypes = sortedMapOf<Int, String>()
        var currentCellColumn = -1
        var currentCellType = ""
        var currentCellHasFormula = false
        var inValue = false
        var inInlineText = false
        val currentValue = StringBuilder()
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when (elementName(localName, qName)) {
                    "row" -> {
                        fallbackRowNumber++
                        currentRowNumber = attributes.valueByName("r")?.toIntOrNull() ?: fallbackRowNumber
                        currentCells = sortedMapOf()
                        currentCellTypes = sortedMapOf()
                    }
                    "c" -> {
                        currentCellColumn = columnIndex(attributes.valueByName("r").orEmpty())
                        currentCellType = attributes.valueByName("t").orEmpty()
                        currentCellHasFormula = false
                        currentValue.clear()
                    }
                    "f" -> if (currentCellColumn >= 0) currentCellHasFormula = true
                    "v" -> inValue = currentCellColumn >= 0
                    "t" -> if (currentCellColumn >= 0 && currentCellType == "inlineStr") inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) currentValue.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (elementName(localName, qName)) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        if (currentCellColumn >= 0) {
                            if (currentCellHasFormula) {
                                invalid("工作表 $sheetName 包含公式单元格；请粘贴为值后再导入。")
                            }
                            val rawValue = currentValue.toString()
                            val value = when (currentCellType) {
                                "s" -> rawValue.toIntOrNull()?.let(sharedStrings::getOrNull)
                                    ?: invalid("工作表 $sheetName 包含无效 shared string 索引。")
                                "b" -> if (rawValue == "1") "TRUE" else "FALSE"
                                else -> rawValue
                            }
                            currentCells[currentCellColumn] = value
                            currentCellTypes[currentCellColumn] = currentCellType
                        }
                        currentCellColumn = -1
                        currentValue.clear()
                    }
                    "row" -> {
                        if (currentCells.values.any(String::isNotBlank)) {
                            val width = (currentCells.lastKeyOrNull() ?: -1) + 1
                            rows += TabularRow(
                                currentRowNumber,
                                List(width) { column -> currentCells[column].orEmpty() },
                                List(width) { column -> currentCellTypes[column].orEmpty() },
                            )
                            if (rows.size > LocalImportLimits.MAX_DATA_ROWS + 1) {
                                throw RowLimitSaxException()
                            }
                        }
                    }
                }
            }
        }, sheetName)
        return rows
    }

    private fun columnIndex(reference: String): Int {
        var value = 0
        var letters = 0
        reference.takeWhile(Char::isLetter).forEach { char ->
            val upper = char.uppercaseChar()
            if (upper !in 'A'..'Z') return -1
            value = value * 26 + (upper - 'A' + 1)
            letters++
            if (value > 16_384) invalid("XLSX 单元格列超出支持范围。")
        }
        return if (letters == 0) -1 else value - 1
    }

    private fun parseXml(xml: ByteArray, handler: DefaultHandler, sheetName: String? = null) {
        val ascii = String(xml, StandardCharsets.ISO_8859_1)
        if (ascii.contains("<!DOCTYPE", ignoreCase = true) || ascii.contains("<!ENTITY", ignoreCase = true)) {
            invalid("XLSX XML 不允许 DTD 或 entity。")
        }
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val reader = factory.newSAXParser().xmlReader
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(ByteArrayInputStream(xml)))
        } catch (error: RowLimitSaxException) {
            throw LocalImportException(
                LocalImportErrorCode.TOO_MANY_ROWS,
                "BOM 数据行超过 5000 行限制。",
                sheetName,
                cause = error,
            )
        } catch (error: LocalImportException) {
            throw error
        } catch (error: Exception) {
            throw LocalImportException(
                LocalImportErrorCode.INVALID_XLSX,
                "XLSX XML 结构无效。",
                sheetName,
                cause = error,
            )
        }
    }

    private fun Attributes.valueByName(name: String): String? {
        for (index in 0 until length) {
            val local = getLocalName(index).ifBlank { getQName(index).substringAfter(':') }
            if (local == name) return getValue(index)
        }
        return null
    }

    private fun elementName(localName: String?, qName: String?): String =
        localName?.takeIf(String::isNotBlank) ?: qName.orEmpty().substringAfter(':')

    private fun <K, V> SortedMap<K, V>.lastKeyOrNull(): K? = if (isEmpty()) null else lastKey()

    private fun invalid(message: String): Nothing = throw LocalImportException(
        LocalImportErrorCode.INVALID_XLSX,
        message,
    )

    private fun zipLimit(message: String): Nothing = throw LocalImportException(
        LocalImportErrorCode.ZIP_LIMIT_EXCEEDED,
        message,
    )

    private data class SheetRef(val name: String, val relationshipId: String, val hidden: Boolean)
    private class RowLimitSaxException : SAXException()
}
