package com.componentvault.android.ui.screen

import com.componentvault.android.data.OfficialCategoryNormalizer
import java.util.Locale

internal object CategoryFilterSemantics {
    fun key(category: String): String = OfficialCategoryNormalizer.normalize(category)

    fun options(categories: Iterable<String>): List<String> = categories
        .map(String::trim)
        .filter(String::isNotBlank)
        .groupBy { key(it).lowercase(Locale.ROOT) }
        .values
        .map { group -> group.firstOrNull { key(it) != it } ?: group.first() }
        .sortedBy { it.lowercase(Locale.ROOT) }

    fun sameCategory(rawCategory: String, selectedKey: String): Boolean =
        key(rawCategory).equals(key(selectedKey), ignoreCase = true)

    fun matchesSearch(rawCategory: String, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return rawCategory.contains(needle, ignoreCase = true) ||
            key(rawCategory).contains(needle, ignoreCase = true) ||
            localizedCategoryLabel(rawCategory, Locale.SIMPLIFIED_CHINESE).contains(needle, ignoreCase = true)
    }
}
