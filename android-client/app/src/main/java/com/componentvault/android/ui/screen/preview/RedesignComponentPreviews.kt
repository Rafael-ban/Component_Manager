package com.componentvault.android.ui.screen.preview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.componentvault.android.ui.screen.InventoryListRow
import com.componentvault.android.ui.screen.SettingsContent
import com.componentvault.android.ui.screen.SettingsSection

@Preview(
    name = "Compact stock card / 360dp / large font",
    group = "Redesign",
    showBackground = true,
    widthDp = 360,
    fontScale = 1.3f,
)
@Composable
private fun CompactStockCardPreview() {
    PreviewHost {
        InventoryListRow(
            item = InventoryPreviewData.compactLongNameCard,
            selected = false,
            modifier = Modifier.padding(12.dp),
            onClick = {},
        )
    }
}

@InventoryNarrowPhonePreview
@Composable
private fun AboutCompactPreview() {
    PreviewHost {
        val state = InventoryPreviewData.settingsState()
        SettingsContent(
            contentPadding = PaddingValues(),
            syncConfiguration = state.syncConfiguration,
            appPreferences = state.appPreferences,
            importLearningSummary = state.importLearningSummary,
            isBusy = false,
            statusMessage = "",
            layoutMode = CompactPreviewLayout,
            selectedSection = SettingsSection.About,
            onSelectSection = {},
            onSaveSyncSettings = { _, _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = { _, _, _ -> },
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}

@InventoryTabletPreview
@Composable
private fun AboutWidePreview() {
    PreviewHost {
        val state = InventoryPreviewData.settingsState()
        SettingsContent(
            contentPadding = PaddingValues(),
            syncConfiguration = state.syncConfiguration,
            appPreferences = state.appPreferences,
            importLearningSummary = state.importLearningSummary,
            isBusy = false,
            statusMessage = "",
            layoutMode = ExpandedPreviewLayout,
            selectedSection = SettingsSection.About,
            onSelectSection = {},
            onSaveSyncSettings = { _, _, _, _ -> },
            onSaveAppPreferences = {},
            onTestConnection = { _, _, _ -> },
            onSyncNow = {},
            onClearImportLearningMappings = {},
        )
    }
}
