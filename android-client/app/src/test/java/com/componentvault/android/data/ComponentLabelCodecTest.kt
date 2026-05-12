package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComponentLabelCodecTest {
    private val seed = ComponentLabelSeed(
        sku = "C7430468",
        name = "1x2P 间距:1.25mm 卧贴",
        category = "Connector",
        packageName = "SMD,P=1.25mm",
        location = "Drawer-A1",
        quantity = 300,
        minStock = 60,
        model = "ZX-MX1.25-2PWT",
        brand = "Megastar",
    )

    @Test
    fun compactQrPayloadRoundTripsFor10x40Labels() {
        val payload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, ComponentLabelTemplate.Qr10x40))

        assertEquals(ComponentLabelPayloadMode.CompactOffline, payload.mode)
        assertTrue(payload.rawValue.startsWith("cvl2|"))

        val parsed = assertNotNull(ComponentLabelCodec.parseScannedPayload(payload.rawValue))
        assertEquals(seed.sku, parsed.sku)
        assertEquals(seed.name, parsed.name)
        assertEquals(seed.packageName, parsed.packageName)
        assertEquals(seed.quantity, parsed.suggestedQuantity)
    }

    @Test
    fun standardWarehousePayloadStillParses() {
        val payload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, ComponentLabelTemplate.Qr30x40))

        assertEquals(ComponentLabelPayloadMode.StandardWarehouse, payload.mode)
        assertTrue(payload.rawValue.contains("\"fmt\":\"component-vault-label\""))

        val parsed = assertNotNull(ComponentLabelCodec.parseScannedPayload(payload.rawValue))
        assertEquals(seed.location, parsed.notes.first().removePrefix("Warehouse location: "))
    }

    @Test
    fun jlcSourcesKeepCompatiblePayloadFor30x40() {
        val jlcSeed = seed.copy(
            sourceLabel = "JLC package QR",
            rawPayload = "{on:SO25020715054,pc:C30926,pm:0603B104K500NT,qty:300}",
        )

        val payload = requireNotNull(ComponentLabelCodec.buildQrPayload(jlcSeed, ComponentLabelTemplate.Qr30x40))

        assertEquals(ComponentLabelPayloadMode.JlcCompatible, payload.mode)
        assertTrue(payload.rawValue.startsWith("{on:"))
    }
}
