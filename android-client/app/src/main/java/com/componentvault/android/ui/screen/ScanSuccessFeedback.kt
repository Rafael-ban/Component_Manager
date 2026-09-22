package com.componentvault.android.ui.screen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class ScanSuccessFeedback(context: Context) : AutoCloseable {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun play(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) runCatching {
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
        val canVibrate = vibrationEnabled && runCatching {
            vibrator?.hasVibrator() == true
        }.getOrDefault(false)
        if (canVibrate) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    override fun close() {
        runCatching { tone?.release() }
        runCatching { vibrator?.cancel() }
    }
}
