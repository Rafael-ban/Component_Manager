package com.componentvault.android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

internal enum class InventoryWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal data class InventoryLayoutMode(
    val widthClass: InventoryWidthClass,
    val usesNavigationRail: Boolean,
    val showsListDetail: Boolean,
    val prefersDialogForms: Boolean,
)

@Composable
internal fun rememberInventoryLayoutMode(): InventoryLayoutMode {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    return remember(screenWidthDp) {
        val widthClass = when {
            screenWidthDp >= 840 -> InventoryWidthClass.Expanded
            screenWidthDp >= 600 -> InventoryWidthClass.Medium
            else -> InventoryWidthClass.Compact
        }

        InventoryLayoutMode(
            widthClass = widthClass,
            usesNavigationRail = widthClass != InventoryWidthClass.Compact,
            showsListDetail = widthClass != InventoryWidthClass.Compact,
            prefersDialogForms = widthClass != InventoryWidthClass.Compact,
        )
    }
}
