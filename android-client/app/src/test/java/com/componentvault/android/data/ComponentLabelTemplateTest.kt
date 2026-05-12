package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComponentLabelTemplateTest {
    @Test
    fun qrLabelSpecsUsePhysicalMillimeterDimensions() {
        assertEquals(10f, ComponentLabelTemplate.Qr10x40.widthMm)
        assertEquals(40f, ComponentLabelTemplate.Qr10x40.heightMm)
        assertEquals(8f, ComponentLabelTemplate.Qr10x40.qrSizeMm)

        assertEquals(30f, ComponentLabelTemplate.Qr30x40.widthMm)
        assertEquals(40f, ComponentLabelTemplate.Qr30x40.heightMm)
        assertEquals(18f, ComponentLabelTemplate.Qr30x40.qrSizeMm)
    }

    @Test
    fun payloadModesFollowSelectedLabelSpec() {
        assertEquals(ComponentLabelPayloadMode.CompactOffline, ComponentLabelTemplate.Qr10x40.payloadMode)
        assertEquals(ComponentLabelPayloadMode.StandardWarehouse, ComponentLabelTemplate.Qr30x40.payloadMode)
        assertEquals(ComponentLabelPayloadMode.None, ComponentLabelTemplate.TextOnly.payloadMode)
    }

    @Test
    fun textOnlyLabelIsIndependentAndAutoWidth() {
        assertFalse(ComponentLabelTemplate.TextOnly.isQrLabel)
        assertNull(ComponentLabelTemplate.TextOnly.widthMm)
        assertEquals(0.5f, ComponentLabelTemplate.TextOnly.textHeightMm)
        assertFalse(ComponentLabelTemplate.TextOnly.supportsCompanionTextLabel)
        assertTrue(ComponentLabelTemplate.Qr10x40.supportsCompanionTextLabel)
    }
}
