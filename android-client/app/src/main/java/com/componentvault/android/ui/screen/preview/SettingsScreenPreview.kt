package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.ui.screen.SettingsContent
import com.componentvault.android.ui.screen.SettingsSection

@InventoryPhonePreview
@Composable
private fun SettingsPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.settingsState()
        SettingsContent(
            contentPadding = PaddingValues(),
            syncConfiguration = uiState.syncConfiguration,
            appPreferences = uiState.appPreferences,
            importLearningSummary = uiState.importLearningSummary,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            layoutMode = CompactPreviewLayout,
            selectedSection = null,
            onSelectSection = {},
            onSaveSyncSettings = { _, _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = { _, _, _ -> },
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}

@InventoryDarkPreview
@InventoryTabletPreview
@Composable
private fun SettingsBusyPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.busySettingsState()
        SettingsContent(
            contentPadding = PaddingValues(),
            syncConfiguration = uiState.syncConfiguration,
            appPreferences = uiState.appPreferences,
            importLearningSummary = uiState.importLearningSummary,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            layoutMode = ExpandedPreviewLayout,
            selectedSection = SettingsSection.ImportAndOcr,
            onSelectSection = {},
            onSaveSyncSettings = { _, _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = { _, _, _ -> },
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}
