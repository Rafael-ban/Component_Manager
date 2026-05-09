package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.model.toLabelSeed
import com.componentvault.android.ui.screen.ComponentLabelPreviewSurface
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass

@InventoryDialogPreview
@Composable
private fun ComponentLabelPreview() {
    PreviewHost {
        ComponentLabelPreviewSurface(
            seed = InventoryPreviewData.selectedComponent.toLabelSeed(),
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
        )
    }
}
