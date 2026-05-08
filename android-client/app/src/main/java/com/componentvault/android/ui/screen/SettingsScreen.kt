package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.SyncConfiguration

@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    syncConfiguration: SyncConfiguration,
    isBusy: Boolean,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
) {
    SettingsContent(
        modifier = modifier,
        syncConfiguration = syncConfiguration,
        isBusy = isBusy,
        statusMessage = statusMessage,
        layoutMode = layoutMode,
        onSaveSettings = onSaveSettings,
        onTestConnection = onTestConnection,
        onSyncNow = onSyncNow,
    )
}

@Composable
internal fun SettingsContent(
    modifier: Modifier,
    syncConfiguration: SyncConfiguration,
    isBusy: Boolean,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSaveSettings: (String, String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
) {
    var serverUrl by remember(syncConfiguration.serverBaseUrl) {
        mutableStateOf(syncConfiguration.serverBaseUrl)
    }
    var apiToken by remember(syncConfiguration.apiToken) {
        mutableStateOf(syncConfiguration.apiToken)
    }
    var autoSyncEnabled by remember(syncConfiguration.autoSyncEnabled) {
        mutableStateOf(syncConfiguration.autoSyncEnabled)
    }
    var showToken by remember { mutableStateOf(false) }

    val saveDraft = {
        onSaveSettings(serverUrl, apiToken, autoSyncEnabled)
    }

    if (layoutMode.showsListDetail) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSummaryPane(
                syncConfiguration = syncConfiguration,
                statusMessage = statusMessage,
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
            )
            SettingsFormPane(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                apiToken = apiToken,
                onApiTokenChange = { apiToken = it },
                autoSyncEnabled = autoSyncEnabled,
                onAutoSyncChange = { autoSyncEnabled = it },
                showToken = showToken,
                onToggleToken = { showToken = !showToken },
                isBusy = isBusy,
                onSave = saveDraft,
                onTestConnection = {
                    saveDraft()
                    onTestConnection()
                },
                onSyncNow = {
                    saveDraft()
                    onSyncNow()
                },
                modifier = Modifier
                    .weight(1.1f)
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
                SettingsSummaryPane(
                    syncConfiguration = syncConfiguration,
                    statusMessage = statusMessage,
                )
            }
            item {
                SettingsFormPane(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    apiToken = apiToken,
                    onApiTokenChange = { apiToken = it },
                    autoSyncEnabled = autoSyncEnabled,
                    onAutoSyncChange = { autoSyncEnabled = it },
                    showToken = showToken,
                    onToggleToken = { showToken = !showToken },
                    isBusy = isBusy,
                    onSave = saveDraft,
                    onTestConnection = {
                        saveDraft()
                        onTestConnection()
                    },
                    onSyncNow = {
                        saveDraft()
                        onSyncNow()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSummaryPane(
    syncConfiguration: SyncConfiguration,
    statusMessage: String,
    modifier: Modifier = Modifier,
) {
    val strings = vaultStrings()

    SectionPane(
        title = strings.settings.summaryTitle,
        supporting = strings.settings.summarySubtitle,
        modifier = modifier,
    ) {
        StatusBanner(message = statusMessage)
        ValueBlock(
            label = strings.settings.endpoint,
            value = syncConfiguration.serverBaseUrl.ifBlank { strings.common.labelNotConfigured },
        )
        ValueBlock(
            label = strings.settings.deviceId,
            value = syncConfiguration.deviceId,
        )
        ValueBlock(
            label = strings.settings.lastSynced,
            value = syncConfiguration.lastSyncedAt,
        )
        ValueBlock(
            label = strings.settings.lastResult,
            value = syncConfiguration.lastSyncMessage,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsFormPane(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    autoSyncEnabled: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    showToken: Boolean,
    onToggleToken: () -> Unit,
    isBusy: Boolean,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = vaultStrings()

    SectionPane(
        title = strings.settings.formTitle,
        supporting = strings.settings.formSubtitle,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldServerUrl) },
            placeholder = { Text(strings.settings.serverUrlPlaceholder) },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldApiToken) },
            singleLine = true,
            visualTransformation = if (showToken) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )
        TextButton(onClick = onToggleToken) {
            Text(
                if (showToken) {
                    strings.common.actionHideToken
                } else {
                    strings.common.actionShowToken
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = strings.settings.autoSync,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = strings.settings.autoSyncDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoSyncEnabled,
                onCheckedChange = onAutoSyncChange,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onSave) {
                Text(strings.common.actionSaveSettings)
            }
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isBusy,
            ) {
                Text(strings.common.actionTestConnection)
            }
            OutlinedButton(
                onClick = onSyncNow,
                enabled = !isBusy,
            ) {
                Text(strings.common.actionSyncNow)
            }
        }
    }
}
