package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.data.ComponentLabelPrintCanvasTemplate
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
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
            selectedTemplate = ComponentLabelTemplate.Qr30x40,
            selectedCanvasTemplate = ComponentLabelPrintCanvasTemplate.Miaomiaoji57x79,
        )
    }
}

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewCompactWithCompanion() {
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
            selectedTemplate = ComponentLabelTemplate.Qr10x40,
            selectedCanvasTemplate = ComponentLabelPrintCanvasTemplate.Miaomiaoji57x79,
            includeCompanionTextLabel = true,
            selectedTextTemplate = ComponentTextLabelTemplate.NamePackageSku,
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
            selectedTemplate = ComponentLabelTemplate.TextOnly,
            selectedCanvasTemplate = ComponentLabelPrintCanvasTemplate.RawLabel,
            selectedTextTemplate = ComponentTextLabelTemplate.NamePackageSku,
        )
    }
}
