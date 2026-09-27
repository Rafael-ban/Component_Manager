package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
            assertEquals(example.model, candidate.name)
            assertEquals(example.model, candidate.model)
            assertEquals(example.value, candidate.toComponentDraft(1, "A", 0)
                .description.lineSequence().first { it.startsWith("参数：${example.key}：") }
                .substringAfterLast('：'))
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
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText, rawPayload = "", sourceLabel = "JLC",
            sku = "C2", name = longModel, model = longModel,
        ).withOfficialMetadata(metadata)
        assertEquals("C2", candidate.name)
        assertEquals(longModel, candidate.model)
        assertEquals(true, candidate.notes.any { it == "参数：阻值：$longValue" })
        val valid = metadata.copy(parameters = mapOf("阻值" to "10kΩ"))
        assertEquals("C2", candidate.withOfficialMetadata(valid).name)
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
        assertEquals("RC0603FR-0710KL", resolved.name)
        assertEquals("RC0603FR-0710KL", resolved.model)
        assertEquals("My resistor", base.copy(name = "My resistor",
            fieldOrigins = base.fieldOrigins.copy(name = ComponentImportFieldOrigin.User))
            .withOfficialMetadata(metadata).name)
        assertEquals("CUSTOM-100", base.copy(name = "CUSTOM-100",
            fieldOrigins = base.fieldOrigins.copy(name = ComponentImportFieldOrigin.Learned))
            .withOfficialMetadata(metadata).name)
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
        assertEquals("10kΩ · ±1%", ComponentRecord(
            id = "1", sku = "C1", name = resolved.name, category = "电阻",
            packageName = "0603", location = "A", description = resolved.toComponentDraft(1, "A", 0).description,
            quantity = 1, minStock = 0, updatedAt = "now", deleted = false,
        ).officialRclSpecificationSummary())
    }

    @Test
    fun descriptionFallbackExtractsOnlyTheActualValueAndNonRclKeepsModel() {
        val capacitor = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcQr, rawPayload = "C3", sourceLabel = "JLC",
            sku = "C3", name = "C0603", model = "C0603", category = "电容",
        ).withOfficialMetadata(ComponentOfficialMetadata(
            category = "电容", model = "C0603", description = "贴片电容 100nF ±10% 50V 促销",
        ))
        assertEquals("C0603", capacitor.name)
        val record = ComponentRecord(id = "3", sku = "C3", name = capacitor.name, category = "电容",
            packageName = "0603", location = "A", description = capacitor.toComponentDraft(1, "A", 0).description,
            quantity = 1, minStock = 0, updatedAt = "now", deleted = false)
        assertEquals("100nF · ±10% · 50V", record.officialRclSpecificationSummary())
        assertEquals("50V", record.officialParameters()["耐压"])
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
    fun modelLikeNameStaysModelWhenOfficialResultOnlyHasMarketingName() {
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

        assertEquals("0603B104K500NT", resolved.name)
        assertEquals(ComponentImportFieldOrigin.Parsed, resolved.fieldOrigins.name)
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

        val custom = imported.copy(name = "My controller",
            fieldOrigins = imported.fieldOrigins.copy(name = ComponentImportFieldOrigin.User)).withOfficialMetadata(
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
    @Test
    fun unitBearingKeysBecomeReadableValuesWithoutInventingCapacityFromModel() {
        val record = ComponentRecord(
            id = "1", sku = "C1", name = "Maker C0603", category = "电容",
            packageName = "0603", location = "A",
            description = "型号：C0603F5000\n参数：额定电压(V)：50\n参数：精度：±5%",
            quantity = 1, minStock = 0, updatedAt = "2026-09-27", deleted = false,
        )
        assertEquals("50V", record.officialParameters()["额定电压(V)"])
        assertEquals("50V", record.copy(description = "参数：额定电压（V）：50").officialParameters()["额定电压（V）"])
        assertEquals(null, record.officialParameters()["容量"])
        assertEquals(null, record.officialRclSpecificationSummary())
        assertEquals("power", officialParameterKind("额定功率(W)"))
        assertEquals("inductance", officialParameterKind("电感值(uH)"))
    }
    @Test
    fun commonOfficialEnglishParameterNamesHaveStableKinds() {
        assertEquals("operating_temperature", officialParameterKind("Operating Temperature"))
        assertEquals("type", officialParameterKind("Type"))
        assertEquals("temperature_coefficient", officialParameterKind("Temperature Coefficient"))
        assertEquals("temperature_coefficient", officialParameterKind("Resistance Temperature Coefficient"))
        assertEquals(null, officialParameterKind("Supplier Marketing Label"))
    }
}
