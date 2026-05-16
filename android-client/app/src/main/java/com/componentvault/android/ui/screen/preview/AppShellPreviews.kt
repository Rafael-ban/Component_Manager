package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.ui.screen.ComponentVaultAppShellContent
import com.componentvault.android.ui.screen.InventoryDestination
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass

@InventoryShellPhonePreview
@Composable
private fun AppShellPhoneInventoryPreview() {
    PreviewHost {
        ComponentVaultAppShellContent(
            uiState = InventoryPreviewData.inventoryState(),
            destination = InventoryDestination.Inventory,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            selectedMovementId = InventoryPreviewData.selectedMovementId,
            onDestinationChange = {},
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onAddComponent = {},
            onImportComponent = {},
            onGenerateLabel = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onRecordMovement = {},
            onSaveSyncSettings = { _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = {},
            onSyncNow = {},
            onClearImportLearningMappings = {},
            onOpenLowStockInventory = {},
            onSelectOverviewComponent = {},
            onOpenMovements = {},
            onSelectMovement = {},
        )
    }
}

@InventoryShellTabletPreview
@Composable
private fun AppShellTabletOverviewPreview() {
    PreviewHost {
        ComponentVaultAppShellContent(
            uiState = InventoryPreviewData.overviewState(),
            destination = InventoryDestination.Overview,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Expanded,
                usesNavigationRail = true,
                showsListDetail = true,
                prefersDialogForms = true,
            ),
            selectedMovementId = InventoryPreviewData.selectedMovementId,
            onDestinationChange = {},
            onQueryChange = {},
            onStockFilterChange = { _: InventoryStockFilter -> },
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onAddComponent = {},
            onImportComponent = {},
            onGenerateLabel = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onScanMovementLabel = {},
            onRetryMovementScan = {},
            onDismissMovementScanResult = {},
            onRecordResolvedMovement = { _, _ -> },
            onSearchInventoryBySku = {},
            onRecordMovement = {},
            onSaveSyncSettings = { _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = {},
            onSyncNow = {},
            onClearImportLearningMappings = {},
            onOpenLowStockInventory = {},
            onSelectOverviewComponent = {},
            onOpenMovements = {},
            onSelectMovement = {},
        )
    }
}
