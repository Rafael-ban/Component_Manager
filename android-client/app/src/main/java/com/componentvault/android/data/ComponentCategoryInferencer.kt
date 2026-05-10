package com.componentvault.android.data

import java.util.Locale

internal object ComponentCategoryInferencer {
    private val connectorModelPatterns = listOf(
        Regex("""\b(?:B|S)?\d+P?-?(?:PH|XH|GH|SH)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPicoBlade\b""", RegexOption.IGNORE_CASE),
        Regex("""\bMX\s?1\.25\b""", RegexOption.IGNORE_CASE),
    )

    private val capacitorModelPatterns = listOf(
        Regex(
            """\b(?:CC(?:0201|0402|0603|0805|1206|1210|1812)[A-Z0-9-]*|C1005[A-Z0-9-]*|C1608[A-Z0-9-]*|C2012[A-Z0-9-]*|C3216[A-Z0-9-]*|C3225[A-Z0-9-]*|GRM(?:033|155|188|216|31)[A-Z0-9-]*|GCM(?:155|188)[A-Z0-9-]*|CL(?:03|05|10|21|31)[A-Z0-9-]*)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\b(?:0201|0402|0603|0805|1206|1210|1812)[A-Z]{1,4}\d{3}(?:[A-Z][A-Z0-9-]*)?\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val resistorModelPatterns = listOf(
        Regex("""\bRC(?:0201|0402|0603|0805|1206|1210|2010|2512)[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:0201|0402|0603|0805|1206|1210|1218|2010|2512)(?:WAF|WGF)[A-Z0-9-]*\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\bCRCW(?:0201|0402|0603|0805|1206|1210|2010|2512)[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bERJ-?[2368][A-Z0-9-]*\b""", RegexOption.IGNORE_CASE),
    )

    private val inductorModelPatterns = listOf(
        Regex("""\bLQH[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:CD(?:RH|LG)|SWPA|MWSA)[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE),
    )

    fun infer(vararg values: String?): String {
        val normalizedValues = values
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
        val haystack = normalizedValues
            .joinToString(separator = " ")
            .lowercase(Locale.US)
        val rawJoined = normalizedValues.joinToString(separator = " ")

        return when {
            matchesAny(rawJoined, connectorModelPatterns) ||
                haystack.contains("connector") || haystack.contains("usb") || haystack.contains("pico") ||
                haystack.contains("header") || haystack.contains("socket") || haystack.contains("\u8FDE\u63A5") ->
                "Connector"

            matchesAny(rawJoined, capacitorModelPatterns) ||
                haystack.contains("capacitor") || haystack.contains("ceramic capacitor") ||
                haystack.contains("electrolytic capacitor") || haystack.contains("mlcc") ||
                haystack.contains("chip capacitor") || haystack.contains("cap") ||
                haystack.contains("uf") || haystack.contains("nf") || haystack.contains("pf") ||
                haystack.contains("\u7535\u5BB9") ->
                "Capacitor"

            matchesAny(rawJoined, resistorModelPatterns) ||
                haystack.contains("resistor") || haystack.contains("chip resistor") ||
                haystack.contains("thick film resistor") || haystack.contains("metal film resistor") ||
                haystack.contains("ohm") || haystack.contains("\u7535\u963B") ->
                "Resistor"

            matchesAny(rawJoined, inductorModelPatterns) ||
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

    private fun matchesAny(value: String, patterns: List<Regex>): Boolean =
        patterns.any { pattern -> pattern.containsMatchIn(value) }
}
