package com.componentvault.android.ui.screen

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
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
    @StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.destination_dashboard, Icons.Outlined.Analytics),
    Components(R.string.destination_components, Icons.Outlined.Memory),
    Movements(R.string.destination_movements, Icons.Outlined.Inventory2),
    Settings(R.string.destination_settings, Icons.Outlined.Settings),
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
            title = { Text(stringResource(R.string.top_bar_title)) },
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
                        Text(stringResource(R.string.action_sync))
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
                    val label = stringResource(item.labelResId)
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
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
                        val label = stringResource(item.labelResId)
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
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
                text = stringResource(R.string.dashboard_overview),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.metric_components), uiState.dashboard.componentCount.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.metric_units), uiState.dashboard.totalUnits.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.metric_low_stock), uiState.dashboard.lowStockCount.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.metric_movements), uiState.dashboard.movementCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.section_low_stock_watchlist_title),
                subtitle = stringResource(R.string.section_low_stock_watchlist_subtitle),
            ) {
                if (uiState.lowStockComponents.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_all_components_healthy))
                } else {
                    uiState.lowStockComponents.forEach { component ->
                        ComponentLine(component = component)
                    }
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.section_recent_activity_title),
                subtitle = stringResource(R.string.section_recent_activity_subtitle),
            ) {
                if (uiState.movements.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_no_movements))
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
                text = stringResource(R.string.components_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.metric_visible), uiState.components.size.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.metric_low_stock), uiState.lowStockComponents.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(
                value = uiState.componentQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_components_label)) },
                placeholder = { Text(stringResource(R.string.search_components_placeholder)) },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !uiState.lowStockOnly,
                    onClick = { onLowStockToggle(false) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                FilterChip(
                    selected = uiState.lowStockOnly,
                    onClick = { onLowStockToggle(true) },
                    label = { Text(stringResource(R.string.filter_low_stock)) },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddComponent) { Text(stringResource(R.string.action_add)) }
                OutlinedButton(
                    onClick = onEditComponent,
                    enabled = uiState.selectedComponentId != null,
                ) { Text(stringResource(R.string.action_edit)) }
                OutlinedButton(
                    onClick = onDeleteComponent,
                    enabled = uiState.selectedComponentId != null,
                ) { Text(stringResource(R.string.action_delete)) }
            }
        }
        item {
            ComponentDetailCard(selectedComponent)
        }
        if (uiState.components.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.empty_no_components_match_filter))
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
                                text = stringResource(
                                    R.string.component_subtitle_format,
                                    component.sku,
                                    component.category,
                                    component.packageName,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AssistChip(
                            onClick = { onSelectComponent(component.id) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.component_quantity_pieces_format,
                                        component.quantity,
                                    ),
                                )
                            },
                        )
                    }
                    val description = if (component.description.isBlank()) {
                        stringResource(R.string.label_no_description)
                    } else {
                        component.description
                    }
                    Text(
                        text = description,
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
                            label = {
                                Text(
                                    stringResource(
                                        R.string.component_min_stock_format,
                                        component.minStock,
                                    ),
                                )
                            },
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
    val subtitle = component?.let {
        stringResource(
            R.string.component_subtitle_format,
            it.sku,
            it.category,
            it.packageName,
        )
    } ?: stringResource(R.string.selected_component_empty_subtitle)

    SectionCard(
        title = stringResource(R.string.selected_component_title),
        subtitle = subtitle,
    ) {
        if (component == null) {
            EmptyState(stringResource(R.string.empty_no_component_selected))
            return@SectionCard
        }

        StatusCard(
            if (component.isLowStock) {
                stringResource(R.string.selected_component_low_stock_status)
            } else {
                stringResource(R.string.selected_component_healthy_status)
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.metric_quantity), component.quantity.toString(), Modifier.weight(1f))
            MetricCard(stringResource(R.string.metric_min_stock), component.minStock.toString(), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(component.location) },
                leadingIcon = { Dot(color = MaterialTheme.colorScheme.primary) },
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (component.isLowStock) {
                            stringResource(R.string.status_low_stock)
                        } else {
                            stringResource(R.string.status_healthy)
                        },
                    )
                },
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

        SettingRow(stringResource(R.string.label_updated), component.updatedAt)
        val description = if (component.description.isBlank()) {
            stringResource(R.string.label_no_description)
        } else {
            component.description
        }
        Text(
            text = description,
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
                text = stringResource(R.string.movements_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(uiState.statusMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.metric_recorded), uiState.movements.size.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.metric_components), uiState.availableComponents.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.record_next_movement_title),
                subtitle = if (canRecordMovement) {
                    stringResource(R.string.record_next_movement_subtitle)
                } else {
                    stringResource(R.string.record_next_movement_subtitle_disabled)
                },
            ) {
                StatusCard(
                    if (canRecordMovement) {
                        stringResource(R.string.record_next_movement_status)
                    } else {
                        stringResource(R.string.record_next_movement_status_disabled)
                    },
                )
                Button(
                    onClick = onRecordMovement,
                    enabled = canRecordMovement,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_record_movement))
                }
            }
        }
        if (uiState.movements.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.empty_no_movements))
            }
        }
        items(uiState.movements, key = { it.id }) { movement ->
            SectionCard(
                title = movement.componentName,
                subtitle = stringResource(
                    R.string.movement_card_subtitle_format,
                    movement.componentSku,
                    movement.happenedAt,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val reason = if (movement.reason.isBlank()) {
                            stringResource(R.string.label_no_reason_recorded)
                        } else {
                            movement.reason
                        }
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        val note = if (movement.note.isBlank()) {
                            stringResource(R.string.label_no_note)
                        } else {
                            movement.note
                        }
                        Text(
                            text = note,
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
                        label = { Text(stringResource(R.string.updated_at_format, movement.updatedAt)) },
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            StatusCard(statusMessage)
        }
        item {
            SectionCard(
                title = stringResource(R.string.sync_summary_title),
                subtitle = stringResource(R.string.sync_summary_subtitle),
            ) {
                val notConfigured = stringResource(R.string.label_not_configured)
                SettingRow(
                    stringResource(R.string.setting_endpoint),
                    syncConfiguration.serverBaseUrl.ifBlank { notConfigured },
                )
                SettingRow(
                    stringResource(R.string.setting_token),
                    syncConfiguration.apiTokenMasked.ifBlank { notConfigured },
                )
                SettingRow(
                    stringResource(R.string.setting_auto_sync),
                    if (syncConfiguration.autoSyncEnabled) {
                        stringResource(R.string.label_enabled)
                    } else {
                        stringResource(R.string.label_disabled)
                    },
                )
                SettingRow(stringResource(R.string.setting_last_synced), syncConfiguration.lastSyncedAt)
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.server_connection_title),
                subtitle = stringResource(R.string.server_connection_subtitle),
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_server_url)) },
                    placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_api_token)) },
                    singleLine = true,
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )
                TextButton(onClick = { showToken = !showToken }) {
                    Text(
                        if (showToken) {
                            stringResource(R.string.action_hide_token)
                        } else {
                            stringResource(R.string.action_show_token)
                        },
                    )
                }
                SettingRow(stringResource(R.string.setting_device_id), syncConfiguration.deviceId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.setting_auto_sync),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.auto_sync_description),
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
                title = stringResource(R.string.sync_actions_title),
                subtitle = stringResource(R.string.sync_actions_subtitle),
            ) {
                StatusCard(
                    if (isBusy) {
                        stringResource(R.string.sync_action_busy)
                    } else {
                        syncConfiguration.lastSyncMessage
                    },
                )
                SettingRow(stringResource(R.string.setting_last_result), syncConfiguration.lastSyncMessage)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_save_settings))
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                            onTestConnection()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_test_connection))
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
                            onSyncNow()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_sync_now))
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
            label = {
                Text(
                    stringResource(
                        R.string.component_ratio_format,
                        component.quantity,
                        component.minStock,
                    ),
                )
            },
            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor = VaultWarningContainer,
                labelColor = VaultWarning,
            ),
        )
    }
}

@Composable
private fun MovementLine(movement: StockMovementRecord) {
    val movementLabel = movementTypeLabel(movement.movementType)
    val reason = if (movement.reason.isBlank()) {
        stringResource(R.string.label_no_reason_recorded)
    } else {
        movement.reason
    }
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
                text = stringResource(
                    R.string.movement_line_reason_format,
                    movementLabel,
                    reason,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatMovementQuantity(movement.quantity),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MovementTypeChip(movementType: String) {
    val label = movementTypeLabel(movementType)
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
    val text = formatMovementQuantity(movement.quantity)
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
private fun movementTypeLabel(movementType: String): String = when (movementType.lowercase()) {
    "inbound" -> stringResource(R.string.movement_type_inbound)
    "outbound" -> stringResource(R.string.movement_type_outbound)
    else -> stringResource(R.string.movement_type_adjustment)
}

@Composable
private fun formatMovementQuantity(quantity: Int): String = if (quantity > 0) {
    stringResource(R.string.movement_positive_quantity_format, quantity)
} else {
    quantity.toString()
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
    val invalidNumberMessage = stringResource(R.string.dialog_invalid_quantity_min_stock)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    stringResource(R.string.dialog_add_component_title)
                } else {
                    stringResource(R.string.dialog_edit_component_title)
                },
            )
        },
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
                        label = { Text(stringResource(R.string.field_sku)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_name)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_category)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_package)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_location)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_description)) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_quantity)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_minimum_stock)) },
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
                        errorMessage = invalidNumberMessage
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
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
    val invalidMovementMessage = stringResource(R.string.movement_editor_invalid_selection)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_record_stock_movement_title)) },
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
                        label = { Text(stringResource(R.string.field_component)) },
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
                        value = movementTypeLabel(movementType),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            )
                            .fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_movement_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypes) },
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypes,
                        onDismissRequest = { expandedTypes = false },
                    ) {
                        movementTypes.forEach { type ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(movementTypeLabel(type)) },
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
                    label = { Text(stringResource(R.string.field_quantity)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_reason)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_note)) },
                )
                Text(
                    text = stringResource(R.string.movement_editor_instruction),
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
                        errorMessage = invalidMovementMessage
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
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
