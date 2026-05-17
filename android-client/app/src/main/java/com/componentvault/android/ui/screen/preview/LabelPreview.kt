package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.ui.screen.ComponentLabelPreviewSurface

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewStandard() {
    PreviewHost {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.selectedLabelSeed,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            selectedTemplate = ComponentLabelTemplate.Qr30x40,
        )
    }
}

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewCompactWithCompanion() {
    PreviewHost {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.selectedLabelSeed,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            selectedTemplate = ComponentLabelTemplate.Qr10x40,
            includeCompanionTextLabel = true,
            selectedTextTemplate = ComponentTextLabelTemplate.NamePackageSku,
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun ComponentLabelPreview30x40LandscapeZhCn() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.wideLabelSeed,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            selectedTemplate = ComponentLabelTemplate.Qr30x40,
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun ComponentLabelPreviewZhCnLongText() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.longLabelSeed,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            selectedTemplate = ComponentLabelTemplate.TextOnly,
            selectedTextTemplate = ComponentTextLabelTemplate.NamePackageSku,
        )
    }
}
