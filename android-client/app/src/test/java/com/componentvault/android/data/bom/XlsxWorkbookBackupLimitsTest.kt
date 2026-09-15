package com.componentvault.android.data.bom

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XlsxWorkbookBackupLimitsTest {
    @Test fun backupModeReadsMoreThanBomFiveThousandRows() {
        val bytes = workbook(5_002)
        val backup = XlsxWorkbookReader.read(
            bytes,
            loadWorksheetRows = true,
            loadAllWorksheets = true,
            limits = XlsxReadLimits.InventoryBackup,
        )
        assertEquals(5_002, backup.rowsBySheetName.getValue("data").size)
        assertFailsWith<LocalImportException> {
            XlsxWorkbookReader.read(bytes, loadWorksheetRows = true)
        }
    }

    @Test fun backupModeRejectsMoreThanOneHundredThousandDataRows() {
        assertFailsWith<LocalImportException> {
            XlsxWorkbookReader.read(
                workbook(100_002),
                loadWorksheetRows = true,
                limits = XlsxReadLimits.InventoryBackup,
            )
        }
    }

    private fun workbook(rowCount: Int): ByteArray {
        val sheet = buildString {
            append("<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            repeat(rowCount) { index -> append("<row r=\"${index + 1}\"><c r=\"A${index + 1}\" t=\"inlineStr\"><is><t>x</t></is></c></row>") }
            append("</sheetData></worksheet>")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun add(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            add("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            add("_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            add("xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"data\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            add("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            add("xl/worksheets/sheet1.xml", sheet)
        }
        return output.toByteArray()
    }
}
