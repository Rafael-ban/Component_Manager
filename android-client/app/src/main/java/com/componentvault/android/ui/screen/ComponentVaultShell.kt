package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.InventoryUiState

@Composable
internal fun ComponentVaultAppShell(
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    onDestinationChange: (InventoryDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onAddComponent: () -> Unit,
    onImportComponent: () -> Unit,
    onGenerateLabel: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onRecordResolvedMovement: (com.componentvault.android.model.MovementEntryDraft, (com.componentvault.android.model.OperationResult) -> Unit) -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenMovementDetail: (String) -> Unit,
    onOpenSettingsHome: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    ComponentVaultAppShellContent(
        uiState = uiState,
        destination = destination,
        layoutMode = layoutMode,
        onDestinationChange = onDestinationChange,
        onQueryChange = onQueryChange,
        onStockFilterChange = onStockFilterChange,
        onCategoryChange = onCategoryChange,
        onLocationChange = onLocationChange,
        onSortChange = onSortChange,
        onSelectComponent = onSelectComponent,
        onOpenComponentDetail = onOpenComponentDetail,
        onAddComponent = onAddComponent,
        onImportComponent = onImportComponent,
        onGenerateLabel = onGenerateLabel,
        onEditComponent = onEditComponent,
        onRequestDeleteComponent = onRequestDeleteComponent,
        onScanMovementLabel = onScanMovementLabel,
        onRetryMovementScan = onRetryMovementScan,
        onDismissMovementScanResult = onDismissMovementScanResult,
        onRecordResolvedMovement = onRecordResolvedMovement,
        onSearchInventoryBySku = onSearchInventoryBySku,
        onRecordMovement = onRecordMovement,
        onOpenLowStockInventory = onOpenLowStockInventory,
        onSelectOverviewComponent = onSelectOverviewComponent,
        onOpenMovements = onOpenMovements,
        onSelectMovement = onSelectMovement,
        onOpenMovementDetail = onOpenMovementDetail,
        onOpenSettingsHome = onOpenSettingsHome,
        onOpenSyncSettings = onOpenSyncSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComponentVaultAppShellContent(
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    onDestinationChange: (InventoryDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onAddComponent: () -> Unit,
    onImportComponent: () -> Unit,
    onGenerateLabel: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onRecordResolvedMovement: (com.componentvault.android.model.MovementEntryDraft, (com.componentvault.android.model.OperationResult) -> Unit) -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenMovementDetail: (String) -> Unit,
    onOpenSettingsHome: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    val strings = vaultStrings()
    val title = strings.shell.destinationLabel(destination)

    val topBar: @Composable () -> Unit = {
        SmallTopAppBar(
            title = { Text(title) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(onClick = onOpenSettingsHome) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = strings.shell.settingsDestination,
                    )
                }
            },
        )
    }

    val floatingActionButton: @Composable () -> Unit = {
        when (destination) {
            InventoryDestination.Inventory -> {
                ExtendedFloatingActionButton(
                    onClick = onAddComponent,
                    text = { Text(strings.common.actionAdd) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                )
            }

            InventoryDestination.Movements -> {
                if (uiState.availableComponents.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = onScanMovementLabel,
                        text = { Text(strings.movements.quickScanAction) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                    )
                }
            }

            else -> Unit
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            InventoryDestination.entries.forEach { item ->
                val label = strings.shell.destinationLabel(item)
                item(
                    selected = destination == item,
                    onClick = { onDestinationChange(item) },
                    icon = { Icon(item.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Scaffold(
            topBar = topBar,
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            ShellContent(
                contentPadding = padding,
                uiState = uiState,
                destination = destination,
                layoutMode = layoutMode,
                onQueryChange = onQueryChange,
                onStockFilterChange = onStockFilterChange,
                onCategoryChange = onCategoryChange,
                onLocationChange = onLocationChange,
                onSortChange = onSortChange,
                onSelectComponent = onSelectComponent,
                onOpenComponentDetail = onOpenComponentDetail,
                onImportComponent = onImportComponent,
                onGenerateLabel = onGenerateLabel,
                onEditComponent = onEditComponent,
                onRequestDeleteComponent = onRequestDeleteComponent,
                onScanMovementLabel = onScanMovementLabel,
                onRetryMovementScan = onRetryMovementScan,
                onDismissMovementScanResult = onDismissMovementScanResult,
                onRecordResolvedMovement = onRecordResolvedMovement,
                onSearchInventoryBySku = onSearchInventoryBySku,
                onRecordMovement = onRecordMovement,
                onOpenLowStockInventory = onOpenLowStockInventory,
                onSelectOverviewComponent = onSelectOverviewComponent,
                onOpenMovements = onOpenMovements,
                onSelectMovement = onSelectMovement,
                onOpenMovementDetail = onOpenMovementDetail,
                onOpenSettings = onOpenSettingsHome,
                onOpenSyncSettings = onOpenSyncSettings,
            )
        }
    }
}

@Composable
private fun ShellContent(
    contentPadding: PaddingValues,
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onImportComponent: () -> Unit,
    onGenerateLabel: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onRecordResolvedMovement: (com.componentvault.android.model.MovementEntryDraft, (com.componentvault.android.model.OperationResult) -> Unit) -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenMovementDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    when (destination) {
        InventoryDestination.Inventory -> InventoryContent(
            contentPadding = contentPadding,
            uiState = uiState.inventory,
            statusMessage = uiState.statusMessage,
            layoutMode = layoutMode,
            onQueryChange = onQueryChange,
            onStockFilterChange = onStockFilterChange,
            onCategoryChange = onCategoryChange,
            onLocationChange = onLocationChange,
            onSortChange = onSortChange,
            onSelectComponent = onSelectComponent,
            onOpenComponentDetail = onOpenComponentDetail,
            onImportComponent = onImportComponent,
            onGenerateLabel = onGenerateLabel,
            onEditComponent = onEditComponent,
            onRequestDeleteComponent = onRequestDeleteComponent,
            onRecordMovement = { componentId -> onRecordMovement(componentId) },
        )

        InventoryDestination.Movements -> MovementsContent(
            contentPadding = contentPadding,
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = layoutMode,
            onSelectMovement = onSelectMovement,
            onOpenMovementDetail = onOpenMovementDetail,
            onScanMovementLabel = onScanMovementLabel,
            onRetryMovementScan = onRetryMovementScan,
            onDismissMovementScanResult = onDismissMovementScanResult,
            onRecordResolvedMovement = onRecordResolvedMovement,
            onSearchInventoryBySku = onSearchInventoryBySku,
            onImportComponent = onImportComponent,
            onRecordMovement = { onRecordMovement(uiState.inventory.list.selectedComponentId) },
        )

        InventoryDestination.Overview -> OverviewContent(
            contentPadding = contentPadding,
            uiState = uiState.overview,
            syncConfiguration = uiState.syncConfiguration,
            statusMessage = uiState.statusMessage,
            onOpenLowStock = onOpenLowStockInventory,
            onSelectLowStockComponent = onSelectOverviewComponent,
            onOpenMovements = { onOpenMovements(null) },
            onSelectMovement = { movementId -> onOpenMovements(movementId) },
            onOpenSettings = onOpenSettings,
            onOpenSyncSettings = onOpenSyncSettings,
        )
    }
}
