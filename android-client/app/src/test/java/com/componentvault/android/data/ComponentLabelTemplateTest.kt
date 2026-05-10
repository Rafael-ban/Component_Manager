package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComponentLabelTemplateTest {
    @Test
    fun standardTemplateKeepsExistingBaselineDimensions() {
        assertEquals(960, ComponentLabelTemplate.Standard.width)
        assertEquals(480, ComponentLabelTemplate.Standard.height)
        assertEquals(260, ComponentLabelTemplate.Standard.qrSize)
    }

    @Test
    fun qrSizeScalesWithSelectedTemplate() {
        assertTrue(ComponentLabelTemplate.Compact.qrSize < ComponentLabelTemplate.Standard.qrSize)
        assertTrue(ComponentLabelTemplate.Large.qrSize > ComponentLabelTemplate.Standard.qrSize)
    }

    @Test
    fun compactTemplateDropsSecondaryMetadataRows() {
        assertFalse(ComponentLabelTemplate.Compact.showsSecondaryFields)
        assertTrue(ComponentLabelTemplate.Standard.showsSecondaryFields)
        assertTrue(ComponentLabelTemplate.Large.showsSecondaryFields)
    }
}
