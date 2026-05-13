package com.componentvault.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.toLabelSeed

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
    var movementEditorInitialType by rememberSaveable { mutableStateOf<String?>(null) }
    var movementEditorAllowManualSelection by rememberSaveable { mutableStateOf(true) }
    var importSurfaceVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var componentEditorInitialDraft by remember { mutableStateOf<ComponentDraft?>(null) }
    var componentEditorImportCandidate by remember { mutableStateOf<ComponentImportCandidate?>(null) }
    var labelPreviewSeed by remember { mutableStateOf<com.componentvault.android.model.ComponentLabelSeed?>(null) }
    var selectedLabelTemplateId by rememberSaveable { mutableStateOf(ComponentLabelTemplate.default.id) }
    var includeCompanionTextLabel by rememberSaveable { mutableStateOf(false) }
    var selectedTextLabelTemplateId by rememberSaveable {
        mutableStateOf(ComponentTextLabelTemplate.default.id)
    }

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
        componentEditorImportCandidate = null
    }
    BackHandler(enabled = movementEditorVisible && !layoutMode.prefersDialogForms) {
        movementEditorVisible = false
        movementEditorTargetId = null
        movementEditorInitialType = null
        movementEditorAllowManualSelection = true
    }
    BackHandler(enabled = importSurfaceVisible && !layoutMode.prefersDialogForms) {
        importSurfaceVisible = false
    }
    BackHandler(enabled = labelPreviewSeed != null && !layoutMode.prefersDialogForms) {
        labelPreviewSeed = null
    }
    BackHandler(enabled = compactDetailComponentId != null && !layoutMode.showsListDetail) {
        compactDetailComponentId = null
    }

    fun openComponentEditor(
        componentId: String?,
        initialDraft: ComponentDraft? = null,
        importCandidate: ComponentImportCandidate? = null,
    ) {
        componentEditorTargetId = componentId
        componentEditorInitialDraft = initialDraft
        componentEditorImportCandidate = importCandidate
        componentEditorVisible = true
    }

    fun openMovementEditor(
        componentId: String?,
        initialMovementType: String? = null,
        allowManualSelection: Boolean = true,
    ) {
        movementEditorTargetId = componentId
        movementEditorInitialType = initialMovementType
        movementEditorAllowManualSelection = allowManualSelection
        movementEditorVisible = true
    }

    ProvideComponentVaultStrings(strings) {
        when {
            uiState.movements.scan.isScannerVisible -> {
                JlcQrScannerSurface(
                    onDismiss = viewModel::dismissMovementScanner,
                    onScanResult = viewModel::resolveMovementComponentFromLabel,
                    title = strings.movements.quickScanAction,
                    permissionTitle = strings.movements.scannerPermissionTitle,
                    permissionDescription = strings.movements.scannerPermissionDescription,
                    permissionDeniedTitle = strings.movements.scannerPermissionDeniedTitle,
                    permissionDeniedDescription = strings.movements.scannerPermissionDeniedDescription,
                    startingMessage = strings.movements.scannerStarting,
                    scanningHint = strings.movements.scannerScanningHint,
                    failedTitle = strings.movements.scannerFailedTitle,
                    failedDescription = strings.movements.scannerFailedDescription,
                    returnActionLabel = strings.common.actionBack,
                )
            }

            componentEditorVisible && !layoutMode.prefersDialogForms -> {
                ComponentEditorSurface(
                    existing = editingComponent,
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                    },
                    onSave = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft, componentEditorImportCandidate)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                    },
                    onSaveAndGenerateLabel = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft, componentEditorImportCandidate)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                        labelPreviewSeed = draft.toLabelSeed()
                    },
                )
            }

            movementEditorVisible && !layoutMode.prefersDialogForms -> {
                MovementEditorSurface(
                    components = uiState.availableComponents,
                    selectedComponentId = movementEditorTargetId ?: uiState.inventory.list.selectedComponentId,
                    layoutMode = layoutMode,
                    initialMovementType = movementEditorInitialType,
                    allowManualComponentSelection = movementEditorAllowManualSelection,
                    onDismiss = {
                        movementEditorVisible = false
                        movementEditorTargetId = null
                        movementEditorInitialType = null
                        movementEditorAllowManualSelection = true
                    },
                    onSave = { draft ->
                        viewModel.recordMovement(draft)
                        movementEditorVisible = false
                        movementEditorTargetId = null
                        movementEditorInitialType = null
                        movementEditorAllowManualSelection = true
                    },
                )
            }

            importSurfaceVisible && !layoutMode.prefersDialogForms -> {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    syncConfiguration = uiState.syncConfiguration,
                    appPreferences = uiState.appPreferences,
                    onDismiss = { importSurfaceVisible = false },
                    onSaveImportedComponent = { draft, sourceCandidate ->
                        viewModel.saveImportedComponent(draft, sourceCandidate)
                        importSurfaceVisible = false
                        labelPreviewSeed = draft.toLabelSeed()
                    },
                    onOpenFullEditor = { draft, sourceCandidate ->
                        importSurfaceVisible = false
                        openComponentEditor(
                            componentId = null,
                            initialDraft = draft,
                            importCandidate = sourceCandidate,
                        )
                    },
                )
            }

            labelPreviewSeed != null && !layoutMode.prefersDialogForms -> {
                ComponentLabelPreviewSurface(
                    seed = requireNotNull(labelPreviewSeed),
                    layoutMode = layoutMode,
                    onDismiss = { labelPreviewSeed = null },
                    selectedTemplate = ComponentLabelTemplate.fromId(selectedLabelTemplateId),
                    includeCompanionTextLabel = includeCompanionTextLabel,
                    selectedTextTemplate = ComponentTextLabelTemplate.fromId(selectedTextLabelTemplateId),
                    onTemplateChange = { selectedLabelTemplateId = it.id },
                    onIncludeCompanionTextLabelChange = { includeCompanionTextLabel = it },
                    onTextTemplateChange = { selectedTextLabelTemplateId = it.id },
                )
            }

            compactDetailComponentId != null && !layoutMode.showsListDetail -> {
                InventoryDetailRoute(
                    component = compactDetailComponent,
                    recentMovements = compactDetailMovements,
                    onDismiss = { compactDetailComponentId = null },
                    onEditComponent = { componentId -> openComponentEditor(componentId) },
                    onGenerateLabel = { component ->
                        labelPreviewSeed = component.toLabelSeed()
                    },
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
                    onGenerateLabel = { componentId ->
                        uiState.availableComponents.firstOrNull { it.id == componentId }?.let { component ->
                            labelPreviewSeed = component.toLabelSeed()
                        }
                    },
                    onEditComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        openComponentEditor(componentId)
                    },
                    onRequestDeleteComponent = { componentId ->
                        viewModel.selectComponent(componentId)
                        showDeleteConfirmation = true
                    },
                    onScanMovementLabel = {
                        destination = InventoryDestination.Movements
                        viewModel.openMovementScanner()
                    },
                    onRetryMovementScan = viewModel::openMovementScanner,
                    onDismissMovementScanResult = viewModel::clearMovementScanState,
                    onSelectQuickMovementAction = { action ->
                        val componentId = uiState.movements.scan.resolution.matchedComponent?.id
                        viewModel.clearMovementScanState()
                        if (componentId != null) {
                            viewModel.selectComponent(componentId)
                            openMovementEditor(
                                componentId = componentId,
                                initialMovementType = action.movementType,
                                allowManualSelection = false,
                            )
                        }
                    },
                    onSearchInventoryBySku = { sku ->
                        destination = InventoryDestination.Inventory
                        compactDetailComponentId = null
                        viewModel.updateComponentQuery(sku)
                    },
                    onRecordMovement = { componentId ->
                        componentId?.let(viewModel::selectComponent)
                        openMovementEditor(componentId)
                    },
                    onSaveSyncSettings = viewModel::saveSyncConfiguration,
                    onSaveAppPreferences = viewModel::saveAppPreferences,
                    onTestConnection = viewModel::testConnection,
                    onSyncNow = viewModel::runSync,
                    onClearImportLearningMappings = viewModel::clearImportLearningMappings,
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

        if (!uiState.movements.scan.isScannerVisible && layoutMode.prefersDialogForms) {
            if (componentEditorVisible) {
                ComponentEditorSurface(
                    existing = editingComponent,
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = {
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                    },
                    onSave = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft, componentEditorImportCandidate)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                    },
                    onSaveAndGenerateLabel = { draft ->
                        if (editingComponent == null && componentEditorInitialDraft != null) {
                            viewModel.saveImportedComponent(draft, componentEditorImportCandidate)
                        } else {
                            viewModel.saveComponent(draft)
                        }
                        componentEditorVisible = false
                        componentEditorTargetId = null
                        componentEditorInitialDraft = null
                        componentEditorImportCandidate = null
                        labelPreviewSeed = draft.toLabelSeed()
                    },
                )
            }

            if (movementEditorVisible) {
                MovementEditorSurface(
                    components = uiState.availableComponents,
                    selectedComponentId = movementEditorTargetId ?: uiState.inventory.list.selectedComponentId,
                    layoutMode = layoutMode,
                    initialMovementType = movementEditorInitialType,
                    allowManualComponentSelection = movementEditorAllowManualSelection,
                    onDismiss = {
                        movementEditorVisible = false
                        movementEditorTargetId = null
                        movementEditorInitialType = null
                        movementEditorAllowManualSelection = true
                    },
                    onSave = { draft ->
                        viewModel.recordMovement(draft)
                        movementEditorVisible = false
                        movementEditorTargetId = null
                        movementEditorInitialType = null
                        movementEditorAllowManualSelection = true
                    },
                )
            }

            if (importSurfaceVisible) {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    syncConfiguration = uiState.syncConfiguration,
                    appPreferences = uiState.appPreferences,
                    onDismiss = { importSurfaceVisible = false },
                    onSaveImportedComponent = { draft, sourceCandidate ->
                        viewModel.saveImportedComponent(draft, sourceCandidate)
                        importSurfaceVisible = false
                        labelPreviewSeed = draft.toLabelSeed()
                    },
                    onOpenFullEditor = { draft, sourceCandidate ->
                        importSurfaceVisible = false
                        openComponentEditor(
                            componentId = null,
                            initialDraft = draft,
                            importCandidate = sourceCandidate,
                        )
                    },
                )
            }

            if (labelPreviewSeed != null) {
                ComponentLabelPreviewSurface(
                    seed = requireNotNull(labelPreviewSeed),
                    layoutMode = layoutMode,
                    onDismiss = { labelPreviewSeed = null },
                    selectedTemplate = ComponentLabelTemplate.fromId(selectedLabelTemplateId),
                    includeCompanionTextLabel = includeCompanionTextLabel,
                    selectedTextTemplate = ComponentTextLabelTemplate.fromId(selectedTextLabelTemplateId),
                    onTemplateChange = { selectedLabelTemplateId = it.id },
                    onIncludeCompanionTextLabelChange = { includeCompanionTextLabel = it },
                    onTextTemplateChange = { selectedTextLabelTemplateId = it.id },
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
