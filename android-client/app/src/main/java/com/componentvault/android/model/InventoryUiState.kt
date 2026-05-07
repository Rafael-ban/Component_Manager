package com.componentvault.android.model

data class InventoryUiState(
    val dashboard: DashboardSnapshot = DashboardSnapshot(
        componentCount = 0,
        totalUnits = 0,
        lowStockCount = 0,
        movementCount = 0,
    ),
    val availableComponents: List<ComponentRecord> = emptyList(),
    val components: List<ComponentRecord> = emptyList(),
    val lowStockComponents: List<ComponentRecord> = emptyList(),
    val movements: List<StockMovementRecord> = emptyList(),
    val syncConfiguration: SyncConfiguration = SyncConfiguration(
        deviceId = "",
        serverBaseUrl = "",
        apiToken = "",
        autoSyncEnabled = false,
        lastSyncedAt = "",
        lastSyncMessage = "",
    ),
    val componentQuery: String = "",
    val lowStockOnly: Boolean = false,
    val selectedComponentId: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String = "",
)
