package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComponentLabelTemplateTest {
    @Test
    fun qrLabelSpecsUseResolvedContentDimensions() {
        assertEquals(40f, ComponentLabelTemplate.Qr10x40.physicalWidthMm)
        assertEquals(10f, ComponentLabelTemplate.Qr10x40.physicalHeightMm)
        assertEquals(10f, ComponentLabelTemplate.Qr10x40.displayWidthMm)
        assertEquals(40f, ComponentLabelTemplate.Qr10x40.displayHeightMm)
        assertEquals(ComponentLabelOrientation.Landscape, ComponentLabelTemplate.Qr10x40.orientation)
        assertEquals(8.8f, ComponentLabelTemplate.Qr10x40.qrSizeMm)

        assertEquals(40f, ComponentLabelTemplate.Qr30x40.physicalWidthMm)
        assertEquals(30f, ComponentLabelTemplate.Qr30x40.physicalHeightMm)
        assertEquals(30f, ComponentLabelTemplate.Qr30x40.displayWidthMm)
        assertEquals(40f, ComponentLabelTemplate.Qr30x40.displayHeightMm)
        assertEquals(ComponentLabelOrientation.Landscape, ComponentLabelTemplate.Qr30x40.orientation)
        assertEquals(16f, ComponentLabelTemplate.Qr30x40.qrSizeMm)
        assertEquals(ComponentLabelLayoutMode.LandscapeLeftQrRightStack, ComponentLabelTemplate.Qr30x40.layoutMode)
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
        assertNull(ComponentLabelTemplate.TextOnly.physicalWidthMm)
        assertEquals(0.5f, ComponentLabelTemplate.TextOnly.textHeightMm)
        assertFalse(ComponentLabelTemplate.TextOnly.supportsCompanionTextLabel)
        assertTrue(ComponentLabelTemplate.Qr10x40.supportsCompanionTextLabel)
    }
}
