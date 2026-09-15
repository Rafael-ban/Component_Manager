package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.MovementBatchQueueItemUiState
import com.componentvault.android.model.MovementQuickAction
import com.componentvault.android.model.MovementScanMatchStatus
import com.componentvault.android.model.MovementScanUiState
import com.componentvault.android.model.MovementsUiState
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
    onDiscardMovementBatch: () -> Unit,
    onCommitMovementBatch: () -> Unit,
    onUpdateMovementBatchItemMovementType: (String, String) -> Unit,
    onUpdateMovementBatchItemQuantity: (String, String) -> Unit,
    onUpdateMovementBatchItemReason: (String, String) -> Unit,
    onUpdateMovementBatchItemNote: (String, String) -> Unit,
    onRemoveMovementBatchItem: (String) -> Unit,
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
        onDiscardMovementBatch = onDiscardMovementBatch,
        onCommitMovementBatch = onCommitMovementBatch,
        onUpdateMovementBatchItemMovementType = onUpdateMovementBatchItemMovementType,
        onUpdateMovementBatchItemQuantity = onUpdateMovementBatchItemQuantity,
        onUpdateMovementBatchItemReason = onUpdateMovementBatchItemReason,
        onUpdateMovementBatchItemNote = onUpdateMovementBatchItemNote,
        onRemoveMovementBatchItem = onRemoveMovementBatchItem,
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
    onDiscardMovementBatch: () -> Unit,
    onCommitMovementBatch: () -> Unit,
    onUpdateMovementBatchItemMovementType: (String, String) -> Unit,
    onUpdateMovementBatchItemQuantity: (String, String) -> Unit,
    onUpdateMovementBatchItemReason: (String, String) -> Unit,
    onUpdateMovementBatchItemNote: (String, String) -> Unit,
    onRemoveMovementBatchItem: (String) -> Unit,
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
                        onDiscardMovementBatch = onDiscardMovementBatch,
                        onCommitMovementBatch = onCommitMovementBatch,
                        onUpdateMovementBatchItemMovementType = onUpdateMovementBatchItemMovementType,
                        onUpdateMovementBatchItemQuantity = onUpdateMovementBatchItemQuantity,
                        onUpdateMovementBatchItemReason = onUpdateMovementBatchItemReason,
                        onUpdateMovementBatchItemNote = onUpdateMovementBatchItemNote,
                        onRemoveMovementBatchItem = onRemoveMovementBatchItem,
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
            onDiscardMovementBatch = onDiscardMovementBatch,
            onCommitMovementBatch = onCommitMovementBatch,
            onUpdateMovementBatchItemMovementType = onUpdateMovementBatchItemMovementType,
            onUpdateMovementBatchItemQuantity = onUpdateMovementBatchItemQuantity,
            onUpdateMovementBatchItemReason = onUpdateMovementBatchItemReason,
            onUpdateMovementBatchItemNote = onUpdateMovementBatchItemNote,
            onRemoveMovementBatchItem = onRemoveMovementBatchItem,
            onSearchInventoryBySku = onSearchInventoryBySku,
            onImportComponent = onImportComponent,
            onRecordMovement = onRecordMovement,
        )
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
    onDiscardMovementBatch: () -> Unit,
    onCommitMovementBatch: () -> Unit,
    onUpdateMovementBatchItemMovementType: (String, String) -> Unit,
    onUpdateMovementBatchItemQuantity: (String, String) -> Unit,
    onUpdateMovementBatchItemReason: (String, String) -> Unit,
    onUpdateMovementBatchItemNote: (String, String) -> Unit,
    onRemoveMovementBatchItem: (String) -> Unit,
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
                hasBatchItems = uiState.batchSession.queuedItems.isNotEmpty(),
                onScanMovementLabel = onScanMovementLabel,
                onRetryMovementScan = onRetryMovementScan,
                onDismissMovementScanResult = onDismissMovementScanResult,
                onSearchInventoryBySku = onSearchInventoryBySku,
                onImportComponent = onImportComponent,
                onRecordMovement = onRecordMovement,
            )
        }

        if (uiState.batchSession.queuedItems.isNotEmpty()) {
            item {
                MovementBatchReviewSummaryPane(
                    uiState = uiState,
                    onScanMovementLabel = onScanMovementLabel,
                    onCommitMovementBatch = onCommitMovementBatch,
                    onDiscardMovementBatch = onDiscardMovementBatch,
                )
            }

            items(uiState.batchSession.queuedItems, key = { it.componentId }) { item ->
                MovementBatchQueueItemCard(
                    item = item,
                    onMovementTypeChange = { movementType ->
                        onUpdateMovementBatchItemMovementType(item.componentId, movementType)
                    },
                    onQuantityChange = { quantityText ->
                        onUpdateMovementBatchItemQuantity(item.componentId, quantityText)
                    },
                    onReasonChange = { reason ->
                        onUpdateMovementBatchItemReason(item.componentId, reason)
                    },
                    onNoteChange = { note ->
                        onUpdateMovementBatchItemNote(item.componentId, note)
                    },
                    onRemove = { onRemoveMovementBatchItem(item.componentId) },
                )
            }
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
    hasBatchItems: Boolean,
    onScanMovementLabel: () -> Unit,
    onRetryMovementScan: () -> Unit,
    onDismissMovementScanResult: () -> Unit,
    onSearchInventoryBySku: (String) -> Unit,
    onImportComponent: () -> Unit,
    onRecordMovement: () -> Unit,
) {
    val strings = vaultStrings()
    val scanActionLabel = if (hasBatchItems) {
        strings.movements.batchContinueAction
    } else {
        strings.movements.quickScanAction
    }

    SectionPane(
        title = strings.movements.recordTitle,
        supporting = strings.movements.recordSubtitle,
    ) {
        FilledTonalButton(
            onClick = onScanMovementLabel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(scanActionLabel)
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
private fun MovementBatchReviewSummaryPane(
    uiState: MovementsUiState,
    onScanMovementLabel: () -> Unit,
    onCommitMovementBatch: () -> Unit,
    onDiscardMovementBatch: () -> Unit,
) {
    val strings = vaultStrings()
    val batchSession = uiState.batchSession

    SectionPane(
        title = strings.movements.batchReviewTitle,
        supporting = strings.movements.batchSummary(
            batchSession.queuedItems.size,
            batchSession.totalScans,
        ),
    ) {
        batchSession.lastQueuedComponentName.takeIf { it.isNotBlank() }?.let { componentName ->
            Text(
                text = strings.movements.batchLastQueued(
                    componentName,
                    batchSession.lastQueuedComponentSku,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onCommitMovementBatch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.batchSaveAction)
        }
        OutlinedButton(
            onClick = onScanMovementLabel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.batchContinueAction)
        }
        OutlinedButton(
            onClick = onDiscardMovementBatch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.batchDiscardAction)
        }
    }
}

@Composable
private fun MovementBatchQueueItemCard(
    item: MovementBatchQueueItemUiState,
    onMovementTypeChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val strings = vaultStrings()
    val projectedQuantity = projectedQuantity(item)

    SectionPane(
        title = item.componentName,
        supporting = item.componentSku,
    ) {
        Text(
            text = strings.movements.batchQueuedScanCount(item.scanCount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueBlock(label = strings.common.fieldCategory, value = localizedCategoryLabel(item.category))
        ValueBlock(label = strings.common.fieldPackage, value = item.packageName)
        ValueBlock(label = strings.common.fieldLocation, value = item.location)
        ValueBlock(label = strings.movements.currentStockLabel, value = item.currentStock.toString())
        ValueBlock(label = strings.movements.minimumStockLabel, value = item.minStock.toString())
        projectedQuantity?.let { quantity ->
            ValueBlock(label = strings.movements.batchProjectedStockLabel, value = quantity.toString())
        }
        MovementTypeSelector(
            movementType = item.movementType,
            onMovementTypeChange = onMovementTypeChange,
        )
        OutlinedTextField(
            value = item.quantityText,
            onValueChange = onQuantityChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldQuantity) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = item.reason,
            onValueChange = onReasonChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldReason) },
            singleLine = true,
        )
        OutlinedTextField(
            value = item.note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldNote) },
            minLines = 2,
        )
        Text(
            text = strings.forms.movementEditorInstruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.movements.batchRemoveAction)
        }
    }
}

private fun projectedQuantity(
    item: MovementBatchQueueItemUiState,
): Int? {
    val quantity = item.quantityText.toIntOrNull() ?: return null
    return when (item.movementType) {
        MovementQuickAction.Inbound.movementType -> item.currentStock + quantity
        MovementQuickAction.Outbound.movementType -> item.currentStock - quantity
        MovementQuickAction.Adjustment.movementType -> item.currentStock + quantity
        else -> null
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
                    value = formatShortLocalTimestamp(movement.happenedAt),
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
                    text = strings.movements.updatedAt(formatShortLocalTimestamp(movement.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
