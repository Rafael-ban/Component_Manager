package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentImportModelsTest {
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
