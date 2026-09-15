package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ImportLookupRequestGateTest {
    @Test fun samePayloadRetryKeepsDisplayedEnrichmentAndAdvancesGeneration() {
        val gate=ImportLookupRequestGate()
        val first=gate.begin("pc:C70565,qty:1",false)
        val retry=gate.begin("pc:C70565,qty:1",true)
        assertTrue(first.replaceDisplayedCandidate)
        assertTrue(retry.samePayload)
        assertFalse(retry.replaceDisplayedCandidate)
        assertFalse(gate.isCurrent(first.generation))
        assertTrue(gate.isCurrent(retry.generation))
        val base=ComponentImportParser.parseScannedQr("pc:C70565,qty:1")
        val enriched=base.copy(name="filled name")
        assertEquals("filled name",gate.candidateForLookup(base,enriched).name)
    }

    @Test fun latestDifferentPayloadIsTheOnlyApplicableGeneration() {
        val gate=ImportLookupRequestGate()
        val old=gate.begin("C70565",true)
        val latest=gate.begin("C30926",true)
        assertFalse(gate.isCurrent(old.generation))
        assertTrue(gate.isCurrent(latest.generation))
        assertTrue(latest.replaceDisplayedCandidate)
    }
}
