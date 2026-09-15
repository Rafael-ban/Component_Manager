package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockUsageSummaryTest {
    @Test
    fun emptySummaryHasZeroFraction() {
        val summary = StockUsageSummary(0, 0)
        assertEquals(0L, summary.total)
        assertEquals(0f, summary.issuedFraction)
    }

    @Test
    fun fullyIssuedSummaryHasFullFraction() {
        assertEquals(1f, StockUsageSummary(0, 25).issuedFraction)
    }

    @Test
    fun largeValuesDoNotOverflowTotal() {
        assertEquals(4_294_967_294L, StockUsageSummary(Int.MAX_VALUE, Int.MAX_VALUE.toLong()).total)
    }

    @Test
    fun invalidValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> { StockUsageSummary(-1, 0) }
        assertFailsWith<IllegalArgumentException> { StockUsageSummary(0, -1) }
        assertFailsWith<IllegalArgumentException> { StockUsageSummary(1, Long.MAX_VALUE) }
    }
}
