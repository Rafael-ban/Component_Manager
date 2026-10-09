package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportMappingPriorityTest {
    private val official = ComponentOfficialMetadata(
        source = "lcsc_domestic_web",
        name = "Official name",
        packageName = "0603",
        category = "电阻/贴片电阻",
        categoryPath = "被动器件/电阻/贴片电阻",
    )

    @Test
    fun learnedMappingSurvivesOfficialLookup() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText,
            rawPayload = "C1",
            sourceLabel = "JLC",
            sku = "C1",
            name = "C1",
            packageName = "C1",
            category = "General",
            recognitionConfidence = "fallback",
        )
        val mapping = ComponentImportLearningMapping(
            id = "mapping-1",
            sourceType = ComponentImportSourceType.JlcText,
            resolvedName = "My resistor",
            resolvedCategory = "General",
            resolvedPackageName = "C1",
        )

        val resolved = candidate.withLearningMapping(
            ComponentImportLearningMatch(mapping, ComponentImportLearningMatchType.Sku),
        ).withOfficialMetadata(official)

        assertEquals("My resistor", resolved.name)
        assertEquals("General", resolved.category)
        assertEquals("C1", resolved.packageName)
        assertEquals(ComponentImportFieldOrigin.Learned, resolved.fieldOrigins.category)
        assertEquals(ComponentImportFieldOrigin.Learned, resolved.fieldOrigins.packageName)
    }

    @Test
    fun userValuesSurviveOfficialLookupButEmptyValuesAreFilled() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText,
            rawPayload = "C1",
            sourceLabel = "JLC",
            sku = "C1",
            category = "General",
            packageName = "C1",
            recognitionConfidence = "fallback",
            fieldOrigins = ComponentImportFieldOrigins(
                category = ComponentImportFieldOrigin.User,
                packageName = ComponentImportFieldOrigin.User,
            ),
        )
        val preserved = candidate.withOfficialMetadata(official)
        assertEquals("General", preserved.category)
        assertEquals("C1", preserved.packageName)
        assertEquals(ComponentImportFieldOrigin.User, preserved.fieldOrigins.category)
        assertEquals(ComponentImportFieldOrigin.User, preserved.fieldOrigins.packageName)

        val filled = candidate.copy(category = "", packageName = "").withOfficialMetadata(official)
        assertEquals("电阻/贴片电阻", filled.category)
        assertEquals("0603", filled.packageName)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, filled.fieldOrigins.category)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, filled.fieldOrigins.packageName)
    }

    @Test
    fun officialLookupCanStillReplaceParsedFallbackValues() {
        val candidate = ComponentImportCandidate(
            sourceType = ComponentImportSourceType.JlcText,
            rawPayload = "C1",
            sourceLabel = "JLC",
            sku = "C1",
            category = "General",
            packageName = "C1",
            recognitionConfidence = "fallback",
        ).withOfficialMetadata(official)

        assertEquals("电阻/贴片电阻", candidate.category)
        assertEquals("0603", candidate.packageName)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, candidate.fieldOrigins.category)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, candidate.fieldOrigins.packageName)
    }
}