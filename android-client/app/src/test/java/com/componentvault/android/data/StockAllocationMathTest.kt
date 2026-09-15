package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockAllocationMathTest {
    @Test fun transferPreservesTotalAndMovesOnlyRequestedQuantity() {
        val result = StockAllocationMath.transfer(source = 8, destination = 3, quantity = 5)
        assertEquals(3 to 8, result)
        StockAllocationMath.requireTotal(11, listOf(result.first, result.second))
    }

    @Test fun rejectsNegativeBinAndOverflow() {
        assertFailsWith<IllegalArgumentException> { StockAllocationMath.applyDelta(2, -3) }
        assertFailsWith<ArithmeticException> { StockAllocationMath.transfer(1, Int.MAX_VALUE, 1) }
    }

    @Test fun rejectsMismatchedAllocationTotal() {
        assertFailsWith<IllegalArgumentException> { StockAllocationMath.requireTotal(4, listOf(1, 2)) }
    }
}
