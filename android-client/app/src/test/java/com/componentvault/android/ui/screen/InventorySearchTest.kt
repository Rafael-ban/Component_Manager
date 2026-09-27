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

    @Test fun searchesParameterValuesAndChineseEnglishFieldAliasesWithoutNameChanges() {
        val capacitor = component.copy(name = "Maker C0603", category = "电容",
            description = "型号：C0603\n参数：容量：100nF\n参数：耐压：50V\n参数：精度：±10%")
        assertTrue(InventorySearch.matches(capacitor, "capacitance 100nf"))
        assertTrue(InventorySearch.matches(capacitor, "voltage 50v"))
        assertTrue(InventorySearch.matches(capacitor, "耐压 50V"))
        assertFalse(InventorySearch.matches(capacitor, "105nF"))
        val inductor = component.copy(name = "Maker L2520", category = "电感",
            description = "参数：电感量：1.5μH")
        assertTrue(InventorySearch.matches(inductor, "inductance 1.5uH"))
    }
    @Test fun searchesConnectedParameterAliasAndValue() {
        val capacitor = component.copy(category = "电容", name = "Maker C0603",
            description = "参数：额定电压(V)：50\n参数：容量：100nF")
        assertTrue(InventorySearch.matches(capacitor, "耐压50V"))
        assertTrue(InventorySearch.matches(capacitor, "voltage50v"))
        assertTrue(InventorySearch.matches(capacitor, "capacitance100nf"))
        assertTrue(InventorySearch.matches(component, "阻值10kohm"))
    }
}
