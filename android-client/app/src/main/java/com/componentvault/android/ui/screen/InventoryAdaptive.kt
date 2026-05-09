package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

@Composable
internal fun rememberContentPadding(
    contentPadding: PaddingValues,
    horizontal: Dp = 0.dp,
    vertical: Dp = 0.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current

    return remember(contentPadding, horizontal, vertical, layoutDirection) {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + horizontal,
            top = contentPadding.calculateTopPadding() + vertical,
            end = contentPadding.calculateEndPadding(layoutDirection) + horizontal,
            bottom = contentPadding.calculateBottomPadding() + vertical,
        )
    }
}
