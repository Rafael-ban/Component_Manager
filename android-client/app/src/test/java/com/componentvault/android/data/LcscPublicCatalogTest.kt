package com.componentvault.android.data

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import com.componentvault.android.model.ComponentImportFieldOrigin
import com.componentvault.android.model.withOfficialMetadata

class LcscPublicCatalogTest {
    private fun page(
        sku: String = "C70565",
        image: String = "",
        category: String = "Crystals, Oscillators, Resonators/Crystals",
    ) = """
        <script type="application/ld+json">{
          "@type":"Product","sku":"$sku","mpn":"X322512MOB4SI",
          "name":"YXC X322512MOB4SI","description":"Crystal 12MHz 12pF",
          "brand":{"name":"YXC"},"category":"$category"$image,
          "additionalProperty":[{"name":"Package","value":"SMD3225-4P"}],
          "offers":{"inventoryLevel":22075,"price":0.1}
        }</script>
    """.trimIndent()

    @Test fun readsOnlyExactProductAndPreservesPackagingQuantity() {
        val metadata = requireNotNull(LcscPublicCatalog.parsePage("c70565", page("C30926") + page()))
        assertEquals("C70565", metadata.sku)
        assertEquals("X322512MOB4SI", metadata.model)
        assertEquals("X322512MOB4SI", metadata.name)
        assertEquals("Crystal 12MHz 12pF", metadata.description)
        assertEquals("SMD3225-4P", metadata.packageName)
        val candidate = JlcImportParser.parseQr("{pc:C70565,qty:20}").withOfficialMetadata(metadata)
        assertEquals(20, candidate.suggestedQuantity)
        assertEquals(ComponentImportFieldOrigin.PublicWeb, candidate.fieldOrigins.name)
        assertEquals("X322512MOB4SI", candidate.name)
        assertTrue(candidate.notes.contains("官方描述：Crystal 12MHz 12pF"))
    }

    @Test fun readsTrustedImageStringAndArrayButRejectsOtherHosts() {
        val stringImage = LcscPublicCatalog.parsePage(
            "C70565",
            page(image = ",\"image\":\"https://assets.lcsc.com/images/lcsc/900x900/C70565.jpg\""),
        )
        assertEquals(
            "https://assets.lcsc.com/images/lcsc/900x900/C70565.jpg",
            stringImage?.imageUrl,
        )
        val arrayImage = LcscPublicCatalog.parsePage(
            "C70565",
            page(
                image = ",\"image\":[\"http://assets.lcsc.com/insecure.jpg\"," +
                    "\"https://www.lcsc.com/images/C70565.webp\"]",
            ),
        )
        assertEquals("https://www.lcsc.com/images/C70565.webp", arrayImage?.imageUrl)
        assertNull(
            LcscPublicCatalog.parsePage(
                "C70565",
                page(image = ",\"image\":\"https://evil.example/C70565.jpg\""),
            )?.imageUrl,
        )
        assertNull(LcscPublicCatalog.parsePage("C70565", page())?.imageUrl)
    }

    @Test fun officialCategoryWinsAndPreservesChineseOrUnknownPaths() {
        assertEquals("晶体", LcscPublicCatalog.parsePage("C70565", page())?.category)
        assertEquals("LED驱动", LcscPublicCatalog.parsePage("C70565", page(category = "LED Drivers/LED Drivers ICs"))?.category)
        assertEquals(
            "晶体/谐振器/晶振",
            LcscPublicCatalog.parsePage(
                "C70565",
                page(category = "晶体/谐振器/晶振"),
            )?.category,
        )
        assertEquals(
            "Official New Category/Exact Leaf",
            LcscPublicCatalog.parsePage(
                "C70565",
                page(category = "Official New Category/Exact Leaf"),
            )?.category,
        )
    }

    @Test fun rejectsVerificationPageAndRecommendationsForOtherProducts() {
        assertNull(LcscPublicCatalog.parsePage("C70565", "<title>Security verification C70565</title>"))
        assertNull(LcscPublicCatalog.parsePage("C70565", page("C30926")))
        assertNull(LcscPublicCatalog.parsePage("C70565", "<script type='application/ld+json'>{bad json}</script>"))
        assertNull(LcscPublicCatalog.productUrl("https://example.com/C70565"))
        assertNull(LcscPublicCatalog.productUrl("C70565/../other"))
    }

    @Test fun acceptsBareSkuAndProductUrlButRejectsAmbiguousCodes() {
        assertEquals("C70565", JlcImportParser.parseQr("c70565").sku)
        assertEquals("C70565", JlcImportParser.parseQr("https://www.lcsc.com/product-detail/C70565.html").sku)
        assertFailsWith<IllegalArgumentException> { JlcImportParser.parseQr("C70565 C30926") }
        assertFailsWith<IllegalArgumentException> { JlcImportParser.parseQr("https://item.szlcsc.com/70565.html") }
    }

    @Test fun cachesAndBacksOffNetworkFailuresWithoutCredentials() {
        var calls = 0
        var time = 1L
        val lookup = LcscPublicLookup(fetch = { calls++; page() }, now = { time })
        assertEquals("C70565", lookup.lookup("C70565")?.sku)
        lookup.lookup("c70565")
        assertEquals(1, calls)
        val failing = LcscPublicLookup(fetch = { calls++; throw IOException("offline") }, now = { time })
        assertFailsWith<IOException> { failing.lookup("C70565") }
        assertNull(failing.lookup("C70565"))
        assertEquals(2, calls)
        time += 30_001
        assertFailsWith<IOException> { failing.lookup("C70565") }
        assertEquals(3, calls)
    }
}
