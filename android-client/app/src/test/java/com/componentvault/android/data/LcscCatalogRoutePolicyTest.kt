package com.componentvault.android.data

import com.componentvault.android.model.AppLanguage
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LcscCatalogRoutePolicyTest {
    private val domesticPage = """
        <script id="__NEXT_DATA__">{"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[{
          "lightProductModel":"中文型号","productVO":{"productCode":"C88001","productId":"88001"}
        }]}}}}}</script>
    """.trimIndent()

    private val internationalPage = """
        <script type="application/ld+json">{"@type":"Product","sku":"C88001","mpn":"EN-MODEL"}</script>
    """.trimIndent()

    @Test fun chineseLanguageAndChineseSystemLocalePreferDomestic() {
        assertEquals(LcscCatalogSource.Domestic, LcscCatalogRoutePolicy.preferredSource(AppLanguage.ZhCn))
        assertEquals(LcscCatalogSource.Domestic, LcscCatalogRoutePolicy.preferredSource(Locale.SIMPLIFIED_CHINESE))
        assertEquals(LcscCatalogSource.International, LcscCatalogRoutePolicy.preferredSource(Locale.ENGLISH))
    }

    @Test fun englishPrefersInternationalWithoutOverwritingItWithDomestic() {
        var domesticCalls = 0
        val result = LcscCombinedLookup(
            domesticFetch = { domesticCalls++; domesticPage },
            international = LcscPublicLookup(fetch = { internationalPage }),
        ).lookupWithRoute("C88001", LcscCatalogSource.International)

        assertEquals("lcsc_public_web", result.metadata?.source)
        assertEquals("EN-MODEL", result.metadata?.model)
        assertEquals(0, domesticCalls)
        assertFalse(result.usedFallback)
    }

    @Test fun blockedPreferredDomesticFallsBackAndReportsTheReason() {
        AppDiagnostics.clear()
        val result = LcscCombinedLookup(
            domesticFetch = { "<script>var _xvasu='challenge';</script>" },
            international = LcscPublicLookup(fetch = { internationalPage }),
        ).lookupWithRoute("C88001", LcscCatalogSource.Domestic)

        assertEquals("lcsc_public_web", result.metadata?.source)
        assertTrue(result.usedFallback)
        assertEquals(LcscCatalogFailureKind.Blocked, result.attempts.single().failure)
        assertFalse(LcscCatalogRoutePolicy.shouldPersist(result))
        assertTrue(AppDiagnostics.report().contains("failure=Blocked"))
    }
}
