package com.componentvault.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.InventoryUiState

@Composable
internal fun ComponentVaultAppShell(
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    selectedMovementId: String?,
    onDestinationChange: (InventoryDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
) {
    ComponentVaultAppShellContent(
        uiState = uiState,
        destination = destination,
        layoutMode = layoutMode,
        selectedMovementId = selectedMovementId,
        onDestinationChange = onDestinationChange,
        onQueryChange = onQueryChange,
        onStockFilterChange = onStockFilterChange,
        onCategoryChange = onCategoryChange,
        onLocationChange = onLocationChange,
        onSortChange = onSortChange,
        onSelectComponent = onSelectComponent,
        onOpenComponentDetail = onOpenComponentDetail,
        onAddComponent = onAddComponent,
        onEditComponent = onEditComponent,
        onRequestDeleteComponent = onRequestDeleteComponent,
        onRecordMovement = onRecordMovement,
        onSaveSettings = onSaveSettings,
        onTestConnection = onTestConnection,
        onSyncNow = onSyncNow,
        onOpenLowStockInventory = onOpenLowStockInventory,
        onSelectOverviewComponent = onSelectOverviewComponent,
        onOpenMovements = onOpenMovements,
        onSelectMovement = onSelectMovement,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComponentVaultAppShellContent(
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    selectedMovementId: String?,
    onDestinationChange: (InventoryDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
) {
    val strings = vaultStrings()
    val title = strings.shell.destinationLabel(destination)

    val topBar: @Composable () -> Unit = {
        if (destination == InventoryDestination.Inventory) {
            MediumTopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        } else {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        }
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
                        onClick = { onRecordMovement(uiState.inventory.list.selectedComponentId) },
                        text = { Text(strings.common.actionRecordMovement) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                    )
                }
            }

            else -> Unit
        }
    }

    if (layoutMode.usesNavigationRail) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavigationRail(
                modifier = Modifier.padding(top = 12.dp),
                header = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RailDot(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = strings.shell.appTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            ) {
                InventoryDestination.entries.forEach { item ->
                    val label = strings.shell.destinationLabel(item)
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { onDestinationChange(item) },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }

            Scaffold(
                topBar = topBar,
                floatingActionButton = floatingActionButton,
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                ShellContent(
                    modifier = Modifier.padding(padding),
                    uiState = uiState,
                    destination = destination,
                    layoutMode = layoutMode,
                    selectedMovementId = selectedMovementId,
                    onQueryChange = onQueryChange,
                    onStockFilterChange = onStockFilterChange,
                    onCategoryChange = onCategoryChange,
                    onLocationChange = onLocationChange,
                    onSortChange = onSortChange,
                    onSelectComponent = onSelectComponent,
                    onOpenComponentDetail = onOpenComponentDetail,
                    onEditComponent = onEditComponent,
                    onRequestDeleteComponent = onRequestDeleteComponent,
                    onRecordMovement = onRecordMovement,
                    onSaveSettings = onSaveSettings,
                    onTestConnection = onTestConnection,
                    onSyncNow = onSyncNow,
                    onOpenLowStockInventory = onOpenLowStockInventory,
                    onSelectOverviewComponent = onSelectOverviewComponent,
                    onOpenMovements = onOpenMovements,
                    onSelectMovement = onSelectMovement,
                    onOpenSettings = { onDestinationChange(InventoryDestination.Settings) },
                )
            }
        }
    } else {
        Scaffold(
            topBar = topBar,
            floatingActionButton = floatingActionButton,
            bottomBar = {
                NavigationBar {
                    InventoryDestination.entries.forEach { item ->
                        val label = strings.shell.destinationLabel(item)
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { onDestinationChange(item) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            ShellContent(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                destination = destination,
                layoutMode = layoutMode,
                selectedMovementId = selectedMovementId,
                onQueryChange = onQueryChange,
                onStockFilterChange = onStockFilterChange,
                onCategoryChange = onCategoryChange,
                onLocationChange = onLocationChange,
                onSortChange = onSortChange,
                onSelectComponent = onSelectComponent,
                onOpenComponentDetail = onOpenComponentDetail,
                onEditComponent = onEditComponent,
                onRequestDeleteComponent = onRequestDeleteComponent,
                onRecordMovement = onRecordMovement,
                onSaveSettings = onSaveSettings,
                onTestConnection = onTestConnection,
                onSyncNow = onSyncNow,
                onOpenLowStockInventory = onOpenLowStockInventory,
                onSelectOverviewComponent = onSelectOverviewComponent,
                onOpenMovements = onOpenMovements,
                onSelectMovement = onSelectMovement,
                onOpenSettings = { onDestinationChange(InventoryDestination.Settings) },
            )
        }
    }
}

@Composable
private fun ShellContent(
    modifier: Modifier,
    uiState: InventoryUiState,
    destination: InventoryDestination,
    layoutMode: InventoryLayoutMode,
    selectedMovementId: String?,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String?) -> Unit,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenLowStockInventory: () -> Unit,
    onSelectOverviewComponent: (String) -> Unit,
    onOpenMovements: (String?) -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (destination) {
        InventoryDestination.Inventory -> InventoryContent(
            modifier = modifier,
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
            onEditComponent = onEditComponent,
            onRequestDeleteComponent = onRequestDeleteComponent,
            onRecordMovement = { componentId -> onRecordMovement(componentId) },
        )

        InventoryDestination.Movements -> MovementsContent(
            modifier = modifier,
            uiState = uiState.movements,
            statusMessage = uiState.statusMessage,
            layoutMode = layoutMode,
            selectedMovementId = selectedMovementId,
            onSelectMovement = onSelectMovement,
            onRecordMovement = { onRecordMovement(uiState.inventory.list.selectedComponentId) },
        )

        InventoryDestination.Overview -> OverviewContent(
            modifier = modifier,
            uiState = uiState.overview,
            syncConfiguration = uiState.syncConfiguration,
            statusMessage = uiState.statusMessage,
            onOpenLowStock = onOpenLowStockInventory,
            onSelectLowStockComponent = onSelectOverviewComponent,
            onOpenMovements = { onOpenMovements(null) },
            onSelectMovement = { movementId -> onOpenMovements(movementId) },
            onOpenSettings = onOpenSettings,
        )

        InventoryDestination.Settings -> SettingsContent(
            modifier = modifier,
            syncConfiguration = uiState.syncConfiguration,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            layoutMode = layoutMode,
            onSaveSettings = onSaveSettings,
            onTestConnection = onTestConnection,
            onSyncNow = onSyncNow,
        )
    }
}
