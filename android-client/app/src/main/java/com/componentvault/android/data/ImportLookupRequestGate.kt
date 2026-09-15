package com.componentvault.android.data

import com.componentvault.android.model.ComponentImportCandidate

internal data class ImportLookupAttempt(
    val generation: Int,
    val samePayload: Boolean,
    val replaceDisplayedCandidate: Boolean,
)

internal class ImportLookupRequestGate {
    private var generation = 0
    private var payload: String? = null

    fun begin(rawPayload: String, hasDisplayedCandidate: Boolean): ImportLookupAttempt {
        val same = payload == rawPayload
        payload = rawPayload
        generation += 1
        return ImportLookupAttempt(generation, same, !same || !hasDisplayedCandidate)
    }

    fun isCurrent(value: Int): Boolean = value == generation

    fun candidateForLookup(base: ComponentImportCandidate, displayed: ComponentImportCandidate?): ComponentImportCandidate =
        displayed?.takeIf { it.rawPayload == base.rawPayload } ?: base
}
