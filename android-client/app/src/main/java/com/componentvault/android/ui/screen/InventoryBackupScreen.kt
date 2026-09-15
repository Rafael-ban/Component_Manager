package com.componentvault.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.R

@Composable
internal fun InventoryBackupScreen(viewModel: InventoryViewModel, onDismiss: () -> Unit) {
    val state = viewModel.backupUiState
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let(viewModel::exportInventoryBackup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewInventoryBackup)
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
        }
    }
}
