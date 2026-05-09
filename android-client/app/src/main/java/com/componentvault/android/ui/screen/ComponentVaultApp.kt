package com.componentvault.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.InventoryStockFilter

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
    var importSurfaceVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var componentEditorInitialDraft by remember { mutableStateOf<ComponentDraft?>(null) }

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
        componentEditorInitialDraft = null
    }
    BackHandler(enabled = movementEditorVisible && !layoutMode.prefersDialogForms) {
        movementEditorVisible = false
        movementEditorTargetId = null
    }
    BackHandler(enabled = importSurfaceVisible && !layoutMode.prefersDialogForms) {
        importSurfaceVisible = false
    }
    BackHandler(enabled = compactDetailComponentId != null && !layoutMode.showsListDetail) {
        compactDetailComponentId = null
    }

    fun openComponentEditor(
        componentId: String?,
        initialDraft: ComponentDraft? = null,
    ) {
        componentEditorTargetId = componentId
        componentEditorInitialDraft = initialDraft
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
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                    },
                    onSave = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
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

            importSurfaceVisible && !layoutMode.prefersDialogForms -> {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    appPreferences = uiState.appPreferences,
                    onDismiss = { importSurfaceVisible = false },
                    onSaveImportedComponent = { draft ->
                        viewModel.saveImportedComponent(draft)
                        importSurfaceVisible = false
                    },
                    onOpenFullEditor = { draft ->
                        importSurfaceVisible = false
                        openComponentEditor(componentId = null, initialDraft = draft)
                    },
                )
            }

            compactDetailComponentId != null && !layoutMode.showsListDetail -> {
                InventoryDetailRoute(
                    component = compactDetailComponent,
                    recentMovements = compactDetailMovements,
                    onDismiss = { compactDetailComponentId = null },
                    onEditComponent = { componentId -> openComponentEditor(componentId) },
                    onRequestDeleteComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        showDeleteConfirmation = true
                    },
                    onRecordMovement = { componentId -> openMovementEditor(componentId) },
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
                        viewModel.setLowStockOnly(stockFilter == InventoryStockFilter.LowStock)
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
                    onImportComponent = { importSurfaceVisible = true },
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
                    onSaveSyncSettings = viewModel::saveSyncConfiguration,
                    onSaveAppPreferences = viewModel::saveAppPreferences,
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
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                    },
                    onSave = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
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

            if (importSurfaceVisible) {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    appPreferences = uiState.appPreferences,
                    onDismiss = { importSurfaceVisible = false },
                    onSaveImportedComponent = { draft ->
                        viewModel.saveImportedComponent(draft)
                        importSurfaceVisible = false
                    },
                    onOpenFullEditor = { draft ->
                        importSurfaceVisible = false
                        openComponentEditor(componentId = null, initialDraft = draft)
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
