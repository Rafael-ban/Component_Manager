package com.componentvault.android.ui.screen.preview

import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.ComponentLabelSeed
import com.componentvault.android.model.toLabelSeed
import com.componentvault.android.model.AppLanguage
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ImportLearningSummary
import com.componentvault.android.model.InventoryDetailUiState
import com.componentvault.android.model.InventoryFiltersUiState
import com.componentvault.android.model.InventoryListItemUiState
import com.componentvault.android.model.InventoryListUiState
import com.componentvault.android.model.InventoryScreenUiState
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.InventoryUiState
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.OcrEngineMode
import com.componentvault.android.model.OverviewUiState
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.SyncConfiguration

internal object InventoryPreviewData {
    private const val PreviewDeviceId = "android-preview-device"
    private const val DefaultServerUrl = "https://lab.example.net:8787"
    private const val DefaultLastSyncedAt = "2026-05-08T10:18:00Z"
    private const val SelectedComponentId = "cmp-2"
    private const val SelectedMovementId = "mov-1"
    private const val SyncCompleteMessage =
        "Sync complete: 1 uploaded, 1 downloaded, 0 conflicts, 2 pending."
    private const val ComponentSavedMessage = "Component saved locally. Sync pending."
    private const val EmptyInventoryMessage = "No components match the current filters."
    private const val MovementRecordedMessage = "Movement recorded locally. Sync pending."
    private const val SyncBusyMessage = "Sync in progress. Please wait."

    private val previewComponents = listOf(
        ComponentRecord(
            id = "cmp-1",
            sku = "CAP-100NF-0603",
            name = "100nF Ceramic Capacitor",
            category = "Capacitor",
            packageName = "0603",
            location = "A-01-03",
            description = "General-purpose decoupling capacitor for MCU and sensor rails.",
            quantity = 420,
            minStock = 120,
            updatedAt = "2026-05-08T09:32:00Z",
            deleted = false,
        ),
        ComponentRecord(
            id = "cmp-2",
            sku = "RES-10K-0402",
            name = "10k Ohm Resistor",
            category = "Resistor",
            packageName = "0402",
            location = "B-02-01",
            description = "Common pull-up and divider resistor kept in the mobile repair kit.",
            quantity = 16,
            minStock = 40,
            updatedAt = "2026-05-08T10:12:00Z",
            deleted = false,
        ),
        ComponentRecord(
            id = "cmp-3",
            sku = "MCU-STM32G431CB",
            name = "STM32G431CBT6",
            category = "MCU",
            packageName = "LQFP-48",
            location = "C-04-02",
            description = "Main controller used by the latest motor and power control boards.",
            quantity = 28,
            minStock = 12,
            updatedAt = "2026-05-07T18:05:00Z",
            deleted = false,
        ),
        ComponentRecord(
            id = "cmp-4",
            sku = "LED-GRN-0805",
            name = "Green LED",
            category = "Indicator",
            packageName = "0805",
            location = "A-01-08",
            description = "Panel indicator LED used in power and IO boards.",
            quantity = 64,
            minStock = 30,
            updatedAt = "2026-05-07T09:22:00Z",
            deleted = false,
        ),
        ComponentRecord(
            id = "cmp-5",
            sku = "CONN-USB-C-16P",
            name = "USB-C Receptacle",
            category = "Connector",
            packageName = "16P",
            location = "D-02-06",
            description = "Board edge USB-C connector for power and firmware flashing.",
            quantity = 8,
            minStock = 18,
            updatedAt = "2026-05-08T07:30:00Z",
            deleted = false,
        ),
    )

    private val previewMovements = listOf(
        StockMovementRecord(
            id = "mov-1",
            componentId = "cmp-2",
            componentSku = "RES-10K-0402",
            componentName = "10k Ohm Resistor",
            movementType = "outbound",
            quantity = -24,
            reason = "Prototype assembly",
            note = "Reserved for the handheld tester batch.",
            happenedAt = "2026-05-08T09:05:00Z",
            updatedAt = "2026-05-08T09:05:00Z",
            deleted = false,
        ),
        StockMovementRecord(
            id = "mov-2",
            componentId = "cmp-1",
            componentSku = "CAP-100NF-0603",
            componentName = "100nF Ceramic Capacitor",
            movementType = "inbound",
            quantity = 200,
            reason = "Restock delivery",
            note = "Supplier batch 2026-W19.",
            happenedAt = "2026-05-08T08:10:00Z",
            updatedAt = "2026-05-08T08:10:00Z",
            deleted = false,
        ),
        StockMovementRecord(
            id = "mov-3",
            componentId = "cmp-3",
            componentSku = "MCU-STM32G431CB",
            componentName = "STM32G431CBT6",
            movementType = "adjustment",
            quantity = -2,
            reason = "Stock correction",
            note = "Removed two damaged units after incoming inspection.",
            happenedAt = "2026-05-07T17:52:00Z",
            updatedAt = "2026-05-07T17:52:00Z",
            deleted = false,
        ),
        StockMovementRecord(
            id = "mov-4",
            componentId = "cmp-5",
            componentSku = "CONN-USB-C-16P",
            componentName = "USB-C Receptacle",
            movementType = "outbound",
            quantity = -6,
            reason = "Repair kit refill",
            note = "",
            happenedAt = "2026-05-07T10:12:00Z",
            updatedAt = "2026-05-07T10:12:00Z",
            deleted = false,
        ),
        StockMovementRecord(
            id = "mov-5",
            componentId = "cmp-4",
            componentSku = "LED-GRN-0805",
            componentName = "Green LED",
            movementType = "inbound",
            quantity = 60,
            reason = "Supplier delivery",
            note = "Warehouse receiving note 118.",
            happenedAt = "2026-05-06T16:44:00Z",
            updatedAt = "2026-05-06T16:44:00Z",
            deleted = false,
        ),
    )

    val components: List<ComponentRecord>
        get() = previewComponents

    val selectedComponent: ComponentRecord
        get() = previewComponents.first { it.id == SelectedComponentId }

    val selectedLabelSeed: ComponentLabelSeed
        get() = selectedComponent.toLabelSeed()

    val longLabelSeed: ComponentLabelSeed
        get() = ComponentLabelSeed(
            sku = "C7430468",
            name = "1x2P 间距:1.25mm 卧贴 系列:PicoBlade(MX 1.25)",
            category = "Connector / Wire-to-board",
            packageName = "SMD,P=1.25mm,卧贴",
            location = "Drawer-CN-12-A",
            quantity = 300,
            minStock = 60,
            model = "ZX-MX1.25-2PWT",
            brand = "Megastar(兆星)",
            sourceLabel = "JLC package QR",
            rawPayload = "{on:SO25020715054,pc:C30926,pm:0603B104K500NT,qty:300}",
        )

    val wideLabelSeed: ComponentLabelSeed
        get() = ComponentLabelSeed(
            sku = "C7430468",
            name = "1x2P \u95f4\u8ddd:1.25mm \u5367\u8d34 \u7cfb\u5217:PicoBlade(MX 1.25)",
            category = "Connector / Wire-to-board",
            packageName = "SMD,P=1.25mm,\u5367\u8d34",
            location = "Drawer-CN-12-A",
            quantity = 300,
            minStock = 60,
            model = "ZX-MX1.25-2PWT",
            brand = "Megastar(\u5146\u661f)",
            sourceLabel = "JLC package QR",
            rawPayload = "{on:SO25020715054,pc:C30926,pm:0603B104K500NT,qty:300}",
        )

    val selectedComponentId: String
        get() = SelectedComponentId

    val selectedMovementId: String
        get() = SelectedMovementId

    private fun toListItem(component: ComponentRecord): InventoryListItemUiState {
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

    private fun previewSyncConfiguration(
        lastSyncMessage: String,
        serverBaseUrl: String = DefaultServerUrl,
        autoSyncEnabled: Boolean = true,
        lastSyncedAt: String = DefaultLastSyncedAt,
    ): SyncConfiguration = SyncConfiguration(
        deviceId = PreviewDeviceId,
        serverBaseUrl = serverBaseUrl,
        apiToken = "preview-sync-token",
        autoSyncEnabled = autoSyncEnabled,
        lastSyncedAt = lastSyncedAt,
        lastSyncMessage = lastSyncMessage,
    )

    private fun filteredComponents(
        filters: InventoryFiltersUiState,
    ): List<ComponentRecord> {
        val filtered = previewComponents.filter { component ->
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
            InventorySortOption.UpdatedNewest -> filtered.sortedByDescending { it.updatedAt }
            InventorySortOption.Name -> filtered.sortedBy { it.name.lowercase() }
            InventorySortOption.QuantityLowToHigh -> filtered.sortedBy { it.quantity }
            InventorySortOption.QuantityHighToLow -> filtered.sortedByDescending { it.quantity }
        }
    }

    private fun previewInventoryScreenState(
        filters: InventoryFiltersUiState,
        selectedComponentId: String? = SelectedComponentId,
    ): InventoryScreenUiState {
        val filtered = filteredComponents(filters)
        val selectedComponent = filtered.firstOrNull { it.id == selectedComponentId }
        return InventoryScreenUiState(
            filters = filters,
            availableCategories = previewComponents.map { it.category }.distinct().sorted(),
            availableLocations = previewComponents.map { it.location }.distinct().sorted(),
            list = InventoryListUiState(
                items = filtered.map(::toListItem),
                selectedComponentId = selectedComponent?.id,
            ),
            detail = InventoryDetailUiState(
                component = selectedComponent,
                recentMovements = if (selectedComponent == null) {
                    emptyList()
                } else {
                    previewMovements.filter { it.componentId == selectedComponent.id }.take(5)
                },
            ),
        )
    }

    private fun previewOverviewUiState(): OverviewUiState {
        return OverviewUiState(
            componentCount = previewComponents.size,
            totalUnits = previewComponents.sumOf { it.quantity },
            lowStockCount = previewComponents.count { it.isLowStock },
            movementCount = previewMovements.size,
            lowStockItems = previewComponents.filter { it.isLowStock }.map(::toListItem),
            recentMovements = previewMovements.take(4),
        )
    }

    private fun previewState(
        syncConfiguration: SyncConfiguration,
        statusMessage: String,
        inventory: InventoryScreenUiState,
        isBusy: Boolean = false,
    ): InventoryUiState = InventoryUiState(
        overview = previewOverviewUiState(),
        availableComponents = previewComponents,
        inventory = inventory,
        movements = MovementsUiState(
            items = previewMovements,
            componentCount = previewComponents.size,
        ),
        importLearningSummary = ImportLearningSummary(mappingCount = 6),
        appPreferences = previewAppPreferences(),
        syncConfiguration = syncConfiguration,
        isBusy = isBusy,
        statusMessage = statusMessage,
    )

    private fun previewAppPreferences(): AppPreferences = AppPreferences(
        defaultImportLocation = "A-01-03",
        lastImportLocation = "B-04-02",
        defaultImportMinStock = 12,
        rememberLastImportLocation = true,
        syncAfterLocalChanges = true,
        enableLocalImportLearning = true,
        enableServerJlcLookup = false,
        ocrEngineMode = OcrEngineMode.Auto,
        appLanguage = AppLanguage.ZhCn,
    )

    fun overviewState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = SyncCompleteMessage),
            statusMessage = SyncCompleteMessage,
            inventory = previewInventoryScreenState(InventoryFiltersUiState()),
        )
    }

    fun inventoryState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = ComponentSavedMessage),
            statusMessage = ComponentSavedMessage,
            inventory = previewInventoryScreenState(InventoryFiltersUiState()),
        )
    }

    fun inventoryLowStockState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = ComponentSavedMessage),
            statusMessage = ComponentSavedMessage,
            inventory = previewInventoryScreenState(
                filters = InventoryFiltersUiState(stockFilter = InventoryStockFilter.LowStock),
                selectedComponentId = "cmp-5",
            ),
        )
    }

    fun inventoryEmptyState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = EmptyInventoryMessage),
            statusMessage = EmptyInventoryMessage,
            inventory = previewInventoryScreenState(
                filters = InventoryFiltersUiState(query = "BGA"),
                selectedComponentId = null,
            ),
        )
    }

    fun movementsState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = MovementRecordedMessage),
            statusMessage = MovementRecordedMessage,
            inventory = previewInventoryScreenState(InventoryFiltersUiState()),
        )
    }

    fun settingsState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(lastSyncMessage = SyncCompleteMessage),
            statusMessage = SyncCompleteMessage,
            inventory = previewInventoryScreenState(InventoryFiltersUiState()),
        )
    }

    fun busySettingsState(): InventoryUiState {
        return previewState(
            syncConfiguration = previewSyncConfiguration(
                lastSyncMessage = SyncBusyMessage,
                serverBaseUrl = "https://staging.example.net:8787",
            ),
            statusMessage = SyncBusyMessage,
            inventory = previewInventoryScreenState(InventoryFiltersUiState()),
            isBusy = true,
        )
    }
}
