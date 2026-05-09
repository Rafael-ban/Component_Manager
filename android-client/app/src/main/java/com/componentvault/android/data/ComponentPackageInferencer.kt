package com.componentvault.android.data

import java.util.Locale

internal object ComponentPackageInferencer {
    private val packagePatterns = listOf(
        Regex("\\b(?:0201|0402|0603|0805|1206|1210|1812|2010|2512)\\b", RegexOption.IGNORE_CASE),
        Regex(
            "\\b(?:SOT-?23(?:-\\d+)?|SOT-?223|SOP-?\\d+|SOIC-?\\d+|SSOP-?\\d+|TSSOP-?\\d+|MSOP-?\\d+|QFN-?\\d+|DFN-?\\d+|QFP-?\\d+|LQFP-?\\d+|BGA-?\\d+|DIP-?\\d+|TO-?92|TO-?220|SMA|SMB|SMC)\\b",
            RegexOption.IGNORE_CASE,
        ),
    )

    fun infer(vararg values: String?): String? {
        val haystack = values
            .filterNotNull()
            .joinToString(separator = " ")
        if (haystack.isBlank()) {
            return null
        }

        return packagePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(haystack)?.value?.uppercase(Locale.US)
        }
    }
}
