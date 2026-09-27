package com.componentvault.android.ui.screen

import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.officialParameterSearchAliases
import java.text.Normalizer
import java.util.Locale

internal object InventorySearch {
    fun matches(component: ComponentRecord, query: String): Boolean {
        val terms = Normalizer.normalize(query, Normalizer.Form.NFKC)
            .trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return true

        val fields = listOf(
            component.name, component.sku, component.category, component.packageName,
            component.location, component.description, component.officialParameterSearchAliases(),
        ).map(::normalize).map { it to compact(it) }
        return terms.all { term ->
            val needle = normalize(term)
            val compactNeedle = compact(needle)
            needle.any(Char::isLetterOrDigit) && (
                fields.any { (field, compactField) ->
                    field.contains(needle) || (compactNeedle.isNotEmpty() && compactField.contains(compactNeedle))
                } || CategoryFilterSemantics.matchesSearch(component.category, term)
            )
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace("μ", "u").replace("ω", "ohm")

    private fun compact(value: String): String = value
        .replace(Regex("[\\s_\\-/\\\\]+"), "")
        .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), "")
}
