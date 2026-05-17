package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.ui.screen.ComponentEditorSurface
import com.componentvault.android.ui.screen.JlcImportSurface
import com.componentvault.android.ui.screen.MovementEditorSurface

@InventoryDialogPreview
@Composable
private fun NewComponentFormPreview() {
    PreviewHost {
        ComponentEditorSurface(
            existing = null,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            onSave = { _, _ -> },
        )
    }
}

@InventoryDialogPreview
@Composable
private fun EditComponentFormPreview() {
    PreviewHost {
        ComponentEditorSurface(
            existing = InventoryPreviewData.selectedComponent,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            onSave = { _, _ -> },
        )
    }
}

@InventoryDialogPreview
@Composable
private fun MovementFormPreview() {
    PreviewHost {
        MovementEditorSurface(
            components = InventoryPreviewData.components,
            selectedComponentId = InventoryPreviewData.selectedComponentId,
            layoutMode = DialogPreviewLayout,
            onDismiss = {},
            onSave = { _, _ -> },
        )
    }
}

@InventoryDialogPreview
@Composable
private fun JlcImportPreview() {
    PreviewHost {
        JlcImportSurface(
            layoutMode = DialogPreviewLayout,
            syncConfiguration = InventoryPreviewData.settingsState().syncConfiguration,
            appPreferences = InventoryPreviewData.settingsState().appPreferences,
            onDismiss = {},
            onSaveImportedComponent = { _, _, _ -> },
            onOpenFullEditor = { _, _ -> },
        )
    }
}
