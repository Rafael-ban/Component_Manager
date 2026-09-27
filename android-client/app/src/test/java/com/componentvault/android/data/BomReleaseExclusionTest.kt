package com.componentvault.android.data

import android.app.Application
import com.componentvault.android.data.bom.BomParseResult
import com.componentvault.android.data.bom.BomRequirement
import com.componentvault.android.data.bom.BomRequirementIdentity
import com.componentvault.android.data.bom.BomReleaseOutcome
import com.componentvault.android.data.bom.BomSheet
import com.componentvault.android.model.ComponentDraft
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BomReleaseExclusionTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var repository: InventoryRepository

    @Before fun setUp() {
        val helper = InventoryDatabaseHelper(context)
        try { context.deleteDatabase(helper.databaseName) } finally { helper.close() }
        repository = InventoryRepository(context)
    }

    @Test fun skippedRowIsExcludedFromAtomicReleaseAndCanBeRestoredInPreview() = runBlocking {
        val saved = repository.saveComponent(ComponentDraft(
            sku = "R-1", name = "Resistor", category = "Passive", packageName = "0603",
            location = "A", description = "", quantity = 8, minStock = 0,
        ))
        assertTrue(saved.isSuccess, saved.message)
        val resistor = BomRequirement(
            identity = BomRequirementIdentity("sku:R-1"), sku = "R-1", name = "Resistor",
            model = null, packageName = "0603", quantityPerSet = 2, requiredQuantity = 2,
            sourceRows = emptyList(),
        )
        val testPoint = BomRequirement(
            identity = BomRequirementIdentity("row:BOM:3"), sku = null, name = "Test-Point",
            model = null, packageName = null, quantityPerSet = 1, requiredQuantity = 1,
            sourceRows = emptyList(),
        )
        val parsed = BomParseResult(
            projectName = "Board", productionSets = 1,
            availableSheets = listOf(BomSheet("BOM", 0)), selectedSheet = BomSheet("BOM", 0),
            requirements = listOf(resistor, testPoint), fileSha256 = "test-file",
        )

        val unresolved = repository.previewBomRelease(parsed)
        assertFalse(unresolved.canCommit)
        assertEquals(2, unresolved.matchingLines.size)

        val skipped = repository.previewBomRelease(parsed, excludedKeys = setOf(testPoint.identity.canonicalKey))
        assertTrue(skipped.canCommit)
        assertEquals(listOf("R-1"), skipped.lines.map { it.componentSku })
        assertEquals(2, skipped.parsed.requirements.size)

        val released = repository.commitBomRelease(skipped, "release-skip-test", "batch-skip-test")
        assertEquals(BomReleaseOutcome.APPLIED, released.outcome)
        assertEquals(6, repository.loadComponents().first { it.sku == "R-1" }.quantity)
    }
}
