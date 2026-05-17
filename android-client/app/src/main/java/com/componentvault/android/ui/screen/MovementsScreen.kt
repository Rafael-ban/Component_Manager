package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.MovementScanMatchStatus
import com.componentvault.android.model.MovementScanUiState
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StockMovementRecord
import kotlinx.coroutines.launch

@Composable
internal fun MovementsScreen(
    contentPadding: PaddingValues,
    uiState: MovementsUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSelectMovement: (String) -> Unit,
    onOpenMovementDetail: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onRecordResolvedMovement: (MovementEntryDraft, (OperationResult) -> Unit) -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onImportComponent: () -> Unit,
    onRecordMovement: () -> Unit,
) {
    MovementsContent(
        contentPadding = contentPadding,
        uiState = uiState,
        statusMessage = statusMessage,
        layoutMode = layoutMode,
        onSelectMovement = onSelectMovement,
        onOpenMovementDetail = onOpenMovementDetail,
        onScanMovementLabel = onScanMovementLabel,
        onRetryMovementScan = onRetryMovementScan,
        onDismissMovementScanResult = onDismissMovementScanResult,
        onRecordResolvedMovement = onRecordResolvedMovement,
        onSearchInventoryBySku = onSearchInventoryBySku,
        onImportComponent = onImportComponent,
        onRecordMovement = onRecordMovement,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun MovementsContent(
    contentPadding: PaddingValues,
    uiState: MovementsUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSelectMovement: (String) -> Unit,
    onOpenMovementDetail: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onRecordResolvedMovement: (MovementEntryDraft, (OperationResult) -> Unit) -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onImportComponent: () -> Unit,
    onRecordMovement: () -> Unit,
) {
    val effectiveSelectedMovement = uiState.items.firstOrNull { it.id == uiState.selectedMovementId }
        ?: uiState.items.firstOrNull()

    if (layoutMode.showsListDetail) {
        val navigator = rememberListDetailPaneScaffoldNavigator<String>()
        val scope = rememberCoroutineScope()

        LaunchedEffect(effectiveSelectedMovement?.id) {
            val movementId = effectiveSelectedMovement?.id ?: return@LaunchedEffect
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, movementId)
        }

        NavigableListDetailPaneScaffold(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(rememberContentPadding(contentPadding, horizontal = 20.dp, vertical = 20.dp)),
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    MovementMasterPane(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(),
                        uiState = uiState,
                        statusMessage = statusMessage,
                        onSelectMovement = { movementId ->
                            onSelectMovement(movementId)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, movementId)
                            }
                        },
                        onScanMovementLabel = onScanMovementLabel,
                        onRetryMovementScan = onRetryMovementScan,
                        onDismissMovementScanResult = onDismissMovementScanResult,
                        onSearchInventoryBySku = onSearchInventoryBySku,
                        onImportComponent = onImportComponent,
                        onRecordMovement = onRecordMovement,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    MovementDetailPane(
                        movement = effectiveSelectedMovement,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    } else {
        MovementMasterPane(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = rememberContentPadding(contentPadding, horizontal = 16.dp, vertical = 16.dp),
            uiState = uiState,
            statusMessage = statusMessage,
            onSelectMovement = { movementId ->
                onSelectMovement(movementId)
                onOpenMovementDetail(movementId)
            },
            onScanMovementLabel = onScanMovementLabel,
            onRetryMovementScan = onRetryMovementScan,
            onDismissMovementScanResult = onDismissMovementScanResult,
            onSearchInventoryBySku = onSearchInventoryBySku,
            onImportComponent = onImportComponent,
            onRecordMovement = onRecordMovement,
        )
    }

    val matchedComponent = uiState.scan.resolution.matchedComponent
    if (uiState.scan.resolution.matchStatus == MovementScanMatchStatus.Matched && matchedComponent != null) {
        var editorState by remember(
            uiState.scan.resolution.rawValue,
            matchedComponent.id,
        ) {
            mutableStateOf(MovementEditorState())
        }
        ModalBottomSheet(
            onDismissRequest = onDismissMovementScanResult,
        ) {
            MovementResolvedEntrySheet(
                component = matchedComponent,
                state = editorState,
                onStateChange = { editorState = it },
                onDismiss = onDismissMovementScanResult,
                onSave = {
                    validateMovementEditorState(
                        strings = vaultStrings(),
                        selectedComponentId = matchedComponent.id,
                        state = editorState,
                    ).onSuccess { draft ->
                        editorState = editorState.copy(errorMessage = null)
                        onRecordResolvedMovement(draft) { result ->
                            if (result.isSuccess) {
                                onDismissMovementScanResult()
                            } else {
                                editorState = editorState.copy(errorMessage = result.message)
                            }
                        }
                    }.onFailure { throwable ->
                        editorState = editorState.copy(
                            errorMessage = throwable.message ?: vaultStrings().forms.chooseComponentTypeReason,
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovementDetailRoute(
    movement: StockMovementRecord?,
    onDismiss: () -> Unit,
) {
    val strings = vaultStrings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = movement?.componentName ?: strings.movements.detailTitle,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(strings.common.actionBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        MovementDetailPane(
            movement = movement,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = rememberContentPadding(padding, horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun MovementMasterPane(
    modifier: Modifier,
    contentPadding: PaddingValues,
    uiState: MovementsUiState,
    statusMessage: String,
    onSelectMovement: (String) -> Unit,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onImportComponent: () -> Unit,
    onRecordMovement: () -> Unit,
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MovementQuickEntryPane(
                statusMessage = statusMessage,
                scanState = uiState.scan,
                onScanMovementLabel = onScanMovementLabel,
                onRetryMovementScan = onRetryMovementScan,
                onDismissMovementScanResult = onDismissMovementScanResult,
                onSearchInventoryBySku = onSearchInventoryBySku,
                onImportComponent = onImportComponent,
                onRecordMovement = onRecordMovement,
            )
        }
        item {
            Text(
                text = strings.movements.resultsSummary(
                    uiState.items.size,
                    uiState.componentCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.items.isEmpty()) {
            item {
                EmptyPane(strings.common.emptyNoMovements)
            }
        } else {
            items(uiState.items, key = { it.id }) { movement ->
                MovementHistoryRow(
                    movement = movement,
                    selected = movement.id == uiState.selectedMovementId,
                    onClick = { onSelectMovement(movement.id) },
                )
            }
        }
    }
}

@Composable
private fun MovementQuickEntryPane(
    statusMessage: String,
    scanState: MovementScanUiState,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onImportComponent: () -> Unit,
    onRecordMovement: () -> Unit,
) {
    val strings = vaultStrings()

    SectionPane(
        title = strings.movements.recordTitle,
        supporting = strings.movements.recordSubtitle,
    ) {
        StatusBanner(message = statusMessage)
        FilledTonalButton(
            onClick = onScanMovementLabel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.quickScanAction)
        }
        OutlinedButton(
            onClick = onRecordMovement,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.quickManualAction)
        }

        when {
            scanState.isResolving -> {
                SectionPane(
                    title = strings.movements.scanResolvingTitle,
                    supporting = strings.movements.scanResolvingSubtitle,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(
                            text = strings.movements.scanResolvingSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            scanState.resolution.matchStatus == MovementScanMatchStatus.InvalidLabel -> {
                SectionPane(
                    title = strings.movements.invalidLabelTitle,
                    supporting = strings.movements.invalidLabelDescription,
                ) {
                    OutlinedButton(
                        onClick = onRetryMovementScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.importer.actionRetryScan)
                    }
                    OutlinedButton(
                        onClick = onImportComponent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionImport)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismissMovementScanResult()
                            onRecordMovement()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
            }

            scanState.resolution.matchStatus == MovementScanMatchStatus.NotFound -> {
                SectionPane(
                    title = strings.movements.notFoundTitle,
                    supporting = strings.movements.notFoundDescription,
                ) {
                    scanState.resolution.parsedSku.takeIf { it.isNotBlank() }?.let { sku ->
                        ValueBlock(label = strings.common.fieldSku, value = sku)
                    }
                    scanState.resolution.parsedName.takeIf { it.isNotBlank() }?.let { name ->
                        ValueBlock(label = strings.common.fieldName, value = name)
                    }
                    scanState.resolution.parsedPackageName.takeIf { it.isNotBlank() }?.let { packageName ->
                        ValueBlock(label = strings.common.fieldPackage, value = packageName)
                    }
                    scanState.resolution.parsedLocation.takeIf { it.isNotBlank() }?.let { location ->
                        ValueBlock(label = strings.common.fieldLocation, value = location)
                    }
                    OutlinedButton(
                        onClick = onRetryMovementScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.importer.actionRetryScan)
                    }
                    scanState.resolution.parsedSku.takeIf { it.isNotBlank() }?.let { sku ->
                        OutlinedButton(
                            onClick = { onSearchInventoryBySku(sku) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.common.actionViewInventory)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onDismissMovementScanResult()
                            onRecordMovement()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
            }

            scanState.resolution.matchStatus == MovementScanMatchStatus.Ambiguous -> {
                SectionPane(
                    title = strings.movements.ambiguousTitle,
                    supporting = strings.movements.ambiguousDescription,
                ) {
                    scanState.resolution.parsedSku.takeIf { it.isNotBlank() }?.let { sku ->
                        ValueBlock(label = strings.common.fieldSku, value = sku)
                    }
                    OutlinedButton(
                        onClick = onRetryMovementScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.importer.actionRetryScan)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismissMovementScanResult()
                            onRecordMovement()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun MovementResolvedEntrySheet(
    component: ComponentRecord,
    state: MovementEditorState,
    onStateChange: (MovementEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = strings.movements.quickActionsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                text = strings.movements.quickActionsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            MovementResolvedComponentSummary(component = component)
        }
        item {
            SectionPane(
                title = strings.forms.movementScopeTitle,
                supporting = strings.forms.movementScopeSubtitle,
            ) {
                MovementTypeSelector(
                    movementType = state.movementType,
                    onMovementTypeChange = { movementType ->
                        onStateChange(
                            state.copy(
                                movementType = movementType,
                                errorMessage = null,
                            ),
                        )
                    },
                )
            }
        }
        item {
            MovementEntryFields(
                state = state,
                onStateChange = onStateChange,
            )
        }
        item {
            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.actionSave)
            }
        }
        item {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.actionCancel)
            }
        }
    }
}

@Composable
private fun MovementResolvedComponentSummary(
    component: ComponentRecord,
) {
    val strings = vaultStrings()

    SectionPane(
        title = component.name,
        supporting = component.sku,
    ) {
        ValueBlock(label = strings.common.fieldCategory, value = component.category)
        ValueBlock(label = strings.common.fieldPackage, value = component.packageName)
        ValueBlock(label = strings.common.fieldLocation, value = component.location)
        ValueBlock(label = strings.movements.currentStockLabel, value = component.quantity.toString())
        ValueBlock(label = strings.movements.minimumStockLabel, value = component.minStock.toString())
    }
}

@Composable
private fun MovementDetailPane(
    movement: StockMovementRecord?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 12.dp),
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (movement == null) {
            item {
                SectionPane(
                    title = strings.movements.detailTitle,
                    supporting = strings.movements.detailEmptySupporting,
                ) {
                    EmptyPane(strings.movements.detailEmpty)
                }
            }
            return@LazyColumn
        }

        item {
            SectionPane(
                title = movement.componentName,
                supporting = movement.componentSku,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MovementTypePill(movement.movementType)
                    MovementQuantityPill(movement)
                }
                ValueBlock(
                    label = strings.movements.detailHappenedAt,
                    value = movement.happenedAt,
                )
                ValueBlock(
                    label = strings.common.fieldReason,
                    value = movement.reason.ifBlank { strings.common.labelNoReasonRecorded },
                )
                ValueBlock(
                    label = strings.common.fieldNote,
                    value = movement.note.ifBlank { strings.common.labelNoNote },
                )
                Text(
                    text = strings.movements.updatedAt(movement.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
