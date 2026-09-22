package com.componentvault.android.data

import java.util.Locale

internal object OfficialCategoryNormalizer {
    private val legacyExactPaths = mapOf(
        "crystals, oscillators, resonators/crystals" to "晶体",
        "capacitors/ceramic capacitors" to "电容",
        "resistors/chip resistor - surface mount" to "电阻",
        "integrated circuits (ics)/embedded/microcontrollers" to "微控制器",
        "led drivers/led drivers ics" to "LED驱动",
    )

    private val knownEnglishSegments = mapOf(
        "capacitors" to "电容器",
        "ceramic capacitors" to "陶瓷电容器",
        "aluminum electrolytic capacitors" to "铝电解电容器",
        "tantalum capacitors" to "钽电容器",
        "film capacitors" to "薄膜电容器",
        "resistors" to "电阻器",
        "chip resistor - surface mount" to "贴片电阻",
        "through hole resistors" to "直插电阻",
        "current sense resistors" to "采样电阻",
        "inductors, coils, chokes" to "电感器/线圈/扼流圈",
        "fixed inductors" to "固定电感器",
        "ferrite beads and chips" to "磁珠",
        "integrated circuits (ics)" to "集成电路",
        "embedded" to "嵌入式处理器及控制器",
        "microcontrollers" to "微控制器",
        "power management (pmic)" to "电源管理芯片",
        "voltage regulators - linear" to "线性稳压器",
        "voltage regulators - linear, low drop out (ldo) regulators" to "线性稳压器（LDO）",
        "dc dc switching regulators" to "DC-DC开关稳压器",
        "memory" to "存储器",
        "logic" to "逻辑器件",
        "amplifiers" to "放大器",
        "operational amplifiers" to "运算放大器",
        "diodes" to "二极管",
        "rectifiers" to "整流器",
        "transistors" to "晶体管",
        "mosfets" to "MOS管",
        "optoelectronics" to "光电器件",
        "led indication - discrete" to "LED指示器件",
        "sensors" to "传感器",
        "temperature sensors" to "温度传感器",
        "connectors, interconnects" to "连接器",
        "headers, male pins" to "排针",
        "rectangular connectors - housings" to "矩形连接器外壳",
        "terminal blocks" to "接线端子",
        "crystals, oscillators, resonators" to "晶体/振荡器/谐振器",
        "crystals" to "晶体",
        "oscillators" to "振荡器",
        "led drivers" to "LED驱动器",
        "led drivers ics" to "LED驱动芯片",
    )

    fun normalize(officialPath: String): String {
        val path = officialPath.trim()
        if (path.isEmpty() || path.any { it.code in 0x4E00..0x9FFF }) return path
        legacyExactPaths[path.lowercase(Locale.ROOT)]?.let { return it }
        val segments = path.split('/').map(String::trim).filter(String::isNotEmpty)
        if (segments.isEmpty()) return path
        return knownEnglishSegments[segments.last().lowercase(Locale.ROOT)] ?: path
    }
}
