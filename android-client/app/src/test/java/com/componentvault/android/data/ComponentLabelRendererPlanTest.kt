package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun miaomiaojiCanvasWrapsResolved10x40Label() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr10x40,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
            canvasTemplate = ComponentLabelPrintCanvasTemplate.Miaomiaoji57x79,
        )

        val page = pages.single()
        assertEquals(57f, page.canvasWidthMm)
        assertEquals(79f, page.canvasHeightMm)
        assertEquals(40f, page.contentWidthMm)
        assertEquals(10f, page.contentHeightMm)
        assertTrue(page.contentLeftMm > 0f)
        assertTrue(page.contentTopMm > 0f)
    }

    @Test
    fun rawCanvasUsesTemplateBoundsFor30x40() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr30x40,
            includeCompanionTextLabel = false,
            textTemplate = ComponentTextLabelTemplate.NameSku,
            canvasTemplate = ComponentLabelPrintCanvasTemplate.RawLabel,
        )

        val page = pages.single()
        assertEquals(30f, page.canvasWidthMm)
        assertEquals(40f, page.canvasHeightMm)
        assertEquals(0f, page.contentLeftMm)
        assertEquals(0f, page.contentTopMm)
    }

    @Test
    fun companionTextPageRespectsSelectedCanvas() {
        val pages = ComponentLabelRenderer.resolvePageSpecs(
            seed = seed,
            template = ComponentLabelTemplate.Qr30x40,
            includeCompanionTextLabel = true,
            textTemplate = ComponentTextLabelTemplate.NamePackageSku,
            canvasTemplate = ComponentLabelPrintCanvasTemplate.Miaomiaoji57x79,
        )

        assertEquals(2, pages.size)
        assertEquals(ComponentLabelTemplate.Qr30x40, pages.first().template)
        assertEquals(ComponentLabelTemplate.TextOnly, pages.last().template)
        assertEquals(57f, pages.last().canvasWidthMm)
        assertEquals(79f, pages.last().canvasHeightMm)
        assertTrue(pages.last().contentWidthMm > 0f)
    }
}
