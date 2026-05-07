package com.componentvault.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.DashboardSnapshot
import com.componentvault.android.model.InventoryUiState
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.SyncConfiguration
import com.componentvault.android.ui.theme.VaultWarning
import com.componentvault.android.ui.theme.VaultWarningContainer

private enum class InventoryDestination(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("Dashboard", Icons.Outlined.Analytics),
    Components("Components", Icons.Outlined.Memory),
    Movements("Movements", Icons.Outlined.Inventory2),
    Settings("Settings", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentVaultApp(
    viewModel: InventoryViewModel,
) {
    val uiState = viewModel.uiState
    var destination by rememberSaveable { mutableStateOf(InventoryDestination.Dashboard) }
    var editingComponent by remember { mutableStateOf<ComponentRecord?>(null) }
    var showComponentEditor by remember { mutableStateOf(false) }
    var showMovementEditor by remember { mutableStateOf(false) }

    val isExpanded = LocalConfiguration.current.screenWidthDp >= 840
    val selectedComponent = uiState.availableComponents.firstOrNull { it.id == uiState.selectedComponentId }

    if (showComponentEditor) {
        ComponentEditorDialog(
            existing = editingComponent,
            onDismiss = {
                showComponentEditor = false
                editingComponent = null
            },
            onSave = { draft ->
                viewModel.saveComponent(draft)
                showComponentEditor = false
                editingComponent = null
            },
        )
    }

    if (showMovementEditor) {
        MovementEditorDialog(
            components = uiState.availableComponents,
            selectedComponentId = selectedComponent?.id,
            onDismiss = { showMovementEditor = false },
            onSave = { draft ->
                viewModel.recordMovement(draft)
                showMovementEditor = false
            },
        )
    }

    val topBar: @Composable () -> Unit = {
        CenterAlignedTopAppBar(
            title = { Text("Component Vault") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    OutlinedButton(
                        onClick = { viewModel.runSync() },
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync")
                    }
                }
            },
        )
    }

    if (isExpanded) {
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
                        Text(
                            text = "CV",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
            ) {
                InventoryDestination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }

            Scaffold(
                topBar = topBar,
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                InventoryContent(
                    modifier = Modifier.padding(padding),
                    destination = destination,
                    uiState = uiState,
                    onQueryChange = viewModel::updateComponentQuery,
                    onLowStockToggle = viewModel::setLowStockOnly,
                    onSelectComponent = viewModel::selectComponent,
                    onAddComponent = {
                        editingComponent = null
                        showComponentEditor = true
                    },
                    onEditComponent = {
                        editingComponent = selectedComponent
                        showComponentEditor = selectedComponent != null
                    },
                    onDeleteComponent = viewModel::deleteSelectedComponent,
                    onRecordMovement = { showMovementEditor = true },
                    onSaveSettings = viewModel::saveSyncConfiguration,
                    onTestConnection = viewModel::testConnection,
                    onSyncNow = viewModel::runSync,
                )
            }
        }
    } else {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                NavigationBar {
                    InventoryDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            InventoryContent(
                modifier = Modifier.padding(padding),
                destination = destination,
                uiState = uiState,
                onQueryChange = viewModel::updateComponentQuery,
                onLowStockToggle = viewModel::setLowStockOnly,
                onSelectComponent = viewModel::selectComponent,
                onAddComponent = {
                    editingComponent = null
                    showComponentEditor = true
                },
                onEditComponent = {
                    editingComponent = selectedComponent
                    showComponentEditor = selectedComponent != null
                },
                onDeleteComponent = viewModel::deleteSelectedComponent,
                onRecordMovement = { showMovementEditor = true },
                onSaveSettings = viewModel::saveSyncConfiguration,
                onTestConnection = viewModel::testConnection,
                onSyncNow = viewModel::runSync,
            )
        }
    }
}

@Composable
private fun InventoryContent(
    modifier: Modifier,
    destination: InventoryDestination,
    uiState: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onLowStockToggle: (Boolean) -> Unit,
    onSelectComponent: (String?) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: () -> Unit,
    onDeleteComponent: () -> Unit,
    onRecordMovement: () -> Unit,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
) {
    when (destination) {
        InventoryDestination.Dashboard -> DashboardScreen(
            modifier = modifier,
            uiState = uiState,
        )
        InventoryDestination.Components -> ComponentsScreen(
            modifier = modifier,
            uiState = uiState,
            onQueryChange = onQueryChange,
            onLowStockToggle = onLowStockToggle,
            onSelectComponent = onSelectComponent,
            onAddComponent = onAddComponent,
            onEditComponent = onEditComponent,
            onDeleteComponent = onDeleteComponent,
        )
        InventoryDestination.Movements -> MovementsScreen(
            modifier = modifier,
            uiState = uiState,
            canRecordMovement = uiState.availableComponents.isNotEmpty(),
            onRecordMovement = onRecordMovement,
        )
        InventoryDestination.Settings -> SettingsScreen(
            modifier = modifier,
            syncConfiguration = uiState.syncConfiguration,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            onSaveSettings = onSaveSettings,
            onTestConnection = onTestConnection,
            onSyncNow = onSyncNow,
        )
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier,
    uiState: InventoryUiState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Components", uiState.dashboard.componentCount.toString(), Modifier.weight(1f))
                MetricCard("Units", uiState.dashboard.totalUnits.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Low stock", uiState.dashboard.lowStockCount.toString(), Modifier.weight(1f))
                MetricCard("Movements", uiState.dashboard.movementCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard(
                title = "Low-stock watchlist",
                subtitle = "Components at or below the configured threshold.",
            ) {
                if (uiState.lowStockComponents.isEmpty()) {
                    EmptyState("All tracked components are above minimum stock.")
                } else {
                    uiState.lowStockComponents.forEach { component ->
                        ComponentLine(component = component)
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Recent activity",
                subtitle = "Latest stock movements stored on the device.",
            ) {
                if (uiState.movements.isEmpty()) {
                    EmptyState("No stock movements recorded yet.")
                } else {
                    uiState.movements.take(8).forEach { movement ->
                        MovementLine(movement = movement)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentsScreen(
    modifier: Modifier,
    uiState: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onLowStockToggle: (Boolean) -> Unit,
    onSelectComponent: (String?) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: () -> Unit,
    onDeleteComponent: () -> Unit,
) {
    val selectedComponent = uiState.availableComponents.firstOrNull {
        it.id == uiState.selectedComponentId
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Components",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Visible", uiState.components.size.toString(), Modifier.weight(1f))
                MetricCard("Low stock", uiState.lowStockComponents.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(
                value = uiState.componentQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search components") },
                placeholder = { Text("SKU, name, category, location") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !uiState.lowStockOnly,
                    onClick = { onLowStockToggle(false) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = uiState.lowStockOnly,
                    onClick = { onLowStockToggle(true) },
                    label = { Text("Low stock") },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddComponent) { Text("Add") }
                OutlinedButton(
                    onClick = onEditComponent,
                    enabled = uiState.selectedComponentId != null,
                ) { Text("Edit") }
                OutlinedButton(
                    onClick = onDeleteComponent,
                    enabled = uiState.selectedComponentId != null,
                ) { Text("Delete") }
            }
        }
        item {
            ComponentDetailCard(selectedComponent)
        }
        if (uiState.components.isEmpty()) {
            item {
                EmptyState("No components match the current filter.")
            }
        }
        items(uiState.components, key = { it.id }) { component ->
            val isSelected = component.id == uiState.selectedComponentId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectComponent(component.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = component.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${component.sku} | ${component.category} | ${component.packageName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AssistChip(
                            onClick = { onSelectComponent(component.id) },
                            label = { Text("${component.quantity} pcs") },
                        )
                    }
                    Text(
                        text = component.description.ifBlank { "No description." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { onSelectComponent(component.id) },
                            label = { Text(component.location) },
                            leadingIcon = { Dot(color = MaterialTheme.colorScheme.primary) },
                        )
                        AssistChip(
                            onClick = { onSelectComponent(component.id) },
                            label = { Text("Min ${component.minStock}") },
                            colors = if (component.isLowStock) {
                                androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = VaultWarningContainer,
                                    labelColor = VaultWarning,
                                )
                            } else {
                                androidx.compose.material3.AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentDetailCard(component: ComponentRecord?) {
    SectionCard(
        title = "Selected component",
        subtitle = component?.let {
            "${it.sku} | ${it.category} | ${it.packageName}"
        } ?: "Pick a component from the list to review stock level, storage location, and update timing.",
    ) {
        if (component == null) {
            EmptyState("No component selected yet.")
            return@SectionCard
        }

        StatusCard(
            if (component.isLowStock) {
                "Reorder recommended. Quantity is at or below the configured minimum stock."
            } else {
                "Stock is above the configured minimum threshold."
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Quantity", component.quantity.toString(), Modifier.weight(1f))
            MetricCard("Min stock", component.minStock.toString(), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(component.location) },
                leadingIcon = { Dot(color = MaterialTheme.colorScheme.primary) },
            )
            AssistChip(
                onClick = {},
                label = { Text(if (component.isLowStock) "Low stock" else "Healthy") },
                colors = if (component.isLowStock) {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = VaultWarningContainer,
                        labelColor = VaultWarning,
                    )
                } else {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors()
                },
            )
        }

        SettingRow("Updated", component.updatedAt)
        Text(
            text = component.description.ifBlank { "No description." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MovementsScreen(
    modifier: Modifier,
    uiState: InventoryUiState,
    canRecordMovement: Boolean,
    onRecordMovement: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Stock movements",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Recorded", uiState.movements.size.toString(), Modifier.weight(1f))
                MetricCard("Components", uiState.availableComponents.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard(
                title = "Record next movement",
                subtitle = if (canRecordMovement) {
                    "Capture inbound, outbound, or adjustment activity after it has been stored locally."
                } else {
                    "Create at least one component before recording stock movement."
                },
            ) {
                StatusCard(
                    if (canRecordMovement) {
                        "Use positive quantities for inbound and outbound. Adjustment can be positive or negative."
                    } else {
                        "Movement capture is unavailable until a component exists in the local inventory."
                    },
                )
                Button(
                    onClick = onRecordMovement,
                    enabled = canRecordMovement,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Record movement")
                }
            }
        }
        if (uiState.movements.isEmpty()) {
            item {
                EmptyState("No stock movements recorded yet.")
            }
        }
        items(uiState.movements, key = { it.id }) { movement ->
            SectionCard(
                title = movement.componentName,
                subtitle = "${movement.componentSku} | ${movement.happenedAt}",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = movement.reason.ifBlank { "No reason recorded." },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = movement.note.ifBlank { "No note." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MovementQuantityChip(movement)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MovementTypeChip(movement.movementType)
                    AssistChip(
                        onClick = {},
                        label = { Text("Updated ${movement.updatedAt}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    syncConfiguration: SyncConfiguration,
    isBusy: Boolean,
    statusMessage: String,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
) {
    var serverUrl by remember(syncConfiguration.serverBaseUrl) {
        mutableStateOf(syncConfiguration.serverBaseUrl)
    }
    var apiToken by remember(syncConfiguration.apiToken) {
        mutableStateOf(syncConfiguration.apiToken)
    }
    var autoSyncEnabled by remember(syncConfiguration.autoSyncEnabled) {
        mutableStateOf(syncConfiguration.autoSyncEnabled)
    }
    var showToken by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Sync settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(statusMessage)
        }
        item {
            SectionCard(
                title = "Sync summary",
                subtitle = "Local changes always land on the device first. Cloud sync remains optional.",
            ) {
                SettingRow(
                    "Endpoint",
                    syncConfiguration.serverBaseUrl.ifBlank { "Not configured" },
                )
                SettingRow(
                    "Token",
                    syncConfiguration.apiTokenMasked.ifBlank { "Not configured" },
                )
                SettingRow(
                    "Auto sync",
                    if (syncConfiguration.autoSyncEnabled) "Enabled" else "Disabled",
                )
                SettingRow("Last synced", syncConfiguration.lastSyncedAt)
            }
        }
        item {
            SectionCard(
                title = "Server connection",
                subtitle = "Local-first sync remains optional and administrator-controlled.",
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") },
                    placeholder = { Text("http://localhost:8787") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API token") },
                    singleLine = true,
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Hide token" else "Show token")
                }
                SettingRow("Device ID", syncConfiguration.deviceId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Auto sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Run on app start and after successful local changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { autoSyncEnabled = it },
                    )
                }
            }
        }
        item {
            SectionCard(
                title = "Sync actions",
                subtitle = "Save settings before testing connectivity or pushing and pulling changes.",
            ) {
                StatusCard(
                    if (isBusy) {
                        "A sync-related action is currently running."
                    } else {
                        syncConfiguration.lastSyncMessage
                    },
                )
                SettingRow("Last result", syncConfiguration.lastSyncMessage)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save settings")
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                            onTestConnection()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Test connection")
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                            onSyncNow()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sync now")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComponentLine(component: ComponentRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = component.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${component.sku} | ${component.location}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            label = { Text("${component.quantity}/${component.minStock}") },
            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor = VaultWarningContainer,
                labelColor = VaultWarning,
            ),
        )
    }
}

@Composable
private fun MovementLine(movement: StockMovementRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movement.componentName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${movement.movementType} | ${movement.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (movement.quantity > 0) "+${movement.quantity}" else movement.quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MovementTypeChip(movementType: String) {
    val label = movementType.replaceFirstChar { it.uppercase() }
    val colors = when (movementType) {
        "inbound" -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        "outbound" -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = VaultWarningContainer,
            labelColor = VaultWarning,
        )
        else -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = colors,
    )
}

@Composable
private fun MovementQuantityChip(movement: StockMovementRecord) {
    val text = if (movement.quantity > 0) "+${movement.quantity}" else movement.quantity.toString()
    val colors = when (movement.movementType) {
        "inbound" -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        "outbound" -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = colors,
    )
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color = color, shape = RoundedCornerShape(99.dp)),
    )
}

@Composable
private fun ComponentEditorDialog(
    existing: ComponentRecord?,
    onDismiss: () -> Unit,
    onSave: (ComponentDraft) -> Unit,
) {
    var sku by remember(existing?.id) { mutableStateOf(existing?.sku ?: "") }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "") }
    var packageName by remember(existing?.id) { mutableStateOf(existing?.packageName ?: "") }
    var location by remember(existing?.id) { mutableStateOf(existing?.location ?: "") }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var quantityText by remember(existing?.id) { mutableStateOf((existing?.quantity ?: 0).toString()) }
    var minStockText by remember(existing?.id) { mutableStateOf((existing?.minStock ?: 0).toString()) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add component" else "Edit component") },
        text = {
            LazyColumn(
                modifier = Modifier.widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SKU") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Category") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Quantity") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minimum stock") },
                        singleLine = true,
                    )
                }
                if (errorMessage.isNotBlank()) {
                    item {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = quantityText.toIntOrNull()
                    val minStock = minStockText.toIntOrNull()
                    if (quantity == null || minStock == null) {
                        errorMessage = "Quantity and minimum stock must be valid integers."
                        return@TextButton
                    }
                    onSave(
                        ComponentDraft(
                            id = existing?.id,
                            sku = sku,
                            name = name,
                            category = category,
                            packageName = packageName,
                            location = location,
                            description = description,
                            quantity = quantity,
                            minStock = minStock,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementEditorDialog(
    components: List<ComponentRecord>,
    selectedComponentId: String?,
    onDismiss: () -> Unit,
    onSave: (MovementEntryDraft) -> Unit,
) {
    var expandedComponents by remember { mutableStateOf(false) }
    var expandedTypes by remember { mutableStateOf(false) }
    var selectedComponent by remember(selectedComponentId, components) {
        mutableStateOf(
            components.firstOrNull { it.id == selectedComponentId } ?: components.firstOrNull(),
        )
    }
    var movementType by remember { mutableStateOf("inbound") }
    var quantityText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val movementTypes = listOf("inbound", "outbound", "adjustment")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record stock movement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expandedComponents,
                    onExpandedChange = { expandedComponents = !expandedComponents },
                ) {
                    OutlinedTextField(
                        value = selectedComponent?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            )
                            .fillMaxWidth(),
                        label = { Text("Component") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedComponents) },
                    )
                    ExposedDropdownMenu(
                        expanded = expandedComponents,
                        onDismissRequest = { expandedComponents = false },
                    ) {
                        components.forEach { component ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${component.name} (${component.sku})") },
                                onClick = {
                                    selectedComponent = component
                                    expandedComponents = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTypes,
                    onExpandedChange = { expandedTypes = !expandedTypes },
                ) {
                    OutlinedTextField(
                        value = movementType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            )
                            .fillMaxWidth(),
                        label = { Text("Movement type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypes) },
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypes,
                        onDismissRequest = { expandedTypes = false },
                    ) {
                        movementTypes.forEach { type ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    movementType = type
                                    expandedTypes = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quantity") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                )
                Text(
                    text = "Use positive values for inbound/outbound. Adjustment can be positive or negative.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = quantityText.toIntOrNull()
                    if (selectedComponent == null || quantity == null) {
                        errorMessage = "Choose a component and enter a valid quantity."
                        return@TextButton
                    }
                    onSave(
                        MovementEntryDraft(
                            componentId = selectedComponent!!.id,
                            movementType = movementType,
                            quantity = quantity,
                            reason = reason,
                            note = note,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
