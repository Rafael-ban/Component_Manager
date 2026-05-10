package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.componentvault.android.AppLocaleManager
import com.componentvault.android.BuildConfig
import com.componentvault.android.model.AppLanguage
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ImportLearningSummary
import com.componentvault.android.model.OcrEngineMode
import com.componentvault.android.model.SyncConfiguration

@Composable
internal fun SettingsScreen(
    contentPadding: PaddingValues,
    syncConfiguration: SyncConfiguration,
    appPreferences: AppPreferences,
    importLearningSummary: ImportLearningSummary,
    isBusy: Boolean,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSaveSyncSettings: (String, String, Boolean) -> Unit,
    onSaveAppPreferences: (AppPreferences) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onClearImportLearningMappings: () -> Unit,
) {
    SettingsContent(
        contentPadding = contentPadding,
        syncConfiguration = syncConfiguration,
        appPreferences = appPreferences,
        importLearningSummary = importLearningSummary,
        isBusy = isBusy,
        statusMessage = statusMessage,
        layoutMode = layoutMode,
        onSaveSyncSettings = onSaveSyncSettings,
        onSaveAppPreferences = onSaveAppPreferences,
        onTestConnection = onTestConnection,
        onSyncNow = onSyncNow,
        onClearImportLearningMappings = onClearImportLearningMappings,
    )
}

@Composable
internal fun SettingsContent(
    contentPadding: PaddingValues,
    syncConfiguration: SyncConfiguration,
    appPreferences: AppPreferences,
    importLearningSummary: ImportLearningSummary,
    isBusy: Boolean,
    statusMessage: String,
    layoutMode: InventoryLayoutMode,
    onSaveSyncSettings: (String, String, Boolean) -> Unit,
    onSaveAppPreferences: (AppPreferences) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onClearImportLearningMappings: () -> Unit,
) {
    val strings = vaultStrings()
    var serverUrl by remember(syncConfiguration.serverBaseUrl) {
        mutableStateOf(syncConfiguration.serverBaseUrl)
    }
    var apiToken by remember(syncConfiguration.apiToken) {
        mutableStateOf(syncConfiguration.apiToken)
    }
    var autoSyncEnabled by remember(syncConfiguration.autoSyncEnabled) {
        mutableStateOf(syncConfiguration.autoSyncEnabled)
    }
    var defaultImportLocation by remember(appPreferences.defaultImportLocation) {
        mutableStateOf(appPreferences.defaultImportLocation)
    }
    var defaultImportMinStockText by remember(appPreferences.defaultImportMinStock) {
        mutableStateOf(appPreferences.defaultImportMinStock.toString())
    }
    var rememberLastImportLocation by remember(appPreferences.rememberLastImportLocation) {
        mutableStateOf(appPreferences.rememberLastImportLocation)
    }
    var syncAfterLocalChanges by remember(appPreferences.syncAfterLocalChanges) {
        mutableStateOf(appPreferences.syncAfterLocalChanges)
    }
    var enableLocalAutoRecognition by remember(appPreferences.enableLocalAutoRecognition) {
        mutableStateOf(appPreferences.enableLocalAutoRecognition)
    }
    var preferAggressiveAutoRecognition by remember(appPreferences.preferAggressiveAutoRecognition) {
        mutableStateOf(appPreferences.preferAggressiveAutoRecognition)
    }
    var enableLocalImportLearning by remember(appPreferences.enableLocalImportLearning) {
        mutableStateOf(appPreferences.enableLocalImportLearning)
    }
    var enableServerJlcLookup by remember(appPreferences.enableServerJlcLookup) {
        mutableStateOf(appPreferences.enableServerJlcLookup)
    }
    var ocrEngineMode by remember(appPreferences.ocrEngineMode) {
        mutableStateOf(appPreferences.ocrEngineMode)
    }
    var appLanguage by remember(appPreferences.appLanguage) {
        mutableStateOf(appPreferences.appLanguage)
    }
    var showToken by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showClearLearningConfirmation by remember { mutableStateOf(false) }

    val saveDraft: (Boolean) -> Boolean = { applyLocale ->
        val minStock = defaultImportMinStockText.toIntOrNull()
        if (minStock == null || minStock < 0) {
            errorMessage = strings.forms.componentNonNegative
            false
        } else {
            errorMessage = null
            onSaveSyncSettings(serverUrl, apiToken, autoSyncEnabled)
            onSaveAppPreferences(
                AppPreferences(
                    defaultImportLocation = defaultImportLocation.trim(),
                    lastImportLocation = appPreferences.lastImportLocation,
                    defaultImportMinStock = minStock,
                    rememberLastImportLocation = rememberLastImportLocation,
                    syncAfterLocalChanges = syncAfterLocalChanges,
                    enableLocalAutoRecognition = enableLocalAutoRecognition,
                    preferAggressiveAutoRecognition = preferAggressiveAutoRecognition,
                    enableLocalImportLearning = enableLocalImportLearning,
                    enableServerJlcLookup = enableServerJlcLookup,
                    ocrEngineMode = ocrEngineMode,
                    appLanguage = appLanguage,
                ),
            )
            if (applyLocale) {
                AppLocaleManager.apply(appLanguage)
            }
            true
        }
    }

    if (layoutMode.showsListDetail) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(rememberContentPadding(contentPadding, horizontal = 20.dp, vertical = 20.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SettingsSummaryPane(
                        syncConfiguration = syncConfiguration,
                        appPreferences = appPreferences,
                        importLearningSummary = importLearningSummary,
                        statusMessage = statusMessage,
                    )
                }
                item {
                    SettingsAboutPane()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                settingsFormItems(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    apiToken = apiToken,
                    onApiTokenChange = { apiToken = it },
                    autoSyncEnabled = autoSyncEnabled,
                    onAutoSyncChange = { autoSyncEnabled = it },
                    defaultImportLocation = defaultImportLocation,
                    onDefaultImportLocationChange = { defaultImportLocation = it },
                    defaultImportMinStockText = defaultImportMinStockText,
                    onDefaultImportMinStockChange = { defaultImportMinStockText = it },
                    rememberLastImportLocation = rememberLastImportLocation,
                    onRememberLastImportLocationChange = { rememberLastImportLocation = it },
                    syncAfterLocalChanges = syncAfterLocalChanges,
                    onSyncAfterLocalChangesChange = { syncAfterLocalChanges = it },
                    enableLocalAutoRecognition = enableLocalAutoRecognition,
                    onEnableLocalAutoRecognitionChange = { enableLocalAutoRecognition = it },
                    preferAggressiveAutoRecognition = preferAggressiveAutoRecognition,
                    onPreferAggressiveAutoRecognitionChange = { preferAggressiveAutoRecognition = it },
                    enableLocalImportLearning = enableLocalImportLearning,
                    onEnableLocalImportLearningChange = { enableLocalImportLearning = it },
                    enableServerJlcLookup = enableServerJlcLookup,
                    onEnableServerJlcLookupChange = { enableServerJlcLookup = it },
                    ocrEngineMode = ocrEngineMode,
                    onOcrEngineModeChange = { ocrEngineMode = it },
                    appLanguage = appLanguage,
                    onAppLanguageChange = { appLanguage = it },
                    importLearningSummary = importLearningSummary,
                    strings = strings,
                    showToken = showToken,
                    onToggleToken = { showToken = !showToken },
                    isBusy = isBusy,
                    errorMessage = errorMessage,
                    onSave = { saveDraft(true) },
                    onTestConnection = {
                        if (saveDraft(false)) {
                            onTestConnection()
                        }
                    },
                    onSyncNow = {
                        if (saveDraft(false)) {
                            onSyncNow()
                        }
                    },
                    onClearImportLearningMappings = { showClearLearningConfirmation = true },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = rememberContentPadding(contentPadding, horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSummaryPane(
                    syncConfiguration = syncConfiguration,
                    appPreferences = appPreferences,
                    importLearningSummary = importLearningSummary,
                    statusMessage = statusMessage,
                )
            }
            settingsFormItems(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                apiToken = apiToken,
                onApiTokenChange = { apiToken = it },
                autoSyncEnabled = autoSyncEnabled,
                onAutoSyncChange = { autoSyncEnabled = it },
                defaultImportLocation = defaultImportLocation,
                onDefaultImportLocationChange = { defaultImportLocation = it },
                defaultImportMinStockText = defaultImportMinStockText,
                onDefaultImportMinStockChange = { defaultImportMinStockText = it },
                rememberLastImportLocation = rememberLastImportLocation,
                onRememberLastImportLocationChange = { rememberLastImportLocation = it },
                syncAfterLocalChanges = syncAfterLocalChanges,
                onSyncAfterLocalChangesChange = { syncAfterLocalChanges = it },
                enableLocalAutoRecognition = enableLocalAutoRecognition,
                onEnableLocalAutoRecognitionChange = { enableLocalAutoRecognition = it },
                preferAggressiveAutoRecognition = preferAggressiveAutoRecognition,
                onPreferAggressiveAutoRecognitionChange = { preferAggressiveAutoRecognition = it },
                enableLocalImportLearning = enableLocalImportLearning,
                onEnableLocalImportLearningChange = { enableLocalImportLearning = it },
                enableServerJlcLookup = enableServerJlcLookup,
                onEnableServerJlcLookupChange = { enableServerJlcLookup = it },
                ocrEngineMode = ocrEngineMode,
                onOcrEngineModeChange = { ocrEngineMode = it },
                appLanguage = appLanguage,
                onAppLanguageChange = { appLanguage = it },
                importLearningSummary = importLearningSummary,
                strings = strings,
                showToken = showToken,
                onToggleToken = { showToken = !showToken },
                isBusy = isBusy,
                errorMessage = errorMessage,
                onSave = { saveDraft(true) },
                onTestConnection = {
                    if (saveDraft(false)) {
                        onTestConnection()
                    }
                },
                onSyncNow = {
                    if (saveDraft(false)) {
                        onSyncNow()
                    }
                },
                onClearImportLearningMappings = { showClearLearningConfirmation = true },
            )
            item {
                SettingsAboutPane()
            }
        }
    }

    if (showClearLearningConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearLearningConfirmation = false },
            title = { Text(strings.settings.clearLearnedMappingsTitle) },
            text = { Text(strings.settings.clearLearnedMappingsMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLearningConfirmation = false
                        onClearImportLearningMappings()
                    },
                ) {
                    Text(strings.settings.clearLearnedMappingsAction)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearLearningConfirmation = false }) {
                    Text(strings.common.actionCancel)
                }
            },
        )
    }
}

@Composable
private fun SettingsSummaryPane(
    syncConfiguration: SyncConfiguration,
    appPreferences: AppPreferences,
    importLearningSummary: ImportLearningSummary,
    statusMessage: String,
) {
    val strings = vaultStrings()

    SectionPane(
        title = strings.settings.summaryTitle,
        supporting = strings.settings.summarySubtitle,
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
        ValueBlock(
            label = strings.common.fieldLocation,
            value = appPreferences.suggestedImportLocation.ifBlank { strings.common.labelNotConfigured },
        )
        ValueBlock(
            label = strings.settings.ocrEngineLabel,
            value = strings.settings.ocrEngineLabel(appPreferences.ocrEngineMode),
        )
        ValueBlock(
            label = strings.settings.languageLabel,
            value = strings.settings.appLanguageLabel(appPreferences.appLanguage),
        )
        ValueBlock(
            label = strings.settings.learnedMappingsCount,
            value = if (importLearningSummary.mappingCount > 0) {
                importLearningSummary.mappingCount.toString()
            } else {
                strings.settings.noLearnedMappings
            },
        )
    }
}

@Composable
private fun SettingsAboutPane() {
    val strings = vaultStrings()

    SectionPane(
        title = strings.settings.aboutTitle,
        supporting = strings.settings.aboutSubtitle,
    ) {
        ValueBlock(
            label = strings.settings.appVersion,
            value = BuildConfig.VERSION_NAME,
        )
        ValueBlock(
            label = strings.settings.localStorage,
            value = strings.settings.localStorageValue,
        )
        Text(
            text = strings.settings.aboutBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.settingsFormItems(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    autoSyncEnabled: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    defaultImportLocation: String,
    onDefaultImportLocationChange: (String) -> Unit,
    defaultImportMinStockText: String,
    onDefaultImportMinStockChange: (String) -> Unit,
    rememberLastImportLocation: Boolean,
    onRememberLastImportLocationChange: (Boolean) -> Unit,
    syncAfterLocalChanges: Boolean,
    onSyncAfterLocalChangesChange: (Boolean) -> Unit,
    enableLocalAutoRecognition: Boolean,
    onEnableLocalAutoRecognitionChange: (Boolean) -> Unit,
    preferAggressiveAutoRecognition: Boolean,
    onPreferAggressiveAutoRecognitionChange: (Boolean) -> Unit,
    enableLocalImportLearning: Boolean,
    onEnableLocalImportLearningChange: (Boolean) -> Unit,
    enableServerJlcLookup: Boolean,
    onEnableServerJlcLookupChange: (Boolean) -> Unit,
    ocrEngineMode: OcrEngineMode,
    onOcrEngineModeChange: (OcrEngineMode) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    importLearningSummary: ImportLearningSummary,
    strings: ComponentVaultStrings,
    showToken: Boolean,
    onToggleToken: () -> Unit,
    isBusy: Boolean,
    errorMessage: String?,
    onSave: () -> Boolean,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onClearImportLearningMappings: () -> Unit,
) {
    item {
        SectionPane(
            title = strings.settings.connectionTitle,
            supporting = strings.settings.connectionSubtitle,
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.common.fieldServerUrl) },
                placeholder = { Text(strings.settings.serverUrlPlaceholder) },
                singleLine = true,
            )
        }
    }
    item {
        SectionPane(
            title = strings.settings.authTitle,
            supporting = strings.settings.authSubtitle,
        ) {
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
        }
    }
    item {
        SectionPane(
            title = strings.settings.syncBehaviorTitle,
            supporting = strings.settings.syncBehaviorSubtitle,
        ) {
            SettingsToggleRow(
                title = strings.settings.syncOnLaunch,
                subtitle = strings.settings.syncOnLaunchDescription,
                checked = autoSyncEnabled,
                onCheckedChange = onAutoSyncChange,
            )
            SettingsToggleRow(
                title = strings.settings.syncAfterWrites,
                subtitle = strings.settings.syncAfterWritesDescription,
                checked = syncAfterLocalChanges,
                onCheckedChange = onSyncAfterLocalChangesChange,
            )
        }
    }
    item {
        SectionPane(
            title = strings.settings.importPreferencesTitle,
            supporting = strings.settings.importPreferencesSubtitle,
        ) {
            OutlinedTextField(
                value = defaultImportLocation,
                onValueChange = onDefaultImportLocationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.common.fieldLocation) },
                singleLine = true,
            )
            OutlinedTextField(
                value = defaultImportMinStockText,
                onValueChange = onDefaultImportMinStockChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.common.fieldMinimumStock) },
                singleLine = true,
            )
            SettingsToggleRow(
                title = strings.settings.rememberLastImportLocation,
                subtitle = strings.settings.rememberLastImportLocationDescription,
                checked = rememberLastImportLocation,
                onCheckedChange = onRememberLastImportLocationChange,
            )
            SettingsToggleRow(
                title = strings.settings.localAutoRecognition,
                subtitle = strings.settings.localAutoRecognitionDescription,
                checked = enableLocalAutoRecognition,
                onCheckedChange = onEnableLocalAutoRecognitionChange,
            )
            SettingsToggleRow(
                title = strings.settings.aggressiveRecognition,
                subtitle = strings.settings.aggressiveRecognitionDescription,
                checked = preferAggressiveAutoRecognition,
                onCheckedChange = onPreferAggressiveAutoRecognitionChange,
            )
            SettingsToggleRow(
                title = strings.settings.localImportLearning,
                subtitle = strings.settings.localImportLearningDescription,
                checked = enableLocalImportLearning,
                onCheckedChange = onEnableLocalImportLearningChange,
            )
            SettingsToggleRow(
                title = strings.settings.serverJlcLookup,
                subtitle = strings.settings.serverJlcLookupDescription,
                checked = enableServerJlcLookup,
                onCheckedChange = onEnableServerJlcLookupChange,
            )
            Text(
                text = strings.settings.ocrEngineLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            SettingsChoiceRow(
                title = strings.settings.ocrEngineAuto,
                subtitle = strings.settings.ocrEngineAutoDescription,
                selected = ocrEngineMode == OcrEngineMode.Auto,
                onClick = { onOcrEngineModeChange(OcrEngineMode.Auto) },
            )
            SettingsChoiceRow(
                title = strings.settings.ocrEngineMlKit,
                subtitle = strings.settings.ocrEngineMlKitDescription,
                selected = ocrEngineMode == OcrEngineMode.MlKit,
                onClick = { onOcrEngineModeChange(OcrEngineMode.MlKit) },
            )
            SettingsChoiceRow(
                title = strings.settings.ocrEnginePaddle,
                subtitle = strings.settings.ocrEnginePaddleDescription,
                selected = ocrEngineMode == OcrEngineMode.PaddleExperimental,
                onClick = { onOcrEngineModeChange(OcrEngineMode.PaddleExperimental) },
            )
            ValueBlock(
                label = strings.settings.learnedMappingsCount,
                value = if (importLearningSummary.mappingCount > 0) {
                    importLearningSummary.mappingCount.toString()
                } else {
                    strings.settings.noLearnedMappings
                },
            )
            OutlinedButton(
                onClick = onClearImportLearningMappings,
                enabled = importLearningSummary.mappingCount > 0 && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.settings.clearLearnedMappingsAction)
            }
        }
    }
    item {
        SectionPane(
            title = strings.settings.languageTitle,
            supporting = strings.settings.languageSubtitle,
        ) {
            SettingsChoiceRow(
                title = strings.settings.languageChinese,
                subtitle = strings.settings.languageSubtitle,
                selected = appLanguage == AppLanguage.ZhCn,
                onClick = { onAppLanguageChange(AppLanguage.ZhCn) },
            )
            SettingsChoiceRow(
                title = strings.settings.languageEnglish,
                subtitle = strings.settings.languageSubtitle,
                selected = appLanguage == AppLanguage.English,
                onClick = { onAppLanguageChange(AppLanguage.English) },
            )
        }
    }
    item {
        SectionPane(
            title = strings.settings.summaryTitle,
            supporting = strings.settings.summarySubtitle,
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onSave() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.common.actionSaveSettings)
                }
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.common.actionTestConnection)
                }
            }
            OutlinedButton(
                onClick = onSyncNow,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.actionSyncNow)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}
