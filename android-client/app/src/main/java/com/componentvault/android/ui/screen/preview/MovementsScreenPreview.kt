package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass
import com.componentvault.android.ui.screen.MovementsContent

@InventoryPhonePreview
@Composable
private fun MovementsPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.movementsState()
        MovementsContent(
            contentPadding = PaddingValues(),
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            selectedMovementId = InventoryPreviewData.selectedMovementId,
            onSelectMovement = {},
            onRecordMovement = {},
        )
    }
}

@InventoryTabletPreview
@Composable
private fun MovementsTabletPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.movementsState()
        MovementsContent(
            contentPadding = PaddingValues(),
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Expanded,
                usesNavigationRail = true,
                showsListDetail = true,
                prefersDialogForms = true,
            ),
            selectedMovementId = InventoryPreviewData.selectedMovementId,
            onSelectMovement = {},
            onRecordMovement = {},
        )
    }
}
