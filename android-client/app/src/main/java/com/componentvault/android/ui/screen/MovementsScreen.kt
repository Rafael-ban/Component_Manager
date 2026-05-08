package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.StockMovementRecord

@Composable
internal fun MovementsScreen(
    modifier: Modifier,
    uiState: MovementsUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    selectedMovementId: String?,
    onSelectMovement: (String) -> Unit,
    onRecordMovement: () -> Unit,
) {
    MovementsContent(
        modifier = modifier,
        uiState = uiState,
        statusMessage = statusMessage,
        layoutMode = layoutMode,
        selectedMovementId = selectedMovementId,
        onSelectMovement = onSelectMovement,
        onRecordMovement = onRecordMovement,
    )
}

@Composable
internal fun MovementsContent(
    modifier: Modifier,
    uiState: MovementsUiState,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    selectedMovementId: String?,
    onSelectMovement: (String) -> Unit,
    onRecordMovement: () -> Unit,
) {
    val strings = vaultStrings()
    val effectiveSelectedMovement = uiState.items.firstOrNull {
        it.id == (selectedMovementId ?: uiState.items.firstOrNull()?.id)
    }

    if (layoutMode.showsListDetail) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.06f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionPane(
                    title = strings.movements.recordTitle,
                    supporting = strings.movements.recordSubtitle,
                ) {
                    StatusBanner(message = statusMessage)
                    FilledTonalButton(
                        onClick = onRecordMovement,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
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
                                selected = movement.id == effectiveSelectedMovement?.id,
                                onClick = { onSelectMovement(movement.id) },
                            )
                        }
                    }
                }
            }
            MovementDetailPane(
                movement = effectiveSelectedMovement,
                modifier = Modifier
                    .weight(0.94f)
                    .fillMaxHeight(),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionPane(
                    title = strings.movements.recordTitle,
                    supporting = strings.movements.recordSubtitle,
                ) {
                    StatusBanner(message = statusMessage)
                    FilledTonalButton(
                        onClick = onRecordMovement,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.common.actionRecordMovement)
                    }
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
                        selected = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementDetailPane(
    movement: StockMovementRecord?,
    modifier: Modifier = Modifier,
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
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
