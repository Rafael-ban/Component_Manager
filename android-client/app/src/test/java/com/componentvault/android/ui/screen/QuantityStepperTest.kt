package com.componentvault.android.ui.screen

import org.junit.Test
import kotlin.test.assertEquals

class QuantityStepperTest {
    @Test
    fun stepsRespectBoundsAndRecoverInvalidInput() {
        assertEquals("2", stepQuantityText("1", 1))
        assertEquals("1", stepQuantityText("1", -1))
        assertEquals("1", stepQuantityText("", 1))
        assertEquals("1", stepQuantityText("bad", -1))
        assertEquals("0", stepQuantityText("0", -1, minimum = 0))
        assertEquals("3", stepQuantityText("3", 1, maximum = 3))
        assertEquals("3", stepQuantityText("4", -1, maximum = 3))
        assertEquals(Int.MAX_VALUE.toString(), stepQuantityText(Int.MAX_VALUE.toString(), 1))
        assertEquals(Int.MIN_VALUE.toString(), stepQuantityText(
            Int.MIN_VALUE.toString(), -1, minimum = Int.MIN_VALUE, skipZero = true,
        ))
        assertEquals("-1", stepQuantityText("1", -1, minimum = Int.MIN_VALUE, skipZero = true))
        assertEquals("1", stepQuantityText("-1", 1, minimum = Int.MIN_VALUE, skipZero = true))
        assertEquals("-1", stepQuantityText("", -1, minimum = Int.MIN_VALUE, skipZero = true))
    }
}
