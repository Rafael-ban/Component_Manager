package com.componentvault.android.ui.screen

internal sealed interface ContinuousCaptureDecision {
    data class Ready(val values: List<String>) : ContinuousCaptureDecision
    data class Duplicate(val duplicateCount: Int) : ContinuousCaptureDecision
    data class CoolingDown(val remainingMs: Long) : ContinuousCaptureDecision
}

/** Session-scoped gate. A value becomes seen only after the queue classifies it. */
internal class ContinuousCaptureGate {
    private val acceptedValues = mutableSetOf<String>()
    private var nextCaptureAtMs = Long.MIN_VALUE

    fun evaluate(values: List<String>, nowMs: Long): ContinuousCaptureDecision {
        val normalized = values.map(String::trim).filter(String::isNotBlank).distinct()
        val fresh = normalized.filterNot(acceptedValues::contains)
        if (fresh.isEmpty()) return ContinuousCaptureDecision.Duplicate(normalized.size)
        if (nowMs < nextCaptureAtMs) {
            return ContinuousCaptureDecision.CoolingDown(nextCaptureAtMs - nowMs)
        }
        return ContinuousCaptureDecision.Ready(fresh)
    }

    fun acknowledgeAccepted(values: List<String>, nowMs: Long, intervalMs: Long) {
        acceptedValues += values.map(String::trim).filter(String::isNotBlank)
        nextCaptureAtMs = nowMs + intervalMs.coerceAtLeast(0L)
    }

    fun acknowledgeDuplicate(values: List<String>) {
        acceptedValues += values.map(String::trim).filter(String::isNotBlank)
    }
}

internal class ContinuousCaptureSuccessHold(
    private val durationMs: Long = 800L,
) {
    private var visibleUntilMs = Long.MIN_VALUE

    fun markSuccess(nowMs: Long) {
        visibleUntilMs = nowMs + durationMs.coerceAtLeast(0L)
    }

    fun canReplace(nowMs: Long): Boolean = nowMs >= visibleUntilMs
}
