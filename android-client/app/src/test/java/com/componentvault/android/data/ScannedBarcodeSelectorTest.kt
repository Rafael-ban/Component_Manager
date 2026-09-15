package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScannedBarcodeSelectorTest {
    @Test fun textEntryAcceptsExistingJlcQrShapeAndBareUniqueSku() {
        assertEquals("C70565", ComponentImportParser.parseJlcText("on:masked,pc:C70565,pm:ABC-1,qty:2,mc:M1,cc:1,pdi:masked,hp:A").sku)
        assertEquals("C70565", ComponentImportParser.parseJlcText("C70565").sku)
    }

    @Test fun invalidPcDoesNotUseOrderOrUrlProductNumberAsSku() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ComponentImportParser.parseScannedQr("on:C70565,pc:12345,url:https://item.szlcsc.com/98765.html")
        }
    }

    @Test fun selectsParseableCodeAndRequiresChoiceForDifferentSkus() {
        val single=ScannedBarcodeSelector.importCandidates(listOf("https://example.test/123", "pc:C70565,qty:1"))
        assertEquals("pc:C70565,qty:1",assertIs<BarcodeSelection.Single>(single).rawValue)
        val multiple=assertIs<BarcodeSelection.Multiple>(ScannedBarcodeSelector.importCandidates(listOf("pc:C70565,qty:1","pc:C30926,qty:1")))
        assertEquals(setOf("C70565","C30926"),multiple.candidates.map{it.sku}.toSet())
        val batches=assertIs<BarcodeSelection.Multiple>(ScannedBarcodeSelector.importCandidates(listOf("pc:C70565,qty:1","pc:C70565,qty:20")))
        assertEquals(listOf(1,20),batches.candidates.map{it.quantity})
        assertTrue(ScannedBarcodeSelector.importCandidates(listOf("on:123,url:https://x/44")) is BarcodeSelection.NoMatch)
    }

    @Test fun movementWarehouseLabelRemainsAValidCandidate(){
        val raw="cvl2|C7430468|1x2P%20PicoBlade|Connector|SMD%2CP%3D1.25mm|ZX-MX1.25-2PWT|Megastar|300"
        val selected=assertIs<BarcodeSelection.Single>(ScannedBarcodeSelector.importCandidates(listOf("unknown",raw)))
        assertEquals(raw,selected.rawValue)
    }
}
