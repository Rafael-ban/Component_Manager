package com.componentvault.android.ui.screen

import android.content.Context

internal const val DEFAULT_BATCH_SCAN_INTERVAL_MS = 1_500L
internal const val MIN_BATCH_SCAN_INTERVAL_MS = 500L
internal const val MAX_BATCH_SCAN_INTERVAL_MS = 5_000L
internal const val BATCH_SCAN_INTERVAL_STEP_MS = 500L

internal data class BatchScannerSettings(
    val intervalMs: Long = DEFAULT_BATCH_SCAN_INTERVAL_MS,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
) {
    fun normalized() = copy(
        intervalMs = intervalMs
            .coerceIn(MIN_BATCH_SCAN_INTERVAL_MS, MAX_BATCH_SCAN_INTERVAL_MS)
            .let { MIN_BATCH_SCAN_INTERVAL_MS +
                ((it - MIN_BATCH_SCAN_INTERVAL_MS) / BATCH_SCAN_INTERVAL_STEP_MS) *
                BATCH_SCAN_INTERVAL_STEP_MS },
    )
}

internal class BatchScannerSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): BatchScannerSettings = BatchScannerSettings(
        intervalMs = preferences.getLong(KEY_INTERVAL_MS, DEFAULT_BATCH_SCAN_INTERVAL_MS),
        soundEnabled = preferences.getBoolean(KEY_SOUND, true),
        vibrationEnabled = preferences.getBoolean(KEY_VIBRATION, true),
    ).normalized()

    fun save(settings: BatchScannerSettings) {
        val value = settings.normalized()
        preferences.edit()
            .putLong(KEY_INTERVAL_MS, value.intervalMs)
            .putBoolean(KEY_SOUND, value.soundEnabled)
            .putBoolean(KEY_VIBRATION, value.vibrationEnabled)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "batch_scanner_settings"
        const val KEY_INTERVAL_MS = "capture_interval_ms"
        const val KEY_SOUND = "success_sound"
        const val KEY_VIBRATION = "success_vibration"
    }
}

