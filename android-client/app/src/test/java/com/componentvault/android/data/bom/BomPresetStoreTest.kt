package com.componentvault.android.data.bom

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BomPresetStoreTest {
    @Test
    fun savesCompleteSourceAndChoicesThenReconfiguresSets() = withStore { store, _ ->
        val preset = samplePreset()
        assertTrue(store.list().isEmpty())

        assertEquals(preset, store.save(preset))
        val restored = store.list().single()
        assertEquals(preset, restored)
        assertEquals("Test-Point", restored.parsed.requirements.single().name)
        assertEquals("TP1", restored.parsed.requirements.single().sourceRows.single().fields["Designator"])
        assertEquals(mapOf("row:BOM:2" to "inventory-1"), restored.selections)
        assertEquals(setOf("row:BOM:3"), restored.excludedKeys)

        val resized = restored.reconfigure(5)
        assertEquals(5, resized.parsed.productionSets)
        assertEquals(15, resized.parsed.requirements.single().requiredQuantity)
        assertEquals(3, resized.parsed.requirements.single().quantityPerSet)
        assertEquals(6, restored.parsed.requirements.single().requiredQuantity)
        assertEquals(restored.selections, resized.selections)
    }

    @Test
    fun sameNameOverwritesWithStableIdAndDeleteIsExplicit() = withStore { store, _ ->
        store.save(samplePreset())
        val replaced = store.save(samplePreset().copy(id = "new-id", name = "board", selections = emptyMap()))
        assertEquals("preset-1", replaced.id)
        assertEquals(1, store.list().size)
        assertTrue(store.list().single().selections.isEmpty())
        assertFalse(store.delete("missing"))
        assertTrue(store.delete("preset-1"))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun malformedFileReportsAnActionableErrorWithoutOverwritingIt() = withStore { store, file ->
        file.writeText("{broken")
        val original = file.readText()
        val error = assertFailsWith<BomPresetStoreException> { store.list() }
        assertTrue(error.message.orEmpty().contains("备份"))
        assertFailsWith<BomPresetStoreException> { store.save(samplePreset()) }
        assertEquals(original, file.readText())
    }

    private fun samplePreset(): BomPreset {
        val sheet = BomSheet("BOM", 0)
        val source = BomSourceRow("BOM", 2, 3, mapOf("Comment" to "Test-Point", "Designator" to "TP1"))
        val requirement = BomRequirement(
            BomRequirementIdentity("row:BOM:2"), null, "Test-Point", null, "TP",
            3, 6, listOf(source),
        )
        return BomPreset(
            id = "preset-1",
            name = "Board",
            parsed = BomParseResult("Board", 2, listOf(sheet), sheet, listOf(requirement), "source-hash"),
            selections = mapOf("row:BOM:2" to "inventory-1"),
            excludedKeys = setOf("row:BOM:3"),
        )
    }

    private fun withStore(block: (BomPresetStore, File) -> Unit) {
        val directory = Files.createTempDirectory("bom-preset-test").toFile()
        try {
            val file = File(directory, "bom-presets.json")
            block(BomPresetStore(file), file)
        } finally {
            directory.deleteRecursively()
        }
    }
}
