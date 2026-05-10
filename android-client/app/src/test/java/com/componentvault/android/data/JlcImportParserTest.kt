package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JlcImportParserTest {
    @Test
    fun parseQrInfersGenericMlccFields() {
        val candidate = JlcImportParser.parseQr(
            "{on:SO25020715054,pc:C30926,pm:0603B104K500NT,qty:300,mc:null,cc:1,pdi:144390018,hp:11}",
        )

        assertEquals("C30926", candidate.sku)
        assertEquals("", candidate.name)
        assertEquals("0603B104K500NT", candidate.model)
        assertEquals("0603", candidate.packageName)
        assertEquals("Capacitor", candidate.category)
        assertEquals(300, candidate.suggestedQuantity)
    }

    @Test
    fun parseTextKeepsJlcClipboardFields() {
        val candidate = JlcImportParser.parseText(
            """
            名称：1x2P 间距:1.25mm 卧贴 系列:PicoBlade(MX 1.25)
            型号：ZX-MX1.25-2PWT
            品牌：Megastar(兆星)
            封装：SMD,P=1.25mm,卧贴
            编号：C7430468
            """.trimIndent(),
        )

        assertEquals("C7430468", candidate.sku)
        assertEquals("1x2P 间距:1.25mm 卧贴 系列:PicoBlade(MX 1.25)", candidate.name)
        assertEquals("ZX-MX1.25-2PWT", candidate.model)
        assertEquals("SMD,P=1.25mm,卧贴", candidate.packageName)
        assertEquals("Connector", candidate.category)
    }

    @Test
    fun parseTextAllowsMissingComponentNameWhenSkuExists() {
        val candidate = JlcImportParser.parseText(
            """
            型号：0603B104K500NT
            品牌：FH
            封装：0603
            编号：C30926
            """.trimIndent(),
        )

        assertEquals("C30926", candidate.sku)
        assertEquals("", candidate.name)
        assertEquals("0603B104K500NT", candidate.model)
        assertEquals("0603", candidate.packageName)
    }

    @Test
    fun parseSupplierTextKeepsCanonicalNameBlankWithoutHumanReadableTitle() {
        val candidate = ComponentImportParser.parseSupplierText(
            """
            SKU: C30926
            MPN: 0603B104K500NT
            Package: 0603
            Qty: 300
            """.trimIndent(),
        )

        assertEquals("C30926", candidate.sku)
        assertEquals("", candidate.name)
        assertEquals("0603B104K500NT", candidate.model)
        assertEquals("0603", candidate.packageName)
        assertTrue(candidate.category.isNotBlank())
    }

    @Test
    fun categoryInferencerRecognizesVendorModelFamiliesOffline() {
        assertEquals("Resistor", ComponentCategoryInferencer.infer("0603WAF1002T5E"))
        assertEquals("Capacitor", ComponentCategoryInferencer.infer("GRM188R71H104KA93D"))
        assertEquals("Inductor", ComponentCategoryInferencer.infer("LQH32CN100K53L"))
    }
}
