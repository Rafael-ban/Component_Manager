package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord

@Composable
internal fun StorageLocationsScreen(
    locations: List<StorageLocationRecord>,
    onDismiss: () -> Unit,
    onSave: (String, String, (OperationResult) -> Unit) -> Unit,
    onDelete: (String, (OperationResult) -> Unit) -> Unit,
) {
    var editor by remember { mutableStateOf<LocationEditor?>(null) }
    var deleteTarget by remember { mutableStateOf<StorageLocationRecord?>(null) }
    var operationMessage by remember { mutableStateOf("") }
    var operationFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    SecondaryPageScaffold(
        title = stringResource(R.string.locations_title),
        onBack = {
            when {
                editor != null -> editor = null
                deleteTarget != null -> deleteTarget = null
                else -> onDismiss()
            }
        },
        navigationEnabled = !busy,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { operationMessage = ""; editor = LocationEditor() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("locations_add"),
            ) { Text(stringResource(R.string.locations_add)) }

            if (operationMessage.isNotBlank()) {
                Text(
                    text = operationMessage,
                    color = if (operationFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("locations_operation_message"),
                )
            }

            if (locations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.locations_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.locations_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("locations_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(locations, key = { it.id }) { location ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(location.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    location.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                ) {
                                    TextButton(
                                        onClick = {
                                            operationMessage = ""
                                            editor = LocationEditor(location.id, location.name, isEditing = true)
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("locations_edit_${location.id}"),
                                    ) { Text(stringResource(R.string.action_edit)) }
                                    TextButton(
                                        onClick = { deleteTarget = location },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("locations_delete_${location.id}"),
                                    ) { Text(stringResource(R.string.action_delete)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editor?.let { value ->
        LocationEditorDialog(
            initial = value,
            busy = busy,
            onDismiss = { if (!busy) editor = null },
            onSave = { code, name, setError ->
                busy = true
                onSave(code, name) { result ->
                    busy = false
                    if (result.isSuccess) {
                        editor = null
                        operationMessage = result.message
                        operationFailed = false
                    } else {
                        setError(result.message)
                    }
                }
            },
        )
    }

    deleteTarget?.let { location ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteTarget = null },
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(stringResource(R.string.locations_delete_description, location.name, location.id)) },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        onDelete(location.id) { result ->
                            busy = false
                            deleteTarget = null
                            operationMessage = result.message
                            operationFailed = !result.isSuccess
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal data class LocationEditor(
    val code: String = "",
    val name: String = "",
    val isEditing: Boolean = false,
)

@Composable
internal fun LocationEditorDialog(
    initial: LocationEditor,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, (String) -> Unit) -> Unit,
) {
    var code by remember(initial) { mutableStateOf(initial.code) }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var error by remember(initial) { mutableStateOf("") }
    val codeInvalid = code.isBlank() || code.length > 120
    val nameInvalid = name.isBlank() || name.length > 200

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(stringResource(if (initial.isEditing) R.string.locations_edit_title else R.string.locations_create_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim(); error = "" },
                    label = { Text(stringResource(R.string.locations_code_label)) },
                    supportingText = if (initial.isEditing) {
                        { Text(stringResource(R.string.locations_code_immutable_hint)) }
                    } else null,
                    isError = codeInvalid && code.isNotEmpty(),
                    readOnly = initial.isEditing,
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("locations_code"),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    label = { Text(stringResource(R.string.locations_name_label)) },
                    isError = nameInvalid && name.isNotEmpty(),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("locations_name"),
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("locations_editor_error"))
                }
                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.locations_saving))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && !codeInvalid && !nameInvalid,
                onClick = { onSave(code.trim(), name.trim()) { error = it } },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
