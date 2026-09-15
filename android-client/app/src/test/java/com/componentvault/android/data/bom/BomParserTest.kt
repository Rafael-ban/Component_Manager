package com.componentvault.android.data.bom

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BomParserTest {
    @Test
    fun parsesChineseCsvAndAggregatesDuplicateRowsBeforeMultiplyingSets() {
        val csv = """
            料号,名称,型号,封装,数量
            C1001,贴片电阻,RC0603FR-0710KL,0603,2
            c1001,贴片电阻,RC0603FR-0710KL,0603,3
        """.trimIndent().toByteArray()

        val result = BomParser.parseCsv(csv, projectName = "控制板", productionSets = 4)

        assertEquals("控制板", result.projectName)
        assertEquals(1, result.requirements.size)
        assertEquals(5, result.requirements.single().quantityPerSet)
        assertEquals(20, result.requirements.single().requiredQuantity)
        assertEquals(listOf(2, 3), result.requirements.single().sourceRows.map { it.rowNumber })
        assertEquals(64, result.fileSha256.length)
    }

    @Test
    fun parsesEnglishAliasesAndQuotedCsv() {
        val csv = """
            Product Code,Name,MPN,Package,Qty
            ,"Capacitor, ceramic",GRM188R71H104KA93D,0603,7
        """.trimIndent().toByteArray()

        val item = BomParser.parseCsv(csv, "Sensor", 2).requirements.single()

        assertNull(item.sku)
        assertEquals("Capacitor, ceramic", item.name)
        assertEquals("GRM188R71H104KA93D", item.model)
        assertEquals(14, item.requiredQuantity)
    }

    @Test
    fun rejectsNonPositiveFractionalAndOverflowingQuantities() {
        listOf("0", "-1", "1.5", "+1").forEach { quantity ->
            val error = assertFailsWith<LocalImportException> {
                BomParser.parseCsv("SKU,Qty\nC1,$quantity".toByteArray(), "P", 1)
            }
            assertEquals(LocalImportErrorCode.INVALID_POSITIVE_INTEGER, error.code)
        }

        val overflow = assertFailsWith<LocalImportException> {
            BomParser.parseCsv("SKU,Qty\nC1,2147483647".toByteArray(), "P", 2)
        }
        assertEquals(LocalImportErrorCode.QUANTITY_OVERFLOW, overflow.code)
    }

    @Test
    fun matcherPrefersSkuThenExposesModelPackageAmbiguity() {
        val csv = """
            SKU,MPN,Package,Qty
            A-1,M1,0603,1
            ,M2,SOT-23,1
        """.trimIndent().toByteArray()
        val requirements = BomParser.parseCsv(csv, "P", 1).requirements
        val inventory = listOf(
            InventoryMatchCandidate("one", "A-1", "different", "other"),
            InventoryMatchCandidate("two", "B-1", "M2", "SOT-23"),
            InventoryMatchCandidate("three", "B-2", "m2", "sot-23"),
        )

        val matches = BomInventoryMatcher.match(requirements, inventory)

        assertEquals(BomMatchKind.EXACT_SKU, matches[0].kind)
        assertEquals("one", matches[0].selectedInventoryId)
        assertEquals(BomMatchKind.EXACT_MODEL_AND_PACKAGE, matches[1].kind)
        assertTrue(matches[1].isAmbiguous)
        assertNull(matches[1].selectedInventoryId)
    }

    @Test
    fun suppliedSkuNeverFallsBackToModelAndInactiveItemsAreIgnored() {
        val requirement = BomParser.parseCsv(
            "LCSC Part#,Manufacturer Part,Package,Qty\nC404,M1,0603,1".toByteArray(),
            "P",
            1,
        ).requirements.single()
        val matches = BomInventoryMatcher.match(
            listOf(requirement),
            listOf(
                InventoryMatchCandidate("model", "OTHER", "M1", "0603"),
                InventoryMatchCandidate("inactive", "C404", "M1", "0603", active = false),
            ),
        )

        assertEquals("C404", requirement.sku)
        assertEquals("M1", requirement.model)
        assertEquals(BomMatchKind.NONE, matches.single().kind)
        assertTrue(matches.single().candidates.isEmpty())
    }

    @Test
    fun parsesSelectedXlsxSheetUsingSharedStrings() {
        val xlsx = workbook(
            sheets = linkedMapOf(
                "Ignore" to worksheet(listOf(listOf("SKU", "Qty"), listOf("C1", "1"))),
                "Production" to worksheet(
                    listOf(
                        listOf("料号", "型号", "封装", "数量"),
                        listOf("C88", "STM32", "LQFP-48", "4"),
                    ),
                ),
            ),
        )

        assertEquals(listOf("Ignore", "Production"), BomParser.listXlsxSheets(xlsx).map { it.name })
        val result = BomParser.parseXlsx(xlsx, "Main board", 3, "Production")

        assertEquals("Production", result.selectedSheet.name)
        assertEquals("C88", result.requirements.single().sku)
        assertEquals(12, result.requirements.single().requiredQuantity)
    }

    @Test
    fun xlsxRejectsMoreThanFiveThousandDataRows() {
        val rows = buildList {
            add(listOf("SKU", "Qty"))
            repeat(LocalImportLimits.MAX_DATA_ROWS + 1) { index -> add(listOf("C$index", "1")) }
        }
        val xlsx = workbook(linkedMapOf("BOM" to worksheet(rows)))

        val error = assertFailsWith<LocalImportException> {
            BomParser.parseXlsx(xlsx, "P", 1)
        }

        assertEquals(LocalImportErrorCode.TOO_MANY_ROWS, error.code)
    }

    @Test
    fun xlsxRejectsHighlyExpandedZipEntry() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            val block = ByteArray(8_192) { 'A'.code.toByte() }
            repeat(LocalImportLimits.MAX_ZIP_ENTRY_BYTES / block.size + 1) { zip.write(block) }
            zip.closeEntry()
        }
        assertTrue(output.size() < LocalImportLimits.MAX_FILE_BYTES)

        val error = assertFailsWith<LocalImportException> { BomParser.listXlsxSheets(output.toByteArray()) }

        assertEquals(LocalImportErrorCode.ZIP_LIMIT_EXCEEDED, error.code)
    }

    private fun workbook(sheets: LinkedHashMap<String, String>): ByteArray {
        val sharedValues = sheets.values
            .flatMap { xml -> Regex("<value>(.*?)</value>").findAll(xml).map { it.groupValues[1] }.toList() }
            .distinct()
        val indexes = sharedValues.withIndex().associate { it.value to it.index }
        val workbookXml = buildString {
            append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
            append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
            sheets.keys.forEachIndexed { index, name ->
                append("<sheet name=\"").append(name).append("\" sheetId=\"").append(index + 1)
                    .append("\" r:id=\"rId").append(index + 1).append("\"/>")
            }
            append("</sheets></workbook>")
        }
        val rels = buildString {
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            sheets.keys.forEachIndexed { index, _ ->
                append("<Relationship Id=\"rId").append(index + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(index + 1).append(".xml\"/>")
            }
            append("</Relationships>")
        }
        val sharedStrings = buildString {
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            sharedValues.forEach { value -> append("<si><t>").append(xmlEscape(value)).append("</t></si>") }
            append("</sst>")
        }
        return zip(
            buildMap {
                put("xl/workbook.xml", workbookXml)
                put("xl/_rels/workbook.xml.rels", rels)
                put("xl/sharedStrings.xml", sharedStrings)
                sheets.values.forEachIndexed { index, worksheet ->
                    put("xl/worksheets/sheet${index + 1}.xml", replaceValues(worksheet, indexes))
                }
            },
        )
    }

    private fun worksheet(rows: List<List<String>>): String = buildString {
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            append("<row r=\"").append(rowIndex + 1).append("\">")
            row.forEachIndexed { columnIndex, value ->
                append("<c r=\"").append(columnName(columnIndex)).append(rowIndex + 1)
                    .append("\" t=\"s\"><v><value>").append(value).append("</value></v></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun replaceValues(xml: String, indexes: Map<String, Int>): String =
        Regex("<value>(.*?)</value>").replace(xml) { match -> indexes.getValue(match.groupValues[1]).toString() }

    private fun zip(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun columnName(index: Int): String {
        var remaining = index + 1
        val result = StringBuilder()
        while (remaining > 0) {
            remaining--
            result.append(('A'.code + remaining % 26).toChar())
            remaining /= 26
        }
        return result.reverse().toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
