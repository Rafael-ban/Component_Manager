package com.componentvault.android.data

import java.util.Locale

internal object OfficialCategoryNormalizer {
    private val exactEnglishPaths = mapOf(
        "crystals, oscillators, resonators/crystals" to "晶体",
        "capacitors/ceramic capacitors" to "电容",
        "resistors/chip resistor - surface mount" to "电阻",
        "integrated circuits (ics)/embedded/microcontrollers" to "微控制器",
        "led drivers/led drivers ics" to "LED驱动",
    )

    fun normalize(officialPath: String): String {
        val path = officialPath.trim()
        if (path.isEmpty() || path.any { it.code in 0x4E00..0x9FFF }) return path
        return exactEnglishPaths[path.lowercase(Locale.ROOT)] ?: path
    }
}
