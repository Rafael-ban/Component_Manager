package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.ui.screen.MovementDetailRoute
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
            layoutMode = CompactPreviewLayout,
            onSelectMovement = {},
            onOpenMovementDetail = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onImportComponent = {},
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
            layoutMode = ExpandedPreviewLayout,
            onSelectMovement = {},
            onOpenMovementDetail = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onImportComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryPhonePreview
@InventoryZhCnPreview
@InventoryLargeFontPreview
@Composable
private fun MovementsMatchedPhonePreview() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        val uiState = InventoryPreviewData.movementsMatchedState()
        MovementsContent(
            contentPadding = PaddingValues(),
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = CompactPreviewLayout,
            onSelectMovement = {},
            onOpenMovementDetail = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onImportComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryPhonePreview
@Composable
private fun MovementsNotFoundPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.movementsNotFoundState()
        MovementsContent(
            contentPadding = PaddingValues(),
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = CompactPreviewLayout,
            onSelectMovement = {},
            onOpenMovementDetail = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onImportComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryNarrowPhonePreview
@Composable
private fun MovementDetailPhonePreview() {
    PreviewHost {
        MovementDetailRoute(
            movement = InventoryPreviewData.movementsState().movements.items.first(),
            onDismiss = {},
        )
    }
}
