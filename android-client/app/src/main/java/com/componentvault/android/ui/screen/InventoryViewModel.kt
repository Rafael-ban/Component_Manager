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
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.MovementScanUiState
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.OverviewUiState
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.SyncConfiguration
import kotlinx.coroutines.launch

class InventoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = InventoryRepository(application)
    private var allComponentsCache: List<ComponentRecord> = emptyList()
    private var allMovementsCache: List<StockMovementRecord> = emptyList()
    private val defaultSyncMessage = application.getString(R.string.sync_no_sync_yet)
    private val defaultLastSyncedAt = application.getString(R.string.sync_never)

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
        maybeAutoSyncOnLaunch()
    }

    fun refresh() {
        viewModelScope.launch {
            reloadState(uiState.statusMessage.takeIf { it.isNotBlank() })
        }
    }

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

    fun selectMovement(movementId: String?) {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = uiState.movements.scan,
                preferredSelectedMovementId = movementId,
            ),
        )
    }

    fun openMovementScanner() {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = MovementScanUiState(isScannerVisible = true),
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
                scanState = currentScanState.copy(isScannerVisible = false),
            ),
        )
    }

    fun clearMovementScanState() {
        uiState = uiState.copy(
            movements = buildMovementsUiState(
                scanState = MovementScanUiState(),
            ),
        )
    }

    fun resolveMovementComponentFromLabel(rawValue: String) {
        viewModelScope.launch {
            uiState = uiState.copy(
                movements = buildMovementsUiState(
                    scanState = MovementScanUiState(
                        isScannerVisible = false,
                        isResolving = true,
                    ),
                ),
            )
            val resolution = repository.resolveComponentByScannedLabel(rawValue)
            uiState = uiState.copy(
                movements = buildMovementsUiState(
                    scanState = MovementScanUiState(
                        isScannerVisible = false,
                        isResolving = false,
                        resolution = resolution,
                    ),
                ),
            )
        }
    }

    fun saveSyncConfiguration(
        serverBaseUrl: String,
        apiToken: String,
        autoSyncEnabled: Boolean,
    ) {
        val result = repository.saveSyncConfiguration(
            serverBaseUrl = serverBaseUrl,
            apiToken = apiToken,
            autoSyncEnabled = autoSyncEnabled,
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

    fun testConnection() {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.testConnection()
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
        val dashboardSnapshot = repository.loadDashboardSnapshot()
        uiState = uiState.copy(
            overview = buildOverviewUiState(dashboardSnapshot),
            availableComponents = allComponentsCache,
            inventory = buildInventoryScreenUiState(),
            movements = buildMovementsUiState(uiState.movements.scan),
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
            movements = buildMovementsUiState(uiState.movements.scan),
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
            availableCategories = allComponentsCache
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() },
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
            ),
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
                component.category.contains(filters.query, ignoreCase = true) ||
                component.packageName.contains(filters.query, ignoreCase = true) ||
                component.location.contains(filters.query, ignoreCase = true)
            val matchesCategory = filters.category.isNullOrBlank() || component.category == filters.category
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
