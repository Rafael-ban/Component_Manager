package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentImportModelsTest {
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
        assertEquals(ComponentImportFieldOrigin.Server, resolved.fieldOrigins.name)
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
