package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.ui.screen.OverviewContent

@InventoryPhonePreview
@Composable
private fun OverviewPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.overviewState()
        OverviewContent(
            contentPadding = PaddingValues(),
            uiState = uiState.overview,
            syncConfiguration = uiState.syncConfiguration,
            statusMessage = uiState.statusMessage,
            onOpenLowStock = {},
            onSelectLowStockComponent = {},
            onOpenMovements = {},
            onSelectMovement = {},
            onOpenSettings = {},
            onOpenSyncSettings = {},
        )
    }
}
