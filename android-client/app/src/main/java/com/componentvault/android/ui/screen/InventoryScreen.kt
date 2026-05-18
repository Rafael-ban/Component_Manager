package com.componentvault.android.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
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
    if (layoutMode.showsListDetail) {
        val navigator = rememberListDetailPaneScaffoldNavigator<String>()
        val scope = rememberCoroutineScope()

        LaunchedEffect(uiState.list.selectedComponentId) {
            val selectedComponentId = uiState.list.selectedComponentId ?: return@LaunchedEffect
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedComponentId)
        }

        NavigableListDetailPaneScaffold(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(rememberContentPadding(contentPadding, horizontal = 20.dp, vertical = 20.dp)),
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    InventoryListPane(
                        uiState = uiState,
                        statusMessage = statusMessage,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(),
                        onQueryChange = onQueryChange,
                        onStockFilterChange = onStockFilterChange,
                        onCategoryChange = onCategoryChange,
                        onLocationChange = onLocationChange,
                        onSortChange = onSortChange,
                        onSelectComponent = { componentId ->
                            onSelectComponent(componentId)
                            scope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    componentId,
                                )
                            }
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    InventoryDetailPane(
                        detail = uiState.detail,
                        modifier = Modifier.fillMaxSize(),
                        onEditComponent = onEditComponent,
                        onGenerateLabel = { component -> onGenerateLabel(component.id) },
                        onRequestDeleteComponent = onRequestDeleteComponent,
                        onRecordMovement = onRecordMovement,
                    )
                }
            },
        )
    } else {
        InventoryListPane(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = rememberContentPadding(contentPadding, horizontal = 16.dp, vertical = 12.dp),
            uiState = uiState,
            statusMessage = statusMessage,
            onQueryChange = onQueryChange,
            onStockFilterChange = onStockFilterChange,
            onCategoryChange = onCategoryChange,
            onLocationChange = onLocationChange,
            onSortChange = onSortChange,
            onSelectComponent = { componentId ->
                onSelectComponent(componentId)
                onOpenComponentDetail(componentId)
            },
        )
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
    modifier: Modifier,
    contentPadding: PaddingValues,
    uiState: InventoryScreenUiState,
    statusMessage: String,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
    onSelectComponent: (String) -> Unit,
) {
    val strings = vaultStrings()

    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InventoryFilterHeader(
            uiState = uiState,
            statusMessage = statusMessage,
            onQueryChange = onQueryChange,
            onStockFilterChange = onStockFilterChange,
            onCategoryChange = onCategoryChange,
            onLocationChange = onLocationChange,
            onSortChange = onSortChange,
        )
        Text(
            text = strings.inventory.resultsSummary(
                uiState.list.items.size,
                uiState.availableCategories.size,
                uiState.availableLocations.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.list.items.isEmpty()) {
                item {
                    EmptyPane(strings.common.emptyNoComponentsMatchFilter)
                }
            } else {
                items(uiState.list.items, key = { it.id }) { item ->
                    InventoryListRow(
                        item = item,
                        selected = item.id == uiState.list.selectedComponentId,
                        onClick = { onSelectComponent(item.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryFilterHeader(
    uiState: InventoryScreenUiState,
    statusMessage: String,
    onQueryChange: (String) -> Unit,
    onStockFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onLocationChange: (String?) -> Unit,
    onSortChange: (InventorySortOption) -> Unit,
) {
    val strings = vaultStrings()
    var categoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var locationMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusBanner(message = statusMessage)
        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            inputField = {
                SearchBarDefaults.InputField(
                    query = uiState.filters.query,
                    onQueryChange = onQueryChange,
                    onSearch = { searchExpanded = false },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    placeholder = { Text(strings.inventory.searchPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (uiState.filters.query.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    searchExpanded = false
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
            },
            expanded = searchExpanded,
            onExpandedChange = { searchExpanded = it },
        ) {
            Text(
                text = strings.inventory.filtersSubtitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            FilterMenuButton(
                label = strings.inventory.filterCategoryLabel,
                value = uiState.filters.category
                    ?.let { localizedCategoryLabel(it) }
                    ?: strings.inventory.filterCategoryAll,
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
                        text = { Text(localizedCategoryLabel(category)) },
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
                Text(
                    text = "$label: $value",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    localizedCategoryLabel(component.category),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = { onEditComponent(component.id) }) {
                        Text(strings.common.actionEdit)
                    }
                    OutlinedButton(
                        onClick = { onRecordMovement(component.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
                OutlinedButton(
                    onClick = { onGenerateLabel(component) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                ValueBlock(label = strings.common.fieldCategory, value = localizedCategoryLabel(component.category))
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
