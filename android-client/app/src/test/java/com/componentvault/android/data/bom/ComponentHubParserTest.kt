package com.componentvault.android.data.bom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ComponentHubParserTest {
    @Test fun numericSourceIdIsStableAcrossChangedRecordsAndClients() {
        fun read(stock: Int) = ComponentHubParser.parse(
            """{"components":[{"id":123,"name":"Part","stock":$stock,"threshold":0}]}""".toByteArray(),
        ).components.single().sku
        assertEquals("CH-D75975A6B3CB", read(1))
        assertEquals(read(1), read(20))
    }

    @Test
    fun mapsFieldsKeepsNotesAndOnlyAcceptsTrustedProductImage() {
        val result = ComponentHubParser.parse(
            """
            {"components":[{
              "productCode":"C123","name":"MCU","model":"STM32F103","brand":"ST",
              "encapStandard":"LQFP-48","category":"IC","subCategory":"MCU",
              "stock":12,"threshold":3,"location":"A1","notes":"开发板库存",
              "image":"https://assets.lcsc.com/images/lcsc.jpg"
            }]}
            """.trimIndent().toByteArray(),
        )

        val item = result.components.single()
        assertEquals("C123", item.sku)
        assertEquals(12, item.quantity)
        assertEquals(3, item.minStock)
        assertTrue("原备注：开发板库存" in item.notes)
        assertTrue("商品图片：https://assets.lcsc.com/images/lcsc.jpg" in item.notes)
        assertTrue(result.canConfirm)
    }

    @Test
    fun dropsUntrustedImageInsteadOfTurningItIntoANote() {
        val item = ComponentHubParser.parse(
            """{"components":[{"productCode":"C1","name":"R","stock":1,"threshold":0,"image":"https://evil.example/lcsc.jpg"}]}"""
                .toByteArray(),
        ).components.single()

        assertEquals(null, item.sourceFields.trustedImageUrl)
        assertTrue(item.notes.none { it.startsWith("商品图片：") })
    }

    @Test
    fun emptyProductCodeUsesStableWindowsCompatibleIdHash() {
        val first = ComponentHubParser.parse(
            """{"components":[{"updatedAt":"later","name":"R","id":"source-42","stock":1,"threshold":0}]}"""
                .toByteArray(),
        ).components.single()
        val reordered = ComponentHubParser.parse(
            """{"components":[{"threshold":0,"stock":1,"id":"source-42","name":"R","updatedAt":"other"}]}"""
                .toByteArray(),
        ).components.single()

        assertEquals("CH-D61DE1952408", first.sku)
        assertEquals(first.sku, reordered.sku)
    }

    @Test
    fun fallbackWithoutSourceIdSortsPropertiesAndIgnoresTimestamps() {
        val firstBytes =
            """{"components":[{"name":"R","brand":"ACME","stock":1,"threshold":0,"createdAt":"one"}]}""".toByteArray()
        val secondBytes =
            """{"components":[{"updatedAt":"two","threshold":0,"stock":1,"brand":"ACME","name":"R"}]}""".toByteArray()
        val first = ComponentHubParser.parse(firstBytes)
        val second = ComponentHubParser.parse(secondBytes)

        assertTrue(first.components.single().sku.startsWith("CH-"))
        assertEquals(first.components.single().sku, second.components.single().sku)
        assertNotEquals(first.fileSha256, second.fileSha256)
        assertEquals(ImportHash.sha256(firstBytes), first.fileSha256)
    }

    @Test
    fun duplicateSkuBlocksByDefaultAndCanBeExplicitlySkipped() {
        val json = """
            {"components":[
              {"productCode":"C1","name":"first","stock":1,"threshold":0},
              {"productCode":"c1","name":"second","stock":2,"threshold":0}
            ]}
        """.trimIndent().toByteArray()

        val blocked = ComponentHubParser.parse(json)
        assertFalse(blocked.canConfirm)
        assertEquals(1, blocked.conflicts.size)
        assertFalse(blocked.conflicts.single().skipped)

        val skipped = ComponentHubParser.parse(json, ComponentHubDuplicatePolicy.SKIP)
        assertTrue(skipped.canConfirm)
        assertTrue(skipped.conflicts.single().skipped)
        assertEquals(1, skipped.skippedDuplicateCount)
        assertEquals("first", skipped.components.single().name)
    }

    @Test
    fun fileHashUsesSha256() {
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            ImportHash.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun prefersChineseCategoryMapsSlugsAndPreservesSourceFields() {
        val chinese = ComponentHubParser.parse(
            """{"components":[{"id":"old-1","name":"R","model":"10K","category":"resistor","categoryName":"电阻器","stock":1,"threshold":0,"params":{"tolerance":"1%"},"value":"10k","price":0.02,"datasheet":"https://example.test/r.pdf","image":"https://evil.example/raw.jpg"}]}"""
                .toByteArray(),
        ).components.single()

        assertEquals("电阻器", chinese.category)
        assertEquals("未知封装", chinese.packageName)
        assertTrue("型号：10K" in chinese.notes)
        assertTrue(chinese.notes.any { it == "参数：{\"tolerance\":\"1%\"}" })
        assertTrue(chinese.notes.any { it == "原图片：https://evil.example/raw.jpg" })
        assertTrue(chinese.notes.any { it.startsWith("来源JSON：") && it.contains("\"price\":0.02") })
        assertEquals("old-1", chinese.sourceFields.sourceId)
        assertEquals("https://evil.example/raw.jpg", chinese.sourceFields.image)

        val slug = ComponentHubParser.parse(
            """{"components":[{"name":"C","category":"capacitor","stock":1,"threshold":0}]}"""
                .toByteArray(),
        ).components.single()
        assertEquals("电容", slug.category)
    }

    @Test
    fun unknownCategoryIsKeptInsteadOfInventingAChineseName() {
        val item = ComponentHubParser.parse(
            """{"components":[{"name":"X","category":"custom-rf-part","stock":1,"threshold":0}]}"""
                .toByteArray(),
        ).components.single()

        assertEquals("custom-rf-part", item.category)
        assertTrue("原分类：custom-rf-part" in item.notes)
    }

    @Test
    fun rejectsJsonDeeperThanConfiguredLimit() {
        val nested = "[".repeat(40) + "null" + "]".repeat(40)
        assertFailsWith<LocalImportException> {
            ComponentHubParser.parse(
                """{"components":[{"name":"X","stock":1,"threshold":0,"params":$nested}]}"""
                    .toByteArray(),
            )
        }
    }
}
