package com.componentvault.android.data

import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.toLabelSeed
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelWorkbookExporterTest {
    @Test
    fun exportWritesSelectedColumnsAsTextAndKeepsUnicodeAndSpecialCharacters() {
        val bytes = LabelWorkbookExporter.export(
            listOf(component(sku = "0012|<&\"", name = "电阻 & <测试>")),
            setOf(LabelWorkbookColumn.Name, LabelWorkbookColumn.Sku, LabelWorkbookColumn.LongQrText, LabelWorkbookColumn.ShortQrText),
        )

        val sheet = entry(bytes, "xl/worksheets/sheet1.xml")
        assertContains(sheet, "电阻 &amp; &lt;测试&gt;")
        assertContains(sheet, "0012|&lt;&amp;&quot;")
        assertContains(sheet, "component-vault-label")
        assertContains(sheet, "cvl3|")
        assertTrue(Regex("<c r=\"B2\" t=\"inlineStr\"").containsMatchIn(sheet))
        assertContains(sheet, "cvl3|0012%7C%3C%26%22|0")
        val seed = component(sku = "0012|<&\"", name = "电阻 & <测试>").toLabelSeed()
        assertEquals(
            "{\"fmt\":\"component-vault-label\",\"v\":1,\"sku\":\"0012|<&\\\"\",\"name\":\"电阻 & <测试>\",\"cat\":\"电阻\",\"pkg\":\"0603\",\"loc\":\"A-01\",\"qty\":0,\"min\":0,\"model\":\"R<&\\\"\",\"brand\":\"中文牌\"}",
            ComponentLabelCodec.buildQrPayload(seed, ComponentLabelTemplate.Qr30x40)?.rawValue,
        )
    }

    @Test
    fun exportSkipsDeletedRowsAndSupportsEmptyInventory() {
        val bytes = LabelWorkbookExporter.export(
            listOf(component(sku = "gone", deleted = true)),
            LabelWorkbookExporter.defaultColumns,
        )
        val sheet = entry(bytes, "xl/worksheets/sheet1.xml")
        assertFalse(sheet.contains("gone"))
        assertEquals(1, Regex("<row ").findAll(sheet).count())
    }

    @Test
    fun exportOnlyBuildsRequestedColumns() {
        val bytes = LabelWorkbookExporter.export(
            listOf(component(sku = "plain")),
            setOf(LabelWorkbookColumn.Name, LabelWorkbookColumn.Sku),
        )
        val sheet = entry(bytes, "xl/worksheets/sheet1.xml")
        assertFalse(sheet.contains("component-vault-label"))
        assertFalse(sheet.contains("cvl3|"))
    }

    private fun component(sku: String, name: String = "名称", deleted: Boolean = false) = ComponentRecord(
        id = "id-$sku", sku = sku, name = name, category = "电阻", packageName = "0603",
        location = "A-01", description = "型号：R<&\"\n品牌：中文牌", quantity = 0, minStock = 0,
        updatedAt = "2026-09-22T00:00:00.000Z", deleted = deleted,
    )

    private fun entry(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val next = zip.nextEntry ?: break
                if (next.name == name) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("Missing $name")
    }
}
