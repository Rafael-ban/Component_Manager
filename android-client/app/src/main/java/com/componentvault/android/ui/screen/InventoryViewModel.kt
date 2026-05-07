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
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.InventoryUiState
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.SyncConfiguration
import kotlinx.coroutines.launch

class InventoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = InventoryRepository(application)
    private var allComponentsCache: List<ComponentRecord> = emptyList()
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
        uiState = uiState.copy(
            componentQuery = query,
            components = applyComponentFilter(
                components = allComponentsCache,
                query = query,
                lowStockOnly = uiState.lowStockOnly,
            ),
        )
    }

    fun setLowStockOnly(enabled: Boolean) {
        uiState = uiState.copy(
            lowStockOnly = enabled,
            components = applyComponentFilter(
                components = allComponentsCache,
                query = uiState.componentQuery,
                lowStockOnly = enabled,
            ),
        )
    }

    fun selectComponent(componentId: String?) {
        uiState = uiState.copy(selectedComponentId = componentId)
    }

    fun saveComponent(draft: ComponentDraft) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.saveComponent(draft)
            reloadState(result.message)
            if (result.isSuccess && uiState.syncConfiguration.autoSyncEnabled) {
                runSyncInternal()
            }
        }
    }

    fun deleteSelectedComponent() {
        val componentId = uiState.selectedComponentId
        if (componentId == null) {
            uiState = uiState.copy(statusMessage = getApplication<Application>().getString(R.string.sync_select_component_first))
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.softDeleteComponent(componentId)
            reloadState(result.message)
            if (result.isSuccess && uiState.syncConfiguration.autoSyncEnabled) {
                runSyncInternal()
            }
        }
    }

    fun recordMovement(draft: MovementEntryDraft) {
        viewModelScope.launch {
            uiState = uiState.copy(isBusy = true)
            val result = repository.recordMovement(draft)
            reloadState(result.message)
            if (result.isSuccess && uiState.syncConfiguration.autoSyncEnabled) {
                runSyncInternal()
            }
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

    private suspend fun runSyncInternal() {
        val result = repository.runSync()
        val syncConfiguration = repository.loadSyncConfiguration()
        allComponentsCache = repository.loadComponents()
        uiState = uiState.copy(
            dashboard = repository.loadDashboardSnapshot(),
            availableComponents = allComponentsCache,
            components = applyComponentFilter(
                components = allComponentsCache,
                query = uiState.componentQuery,
                lowStockOnly = uiState.lowStockOnly,
            ),
            lowStockComponents = repository.loadLowStockComponents(),
            movements = repository.loadMovements(),
            syncConfiguration = syncConfiguration,
            statusMessage = if (result.isSuccess) {
                syncConfiguration.lastSyncMessage
            } else {
                result.message
            },
            isBusy = false,
        )
    }

    private suspend fun reloadState(statusMessage: String? = null) {
        allComponentsCache = repository.loadComponents()
        val syncConfiguration = repository.loadSyncConfiguration()
        val filteredComponents = applyComponentFilter(
            components = allComponentsCache,
            query = uiState.componentQuery,
            lowStockOnly = uiState.lowStockOnly,
        )
        uiState = uiState.copy(
            dashboard = repository.loadDashboardSnapshot(),
            availableComponents = allComponentsCache,
            components = filteredComponents,
            lowStockComponents = repository.loadLowStockComponents(),
            movements = repository.loadMovements(),
            syncConfiguration = syncConfiguration,
            selectedComponentId = uiState.selectedComponentId?.takeIf { selectedId ->
                filteredComponents.any { it.id == selectedId }
            },
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

    private fun applyComponentFilter(
        components: List<ComponentRecord>,
        query: String,
        lowStockOnly: Boolean,
    ): List<ComponentRecord> {
        return components.filter { component ->
            val matchesLowStock = !lowStockOnly || component.isLowStock
            val matchesQuery = query.isBlank() ||
                component.name.contains(query, ignoreCase = true) ||
                component.sku.contains(query, ignoreCase = true) ||
                component.category.contains(query, ignoreCase = true) ||
                component.location.contains(query, ignoreCase = true)

            matchesLowStock && matchesQuery
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
