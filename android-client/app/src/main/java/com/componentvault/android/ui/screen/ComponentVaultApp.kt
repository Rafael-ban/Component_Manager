package com.componentvault.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun ComponentVaultApp(
    viewModel: InventoryViewModel,
) {
    val uiState = viewModel.uiState
    val layoutMode = rememberInventoryLayoutMode()
    val strings = runtimeComponentVaultStrings()

    var destination by rememberSaveable { mutableStateOf(InventoryDestination.Inventory) }
    var compactDetailComponentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMovementId by rememberSaveable { mutableStateOf<String?>(null) }
    var componentEditorVisible by rememberSaveable { mutableStateOf(false) }
    var componentEditorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var movementEditorVisible by rememberSaveable { mutableStateOf(false) }
    var movementEditorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val editingComponent = uiState.availableComponents.firstOrNull { it.id == componentEditorTargetId }
    val compactDetailComponent = uiState.inventory.detail.component
        ?.takeIf { it.id == compactDetailComponentId }
        ?: uiState.availableComponents.firstOrNull { it.id == compactDetailComponentId }
    val compactDetailMovements = if (compactDetailComponent == null) {
        emptyList()
    } else {
        uiState.movements.items.filter { it.componentId == compactDetailComponent.id }.take(5)
    }

    BackHandler(enabled = componentEditorVisible && !layoutMode.prefersDialogForms) {
        componentEditorVisible = false
        componentEditorTargetId = null
    }
    BackHandler(enabled = movementEditorVisible && !layoutMode.prefersDialogForms) {
        movementEditorVisible = false
        movementEditorTargetId = null
    }
    BackHandler(enabled = compactDetailComponentId != null && !layoutMode.showsListDetail) {
        compactDetailComponentId = null
    }

    fun openComponentEditor(componentId: String?) {
        componentEditorTargetId = componentId
        componentEditorVisible = true
    }

    fun openMovementEditor(componentId: String?) {
        movementEditorTargetId = componentId
        movementEditorVisible = true
    }

    ProvideComponentVaultStrings(strings) {
        when {
            componentEditorVisible && !layoutMode.prefersDialogForms -> {
                ComponentEditorSurface(
                    existing = editingComponent,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                    },
                    onSave = { draft ->
                        viewModel.saveComponent(draft)
                        componentEditorVisible = false
                        componentEditorTargetId = null
                    },
                )
            }

            movementEditorVisible && !layoutMode.prefersDialogForms -> {
                MovementEditorSurface(
                    components = uiState.availableComponents,
                    selectedComponentId = movementEditorTargetId ?: uiState.inventory.list.selectedComponentId,
                    layoutMode = layoutMode,
                    onDismiss = {
                        movementEditorVisible = false
                        movementEditorTargetId = null
                    },
                    onSave = { draft ->
                        viewModel.recordMovement(draft)
                        movementEditorVisible = false
                        movementEditorTargetId = null
                    },
                )
            }

            compactDetailComponentId != null && !layoutMode.showsListDetail -> {
                InventoryDetailRoute(
                    component = compactDetailComponent,
                    recentMovements = compactDetailMovements,
                    onDismiss = { compactDetailComponentId = null },
                    onEditComponent = { componentId ->
                        openComponentEditor(componentId)
                    },
                    onRequestDeleteComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        showDeleteConfirmation = true
                    },
                    onRecordMovement = { componentId ->
                        openMovementEditor(componentId)
                    },
                )
            }

            else -> {
                ComponentVaultAppShell(
                    uiState = uiState,
                    destination = destination,
                    layoutMode = layoutMode,
                    selectedMovementId = selectedMovementId,
                    onDestinationChange = { nextDestination ->
                        destination = nextDestination
                        compactDetailComponentId = null
                    },
                    onQueryChange = viewModel::updateComponentQuery,
                    onStockFilterChange = { stockFilter ->
                        viewModel.setLowStockOnly(stockFilter == com.componentvault.android.model.InventoryStockFilter.LowStock)
                    },
                    onCategoryChange = viewModel::updateCategoryFilter,
                    onLocationChange = viewModel::updateLocationFilter,
                    onSortChange = viewModel::updateSort,
                    onSelectComponent = viewModel::selectComponent,
                    onOpenComponentDetail = { componentId ->
                        viewModel.selectComponent(componentId)
                        if (!layoutMode.showsListDetail) {
                            compactDetailComponentId = componentId
                        }
                    },
                    onAddComponent = { openComponentEditor(null) },
                    onEditComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        openComponentEditor(componentId)
                    },
                    onRequestDeleteComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        showDeleteConfirmation = true
                    },
                    onRecordMovement = { componentId ->
                        componentId?.let(viewModel::selectComponent)
                        openMovementEditor(componentId)
                    },
                    onSaveSettings = viewModel::saveSyncConfiguration,
                    onTestConnection = viewModel::testConnection,
                    onSyncNow = viewModel::runSync,
                    onOpenLowStockInventory = {
                        destination = InventoryDestination.Inventory
                        compactDetailComponentId = null
                        viewModel.focusLowStockInventory()
                    },
                    onSelectOverviewComponent = { componentId ->
                        destination = InventoryDestination.Inventory
                        viewModel.focusLowStockInventory(componentId)
                        if (!layoutMode.showsListDetail) {
                            compactDetailComponentId = componentId
                        }
                    },
                    onOpenMovements = { movementId ->
                        destination = InventoryDestination.Movements
                        selectedMovementId = movementId
                    },
                    onSelectMovement = { movementId ->
                        selectedMovementId = movementId
                    },
                )
            }
        }

        if (layoutMode.prefersDialogForms) {
            if (componentEditorVisible) {
                ComponentEditorSurface(
                    existing = editingComponent,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                    },
                    onSave = { draft ->
                        viewModel.saveComponent(draft)
                        componentEditorVisible = false
                        componentEditorTargetId = null
                    },
                )
            }

            if (movementEditorVisible) {
                MovementEditorSurface(
                    components = uiState.availableComponents,
                    selectedComponentId = movementEditorTargetId ?: uiState.inventory.list.selectedComponentId,
                    layoutMode = layoutMode,
                    onDismiss = {
                        movementEditorVisible = false
                        movementEditorTargetId = null
                    },
                    onSave = { draft ->
                        viewModel.recordMovement(draft)
                        movementEditorVisible = false
                        movementEditorTargetId = null
                    },
                )
            }
        }

        if (showDeleteConfirmation) {
            DeleteComponentConfirmationDialog(
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    viewModel.deleteSelectedComponent()
                    showDeleteConfirmation = false
                    compactDetailComponentId = null
                },
            )
        }
    }
}
