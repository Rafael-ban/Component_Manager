package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.OverviewUiState
import com.componentvault.android.model.SyncConfiguration

@Composable
internal fun OverviewScreen(
    contentPadding: PaddingValues,
    uiState: OverviewUiState,
    syncConfiguration: SyncConfiguration,
    statusMessage: String,
    onOpenLowStock: () -> Unit,
    onSelectLowStockComponent: (String) -> Unit,
    onOpenMovements: () -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    OverviewContent(
        contentPadding = contentPadding,
        uiState = uiState,
        syncConfiguration = syncConfiguration,
        statusMessage = statusMessage,
        onOpenLowStock = onOpenLowStock,
        onSelectLowStockComponent = onSelectLowStockComponent,
        onOpenMovements = onOpenMovements,
        onSelectMovement = onSelectMovement,
        onOpenSettings = onOpenSettings,
        onOpenSyncSettings = onOpenSyncSettings,
    )
}

@Composable
internal fun OverviewContent(
    contentPadding: PaddingValues,
    uiState: OverviewUiState,
    syncConfiguration: SyncConfiguration,
    statusMessage: String,
    onOpenLowStock: () -> Unit,
    onSelectLowStockComponent: (String) -> Unit,
    onOpenMovements: () -> Unit,
    onSelectMovement: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    val strings = vaultStrings()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
        contentPadding = rememberContentPadding(contentPadding, horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatusBanner(message = statusMessage)
        }
        item {
            SectionPane(
                title = strings.overview.syncTitle,
                supporting = strings.overview.syncSubtitle,
            ) {
                ValueBlock(
                    label = strings.settings.endpoint,
                    value = syncConfiguration.serverBaseUrl.ifBlank { strings.common.labelNotConfigured },
                )
                ValueBlock(
                    label = strings.settings.lastSynced,
                    value = syncConfiguration.lastSyncedAt,
                )
                TextButton(onClick = onOpenSyncSettings) {
                    Text(strings.common.actionManageSync)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = strings.overview.metricComponents,
                    value = uiState.componentCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = strings.overview.metricUnits,
                    value = uiState.totalUnits.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = strings.overview.metricLowStock,
                    value = uiState.lowStockCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = strings.overview.metricMovements,
                    value = uiState.movementCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SectionPane(
                title = strings.overview.lowStockSectionTitle,
                supporting = strings.overview.lowStockSupporting,
            ) {
                if (uiState.lowStockItems.isEmpty()) {
                    EmptyPane(strings.common.emptyAllComponentsHealthy)
                } else {
                    uiState.lowStockItems.forEach { item ->
                        InventoryListRow(
                            item = item,
                            selected = false,
                            onClick = { onSelectLowStockComponent(item.id) },
                        )
                    }
                }
                TextButton(onClick = onOpenLowStock) {
                    Text(strings.common.actionViewInventory)
                }
            }
        }
        item {
            SectionPane(
                title = strings.overview.recentActivitySectionTitle,
                supporting = strings.overview.recentActivitySupporting,
            ) {
                if (uiState.recentMovements.isEmpty()) {
                    EmptyPane(strings.common.emptyNoMovements)
                } else {
                    uiState.recentMovements.forEach { movement ->
                        MovementHistoryRow(
                            movement = movement,
                            selected = false,
                            onClick = { onSelectMovement(movement.id) },
                        )
                    }
                }
                TextButton(onClick = onOpenMovements) {
                    Text(strings.common.actionViewMovements)
                }
            }
        }
        item {
            Text(
                text = strings.overview.inventoryHint,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
