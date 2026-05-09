package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.ui.screen.InventoryContent
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass

@InventoryPhonePreview
@Composable
private fun InventoryPhoneListPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryZhCnPreview
@Composable
private fun InventoryPhoneLowStockPreview() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        val uiState = InventoryPreviewData.inventoryLowStockState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onQueryChange = {},
            onStockFilterChange = { _: InventoryStockFilter -> },
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryLargeFontPreview
@Composable
private fun InventoryPhoneEmptyPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryEmptyState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryTabletPreview
@Composable
private fun InventoryTabletListDetailPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Expanded,
                usesNavigationRail = true,
                showsListDetail = true,
                prefersDialogForms = true,
            ),
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}
