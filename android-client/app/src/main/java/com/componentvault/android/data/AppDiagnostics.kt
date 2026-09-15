package com.componentvault.android.data

import java.time.Instant

internal object AppDiagnostics {
    private const val MaxEntries = 100
    private const val MaxChars = 16 * 1024
    private val allowed = setOf("scanner_session", "scanner_decode", "scanner_choice", "scanner_failure", "lookup_domestic", "lookup_international")
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun record(event: String, vararg fields: Pair<String, Any?>) {
        if (event !in allowed) return
        val safe = fields.mapNotNull { (key, value) ->
            if (!key.matches(Regex("[a-z_]{1,24}"))) null else when (value) {
                is Number, is Boolean -> "$key=$value"
                is Enum<*> -> "$key=${value.name}"
                is Class<*> -> "$key=${value.simpleName}"
                else -> null
            }
        }
        entries += Instant.now().toString() + " event=$event" +
            if (safe.isEmpty()) "" else " " + safe.joinToString(" ")
        while (entries.size > MaxEntries || entries.sumOf { it.length + 1 } > MaxChars) entries.removeFirst()
    }

    @Synchronized fun report(): String = entries.takeIf { it.isNotEmpty() }?.joinToString("\n")
        ?: "No diagnostic events recorded in this app session."
    @Synchronized fun clear() = entries.clear()
}
