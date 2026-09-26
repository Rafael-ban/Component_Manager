package com.componentvault.android.ui.screen

import com.componentvault.android.model.ComponentRecord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventorySearchTest {
    private val component = ComponentRecord(
        id = "1", sku = "C30926", name = "RC-0603FR-0710KL", category = "电阻",
        packageName = "0603", location = "A-12", description = "型号：RC-0603FR-0710KL\n参数：阻值：10kΩ\n参数：精度：±1%",
        quantity = 10, minStock = 1, updatedAt = "2026-09-27", deleted = false,
    )

    @Test fun matchesMultipleTermsAcrossFieldsAndStoredSpecifications() {
        assertTrue(InventorySearch.matches(component, "c309 10KΩ"))
        assertTrue(InventorySearch.matches(component, "10kohm"))
        assertTrue(InventorySearch.matches(component, "0603FR 1%"))
        assertTrue(InventorySearch.matches(component, "阻值 A12"))
        assertFalse(InventorySearch.matches(component, "10kΩ 电容"))
    }

    @Test fun normalizesFullWidthTextAndModelSeparatorsWithoutDigitTypos() {
        assertTrue(InventorySearch.matches(component, "ＲＣ０６０３ＦＲ０７１０ＫＬ"))
        assertTrue(InventorySearch.matches(component, "rc_0603fr_0710kl"))
        assertTrue(InventorySearch.matches(component.copy(name = "ABC.123"), "ABC123"))
        assertFalse(InventorySearch.matches(component.copy(name = "10.5kΩ", description = ""), "105kΩ"))
        assertFalse(InventorySearch.matches(component, "C30927"))
        assertFalse(InventorySearch.matches(component, "-"))
    }

    @Test fun searchesBrandAlreadyStoredInImportDescription() {
        val branded = component.copy(description = component.description + "\n品牌：Yageo\n识别厂商：国巨")
        assertTrue(InventorySearch.matches(branded, "YAGEO 10kohm"))
        assertTrue(InventorySearch.matches(branded, "国巨 C309"))
    }
}
