package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass
import com.componentvault.android.ui.screen.SettingsContent

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
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onSaveSyncSettings = { _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = {},
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}

@InventoryDarkPreview
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
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onSaveSyncSettings = { _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = {},
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}
