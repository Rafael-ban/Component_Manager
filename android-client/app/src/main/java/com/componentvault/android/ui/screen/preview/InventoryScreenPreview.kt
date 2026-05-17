package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.ui.screen.InventoryContent

@InventoryPhonePreview
@Composable
private fun InventoryPhoneListPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = CompactPreviewLayout,
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onGenerateLabel = {},
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
            layoutMode = CompactPreviewLayout,
            onQueryChange = {},
            onStockFilterChange = { _: InventoryStockFilter -> },
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onGenerateLabel = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryLargeFontPreview
@InventoryNarrowPhonePreview
@Composable
private fun InventoryPhoneEmptyPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryEmptyState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = CompactPreviewLayout,
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onGenerateLabel = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}

@InventoryMediumPreview
@Composable
private fun InventoryMediumListDetailPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.inventoryState()
        InventoryContent(
            contentPadding = PaddingValues(),
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = MediumPreviewLayout,
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onGenerateLabel = {},
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
            layoutMode = ExpandedPreviewLayout,
            onQueryChange = {},
            onStockFilterChange = {},
            onCategoryChange = {},
            onLocationChange = {},
            onSortChange = {},
            onSelectComponent = {},
            onOpenComponentDetail = {},
            onImportComponent = {},
            onGenerateLabel = {},
            onEditComponent = {},
            onRequestDeleteComponent = {},
            onRecordMovement = {},
        )
    }
}
