package com.componentvault.android.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryFilterSemanticsTest {
    @Test fun mergesKnownEnglishPathsWithStoredChineseCategory() {
        val english = "Integrated Circuits (ICs)/Embedded/Microcontrollers"
        assertEquals(listOf(english), CategoryFilterSemantics.options(listOf("微控制器", english)))
        assertTrue(CategoryFilterSemantics.sameCategory(english, "微控制器"))
        assertTrue(CategoryFilterSemantics.sameCategory("微控制器", "微控制器"))
    }

    @Test fun preservesUnknownAndCustomCategories() {
        assertEquals(
            listOf("Another/Leaf", "User Parent/Custom Leaf"),
            CategoryFilterSemantics.options(listOf("User Parent/Custom Leaf", "Another/Leaf")),
        )
        assertFalse(CategoryFilterSemantics.sameCategory("User Parent/Custom Leaf", "Another/Leaf"))
    }

    @Test fun chineseSearchMatchesHistoricalEnglishCategoryAndRawEnglishStillWorks() {
        val historical = "Integrated Circuits (ICs)/Power Management (PMIC)/Voltage Regulators - Linear"
        assertTrue(CategoryFilterSemantics.matchesSearch(historical, "线性稳压器"))
        assertTrue(CategoryFilterSemantics.matchesSearch(historical, "Power Management"))
    }
}
