package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.ui.screen.BluetoothLabelPrintScreen

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewStandard() {
    PreviewHost {
        BluetoothLabelPrintScreen(
            components = emptyList(),
            initialSeed = InventoryPreviewData.selectedLabelSeed,
            onDismiss = {},
            initialTemplateId = ComponentLabelTemplate.Qr30x40.id,
        )
    }
}

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreviewCompact() {
    PreviewHost {
        BluetoothLabelPrintScreen(
            components = emptyList(),
            initialSeed = InventoryPreviewData.selectedLabelSeed,
            onDismiss = {},
            initialTemplateId = ComponentLabelTemplate.Qr10x40.id,
            initialTextTemplateId = ComponentTextLabelTemplate.NamePackageSku.id,
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun ComponentLabelPreview30x40LandscapeZhCn() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        BluetoothLabelPrintScreen(
            components = emptyList(),
            initialSeed = InventoryPreviewData.wideLabelSeed,
            onDismiss = {},
            initialTemplateId = ComponentLabelTemplate.Qr30x40.id,
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun ComponentLabelPreviewZhCnLongText() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        BluetoothLabelPrintScreen(
            components = emptyList(),
            initialSeed = InventoryPreviewData.longLabelSeed,
            onDismiss = {},
            initialTemplateId = ComponentLabelTemplate.TextOnly.id,
            initialTextTemplateId = ComponentTextLabelTemplate.NamePackageSku.id,
        )
    }
}
