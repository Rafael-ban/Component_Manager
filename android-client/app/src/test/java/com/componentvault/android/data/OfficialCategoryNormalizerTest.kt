package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialCategoryNormalizerTest {
    @Test
    fun mapsKnownOfficialLeavesAcrossCommonHierarchies() {
        assertEquals(
            "线性稳压器",
            OfficialCategoryNormalizer.normalize(
                "Integrated Circuits (ICs) / Power Management (PMIC) / Voltage Regulators - Linear",
            ),
        )
        assertEquals(
            "贴片电阻",
            OfficialCategoryNormalizer.normalize("Passive Components/Resistors/Chip Resistor - Surface Mount"),
        )
        assertEquals("磁珠", OfficialCategoryNormalizer.normalize("Unknown Parent/FERRITE BEADS AND CHIPS"))
        assertEquals(
            "线性稳压器（LDO）",
            OfficialCategoryNormalizer.normalize("Power Management (PMIC)/Voltage Regulators - Linear, Low Drop Out (LDO) Regulators"),
        )
        assertEquals(
            "线性稳压器（LDO）",
            OfficialCategoryNormalizer.normalize("Power Management ICs/LDO Regulators"),
        )
    }

    @Test
    fun preservesLegacyOutputsChineseAndUnknownCategories() {
        assertEquals("电阻", OfficialCategoryNormalizer.normalize("Resistors/Chip Resistor - Surface Mount"))
        assertEquals("LED驱动", OfficialCategoryNormalizer.normalize("LED Drivers/LED Drivers ICs"))
        assertEquals("晶体/谐振器/晶振", OfficialCategoryNormalizer.normalize("晶体/谐振器/晶振"))
        assertEquals(
            "User Parent/Custom Leaf",
            OfficialCategoryNormalizer.normalize(" User Parent/Custom Leaf "),
        )
    }
}
