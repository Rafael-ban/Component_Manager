package com.componentvault.android.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContinuousCaptureGateTest {
    @Test
    fun scannerSettingsUseRequestedDefaultRangeAndStep() {
        assertEquals(1_500L, BatchScannerSettings().intervalMs)
        assertEquals(500L, BatchScannerSettings(intervalMs = 100).normalized().intervalMs)
        assertEquals(5_000L, BatchScannerSettings(intervalMs = 8_000).normalized().intervalMs)
        assertEquals(2_000L, BatchScannerSettings(intervalMs = 2_249).normalized().intervalMs)
    }

    @Test
    fun acceptedCodeStaysDuplicateAfterCooldownAndDoesNotBecomeReadyAgain() {
        val gate = ContinuousCaptureGate()
        assertIs<ContinuousCaptureDecision.Ready>(gate.evaluate(listOf("package-a"), 1_000))
        gate.acknowledgeAccepted(listOf("package-a"), 1_000, 1_500)

        assertIs<ContinuousCaptureDecision.Duplicate>(gate.evaluate(listOf("package-a"), 1_100))
        assertIs<ContinuousCaptureDecision.Duplicate>(gate.evaluate(listOf("package-a"), 8_000))
    }

    @Test
    fun intervalBlocksOnlyFreshCodesAfterAnAcceptedCapture() {
        val gate = ContinuousCaptureGate()
        gate.acknowledgeAccepted(listOf("package-a"), 1_000, 1_500)

        val cooling = assertIs<ContinuousCaptureDecision.CoolingDown>(
            gate.evaluate(listOf("package-b"), 2_000),
        )
        assertEquals(500L, cooling.remainingMs)
        assertEquals(
            listOf("package-b"),
            assertIs<ContinuousCaptureDecision.Ready>(
                gate.evaluate(listOf("package-b"), 2_500),
            ).values,
        )
    }

    @Test
    fun unacknowledgedValueIsImmediatelyRetryable() {
        val gate = ContinuousCaptureGate()
        assertIs<ContinuousCaptureDecision.Ready>(gate.evaluate(listOf("package-a"), 1_000))
        assertIs<ContinuousCaptureDecision.Ready>(gate.evaluate(listOf("package-a"), 1_001))
    }

    @Test
    fun queueDuplicateBecomesSessionDuplicateWithoutStartingCooldown() {
        val gate = ContinuousCaptureGate()
        gate.acknowledgeDuplicate(listOf("package-a"))

        assertIs<ContinuousCaptureDecision.Duplicate>(gate.evaluate(listOf("package-a"), 1_000))
        assertIs<ContinuousCaptureDecision.Ready>(gate.evaluate(listOf("package-b"), 1_001))
    }

    @Test
    fun successStatusCannotBeReplacedDuringMinimumVisibilityWindow() {
        val hold = ContinuousCaptureSuccessHold()
        hold.markSuccess(1_000)

        assertEquals(false, hold.canReplace(1_799))
        assertEquals(true, hold.canReplace(1_800))
    }
}
