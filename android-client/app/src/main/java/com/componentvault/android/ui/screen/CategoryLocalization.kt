package com.componentvault.android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private val zhCategoryMap = linkedMapOf(
    "General" to "通用",
    "Capacitor" to "电容",
    "Resistor" to "电阻",
    "Inductor" to "电感",
    "Ferrite Bead" to "磁珠",
    "Diode" to "二极管",
    "LED" to "发光二极管",
    "Indicator" to "指示器件",
    "Transistor" to "晶体管",
    "MOSFET" to "MOS 管",
    "Regulator" to "稳压器",
    "Power" to "电源",
    "Battery" to "电池",
    "Fuse" to "保险丝",
    "Relay" to "继电器",
    "Switch" to "开关",
    "Button" to "按键",
    "Connector" to "连接器",
    "Wire-to-board" to "线对板",
    "Board-to-board" to "板对板",
    "Cable" to "线缆",
    "Crystal" to "晶振",
    "Oscillator" to "振荡器",
    "Sensor" to "传感器",
    "MCU" to "单片机",
    "Microcontroller" to "单片机",
    "IC" to "集成电路",
    "Memory" to "存储器",
    "Logic" to "逻辑器件",
    "OpAmp" to "运放",
    "Amplifier" to "放大器",
    "Driver" to "驱动器",
    "Display" to "显示器件",
    "Module" to "模块",
    "RF" to "射频",
    "Antenna" to "天线",
    "Protection" to "保护器件",
)

@Composable
internal fun localizedCategoryLabel(category: String): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    return localizedCategoryLabel(category = category, locale = locale)
}

internal fun localizedCategoryLabel(category: String, locale: Locale): String {
    val normalized = category.trim()
    if (normalized.isBlank()) {
        return category
    }
    if (!locale.language.equals("zh", ignoreCase = true)) {
        return normalized
    }
    return normalized
        .split('/')
        .map { segment ->
            val trimmed = segment.trim()
            zhCategoryMap.entries.firstOrNull { (key, _) ->
                trimmed.equals(key, ignoreCase = true)
            }?.value ?: trimmed
        }
        .joinToString(separator = " / ")
}
