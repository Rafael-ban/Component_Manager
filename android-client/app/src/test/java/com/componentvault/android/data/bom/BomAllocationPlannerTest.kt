package com.componentvault.android.data.bom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BomAllocationPlannerTest {
    @Test fun usesDeterministicLargestBinThenStableLocationCode() {
        assertEquals(
            listOf(BomAllocationPlan("A", 4), BomAllocationPlan("B", 2)),
            BomAllocationPlanner.plan(6, listOf("B" to 3, "A" to 4, "C" to 1)),
        )
    }

    @Test fun rejectsInsufficientAllocatedStock() {
        assertFailsWith<IllegalArgumentException> { BomAllocationPlanner.plan(3, listOf("A" to 2)) }
    }

    @Test fun manuallySelectedSearchCandidateCanProduceCommittablePreview() {
        val requirement = BomRequirement(
            identity = BomRequirementIdentity("model:unmatched"),
            sku = null,
            name = "Controller",
            model = "unmatched",
            packageName = "QFN",
            quantityPerSet = 2,
            requiredQuantity = 2,
            sourceRows = emptyList(),
        )
        val manuallySelected = InventoryMatchCandidate("inventory-1", "C100", "OTHER", "QFN")
        val preview = BomReleasePreview(
            parsed = BomParseResult(
                projectName = "P",
                productionSets = 1,
                availableSheets = listOf(BomSheet("BOM", 0)),
                selectedSheet = BomSheet("BOM", 0),
                requirements = listOf(requirement),
                fileSha256 = "hash",
            ),
            lines = listOf(
                BomReleaseLine(
                    requirement = requirement,
                    componentId = manuallySelected.inventoryId,
                    componentSku = manuallySelected.sku,
                    availableQuantity = 5,
                    expectedUpdatedAt = "2026-09-16T00:00:00Z",
                    candidates = listOf(manuallySelected),
                    allocationPlan = listOf(BomAllocationPlan("A1", 2)),
                ),
            ),
        )

        assertTrue(preview.canCommit)
    }

    @Test fun aggregationPreservesOriginalSelectionKeysForLaterRematching() {
        fun line(key: String, quantity: Int) = BomReleaseLine(
            requirement = BomRequirement(
                identity = BomRequirementIdentity(key),
                sku = null,
                name = key,
                model = key,
                packageName = null,
                quantityPerSet = quantity,
                requiredQuantity = quantity,
                sourceRows = emptyList(),
            ),
            componentId = "shared",
            componentSku = "C100",
            availableQuantity = 20,
            expectedUpdatedAt = "now",
            candidates = listOf(InventoryMatchCandidate("shared", "C100")),
        )

        val aggregated = BomReleaseAggregator.aggregate(listOf(line("first", 2), line("second", 3))).single()

        assertEquals("component:shared", aggregated.requirement.identity.canonicalKey)
        assertEquals(5, aggregated.requirement.requiredQuantity)
        assertEquals(listOf("first", "second"), aggregated.selectionKeys)
    }

    @Test fun changingOneOriginalRequirementSplitsPreviouslyMergedCommitLines() {
        fun line(key: String, componentId: String, sku: String, quantity: Int) = BomReleaseLine(
            requirement = BomRequirement(
                identity = BomRequirementIdentity(key),
                sku = null,
                name = key,
                model = key,
                packageName = null,
                quantityPerSet = quantity,
                requiredQuantity = quantity,
                sourceRows = emptyList(),
            ),
            componentId = componentId,
            componentSku = sku,
            availableQuantity = 20,
            expectedUpdatedAt = "now",
            candidates = listOf(InventoryMatchCandidate(componentId, sku)),
        )

        val merged = BomReleaseAggregator.aggregate(
            listOf(line("first", "shared", "C1", 2), line("second", "shared", "C1", 3)),
        )
        assertEquals(1, merged.size)
        assertEquals(5, merged.single().requirement.requiredQuantity)

        val split = BomReleaseAggregator.aggregate(
            listOf(line("first", "replacement", "C2", 2), line("second", "shared", "C1", 3)),
        )
        assertEquals(2, split.size)
        assertEquals(setOf("replacement", "shared"), split.mapNotNull { it.componentId }.toSet())
        assertEquals(5, split.sumOf { it.requirement.requiredQuantity })
    }
}
