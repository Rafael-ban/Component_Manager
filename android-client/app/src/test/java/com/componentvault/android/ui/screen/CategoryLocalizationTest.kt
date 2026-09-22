package com.componentvault.android.ui.screen

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryLocalizationTest {
    @Test
    fun chineseUiLocalizesKnownOfficialHistoryWithoutChangingUnknownValues() {
        assertEquals(
            "线性稳压器",
            localizedCategoryLabel(
                "Integrated Circuits (ICs)/Power Management (PMIC)/Voltage Regulators - Linear",
                Locale.SIMPLIFIED_CHINESE,
            ),
        )
        assertEquals(
            "User Parent / Custom Leaf",
            localizedCategoryLabel("User Parent/Custom Leaf", Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun nonChineseUiPreservesOfficialEnglishPath() {
        val category = "Integrated Circuits (ICs)/Embedded/Microcontrollers"
        assertEquals(category, localizedCategoryLabel(category, Locale.US))
    }
}
