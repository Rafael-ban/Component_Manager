package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComponentLabelRendererPlanTest {
    private val seed = ComponentLabelSeed(
        sku = "C7430468",
        name = "1x2P 1.25mm PicoBlade",
        category = "Connector",
        packageName = "SMD,P=1.25mm,卧贴",
        location = "Drawer-A1",
        quantity = 300,
        minStock = 60,
        model = "ZX-MX1.25-2PWT",
        brand = "Megastar",
    )

    @Test
    fun qrLabelsExportAtTheirOwnResolvedSize() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr10x40,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
        )

        val page = pages.single()
        assertEquals(ComponentLabelTemplate.Qr10x40, page.template)
        assertEquals(40f, page.canvasWidthMm)
        assertEquals(10f, page.canvasHeightMm)
        assertEquals(40f, page.contentWidthMm)
        assertEquals(10f, page.contentHeightMm)
        assertEquals(0f, page.contentLeftMm)
        assertEquals(0f, page.contentTopMm)
    }

    @Test
    fun qr30x40UsesLeftQrRightDetailsLayoutAndOwnPageBounds() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr30x40,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
        )

        val page = pages.single()
        assertEquals(30f, page.canvasWidthMm)
        assertEquals(40f, page.canvasHeightMm)
        assertEquals(0f, page.contentLeftMm)
        assertEquals(0f, page.contentTopMm)
        assertEquals(ComponentLabelLayoutMode.LeftQrRightDetails, page.template.layoutMode)
    }

    @Test
    fun companionTextPageUsesIndependentAutoWidthWithoutCanvasWrapper() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr30x40,
            includeCompanionTextLabel = true,
            textTemplate = ComponentTextLabelTemplate.NamePackageSku,
        )

        assertEquals(2, pages.size)
        assertEquals(ComponentLabelTemplate.Qr30x40, pages.first().template)
        assertEquals(ComponentLabelTemplate.TextOnly, pages.last().template)
        assertEquals(ComponentLabelTemplate.TextOnly.heightMm, pages.last().canvasHeightMm)
        assertEquals(0f, pages.last().contentLeftMm)
        assertEquals(0f, pages.last().contentTopMm)
        assertTrue(pages.last().contentWidthMm > 0f)
        assertNotNull(pages.last().textOnlyFontSizeMm)
    }

    @Test
    fun textOnlyLabelPrefersLargerFontsAndShrinksLongContent() {
        val shortPage = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed.copy(name = "10uF"),
            template = ComponentLabelTemplate.TextOnly,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
        ).single()
        val longPage = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed.copy(name = "Very Long Ceramic Capacitor Label For Dense Drawer Bins"),
            template = ComponentLabelTemplate.TextOnly,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
        ).single()

        val shortFont = requireNotNull(shortPage.textOnlyFontSizeMm)
        val longFont = requireNotNull(longPage.textOnlyFontSizeMm)
        assertTrue(shortFont >= longFont)
        assertTrue(shortFont > 1.5f)
        assertTrue(longPage.canvasWidthMm <= 120f)
    }
}
