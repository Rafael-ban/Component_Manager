package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComponentImportModelsTest {
    @Test
    fun fiveCatalogRclExamplesKeepModelAndOfficialValuesVisible() {
        data class Example(
            val sku: String, val model: String, val category: String,
            val key: String, val value: String, val tolerance: String,
        )
        val examples = listOf(
            Example("C5137569", "FCC0402N220J500AT", "电容", "容值(pF)", "22pF", "±5%"),
            Example("C2907005", "FRC0603F2201TS", "电阻", "阻值(Ω)", "2.2kΩ", "±1%"),
            Example("C5126214", "FRH0603B1002TS", "电阻", "阻值", "10kΩ", "±0.1%"),
            Example("C178304", "1206X107M6R3NT", "电容", "容量", "100uF", "±20%"),
            Example("C275673", "SLO252012F1R5MTT", "电感", "电感量", "1.5uH", "±20%"),
        )
        examples.forEach { example ->
            val metadata = ComponentOfficialMetadata(
                source = "lcsc_domestic_web", sku = example.sku,
                category = example.category, model = example.model,
                description = "官方商品描述 ${example.value} ${example.tolerance}",
                parameters = mapOf(example.key to example.value, "精度" to example.tolerance),
            )
            val candidate = ComponentImportCandidate(
                sourceType = ComponentImportSourceType.JlcText,
                rawPayload = "", sourceLabel = "JLC", sku = example.sku,
                name = example.model, model = example.model,
            ).withOfficialMetadata(metadata)
            assertEquals("${example.model} · ${example.value} · ${example.tolerance}", candidate.name)
            assertEquals(example.model, candidate.model)
        }
    }

    @Test
    fun unrelatedParameterNamesAndToleranceAloneCannotReplaceTheModel() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText, rawPayload = "", sourceLabel = "JLC",
            sku = "C1", name = "R0603", model = "R0603",
        )
        val metadata = ComponentOfficialMetadata(
            source = "lcsc_domestic_web", category = "电阻", model = "R0603",
            parameters = mapOf(
                "Resistance Temperature Coefficient" to "100ppm/°C",
                "绝缘电阻" to "100MΩ", "精度" to "±1%",
            ),
        )
        assertNull(metadata.compactRclDisplayName())
        assertEquals("R0603", candidate.withOfficialMetadata(metadata).name)
    }

    @Test
    fun longCatalogFieldsStayInDescriptionButDoNotBecomeAutoName() {
        val longModel = "R".repeat(81)
        val longValue = "value".repeat(7)
        val metadata = ComponentOfficialMetadata(
            source = "lcsc_domestic_web", sku = "C2", category = "电阻",
            model = longModel, description = "官方描述", parameters = mapOf("阻值" to longValue),
        )
        assertNull(metadata.compactRclDisplayName())
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText, rawPayload = "", sourceLabel = "JLC",
            sku = "C2", name = longModel, model = longModel,
        ).withOfficialMetadata(metadata)
        assertEquals("C2", candidate.name)
        assertEquals(longModel, candidate.model)
        assertEquals(true, candidate.notes.any { it == "参数：阻值：$longValue" })
        val valid = metadata.copy(parameters = mapOf("阻值" to "10kΩ"))
        assertEquals("C2 · 10kΩ", valid.compactRclDisplayName())
    }

    @Test
    fun officialRclSpecificationsStayVisibleWithoutReplacingCustomNames() {
        val metadata = ComponentOfficialMetadata(
            source = "lcsc_domestic_web", category = "电阻/贴片电阻",
            model = "RC0603FR-0710KL", name = "RC0603FR-0710KL",
            description = "很长的销售标题 10kΩ 电阻",
            parameters = mapOf("阻值" to "10kΩ", "精度" to "±1%"),
        )
        val base = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr, rawPayload = "C1",
            sourceLabel = "JLC", sku = "C1", name = "RC0603FR-0710KL", model = "RC0603FR-0710KL",
        )
        val resolved = base.withOfficialMetadata(metadata)
        assertEquals("RC0603FR-0710KL · 10kΩ · ±1%", resolved.name)
        assertEquals("RC0603FR-0710KL", resolved.model)
        assertEquals("My resistor", base.copy(name = "My resistor").withOfficialMetadata(metadata).name)
        assertEquals("10kΩ · ±1%", ComponentRecord(
            id = "1", sku = "C1", name = "My resistor", category = "电阻",
            packageName = "0603", location = "A", description = resolved.toComponentDraft(1, "A", 0).description,
            quantity = 1, minStock = 0, updatedAt = "now", deleted = false,
        ).officialRclSpecificationSummary())
        assertEquals("10kΩ · ±1%", ComponentRecord(
            id = "2", sku = "C1", name = "My resistor", category = "电阻",
            packageName = "0603", location = "A",
            description = resolved.toComponentDraft(1, "A", 0).description.replace("参数：", "参数·"),
            quantity = 1, minStock = 0, updatedAt = "now", deleted = false,
        ).officialRclSpecificationSummary())
        assertNull(ComponentRecord(
            id = "1", sku = "C1", name = resolved.name, category = "电阻",
            packageName = "0603", location = "A", description = resolved.toComponentDraft(1, "A", 0).description,
            quantity = 1, minStock = 0, updatedAt = "now", deleted = false,
        ).officialRclSpecificationSummary())
    }

    @Test
    fun descriptionFallbackExtractsOnlyTheActualValueAndNonRclKeepsModel() {
        assertEquals("C0603 · 100nF · ±10%", ComponentOfficialMetadata(
            category = "电容", model = "C0603", description = "贴片电容 100nF ±10% 50V 促销",
        ).compactRclDisplayName())
        assertNull(ComponentOfficialMetadata(
            category = "IC", model = "IC-1", parameters = mapOf("精度" to "1%"),
        ).compactRclDisplayName())
    }

    @Test
    fun officialCategoryOverridesLocalGuess() {
        val candidate = ComponentImportCandidate(
            sku = "C49208388", name = "LED driver", category = "IC",
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = "C49208388", sourceLabel = "JLC",
        ).withOfficialMetadata(ComponentOfficialMetadata(
            source = "lcsc_public_web", category = "LED驱动",
            categoryPath = "LED Drivers/LED Drivers ICs",
        ))
        assertEquals("LED驱动", candidate.category)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, candidate.fieldOrigins.category)
    }

    @Test
    fun officialProductImageUsesThePortableDescriptionNote() {
        val candidate = ComponentImportCandidate(
            sku = "C70565",
            name = "Crystal",
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = "{pc:C70565}",
            sourceLabel = "JLC package QR",
        ).withOfficialMetadata(
            ComponentOfficialMetadata(
                imageUrl = "https://assets.lcsc.com/images/C70565.jpg",
            ),
        )

        val description = candidate.toComponentDraft(
            location = "A1",
            quantity = 1,
            minStock = 0,
        ).description
        assertEquals(
            "https://assets.lcsc.com/images/C70565.jpg",
            productImageUrlFromDescription(description),
        )
    }

    @Test
    fun officialMetadataReplacesModelLikeCanonicalName() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = "{pc:C30926,pm:0603B104K500NT}",
            sourceLabel = "JLC package QR",
            sku = "C30926",
            name = "0603B104K500NT",
            model = "0603B104K500NT",
        )

        val resolved = candidate.withOfficialMetadata(
            ComponentOfficialMetadata(
                source = "lcsc_public_web",
                name = "100nF Ceramic Capacitor",
            ),
        )

        assertEquals("100nF Ceramic Capacitor", resolved.name)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, resolved.fieldOrigins.name)
    }

    @Test
    fun officialModelWinsOverDescriptionButDoesNotReplaceCustomName() {
        val imported = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = "{pc:C1,pm:MODEL-1}",
            sourceLabel = "JLC package QR",
            sku = "C1",
            name = "MODEL-1",
            model = "MODEL-1",
        ).withOfficialMetadata(
            ComponentOfficialMetadata(
                source = "lcsc_public_web",
                name = "A very long marketplace description",
                description = "A very long marketplace description",
                model = "MODEL-1",
            ),
        )
        assertEquals("MODEL-1", imported.name)

        val custom = imported.copy(name = "My controller").withOfficialMetadata(
            ComponentOfficialMetadata(name = "Marketplace description", model = "MODEL-2"),
        )
        assertEquals("My controller", custom.name)
    }

    @Test
    fun recognitionMetadataPromotesVendorIntoBrandWhenBrandIsMissing() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr,
            rawPayload = "{pc:C30926,pm:0603B104K500NT}",
            sourceLabel = "JLC package QR",
            sku = "C30926",
            model = "0603B104K500NT",
        )

        val resolved = candidate.withRecognitionMetadata(
            ComponentRecognitionMetadata(
                vendor = "FH",
                category = "Capacitor",
            ),
        )

        assertEquals("FH", resolved.brand)
        assertEquals(ComponentImportFieldOrigin.Rule, resolved.fieldOrigins.brand)
    }
}
