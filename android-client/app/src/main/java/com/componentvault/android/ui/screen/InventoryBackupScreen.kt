package com.componentvault.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import com.componentvault.android.data.LabelWorkbookColumn
import com.componentvault.android.data.LabelWorkbookExporter
import com.componentvault.android.model.ComponentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun InventoryBackupScreen(
    viewModel: InventoryViewModel,
    components: List<ComponentRecord> = emptyList(),
    onDismiss: () -> Unit,
) {
    val state = viewModel.backupUiState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labelColumns by remember { mutableStateOf(LabelWorkbookExporter.defaultColumns) }
    var labelExportMessage by remember { mutableStateOf("") }
    var labelExportDialogVisible by remember { mutableStateOf(false) }
    var labelExportBusy by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let(viewModel::exportInventoryBackup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewInventoryBackup)
    }
    val createLabels = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null) {
            labelExportBusy = true
            val selectedColumns = labelColumns
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.Default) {
                        LabelWorkbookExporter.export(components, selectedColumns)
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                            ?: error("无法打开导出文件。")
                    }
                }.onSuccess {
                    labelExportMessage = context.getString(R.string.label_workbook_export_success)
                }.onFailure {
                    labelExportMessage = it.message ?: context.getString(R.string.label_workbook_export_failure)
                }
                labelExportBusy = false
            }
        }
    }
    SecondaryPageScaffold(
        title = stringResource(R.string.backup_title),
        onBack = onDismiss,
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.backup_description))
            Button(
                onClick = { create.launch("component-vault-backup.xlsx") },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(
                onClick = { open.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip")) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_choose)) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.preview?.let { preview ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (preview.workbook.source.name == "ComponentVault") R.string.backup_own_source else R.string.backup_lcsc_source),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.backup_counts, preview.newComponentCount, preview.newLocationCount, preview.skippedComponentCount))
                        preview.warnings.forEach { warning -> Text("• $warning", color = MaterialTheme.colorScheme.tertiary) }
                        Text(stringResource(R.string.backup_merge_notice), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = viewModel::confirmInventoryRestore,
                            enabled = !state.loading && (preview.newComponentCount > 0 || preview.newLocationCount > 0),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.backup_confirm)) }
                    }
                }
            }
            if (state.message.isNotBlank()) Text(state.message)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_workbook_export_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.label_workbook_export_description), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { labelExportDialogVisible = true },
                        enabled = !state.loading && !labelExportBusy && components.any { !it.deleted },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.label_workbook_export_action)) }
                    if (components.none { !it.deleted }) Text(stringResource(R.string.label_workbook_export_empty), style = MaterialTheme.typography.bodySmall)
                    if (labelExportMessage.isNotBlank()) Text(labelExportMessage)
                }
            }
        }
    }
    if (labelExportDialogVisible) {
        AlertDialog(
            onDismissRequest = { labelExportDialogVisible = false },
            title = { Text(stringResource(R.string.label_workbook_export_fields_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(LabelWorkbookColumn.entries.size) { index ->
                        val column = LabelWorkbookColumn.entries[index]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = column in labelColumns,
                                onCheckedChange = { checked -> labelColumns = if (checked) labelColumns + column else labelColumns - column },
                            )
                            Text(labelWorkbookColumnTitle(column))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        labelExportDialogVisible = false
                        createLabels.launch("component-vault-labels.xlsx")
                    },
                    enabled = labelColumns.isNotEmpty(),
                ) { Text(stringResource(R.string.label_workbook_export_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { labelExportDialogVisible = false }) { Text(stringResource(R.string.label_workbook_export_cancel)) }
            },
        )
    }
}

@Composable
private fun labelWorkbookColumnTitle(column: LabelWorkbookColumn): String = stringResource(
    when (column) {
        LabelWorkbookColumn.Name -> R.string.label_workbook_column_name
        LabelWorkbookColumn.Sku -> R.string.label_workbook_column_sku
        LabelWorkbookColumn.Model -> R.string.label_workbook_column_model
        LabelWorkbookColumn.PackageName -> R.string.label_workbook_column_package
        LabelWorkbookColumn.Category -> R.string.label_workbook_column_category
        LabelWorkbookColumn.Location -> R.string.label_workbook_column_location
        LabelWorkbookColumn.Quantity -> R.string.label_workbook_column_quantity
        LabelWorkbookColumn.LongQrText -> R.string.label_workbook_column_long_qr
        LabelWorkbookColumn.ShortQrText -> R.string.label_workbook_column_short_qr
    },
)
