package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import com.componentvault.android.data.BatchTransferLine
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.InventoryListItemUiState
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord

internal typealias BatchTransferAction =
    (String, String, List<BatchTransferLine>, (OperationResult) -> Unit) -> Unit

@Composable
internal fun BatchTransferDialog(
    items: List<InventoryListItemUiState>,
    allocations: List<ComponentAllocationRecord>,
    locations: List<StorageLocationRecord>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, List<BatchTransferLine>, (OperationResult) -> Unit) -> Unit,
) {
    val sourceOptions = locations.filter { location ->
        items.isNotEmpty() && items.all { item -> allocations.any {
            it.componentId == item.id && it.locationId == location.id && it.quantity > 0
        } }
    }
    var sourceId by remember(items, allocations) { mutableStateOf(sourceOptions.firstOrNull()?.id.orEmpty()) }
    var destinationId by remember(items, locations) {
        mutableStateOf(locations.firstOrNull { it.id != sourceId }?.id.orEmpty())
    }
    var quantities by remember(sourceId, items, allocations) {
        mutableStateOf(items.associate { item ->
            item.id to allocations.firstOrNull {
                it.componentId == item.id && it.locationId == sourceId
            }?.quantity?.takeIf { it > 0 }?.toString().orEmpty()
        })
    }
    var reviewing by remember { mutableStateOf(false) }
    var reviewedLines by remember { mutableStateOf<List<BatchTransferLine>?>(null) }
    var reviewedSourceId by remember { mutableStateOf("") }
    var reviewedDestinationId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val availableById = items.associate { item -> item.id to (
        allocations.firstOrNull { it.componentId == item.id && it.locationId == sourceId }?.quantity ?: 0
    ) }
    val valid = sourceId.isNotBlank() && destinationId.isNotBlank() && sourceId != destinationId &&
        items.isNotEmpty() && items.all { item ->
            val quantity = quantities[item.id]?.toIntOrNull()
            quantity != null && quantity > 0 && quantity <= availableById.getValue(item.id)
        }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.batch_transfer_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.batch_transfer_count, items.size))
                if (locations.size < 2) Text(stringResource(R.string.batch_transfer_need_two_locations))
                if (sourceOptions.isEmpty()) Text(stringResource(R.string.batch_transfer_no_common_source))
                LocationPicker(
                    label = stringResource(R.string.batch_transfer_source),
                    selectedId = sourceId,
                    options = sourceOptions,
                    enabled = !reviewing && !busy,
                    onSelect = { sourceId = it; if (destinationId == it) {
                        destinationId = locations.firstOrNull { location -> location.id != it }?.id.orEmpty()
                    } },
                )
                LocationPicker(
                    label = stringResource(R.string.batch_transfer_destination),
                    selectedId = destinationId,
                    options = locations.filter { it.id != sourceId },
                    enabled = !reviewing && !busy,
                    onSelect = { destinationId = it },
                )
                items.forEach { item ->
                    val available = availableById.getValue(item.id)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${item.name} (${item.sku})")
                        Text(stringResource(R.string.batch_transfer_available, available))
                        if (reviewing) {
                            Text(stringResource(R.string.batch_transfer_quantity_review, quantities[item.id].orEmpty()))
                        } else {
                            OutlinedTextField(
                                value = quantities[item.id].orEmpty(),
                                onValueChange = { quantities = quantities + (item.id to it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.batch_transfer_quantity)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !busy,
                            )
                        }
                    }
                }
                if (reviewing) Text(stringResource(R.string.batch_transfer_review_prompt))
                if (error.isNotBlank()) Text(error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                if (!reviewing) {
                    reviewedLines = items.map { item ->
                        BatchTransferLine(
                            item.id,
                            requireNotNull(quantities[item.id]?.toIntOrNull()),
                            item.updatedAt,
                        )
                    }
                    reviewedSourceId = sourceId
                    reviewedDestinationId = destinationId
                    reviewing = true
                    error = ""
                } else {
                    busy = true
                    onSubmit(reviewedSourceId, reviewedDestinationId, requireNotNull(reviewedLines)) { result ->
                        busy = false
                        if (result.isSuccess) onDismiss() else error = result.message
                    }
                }
            }) { Text(stringResource(if (reviewing) R.string.batch_transfer_submit else R.string.batch_transfer_review)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                if (reviewing) { reviewing = false; reviewedLines = null } else onDismiss()
            }) { Text(stringResource(if (reviewing) R.string.action_back else R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LocationPicker(
    label: String,
    selectedId: String,
    options: List<StorageLocationRecord>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty()) {
                Text(options.firstOrNull { it.id == selectedId }?.name ?: "—")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { location ->
                    DropdownMenuItem(
                        text = { Text("${location.name} (${location.id})") },
                        onClick = { onSelect(location.id); expanded = false },
                    )
                }
            }
        }
    }
}
