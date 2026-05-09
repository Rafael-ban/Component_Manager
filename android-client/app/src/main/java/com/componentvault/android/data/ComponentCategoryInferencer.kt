package com.componentvault.android.data

import java.util.Locale

internal object ComponentCategoryInferencer {
    fun infer(vararg values: String?): String {
        val haystack = values
            .filterNotNull()
            .joinToString(separator = " ")
            .lowercase(Locale.US)

        return when {
            haystack.contains("connector") || haystack.contains("usb") || haystack.contains("pico") ||
                haystack.contains("header") || haystack.contains("socket") || haystack.contains("\u8FDE\u63A5") ->
                "Connector"

            haystack.contains("capacitor") || haystack.contains("ceramic capacitor") ||
                haystack.contains("electrolytic capacitor") || haystack.contains("mlcc") ||
                haystack.contains("chip capacitor") || haystack.contains("cap") ||
                haystack.contains("uf") || haystack.contains("nf") || haystack.contains("pf") ||
                haystack.contains("\u7535\u5BB9") ->
                "Capacitor"

            haystack.contains("resistor") || haystack.contains("chip resistor") ||
                haystack.contains("thick film resistor") || haystack.contains("metal film resistor") ||
                haystack.contains("ohm") || haystack.contains("\u7535\u963B") ->
                "Resistor"

            haystack.contains("inductor") || haystack.contains("coil") || haystack.contains("\u7535\u611F") ->
                "Inductor"

            haystack.contains("diode") || haystack.contains("schottky") ||
                haystack.contains("tvs") || haystack.contains("\u4E8C\u6781\u7BA1") ->
                "Diode"

            haystack.contains("mosfet") || haystack.contains("transistor") || haystack.contains("bjt") ||
                haystack.contains("\u4E09\u6781\u7BA1") ->
                "Transistor"

            haystack.contains("mcu") || haystack.contains("stm32") || haystack.contains("ic") ||
                haystack.contains("controller") || haystack.contains("amplifier") ||
                haystack.contains("comparator") || haystack.contains("\u82AF\u7247") ->
                "IC"

            haystack.contains("led") -> "LED"
            else -> "General"
        }
    }
}
