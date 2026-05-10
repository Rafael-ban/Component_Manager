package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.ui.screen.ComponentLabelPreviewSurface
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewStandard() {
    PreviewHost {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.selectedLabelSeed,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            initialTemplate = ComponentLabelTemplate.Standard,
        )
    }
}

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewCompact() {
    PreviewHost {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.selectedLabelSeed,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            initialTemplate = ComponentLabelTemplate.Compact,
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun ComponentLabelPreviewZhCnLongText() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.longLabelSeed,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            initialTemplate = ComponentLabelTemplate.Large,
        )
    }
}
