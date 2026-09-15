package com.componentvault.android.data.bom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
