package com.componentvault.android.ui.screen

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.componentvault.android.R
import com.componentvault.android.data.InventoryRepository
import com.componentvault.android.data.InventoryWorkbookCodec
import com.componentvault.android.data.bom.BomParseResult
import com.componentvault.android.data.bom.BomParser
import com.componentvault.android.data.bom.BomReleasePreview
import com.componentvault.android.data.bom.BomReleaseResult
import com.componentvault.android.data.bom.BomSheet
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.DashboardSnapshot
import com.componentvault.android.model.InventoryDetailUiState
import com.componentvault.android.model.InventoryFiltersUiState
import com.componentvault.android.model.InventoryListItemUiState
import com.componentvault.android.model.InventoryListUiState
import com.componentvault.android.model.InventoryScreenUiState
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.InventoryUiState
import com.componentvault.android.model.MovementBatchQueueItemUiState
import com.componentvault.android.model.MovementBatchSessionUiState
import com.componentvault.android.model.MovementBatchStage
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.MovementQuickAction
import com.componentvault.android.model.MovementScanResolutionUiState
import com.componentvault.android.model.MovementScanUiState
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.OverviewUiState
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.StorageLocationRecord
import com.componentvault.android.model.StorageLocationSaveIntent
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.SyncConfiguration
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

internal data class InventoryBackupUiState(
    val loading:Boolean=false,
    val preview:InventoryRepository.WorkbookPreview?=null,
    val message:String="",
)

class InventoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = InventoryRepository(application)
    var startupFailure by mutableStateOf<String?>(null)
        private set
    private var initialLoadComplete = false
    private var allComponentsCache: List<ComponentRecord> = emptyList()
    private var allMovementsCache: List<StockMovementRecord> = emptyList()
    private var issuedQuantitiesCache: Map<String, Long> = emptyMap()
    private var storageLocationsCache: List<StorageLocationRecord> = emptyList()
    private var allocationsCache: List<ComponentAllocationRecord> = emptyList()
    private val defaultSyncMessage = application.getString(R.string.sync_no_sync_yet)
    private val defaultLastSyncedAt = application.getString(R.string.sync_never)
    internal var backupUiState by mutableStateOf(InventoryBackupUiState())
        private set

    var uiState by mutableStateOf(
        InventoryUiState(
            syncConfiguration = SyncConfiguration(
                deviceId = "",
                serverBaseUrl = "",
                apiToken = "",
                autoSyncEnabled = false,
                lastSyncedAt = defaultLastSyncedAt,
                lastSyncMessage = defaultSyncMessage,
            ),
            statusMessage = defaultSyncMessage,
        ),
    )
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                reloadState(uiState.statusMessage.takeIf { it.isNotBlank() })
                startupFailure = null
                if (!initialLoadComplete) {
                    initialLoadComplete = true
                    maybeAutoSyncOnLaunch()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                startupFailure = startupFailureDetails(error)
            }
        }
    }

    fun onBatchJlcCommitted(){viewModelScope.launch{reloadState("批量入库已完成。");if(uiState.appPreferences.syncAfterLocalChanges)runSyncInternal()}}

    fun updateComponentQuery(query: String) {
        updateInventoryFilters(uiState.inventory.filters.copy(query = query))
    }

    fun setLowStockOnly(enabled: Boolean) {
        updateInventoryFilters(
            uiState.inventory.filters.copy(
                stockFilter = if (enabled) {
                    InventoryStockFilter.LowStock
                } else {
                    InventoryStockFilter.All
                },
            ),
        )
    }

    fun updateCategoryFilter(category: String?) {
        updateInventoryFilters(uiState.inventory.filters.copy(category = category))
    }

    fun updateLocationFilter(location: String?) {
        updateInventoryFilters(uiState.inventory.filters.copy(location = location))
    }

    fun updateSort(sort: InventorySortOption) {
        updateInventoryFilters(uiState.inventory.filters.copy(sort = sort))
    }

    fun focusLowStockInventory(componentId: String? = null) {
        updateInventoryFilters(
            uiState.inventory.filters.copy(stockFilter = InventoryStockFilter.LowStock),
        )
        if (componentId != null) {
            selectComponent(componentId)
        }
    }

    fun selectComponent(componentId: String?) {
        val selectedComponent = allComponentsCache.firstOrNull { it.id == componentId }
        val selectedId = selectedComponent?.id
        val recentMovements = if (selectedId == null) {
            emptyList()
        } else {
            allMovementsCache.filter { it.componentId == selectedId }.take(5)
        }
        uiState = uiState.copy(
            inventory = uiState.inventory.copy(
                list = uiState.inventory.list.copy(selectedComponentId = selectedId),
                detail = InventoryDetailUiState(
                    component = selectedComponent,
                    recentMovements = recentMovements,
                    issuedQuantity = selectedId?.let { issuedQuantitiesCache[it] } ?: 0,
                    allocations = selectedId?.let { id -> allocationsCache.filter { it.componentId == id } }.orEmpty(),
                ),
            ),
        )
    }

    fun saveComponent(
        draft: ComponentDraft,
        onComplete: (OperationResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.saveComponent(draft)
            reloadState(
                statusMessage = result.message,
                preferredSelectedComponentId = result.entityId,
            )
            if (result.isSuccess) {
                result.entityId?.let(::revealSavedComponent)
            }
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    fun exportInventoryBackup(uri:Uri){viewModelScope.launch{backupUiState=InventoryBackupUiState(loading=true);runCatching{val bytes=repository.exportInventoryWorkbook();withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openOutputStream(uri,"w")!!.use{it.write(bytes)}}}.onSuccess{backupUiState=InventoryBackupUiState(message="备份已导出。")}.onFailure{backupUiState=InventoryBackupUiState(message=it.message?:"备份导出失败。")}}}
    fun previewInventoryBackup(uri:Uri){viewModelScope.launch{backupUiState=InventoryBackupUiState(loading=true);runCatching{val bytes=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)!!.use{input->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(DEFAULT_BUFFER_SIZE);var total=0;while(true){val count=input.read(buffer);if(count<0)break;total=Math.addExact(total,count);require(total<=InventoryWorkbookCodec.MAX_FILE_BYTES){"备份文件超过 50 MiB。"};output.write(buffer,0,count)};output.toByteArray()}};repository.previewInventoryWorkbook(bytes)}.onSuccess{backupUiState=InventoryBackupUiState(preview=it)}.onFailure{backupUiState=InventoryBackupUiState(message=it.message?:"备份预览失败。")}}}
    fun confirmInventoryRestore(){val p=backupUiState.preview?:return;viewModelScope.launch{backupUiState=backupUiState.copy(loading=true);val result=repository.restoreInventoryWorkbook(p);backupUiState=InventoryBackupUiState(message=result.message);if(result.isSuccess)refresh()}}
    fun clearInventoryBackupState(){backupUiState=InventoryBackupUiState()}

    fun saveImportedComponent(
        draft: ComponentDraft,
        onComplete: (OperationResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.saveImportedComponent(
                draft = draft,
                sourceCandidate = null,
                appPreferences = uiState.appPreferences,
            )
            if (result.isSuccess && uiState.appPreferences.rememberLastImportLocation) {
                repository.rememberLastImportLocation(draft.location)
            }
            reloadState(
                statusMessage = result.message,
                preferredSelectedComponentId = result.entityId,
            )
            if (result.isSuccess) {
                result.entityId?.let(::revealSavedComponent)
            }
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    internal fun findExistingImportTarget(
        sku: String,
        onError: (OperationResult) -> Unit,
        onComplete: (com.componentvault.android.data.ExistingImportTarget?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.findExistingImportTarget(sku) }
                .onSuccess(onComplete)
                .onFailure { onError(OperationResult(false, it.message ?: "无法检查已有料号。")) }
        }
    }

    internal fun appendImportedStock(
        target: com.componentvault.android.data.ExistingImportTarget,
        quantity: Int,
        locationId: String,
        onComplete: (OperationResult) -> Unit,
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.appendImportedStock(target, quantity, locationId)
            reloadState(statusMessage = result.message, preferredSelectedComponentId = result.entityId)
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) runSyncInternal()
        }
    }

    fun saveImportedComponent(
        draft: ComponentDraft,
        sourceCandidate: ComponentImportCandidate?,
        onComplete: (OperationResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.saveImportedComponent(
                draft = draft,
                sourceCandidate = sourceCandidate,
                appPreferences = uiState.appPreferences,
            )
            if (result.isSuccess && uiState.appPreferences.rememberLastImportLocation) {
                repository.rememberLastImportLocation(draft.location)
            }
            reloadState(
                statusMessage = result.message,
                preferredSelectedComponentId = result.entityId,
            )
            if (result.isSuccess) {
                result.entityId?.let(::revealSavedComponent)
            }
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    fun deleteSelectedComponent() {
        val componentId = uiState.inventory.list.selectedComponentId
        if (componentId == null) {
            uiState = uiState.copy(statusMessage = getApplication<Application>().getString(R.string.sync_select_component_first))
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.softDeleteComponent(componentId)
            reloadState(result.message)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    fun recordMovement(
        draft: MovementEntryDraft,
        onComplete: (OperationResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.recordMovement(draft)
            reloadState(
                statusMessage = result.message,
                preferredSelectedComponentId = if (result.isSuccess) {
                    draft.componentId
                } else {
                    null
                },
            )
            if (result.isSuccess) {
                selectComponent(draft.componentId)
            }
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    fun saveStorageLocation(
        id: String,
        name: String,
        intent: StorageLocationSaveIntent,
        onComplete: (OperationResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = repository.saveStorageLocation(id, name, intent)
            reloadState(result.message)
            onComplete(result)
        }
    }

    fun deleteStorageLocation(id: String, onComplete: (OperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteStorageLocation(id)
            reloadState(result.message)
            onComplete(result)
        }
    }

    fun selectMovement(movementId: String?) {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                batchSession = uiState.movements.batchSession,
                preferredSelectedMovementId = movementId,
            ),
        )
    }

    fun openMovementScanner() {
        val nextBatchSession = uiState.movements.batchSession
            .takeIf { it.isActive }
            ?.copy(stage = MovementBatchStage.Scanning)
            ?: MovementBatchSessionUiState(stage = MovementBatchStage.Scanning)
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = MovementScanUiState(
                    isScannerVisible = true,
                    scanSessionToken = uiState.movements.scan.scanSessionToken + 1,
                ),
                batchSession = nextBatchSession,
            ),
        )
    }

    fun dismissMovementScanner() {
        val currentScanState = uiState.movements.scan
        if (!currentScanState.isScannerVisible) {
            return
        }
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = currentScanState.copy(
                    isScannerVisible = false,
                    isResolving = false,
                ),
                batchSession = uiState.movements.batchSession.copy(
                    stage = MovementBatchStage.Review,
                ),
            ),
        )
    }

    fun clearMovementScanState() {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan.copy(
                    isResolving = false,
                    resolution = MovementScanResolutionUiState(),
                ),
                batchSession = uiState.movements.batchSession,
            ),
        )
    }

    fun discardMovementBatchSession() {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = MovementScanUiState(),
                batchSession = MovementBatchSessionUiState(),
            ),
        )
    }

    fun updateMovementBatchItemMovementType(
        componentId: String,
        movementType: String,
    ) {
        updateMovementBatchItem(componentId) { item ->
            item.copy(
                movementType = movementType,
                errorMessage = null,
            )
        }
    }

    fun updateMovementBatchItemQuantity(
        componentId: String,
        quantityText: String,
    ) {
        updateMovementBatchItem(componentId) { item ->
            item.copy(
                quantityText = quantityText,
                errorMessage = null,
                isDirty = true,
            )
        }
    }

    fun updateMovementBatchItemReason(
        componentId: String,
        reason: String,
    ) {
        updateMovementBatchItem(componentId) { item ->
            item.copy(
                reason = reason,
                errorMessage = null,
            )
        }
    }

    fun updateMovementBatchItemNote(
        componentId: String,
        note: String,
    ) {
        updateMovementBatchItem(componentId) { item ->
            item.copy(
                note = note,
                errorMessage = null,
            )
        }
    }

    fun removeMovementBatchItem(componentId: String) {
        val remainingItems = uiState.movements.batchSession.queuedItems.filterNot { it.componentId == componentId }
        if (remainingItems.isEmpty()) {
            discardMovementBatchSession()
            return
        }
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                batchSession = uiState.movements.batchSession.copy(
                    queuedItems = remainingItems,
                    stage = MovementBatchStage.Review,
                ),
            ),
        )
    }

    fun commitMovementBatch(
        onComplete: (OperationResult) -> Unit = {},
    ) {
        val validation = validateMovementBatchSession(uiState.movements.batchSession)
        if (!validation.isValid) {
            uiState = uiState.copy(
                movements = buildMovementsUiState(
                    scanState = uiState.movements.scan.copy(
                        isScannerVisible = false,
                        isResolving = false,
                    ),
                    batchSession = validation.session,
                ),
                statusMessage = validation.message,
            )
            onComplete(OperationResult(isSuccess = false, message = validation.message))
            return
        }

        val drafts = validation.drafts
        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                movements = buildMovementsUiState(
                    scanState = uiState.movements.scan.copy(
                        isScannerVisible = false,
                        isResolving = false,
                        resolution = MovementScanResolutionUiState(),
                    ),
                    batchSession = validation.session.copy(stage = MovementBatchStage.Review),
                ),
            )
            val result = repository.recordMovementsBatch(drafts)
            if (result.isSuccess) {
                uiState = uiState.copy(
                    movements = buildMovementsUiState(
                        scanState = MovementScanUiState(),
                        batchSession = MovementBatchSessionUiState(),
                    ),
                )
            }
            reloadState(
                statusMessage = result.message,
                preferredSelectedComponentId = if (result.isSuccess) {
                    drafts.lastOrNull()?.componentId
                } else {
                    null
                },
            )
            onComplete(result)
            if (result.isSuccess && uiState.appPreferences.syncAfterLocalChanges) {
                runSyncInternal()
            }
        }
    }

    fun resolveMovementComponentFromLabel(rawValue: String) {
        viewModelScope.launch {
            uiState = uiState.copy(
                movements = buildMovementsUiState(
                    scanState = uiState.movements.scan.copy(
                        isScannerVisible = true,
                        isResolving = true,
                        resolution = MovementScanResolutionUiState(),
                    ),
                    batchSession = uiState.movements.batchSession.copy(
                        stage = MovementBatchStage.Scanning,
                    ),
                ),
            )
            val resolution = repository.resolveComponentByScannedLabel(rawValue)
            val matchedComponent = resolution.matchedComponent
            if (matchedComponent != null) {
                uiState = uiState.copy(
                    movements = buildMovementsUiState(
                        scanState = uiState.movements.scan.copy(
                            isScannerVisible = true,
                            isResolving = false,
                            resolution = MovementScanResolutionUiState(),
                            scanSessionToken = uiState.movements.scan.scanSessionToken + 1,
                        ),
                        batchSession = enqueueMovementBatchItem(
                            session = uiState.movements.batchSession,
                            component = matchedComponent,
                        ),
                    ),
                )
            } else {
                uiState = uiState.copy(
                    movements = buildMovementsUiState(
                        scanState = uiState.movements.scan.copy(
                            isScannerVisible = false,
                            isResolving = false,
                            resolution = resolution,
                        ),
                        batchSession = uiState.movements.batchSession.copy(
                            stage = MovementBatchStage.Review,
                        ),
                    ),
                )
            }
        }
    }

    fun saveSyncConfiguration(
        serverBaseUrl: String,
        apiToken: String,
        autoSyncEnabled: Boolean,
        externalServerBaseUrl: String,
    ) {
        val result = repository.saveSyncConfiguration(
            serverBaseUrl = serverBaseUrl,
            apiToken = apiToken,
            autoSyncEnabled = autoSyncEnabled,
            externalServerBaseUrl = externalServerBaseUrl,
        )
        uiState = uiState.copy(
            syncConfiguration = repository.loadSyncConfiguration(),
            statusMessage = result.message,
        )
    }

    fun saveAppPreferences(preferences: AppPreferences) {
        val result = repository.saveAppPreferences(preferences)
        uiState = uiState.copy(
            appPreferences = repository.loadAppPreferences(),
            statusMessage = result.message,
        )
    }

    fun testConnection(serverBaseUrl: String, apiToken: String, externalServerBaseUrl: String) {
        if (uiState.isBusy) return
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.testConnection(serverBaseUrl, apiToken, externalServerBaseUrl)
            uiState = uiState.copy(
                syncConfiguration = repository.loadSyncConfiguration(),
                statusMessage = result.message,
                isBusy = false,
            )
        }
    }

    fun runSync() {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            runSyncInternal()
        }
    }

    fun clearImportLearningMappings() {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.clearImportLearningMappings()
            reloadState(result.message)
        }
    }

    private suspend fun runSyncInternal() {
        val result = repository.runSync()
        val syncConfiguration = repository.loadSyncConfiguration()
        val appPreferences = repository.loadAppPreferences()
        val importLearningSummary = repository.loadImportLearningSummary()
        allComponentsCache = repository.loadComponents()
        allMovementsCache = repository.loadMovements()
        issuedQuantitiesCache = repository.loadIssuedQuantities()
        storageLocationsCache = repository.loadStorageLocations()
        allocationsCache = repository.loadAllocations()
        val dashboardSnapshot = repository.loadDashboardSnapshot()
        uiState = uiState.copy(
            overview = buildOverviewUiState(dashboardSnapshot),
            availableComponents = allComponentsCache,
            inventory = buildInventoryScreenUiState(),
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                batchSession = uiState.movements.batchSession,
            ),
            importLearningSummary = importLearningSummary,
            appPreferences = appPreferences,
            syncConfiguration = syncConfiguration,
            statusMessage = if (result.isSuccess) {
                syncConfiguration.lastSyncMessage
            } else {
                result.message
            },
            isBusy = false,
        )
    }

    private suspend fun reloadState(
        statusMessage: String? = null,
        preferredSelectedComponentId: String? = null,
    ) {
        allComponentsCache = repository.loadComponents()
        allMovementsCache = repository.loadMovements()
        issuedQuantitiesCache = repository.loadIssuedQuantities()
        storageLocationsCache = repository.loadStorageLocations()
        allocationsCache = repository.loadAllocations()
        val appPreferences = repository.loadAppPreferences()
        val syncConfiguration = repository.loadSyncConfiguration()
        val importLearningSummary = repository.loadImportLearningSummary()
        val dashboardSnapshot = repository.loadDashboardSnapshot()
        uiState = uiState.copy(
            overview = buildOverviewUiState(dashboardSnapshot),
            availableComponents = allComponentsCache,
            inventory = buildInventoryScreenUiState(
                preferredSelectedComponentId = preferredSelectedComponentId,
            ),
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                batchSession = uiState.movements.batchSession,
            ),
            importLearningSummary = importLearningSummary,
            appPreferences = appPreferences,
            syncConfiguration = syncConfiguration,
            statusMessage = statusMessage ?: syncConfiguration.lastSyncMessage,
            isBusy = false,
        )
    }

    private fun maybeAutoSyncOnLaunch() {
        viewModelScope.launch {
            val syncConfiguration = repository.loadSyncConfiguration()
            if (
                syncConfiguration.autoSyncEnabled &&
                syncConfiguration.serverBaseUrl.isNotBlank() &&
                syncConfiguration.apiToken.isNotBlank()
            ) {
                uiState = uiState.copy(isBusy = true)
                runSyncInternal()
            }
        }
    }

    private fun updateInventoryFilters(filters: InventoryFiltersUiState) {
        uiState = uiState.copy(
            inventory = buildInventoryScreenUiState(filters = filters),
        )
    }

    private fun buildOverviewUiState(
        componentSnapshot: DashboardSnapshot,
    ): OverviewUiState {
        return OverviewUiState(
            componentCount = componentSnapshot.componentCount,
            totalUnits = componentSnapshot.totalUnits,
            lowStockCount = componentSnapshot.lowStockCount,
            movementCount = componentSnapshot.movementCount,
            lowStockItems = allComponentsCache
                .filter { it.isLowStock }
                .sortedWith(compareBy<ComponentRecord> { it.quantity }.thenBy { it.name.lowercase() })
                .take(6)
                .map(::toInventoryListItem),
            recentMovements = allMovementsCache.take(8),
        )
    }

    private fun buildMovementsUiState(
        scanState: MovementScanUiState = uiState.movements.scan,
        batchSession: MovementBatchSessionUiState = uiState.movements.batchSession,
        preferredSelectedMovementId: String? = uiState.movements.selectedMovementId,
    ): MovementsUiState {
        val selectedMovementId = preferredSelectedMovementId?.takeIf { selectedId ->
            allMovementsCache.any { it.id == selectedId }
        }
        return MovementsUiState(
            items = allMovementsCache,
            componentCount = allComponentsCache.size,
            selectedMovementId = selectedMovementId,
            scan = scanState,
            batchSession = reconcileMovementBatchSession(batchSession),
        )
    }

    private fun buildInventoryScreenUiState(
        filters: InventoryFiltersUiState = uiState.inventory.filters,
        preferredSelectedComponentId: String? = null,
    ): InventoryScreenUiState {
        val filteredComponents = applyComponentFilter(
            components = allComponentsCache,
            filters = filters,
        )
        val selectedComponentId = (preferredSelectedComponentId ?: uiState.inventory.list.selectedComponentId)?.takeIf { selectedId ->
            filteredComponents.any { it.id == selectedId }
        }
        val selectedComponent = filteredComponents.firstOrNull { it.id == selectedComponentId }

        return InventoryScreenUiState(
            filters = filters,
            availableCategories = CategoryFilterSemantics.options(allComponentsCache.map { it.category }),
            availableLocations = allComponentsCache
                .map { it.location }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() },
            list = InventoryListUiState(
                items = filteredComponents.map(::toInventoryListItem),
                selectedComponentId = selectedComponentId,
            ),
            detail = InventoryDetailUiState(
                component = selectedComponent,
                recentMovements = if (selectedComponent == null) {
                    emptyList()
                } else {
                    allMovementsCache.filter { it.componentId == selectedComponent.id }.take(5)
                },
                issuedQuantity = selectedComponent?.let { issuedQuantitiesCache[it.id] } ?: 0,
                allocations = selectedComponent?.let { selected ->
                    allocationsCache.filter { it.componentId == selected.id }
                }.orEmpty(),
            ),
            storageLocations = storageLocationsCache,
        )
    }

    private fun applyComponentFilter(
        components: List<ComponentRecord>,
        filters: InventoryFiltersUiState,
    ): List<ComponentRecord> {
        val filtered = components.filter { component ->
            val matchesLowStock = filters.stockFilter != InventoryStockFilter.LowStock || component.isLowStock
            val matchesQuery = filters.query.isBlank() ||
                component.name.contains(filters.query, ignoreCase = true) ||
                component.sku.contains(filters.query, ignoreCase = true) ||
                CategoryFilterSemantics.matchesSearch(component.category, filters.query) ||
                component.packageName.contains(filters.query, ignoreCase = true) ||
                component.location.contains(filters.query, ignoreCase = true)
            val matchesCategory = filters.category.isNullOrBlank() ||
                CategoryFilterSemantics.sameCategory(component.category, filters.category.orEmpty())
            val matchesLocation = filters.location.isNullOrBlank() || component.location == filters.location

            matchesLowStock && matchesQuery && matchesCategory && matchesLocation
        }

        return when (filters.sort) {
            InventorySortOption.UpdatedNewest -> filtered.sortedWith(
                compareByDescending<ComponentRecord> { it.updatedAt }.thenBy { it.name.lowercase() },
            )
            InventorySortOption.Name -> filtered.sortedBy { it.name.lowercase() }
            InventorySortOption.QuantityLowToHigh -> filtered.sortedWith(
                compareBy<ComponentRecord> { it.quantity }.thenBy { it.name.lowercase() },
            )
            InventorySortOption.QuantityHighToLow -> filtered.sortedWith(
                compareByDescending<ComponentRecord> { it.quantity }.thenBy { it.name.lowercase() },
            )
        }
    }

    private fun toInventoryListItem(component: ComponentRecord): InventoryListItemUiState {
        return InventoryListItemUiState(
            id = component.id,
            name = component.name,
            sku = component.sku,
            category = component.category,
            packageName = component.packageName,
            location = component.location,
            quantity = component.quantity,
            minStock = component.minStock,
            isLowStock = component.isLowStock,
            updatedAt = component.updatedAt,
            productImageUrl = component.productImageUrl,
            issuedQuantity = issuedQuantitiesCache[component.id] ?: 0,
        )
    }

    private fun revealSavedComponent(componentId: String) {
        val filters = uiState.inventory.filters
        val isVisibleUnderCurrentFilters = applyComponentFilter(
            components = allComponentsCache,
            filters = filters,
        ).any { it.id == componentId }
        val effectiveFilters = if (isVisibleUnderCurrentFilters) {
            filters
        } else {
            filters.copy(
                query = "",
                stockFilter = InventoryStockFilter.All,
                category = null,
                location = null,
            )
        }
        uiState = uiState.copy(
            inventory = buildInventoryScreenUiState(
                filters = effectiveFilters,
                preferredSelectedComponentId = componentId,
            ),
        )
    }

    private fun updateMovementBatchItem(
        componentId: String,
        transform: (MovementBatchQueueItemUiState) -> MovementBatchQueueItemUiState,
    ) {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                batchSession = uiState.movements.batchSession.copy(
                    stage = MovementBatchStage.Review,
                    queuedItems = uiState.movements.batchSession.queuedItems.map { item ->
                        if (item.componentId == componentId) {
                            transform(item)
                        } else {
                            item
                        }
                    },
                ),
            ),
        )
    }

    private fun enqueueMovementBatchItem(
        session: MovementBatchSessionUiState,
        component: ComponentRecord,
    ): MovementBatchSessionUiState {
        val existingItem = session.queuedItems.firstOrNull { it.componentId == component.id }
        val queuedItems = if (existingItem == null) {
            session.queuedItems + toMovementBatchQueueItem(component)
        } else {
            session.queuedItems.map { item ->
                if (item.componentId != component.id) {
                    item
                } else {
                    val nextScanCount = item.scanCount + 1
                    item.copy(
                        componentName = component.name,
                        componentSku = component.sku,
                        category = component.category,
                        packageName = component.packageName,
                        location = component.location,
                        currentStock = component.quantity,
                        minStock = component.minStock,
                        scanCount = nextScanCount,
                        quantityText = if (item.isDirty) {
                            item.quantityText
                        } else {
                            nextScanCount.toString()
                        },
                        errorMessage = null,
                    )
                }
            }
        }

        return session.copy(
            stage = MovementBatchStage.Scanning,
            queuedItems = queuedItems,
            totalScans = session.totalScans + 1,
            lastQueuedComponentName = component.name,
            lastQueuedComponentSku = component.sku,
        )
    }

    private fun toMovementBatchQueueItem(
        component: ComponentRecord,
    ): MovementBatchQueueItemUiState {
        return MovementBatchQueueItemUiState(
            componentId = component.id,
            componentName = component.name,
            componentSku = component.sku,
            category = component.category,
            packageName = component.packageName,
            location = component.location,
            currentStock = component.quantity,
            minStock = component.minStock,
        )
    }

    private fun reconcileMovementBatchSession(
        session: MovementBatchSessionUiState,
    ): MovementBatchSessionUiState {
        if (!session.isActive) {
            return session
        }

        val missingComponentMessage = getApplication<Application>()
            .getString(R.string.sync_choose_active_component_first)

        return session.copy(
            queuedItems = session.queuedItems.map { item ->
                val component = allComponentsCache.firstOrNull { it.id == item.componentId }
                if (component == null) {
                    item.copy(errorMessage = missingComponentMessage)
                } else {
                    item.copy(
                        componentName = component.name,
                        componentSku = component.sku,
                        category = component.category,
                        packageName = component.packageName,
                        location = component.location,
                        currentStock = component.quantity,
                        minStock = component.minStock,
                    )
                }
            },
        )
    }

    private fun validateMovementBatchSession(
        session: MovementBatchSessionUiState,
    ): MovementBatchValidationResult {
        val application = getApplication<Application>()
        if (session.queuedItems.isEmpty()) {
            val message = application.getString(R.string.sync_movement_batch_empty)
            return MovementBatchValidationResult(
                isValid = false,
                drafts = emptyList(),
                session = session.copy(stage = MovementBatchStage.Review),
                message = message,
            )
        }

        var firstError: String? = null
        val drafts = mutableListOf<MovementEntryDraft>()
        val queuedItems = session.queuedItems.map { item ->
            val quantity = item.quantityText.toIntOrNull()
            val errorMessage = when {
                allComponentsCache.none { it.id == item.componentId } ->
                    application.getString(R.string.sync_choose_active_component_first)
                item.reason.isBlank() || item.movementType.isBlank() ->
                    application.getString(R.string.sync_choose_component_type_reason)
                quantity == null ->
                    application.getString(R.string.sync_invalid_movement_quantity)
                item.movementType == MovementQuickAction.Adjustment.movementType && quantity == 0 ->
                    application.getString(R.string.sync_adjustment_non_zero)
                item.movementType != MovementQuickAction.Adjustment.movementType && quantity <= 0 ->
                    application.getString(R.string.sync_movement_quantity_positive)
                item.movementType == MovementQuickAction.Outbound.movementType &&
                    item.currentStock - quantity < 0 ->
                    application.getString(R.string.sync_negative_stock_error)
                item.movementType == MovementQuickAction.Adjustment.movementType &&
                    item.currentStock + quantity < 0 ->
                    application.getString(R.string.sync_negative_stock_error)
                else -> null
            }

            if (errorMessage == null && quantity != null) {
                drafts += MovementEntryDraft(
                    componentId = item.componentId,
                    movementType = item.movementType,
                    quantity = quantity,
                    reason = item.reason,
                    note = item.note,
                )
                item.copy(errorMessage = null)
            } else {
                if (firstError == null) {
                    firstError = errorMessage
                }
                item.copy(errorMessage = errorMessage)
            }
        }

        return MovementBatchValidationResult(
            isValid = firstError == null,
            drafts = drafts,
            session = session.copy(
                stage = MovementBatchStage.Review,
                queuedItems = queuedItems,
            ),
            message = firstError ?: "",
        )
    }

    private data class MovementBatchValidationResult(
        val isValid: Boolean,
        val drafts: List<MovementEntryDraft>,
        val session: MovementBatchSessionUiState,
        val message: String,
    )

    fun listBomSheets(bytes: ByteArray, onComplete: (Result<List<BomSheet>>) -> Unit) {
        viewModelScope.launch {
            onComplete(runCatching { withContext(Dispatchers.Default) { BomParser.listXlsxSheets(bytes) } })
        }
    }

    fun inspectBom(bytes: ByteArray, fileName: String, sheetName: String?, onComplete: (Result<com.componentvault.android.data.bom.BomTableInspection>) -> Unit) {
        viewModelScope.launch {
            onComplete(runCatching { withContext(Dispatchers.Default) {
                if (fileName.endsWith(".xlsx", true)) com.componentvault.android.data.bom.BomParser.inspectXlsx(bytes, sheetName)
                else com.componentvault.android.data.bom.BomParser.inspectCsv(bytes)
            } })
        }
    }

    fun previewBom(
        bytes: ByteArray,
        fileName: String,
        projectName: String,
        productionSets: Int,
        sheetName: String?,
        mapping: com.componentvault.android.data.bom.BomColumnMapping? = null,
        selections: Map<String, String> = emptyMap(),
        searchQueries: Map<String, String> = emptyMap(),
        onComplete: (Result<BomReleasePreview>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(runCatching {
                val parsed: BomParseResult = withContext(Dispatchers.Default) { if (fileName.endsWith(".xlsx", true)) {
                    BomParser.parseXlsx(bytes, projectName, productionSets, sheetName, mapping)
                } else {
                    require(fileName.endsWith(".csv", true)) { "仅支持 CSV 或 XLSX BOM。" }
                    BomParser.parseCsv(bytes, projectName, productionSets, mapping)
                } }
                repository.previewBomRelease(parsed, selections, searchQueries)
            })
        }
    }

    fun commitBomRelease(
        preview: BomReleasePreview,
        releaseId: String,
        batchId: String,
        onComplete: (BomReleaseResult) -> Unit,
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.commitBomRelease(preview, releaseId, batchId)
            reloadState(result.message)
            if (result.outcome == com.componentvault.android.data.bom.BomReleaseOutcome.APPLIED) {
                if (uiState.appPreferences.syncAfterLocalChanges) runSyncInternal()
            }
            onComplete(result)
        }
    }

    fun previewComponentHub(
        bytes: ByteArray,
        skipDuplicates: Boolean,
        onComplete: (Result<com.componentvault.android.data.bom.ComponentHubParseResult>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(runCatching {
                repository.previewComponentHub(
                    bytes,
                    if (skipDuplicates) {
                        com.componentvault.android.data.bom.ComponentHubDuplicatePolicy.SKIP
                    } else {
                        com.componentvault.android.data.bom.ComponentHubDuplicatePolicy.BLOCK
                    },
                )
            })
        }
    }

    fun importComponentHub(
        preview: com.componentvault.android.data.bom.ComponentHubParseResult,
        onComplete: (com.componentvault.android.data.bom.ComponentHubImportResult) -> Unit,
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.importComponentHub(preview)
            reloadState(result.message)
            if (result.outcome == com.componentvault.android.data.bom.ComponentHubImportOutcome.APPLIED) {
                if (uiState.appPreferences.syncAfterLocalChanges) runSyncInternal()
            }
            onComplete(result)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return InventoryViewModel(application) as T
                }
            }
    }
}
