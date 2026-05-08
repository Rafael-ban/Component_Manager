package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass
import com.componentvault.android.ui.screen.SettingsContent

@InventoryPhonePreview
@Composable
private fun SettingsPhonePreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.settingsState()
        SettingsContent(
            modifier = androidx.compose.ui.Modifier,
            syncConfiguration = uiState.syncConfiguration,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onSaveSettings = { _, _, _ -> },
            onTestConnection = {},
            onSyncNow = {},
        )
    }
}

@InventoryDarkPreview
@Composable
private fun SettingsBusyPreview() {
    PreviewHost {
        val uiState = InventoryPreviewData.busySettingsState()
        SettingsContent(
            modifier = androidx.compose.ui.Modifier,
            syncConfiguration = uiState.syncConfiguration,
            isBusy = uiState.isBusy,
            statusMessage = uiState.statusMessage,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onSaveSettings = { _, _, _ -> },
            onTestConnection = {},
            onSyncNow = {},
        )
    }
}
