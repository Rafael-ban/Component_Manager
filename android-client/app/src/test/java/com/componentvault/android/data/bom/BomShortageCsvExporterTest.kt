package com.componentvault.android.data.bom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BomShortageCsvExporterTest {
    @Test fun exportsChineseQuotesUnmatchedRowsAndPreventsExcelFormulaExecution() {
        val requirement = requirement("=C1", "型号\"甲", 3)
        val preview = BomReleasePreview(
            parsed = BomParseResult("项目", 1, listOf(BomSheet("CSV", 0)), BomSheet("CSV", 0), listOf(requirement), "hash"),
            lines = emptyList(),
            matchingLines = listOf(BomReleaseLine(requirement, null, null, 0, null, emptyList())),
        )
        val bytes = BomShortageCsvExporter.export(preview)
        assertTrue(bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val text = bytes.drop(3).toByteArray().decodeToString()
        assertTrue("\"'=C1\"" in text)
        assertTrue("\"型号\"\"甲\"" in text)
        assertTrue("未匹配" in text)
        assertEquals(1, BomShortageCsvExporter.shortageCount(preview))
    }

    @Test fun reportsZeroShortagesClearly() {
        val requirement = requirement("C1", "M1", 2)
        val line = BomReleaseLine(requirement, "id", "C1", 5, "now", listOf(InventoryMatchCandidate("id", "C1")))
        val preview = BomReleasePreview(BomParseResult("P", 1, listOf(BomSheet("CSV", 0)), BomSheet("CSV", 0), listOf(requirement), "hash"), listOf(line))
        assertEquals(0, BomShortageCsvExporter.shortageCount(preview))
        assertTrue("库存充足" in BomShortageCsvExporter.export(preview).drop(3).toByteArray().decodeToString())
    }

    private fun requirement(sku: String, model: String, required: Int) = BomRequirement(
        BomRequirementIdentity("sku:$sku"), sku, null, model, null, required, required,
        listOf(BomSourceRow("CSV", 2, required, emptyMap())),
    )
}
