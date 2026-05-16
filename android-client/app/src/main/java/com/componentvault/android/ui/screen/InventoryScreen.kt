package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.InventoryDetailUiState
import com.componentvault.android.model.InventoryScreenUiState
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.InventoryStockFilter
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.ui.theme.VaultWarning
import com.componentvault.android.ui.theme.VaultWarningContainer

@Composable
internal fun InventoryScreen(
    contentPadding: PaddingValues,
    uiState: InventoryScreenUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onImportComponent: () -> Unit,
    onGenerateLabel: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String) -> Unit,
) {
    InventoryContent(
        contentPadding = contentPadding,
        uiState = uiState,
        statusMessage = statusMessage,
        layoutMode = layoutMode,
        onQueryChange = onQueryChange,
        onStockFilterChange = onStockFilterChange,
        onCategoryChange = onCategoryChange,
        onLocationChange = onLocationChange,
        onSortChange = onSortChange,
        onSelectComponent = onSelectComponent,
        onOpenComponentDetail = onOpenComponentDetail,
        onImportComponent = onImportComponent,
        onGenerateLabel = onGenerateLabel,
        onEditComponent = onEditComponent,
        onRequestDeleteComponent = onRequestDeleteComponent,
        onRecordMovement = onRecordMovement,
    )
}

@Composable
internal fun InventoryContent(
    contentPadding: PaddingValues,
    uiState: InventoryScreenUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
    onImportComponent: () -> Unit,
    onGenerateLabel: (String) -> Unit,
    onEditComponent: (String) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String) -> Unit,
) {
    val strings = vaultStrings()

    if (layoutMode.showsListDetail) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(rememberContentPadding(contentPadding, horizontal = 20.dp, vertical = 20.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InventoryListPane(
                uiState = uiState,
                statusMessage = statusMessage,
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                onQueryChange = onQueryChange,
                onStockFilterChange = onStockFilterChange,
                onCategoryChange = onCategoryChange,
                onLocationChange = onLocationChange,
                onSortChange = onSortChange,
                onImportComponent = onImportComponent,
                onSelectComponent = onSelectComponent,
                onOpenComponentDetail = onSelectComponent,
            )
            InventoryDetailPane(
                detail = uiState.detail,
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                onEditComponent = onEditComponent,
                onGenerateLabel = { component -> onGenerateLabel(component.id) },
                onRequestDeleteComponent = onRequestDeleteComponent,
                onRecordMovement = onRecordMovement,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = rememberContentPadding(contentPadding, horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InventoryFilterBar(
                    uiState = uiState,
                    statusMessage = statusMessage,
                    onQueryChange = onQueryChange,
                    onStockFilterChange = onStockFilterChange,
                    onCategoryChange = onCategoryChange,
                    onLocationChange = onLocationChange,
                    onSortChange = onSortChange,
                    onImportComponent = onImportComponent,
                )
            }
            item {
                Text(
                    text = strings.inventory.resultsSummary(
                        uiState.list.items.size,
                        uiState.availableCategories.size,
                        uiState.availableLocations.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.list.items.isEmpty()) {
                item {
                    EmptyPane(strings.common.emptyNoComponentsMatchFilter)
                }
            } else {
                items(uiState.list.items, key = { it.id }) { item ->
                    InventoryListRow(
                        item = item,
                        selected = item.id == uiState.list.selectedComponentId,
                        onClick = {
                            onSelectComponent(item.id)
                            onOpenComponentDetail(item.id)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryDetailRoute(
    component: ComponentRecord?,
    recentMovements: List<StockMovementRecord>,
    onDismiss: () -> Unit,
    onEditComponent: (String) -> Unit,
    onGenerateLabel: (ComponentRecord) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String) -> Unit,
    onAddComponent: () -> Unit,
) {
    val strings = vaultStrings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = component?.name ?: strings.inventory.selectedComponentTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(strings.common.actionBack)
                    }
                },
                actions = {
                    TextButton(onClick = onAddComponent) {
                        Text(strings.common.actionAdd)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        InventoryDetailPane(
            detail = InventoryDetailUiState(
                component = component,
                recentMovements = recentMovements,
            ),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = rememberContentPadding(padding, horizontal = 16.dp, vertical = 16.dp),
            onEditComponent = onEditComponent,
            onGenerateLabel = onGenerateLabel,
            onRequestDeleteComponent = onRequestDeleteComponent,
            onRecordMovement = onRecordMovement,
        )
    }
}

@Composable
private fun InventoryListPane(
    uiState: InventoryScreenUiState,
    statusMessage: String,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onImportComponent: () -> Unit,
    onSelectComponent: (String) -> Unit,
    onOpenComponentDetail: (String) -> Unit,
) {
    val strings = vaultStrings()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InventoryFilterBar(
            uiState = uiState,
            statusMessage = statusMessage,
            onQueryChange = onQueryChange,
            onStockFilterChange = onStockFilterChange,
            onCategoryChange = onCategoryChange,
            onLocationChange = onLocationChange,
            onSortChange = onSortChange,
            onImportComponent = onImportComponent,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                Text(
                    text = strings.inventory.resultsSummary(
                        uiState.list.items.size,
                        uiState.availableCategories.size,
                        uiState.availableLocations.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.list.items.isEmpty()) {
                item {
                    EmptyPane(strings.common.emptyNoComponentsMatchFilter)
                }
            } else {
                items(uiState.list.items, key = { it.id }) { item ->
                    InventoryListRow(
                        item = item,
                        selected = item.id == uiState.list.selectedComponentId,
                        onClick = {
                            onSelectComponent(item.id)
                            onOpenComponentDetail(item.id)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryFilterBar(
    uiState: InventoryScreenUiState,
    statusMessage: String,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onImportComponent: () -> Unit,
) {
    val strings = vaultStrings()
    var categoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var locationMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }

    SectionPane(
        title = strings.inventory.filtersTitle,
        supporting = strings.inventory.filtersSubtitle,
    ) {
        StatusBanner(message = statusMessage)
        OutlinedTextField(
            value = uiState.filters.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.inventory.searchLabel) },
            placeholder = { Text(strings.inventory.searchPlaceholder) },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.filters.stockFilter == InventoryStockFilter.All,
                onClick = { onStockFilterChange(InventoryStockFilter.All) },
                label = { Text(strings.inventory.filterAll) },
            )
            FilterChip(
                selected = uiState.filters.stockFilter == InventoryStockFilter.LowStock,
                onClick = { onStockFilterChange(InventoryStockFilter.LowStock) },
                label = { Text(strings.inventory.filterLowStock) },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterMenuButton(
                label = strings.inventory.filterCategoryLabel,
                value = uiState.filters.category ?: strings.inventory.filterCategoryAll,
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.inventory.filterCategoryAll) },
                    onClick = {
                        onCategoryChange(null)
                        categoryMenuExpanded = false
                    },
                )
                uiState.availableCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onCategoryChange(category)
                            categoryMenuExpanded = false
                        },
                    )
                }
            }
            FilterMenuButton(
                label = strings.inventory.filterLocationLabel,
                value = uiState.filters.location ?: strings.inventory.filterLocationAll,
                expanded = locationMenuExpanded,
                onExpandedChange = { locationMenuExpanded = it },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.inventory.filterLocationAll) },
                    onClick = {
                        onLocationChange(null)
                        locationMenuExpanded = false
                    },
                )
                uiState.availableLocations.forEach { location ->
                    DropdownMenuItem(
                        text = { Text(location) },
                        onClick = {
                            onLocationChange(location)
                            locationMenuExpanded = false
                        },
                    )
                }
            }
            FilterMenuButton(
                label = strings.inventory.filterSortLabel,
                value = inventorySortLabel(uiState.filters.sort),
                expanded = sortMenuExpanded,
                onExpandedChange = { sortMenuExpanded = it },
            ) {
                InventorySortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(inventorySortLabel(option)) },
                        onClick = {
                            onSortChange(option)
                            sortMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterMenuButton(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Box {
        OutlinedButton(onClick = { onExpandedChange(true) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            menuContent()
        }
    }
}

@Composable
internal fun InventoryDetailPane(
    detail: InventoryDetailUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 12.dp),
    onEditComponent: (String) -> Unit,
    onGenerateLabel: (ComponentRecord) -> Unit,
    onRequestDeleteComponent: (String) -> Unit,
    onRecordMovement: (String) -> Unit,
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val component = detail.component
        if (component == null) {
            item {
                SectionPane(
                    title = strings.inventory.selectedComponentTitle,
                    supporting = strings.inventory.selectedComponentEmptySubtitle,
                ) {
                    EmptyPane(strings.common.emptyNoComponentSelected)
                }
            }
            return@LazyColumn
        }

        item {
            SectionPane(
                title = component.name,
                supporting = strings.inventory.componentSubtitle(
                    component.sku,
                    component.category,
                    component.packageName,
                ),
            ) {
                StatusBanner(
                    message = if (component.isLowStock) {
                        strings.inventory.selectedComponentLowStockStatus
                    } else {
                        strings.inventory.selectedComponentHealthyStatus
                    },
                    containerColor = if (component.isLowStock) {
                        VaultWarningContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (component.isLowStock) {
                        VaultWarning
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        label = strings.inventory.metricQuantity,
                        value = component.quantity.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = strings.inventory.metricMinStock,
                        value = component.minStock.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onEditComponent(component.id) }) {
                        Text(strings.common.actionEdit)
                    }
                    OutlinedButton(onClick = { onRecordMovement(component.id) }) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
                OutlinedButton(onClick = { onGenerateLabel(component) }) {
                    Text(strings.common.actionGenerateLabel)
                }
                TextButton(onClick = { onRequestDeleteComponent(component.id) }) {
                    Text(strings.common.actionDelete)
                }
            }
        }
        item {
            SectionPane(title = strings.inventory.detailBasicTitle) {
                ValueBlock(label = strings.common.fieldSku, value = component.sku)
                ValueBlock(label = strings.common.fieldName, value = component.name)
                ValueBlock(label = strings.common.fieldCategory, value = component.category)
                ValueBlock(label = strings.common.fieldPackage, value = component.packageName)
            }
        }
        item {
            SectionPane(title = strings.inventory.detailStockTitle) {
                ValueBlock(
                    label = strings.inventory.metricQuantity,
                    value = component.quantity.toString(),
                )
                ValueBlock(
                    label = strings.inventory.metricMinStock,
                    value = component.minStock.toString(),
                )
                ValueBlock(
                    label = strings.common.labelUpdated,
                    value = component.updatedAt,
                )
            }
        }
        item {
            SectionPane(title = strings.inventory.detailLocationTitle) {
                ValueBlock(label = strings.common.fieldLocation, value = component.location)
            }
        }
        item {
            SectionPane(title = strings.inventory.detailNotesTitle) {
                Text(
                    text = component.description.ifBlank { strings.common.labelNoDescription },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionPane(title = strings.inventory.detailRecentMovementsTitle) {
                if (detail.recentMovements.isEmpty()) {
                    EmptyPane(strings.inventory.detailRecentMovementsEmpty)
                } else {
                    detail.recentMovements.forEachIndexed { index, movement ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MovementTypePill(movement.movementType)
                                MovementQuantityPill(movement)
                            }
                            Text(
                                text = movement.reason.ifBlank { strings.common.labelNoReasonRecorded },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = movement.note.ifBlank { strings.common.labelNoNote },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = movement.happenedAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
