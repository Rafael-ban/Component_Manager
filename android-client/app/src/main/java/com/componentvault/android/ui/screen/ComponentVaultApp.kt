package com.componentvault.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.toLabelSeed

@Composable
fun ComponentVaultApp(
    viewModel: InventoryViewModel,
) {
    val uiState = viewModel.uiState
    val layoutMode = rememberInventoryLayoutMode()
    val strings = runtimeComponentVaultStrings()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastHandledStatusMessage by rememberSaveable { mutableStateOf(uiState.statusMessage) }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (message.isNotBlank() && message != lastHandledStatusMessage) {
            lastHandledStatusMessage = message
            snackbarHostState.showSnackbar(message)
        }
    }

    var destination by rememberSaveable { mutableStateOf(InventoryDestination.Overview) }
    var compactDetailComponentId by rememberSaveable { mutableStateOf<String?>(null) }
    var compactMovementDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSettingsSectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var componentEditorVisible by rememberSaveable { mutableStateOf(false) }
    var componentEditorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var movementEditorVisible by rememberSaveable { mutableStateOf(false) }
    var movementEditorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var movementEditorInitialType by rememberSaveable { mutableStateOf<String?>(null) }
    var movementEditorAllowManualSelection by rememberSaveable { mutableStateOf(true) }
    var importSurfaceVisible by rememberSaveable { mutableStateOf(false) }
    var bomImportVisible by rememberSaveable { mutableStateOf(false) }
    var storageLocationsVisible by rememberSaveable { mutableStateOf(false) }
    var inventoryBackupVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAddEntrySheet by rememberSaveable { mutableStateOf(false) }
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
    val compactMovementDetail = uiState.movements.items.firstOrNull { it.id == compactMovementDetailId }
    val compactDetailMovements = if (compactDetailComponent == null) {
        emptyList()
    } else {
        uiState.movements.items.filter { it.componentId == compactDetailComponent.id }.take(5)
    }
    val selectedSettingsSection = SettingsSection.entries.firstOrNull { it.name == selectedSettingsSectionName }

    fun resetSecondaryRoutes() {
        compactDetailComponentId = null
        compactMovementDetailId = null
        selectedSettingsSectionName = null
    }

    fun openSettingsRoute(section: SettingsSection? = null) {
        destination = InventoryDestination.Settings
        compactDetailComponentId = null
        compactMovementDetailId = null
        selectedSettingsSectionName = section?.name
    }

    fun closeComponentEditor() {
        componentEditorVisible = false
        componentEditorTargetId = null
        componentEditorInitialDraft = null
        componentEditorImportCandidate = null
    }

    fun closeMovementEditor() {
        movementEditorVisible = false
        movementEditorTargetId = null
        movementEditorInitialType = null
        movementEditorAllowManualSelection = true
    }

    fun closeImportSurface() {
        importSurfaceVisible = false
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

    fun openCompactMovementDetail(movementId: String) {
        viewModel.selectMovement(movementId)
        if (!layoutMode.showsListDetail) {
            compactMovementDetailId = movementId
        }
    }

    fun revealSavedComponent(result: OperationResult) {
        result.entityId?.let { componentId ->
            viewModel.selectComponent(componentId)
            if (!layoutMode.showsListDetail) {
                compactDetailComponentId = componentId
            }
        }
    }

    fun submitComponentEditorDraft(
        draft: ComponentDraft,
        generateLabelAfterSave: Boolean,
        onComplete: (OperationResult) -> Unit,
    ) {
        val handleResult: (OperationResult) -> Unit = { result ->
            onComplete(result)
            if (result.isSuccess) {
                closeComponentEditor()
                revealSavedComponent(result)
                if (generateLabelAfterSave) {
                    labelPreviewSeed = draft.toLabelSeed()
                }
            }
        }
        if (editingComponent == null && componentEditorInitialDraft != null) {
            viewModel.saveImportedComponent(draft, componentEditorImportCandidate, handleResult)
        } else {
            viewModel.saveComponent(draft, handleResult)
        }
    }

    fun submitImportedDraft(
        draft: ComponentDraft,
        sourceCandidate: ComponentImportCandidate,
        onComplete: (OperationResult) -> Unit,
    ) {
        viewModel.saveImportedComponent(draft, sourceCandidate) { result ->
            onComplete(result)
            if (!result.isSuccess) {
                return@saveImportedComponent
            }
            closeImportSurface()
            revealSavedComponent(result)
            labelPreviewSeed = draft.toLabelSeed()
        }
    }

    BackHandler(enabled = componentEditorVisible && !layoutMode.prefersDialogForms) {
        closeComponentEditor()
    }
    BackHandler(enabled = movementEditorVisible && !layoutMode.prefersDialogForms) {
        closeMovementEditor()
    }
    BackHandler(enabled = importSurfaceVisible && !layoutMode.prefersDialogForms) {
        closeImportSurface()
    }
    BackHandler(enabled = labelPreviewSeed != null && !layoutMode.prefersDialogForms) {
        labelPreviewSeed = null
    }
    BackHandler(enabled = destination == InventoryDestination.Settings && selectedSettingsSection != null && !layoutMode.showsListDetail) {
        if (selectedSettingsSection != null) {
            selectedSettingsSectionName = null
        }
    }
    BackHandler(enabled = compactDetailComponentId != null && !layoutMode.showsListDetail) {
        compactDetailComponentId = null
    }
    BackHandler(enabled = compactMovementDetailId != null && !layoutMode.showsListDetail) {
        compactMovementDetailId = null
    }

    ProvideComponentVaultStrings(strings) {
        when {
            uiState.movements.scan.isScannerVisible -> {
                JlcQrScannerSurface(
                    onDismiss = viewModel::dismissMovementScanner,
                    onScanResult = viewModel::resolveMovementComponentFromLabel,
                    scanSessionToken = uiState.movements.scan.scanSessionToken,
                    title = strings.movements.quickScanAction,
                    permissionTitle = strings.movements.scannerPermissionTitle,
                    permissionDescription = strings.movements.scannerPermissionDescription,
                    permissionDeniedTitle = strings.movements.scannerPermissionDeniedTitle,
                    permissionDeniedDescription = strings.movements.scannerPermissionDeniedDescription,
                    startingMessage = strings.movements.scannerStarting,
                    scanningHint = if (uiState.movements.batchSession.queuedItems.isNotEmpty()) {
                        strings.movements.batchScannerHint(
                            uiState.movements.batchSession.queuedItems.size,
                            uiState.movements.batchSession.totalScans,
                        )
                    } else {
                        strings.movements.scannerScanningHint
                    },
                    failedTitle = strings.movements.scannerFailedTitle,
                    failedDescription = strings.movements.scannerFailedDescription,
                    returnActionLabel = if (uiState.movements.batchSession.queuedItems.isNotEmpty()) {
                        strings.movements.batchReviewAction
                    } else {
                        strings.common.actionBack
                    },
                    scannerMode = JlcQrScannerMode.MovementSmallLabel,
                )
            }

            componentEditorVisible && !layoutMode.prefersDialogForms -> {
                ComponentEditorSurface(
                    existing = editingComponent,
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = ::closeComponentEditor,
                    onSave = { draft, onComplete ->
                        submitComponentEditorDraft(
                            draft = draft,
                            generateLabelAfterSave = false,
                            onComplete = onComplete,
                        )
                    },
                    onSaveAndGenerateLabel = { draft, onComplete ->
                        submitComponentEditorDraft(
                            draft = draft,
                            generateLabelAfterSave = true,
                            onComplete = onComplete,
                        )
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
                    onDismiss = ::closeMovementEditor,
                    onSave = { draft, onComplete ->
                        viewModel.recordMovement(draft) { result ->
                            onComplete(result)
                            if (result.isSuccess) {
                                closeMovementEditor()
                            }
                        }
                    },
                    storageLocations = uiState.inventory.storageLocations,
                )
            }

            importSurfaceVisible && !layoutMode.prefersDialogForms -> {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    syncConfiguration = uiState.syncConfiguration,
                    appPreferences = uiState.appPreferences,
                    onDismiss = ::closeImportSurface,
                    onSaveImportedComponent = { draft, sourceCandidate, onComplete ->
                        submitImportedDraft(
                            draft = draft,
                            sourceCandidate = sourceCandidate,
                            onComplete = onComplete,
                        )
                    },
                    onOpenFullEditor = { draft, sourceCandidate ->
                        closeImportSurface()
                        openComponentEditor(
                            componentId = null,
                            initialDraft = draft,
                            importCandidate = sourceCandidate,
                        )
                    },
                )
            }

            bomImportVisible -> {
                BomImportScreen(
                    viewModel = viewModel,
                    onDismiss = { bomImportVisible = false },
                )
            }

            storageLocationsVisible -> {
                StorageLocationsScreen(
                    locations = uiState.inventory.storageLocations,
                    onDismiss = { storageLocationsVisible = false },
                    onSave = viewModel::saveStorageLocation,
                    onDelete = viewModel::deleteStorageLocation,
                )
            }

            inventoryBackupVisible -> InventoryBackupScreen(
                viewModel = viewModel,
                onDismiss = { inventoryBackupVisible = false; viewModel.clearInventoryBackupState() },
            )

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
                    issuedQuantity = uiState.inventory.detail.issuedQuantity,
                    allocations = uiState.inventory.detail.allocations,
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
                    onAddComponent = { showAddEntrySheet = true },
                )
            }

            compactMovementDetailId != null && !layoutMode.showsListDetail -> {
                MovementDetailRoute(
                    movement = compactMovementDetail,
                    onDismiss = { compactMovementDetailId = null },
                )
            }

            else -> {
                ComponentVaultAppShell(
                    uiState = uiState,
                    destination = destination,
                    layoutMode = layoutMode,
                    onDestinationChange = { nextDestination ->
                        destination = nextDestination
                        resetSecondaryRoutes()
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
                    onAddComponent = { showAddEntrySheet = true },
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
                    onDiscardMovementBatch = viewModel::discardMovementBatchSession,
                    onCommitMovementBatch = { viewModel.commitMovementBatch() },
                    onUpdateMovementBatchItemMovementType = viewModel::updateMovementBatchItemMovementType,
                    onUpdateMovementBatchItemQuantity = viewModel::updateMovementBatchItemQuantity,
                    onUpdateMovementBatchItemReason = viewModel::updateMovementBatchItemReason,
                    onUpdateMovementBatchItemNote = viewModel::updateMovementBatchItemNote,
                    onRemoveMovementBatchItem = viewModel::removeMovementBatchItem,
                    onSearchInventoryBySku = { sku ->
                        destination = InventoryDestination.Inventory
                        compactDetailComponentId = null
                        compactMovementDetailId = null
                        selectedSettingsSectionName = null
                        viewModel.updateComponentQuery(sku)
                    },
                    onRecordMovement = { componentId ->
                        componentId?.let(viewModel::selectComponent)
                        openMovementEditor(componentId)
                    },
                    onOpenLowStockInventory = {
                        destination = InventoryDestination.Inventory
                        compactDetailComponentId = null
                        compactMovementDetailId = null
                        selectedSettingsSectionName = null
                        viewModel.focusLowStockInventory()
                    },
                    onSelectOverviewComponent = { componentId ->
                        destination = InventoryDestination.Inventory
                        compactMovementDetailId = null
                        selectedSettingsSectionName = null
                        viewModel.focusLowStockInventory(componentId)
                        if (!layoutMode.showsListDetail) {
                            compactDetailComponentId = componentId
                        }
                    },
                    onOpenMovements = { movementId ->
                        destination = InventoryDestination.Movements
                        compactDetailComponentId = null
                        selectedSettingsSectionName = null
                        viewModel.selectMovement(movementId)
                        compactMovementDetailId = movementId?.takeIf { !layoutMode.showsListDetail }
                    },
                    onSelectMovement = { movementId ->
                        viewModel.selectMovement(movementId)
                    },
                    onOpenMovementDetail = ::openCompactMovementDetail,
                    onOpenSettingsHome = { openSettingsRoute() },
                    onOpenSyncSettings = { openSettingsRoute(SettingsSection.Sync) },
                    selectedSettingsSection = selectedSettingsSection,
                    onBackFromSettingsSection = { selectedSettingsSectionName = null },
                    settingsContent = { padding ->
                        SettingsContent(
                            contentPadding = padding,
                            syncConfiguration = uiState.syncConfiguration,
                            appPreferences = uiState.appPreferences,
                            importLearningSummary = uiState.importLearningSummary,
                            isBusy = uiState.isBusy,
                            statusMessage = "",
                            layoutMode = layoutMode,
                            selectedSection = selectedSettingsSection,
                            onSelectSection = { section -> selectedSettingsSectionName = section?.name },
                            onSaveSyncSettings = viewModel::saveSyncConfiguration,
                            onSaveAppPreferences = viewModel::saveAppPreferences,
                            onTestConnection = viewModel::testConnection,
                            onSyncNow = viewModel::runSync,
                            onClearImportLearningMappings = viewModel::clearImportLearningMappings,
                        )
                    },
                    snackbarHostState = snackbarHostState,
                )
            }
        }

        if (!uiState.movements.scan.isScannerVisible && layoutMode.prefersDialogForms) {
            if (componentEditorVisible) {
                ComponentEditorSurface(
                    existing = editingComponent,
                    initialDraft = componentEditorInitialDraft,
                    layoutMode = layoutMode,
                    onDismiss = ::closeComponentEditor,
                    onSave = { draft, onComplete ->
                        submitComponentEditorDraft(
                            draft = draft,
                            generateLabelAfterSave = false,
                            onComplete = onComplete,
                        )
                    },
                    onSaveAndGenerateLabel = { draft, onComplete ->
                        submitComponentEditorDraft(
                            draft = draft,
                            generateLabelAfterSave = true,
                            onComplete = onComplete,
                        )
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
                    onDismiss = ::closeMovementEditor,
                    onSave = { draft, onComplete ->
                        viewModel.recordMovement(draft) { result ->
                            onComplete(result)
                            if (result.isSuccess) {
                                closeMovementEditor()
                            }
                        }
                    },
                    storageLocations = uiState.inventory.storageLocations,
                )
            }

            if (importSurfaceVisible) {
                JlcImportSurface(
                    layoutMode = layoutMode,
                    syncConfiguration = uiState.syncConfiguration,
                    appPreferences = uiState.appPreferences,
                    onDismiss = ::closeImportSurface,
                    onSaveImportedComponent = { draft, sourceCandidate, onComplete ->
                        submitImportedDraft(
                            draft = draft,
                            sourceCandidate = sourceCandidate,
                            onComplete = onComplete,
                        )
                    },
                    onOpenFullEditor = { draft, sourceCandidate ->
                        closeImportSurface()
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

        if (showAddEntrySheet) {
            AddComponentEntrySheet(
                onDismiss = { showAddEntrySheet = false },
                onImportComponent = {
                    showAddEntrySheet = false
                    importSurfaceVisible = true
                },
                onAddComponent = {
                    showAddEntrySheet = false
                    openComponentEditor(null)
                },
                onImportBom = {
                    showAddEntrySheet = false
                    bomImportVisible = true
                },
                onManageLocations = {
                    showAddEntrySheet = false
                    storageLocationsVisible = true
                },
                onBackupRestore = {
                    showAddEntrySheet = false
                    inventoryBackupVisible = true
                },
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddComponentEntrySheet(
    onDismiss: () -> Unit,
    onImportComponent: () -> Unit,
    onAddComponent: () -> Unit,
    onImportBom: () -> Unit,
    onManageLocations: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    val strings = vaultStrings()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        SectionPane(
            title = strings.common.actionAdd,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            FilledTonalButton(
                onClick = onImportComponent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.actionImport)
            }
            OutlinedButton(
                onClick = onImportBom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("BOM / Component Hub")
            }
            OutlinedButton(
                onClick = onAddComponent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.forms.addComponentTitle)
            }
            OutlinedButton(onClick = onManageLocations, modifier = Modifier.fillMaxWidth()) {
                Text("管理库位")
            }
            OutlinedButton(onClick = onBackupRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Excel 备份与恢复")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.actionCancel)
            }
        }
    }
}
