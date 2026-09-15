package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDiagnosticsTest {
    @Test fun ringIsBoundedAndDropsNonAllowlistedOrTextFields() {
        AppDiagnostics.clear()
        AppDiagnostics.record("not_allowed", "value" to 1)
        repeat(150){AppDiagnostics.record("scanner_decode","width" to it,"raw" to "secret-order-url")}
        val report=AppDiagnostics.report()
        assertTrue(report.lines().size<=100);assertTrue(report.length<=16*1024)
        assertFalse(report.contains("secret-order-url"));assertFalse(report.contains("not_allowed"))
    }

    @Test fun emptyInternationalMetadataIsLoggedAsUnmatchedAndCacheHitIsExplicit(){
        AppDiagnostics.clear()
        val lookup=LcscPublicLookup(fetch={"<html></html>"},now={1_000L})
        lookup.lookup("C70565");lookup.lookup("C70565")
        val report=AppDiagnostics.report()
        assertTrue(report.contains("cache=false matched=false"))
        assertTrue(report.contains("cache=true matched=false"))
        assertFalse(report.contains("success=true"))
    }
}
