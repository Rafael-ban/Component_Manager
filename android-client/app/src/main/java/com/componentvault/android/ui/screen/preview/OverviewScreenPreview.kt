package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.ui.screen.OverviewContent

@InventoryPhonePreview
@Composable
private fun OverviewPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.overviewState()
        OverviewContent(
            modifier = androidx.compose.ui.Modifier,
            uiState = uiState.overview,
            syncConfiguration = uiState.syncConfiguration,
            statusMessage = uiState.statusMessage,
            onOpenLowStock = {},
            onSelectLowStockComponent = {},
            onOpenMovements = {},
            onSelectMovement = {},
            onOpenSettings = {},
        )
    }
}
