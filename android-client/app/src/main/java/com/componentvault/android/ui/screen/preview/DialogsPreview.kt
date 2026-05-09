package com.componentvault.android.ui.screen.preview

import androidx.compose.runtime.Composable
import com.componentvault.android.ui.screen.ComponentEditorSurface
import com.componentvault.android.ui.screen.InventoryLayoutMode
import com.componentvault.android.ui.screen.InventoryWidthClass
import com.componentvault.android.ui.screen.JlcImportSurface
import com.componentvault.android.ui.screen.MovementEditorSurface

@InventoryDialogPreview
@Composable
private fun NewComponentFormPreview() {
    PreviewHost {
        ComponentEditorSurface(
            existing = null,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            onSave = {},
        )
    }
}

@InventoryDialogPreview
@Composable
private fun EditComponentFormPreview() {
    PreviewHost {
        ComponentEditorSurface(
            existing = InventoryPreviewData.selectedComponent,
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            onSave = {},
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
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            onDismiss = {},
            onSave = {},
        )
    }
}

@InventoryDialogPreview
@Composable
private fun JlcImportPreview() {
    PreviewHost {
        JlcImportSurface(
            layoutMode = InventoryLayoutMode(
                widthClass = InventoryWidthClass.Compact,
                usesNavigationRail = false,
                showsListDetail = false,
                prefersDialogForms = false,
            ),
            appPreferences = InventoryPreviewData.settingsState().appPreferences,
            onDismiss = {},
            onSaveImportedComponent = {},
            onOpenFullEditor = {},
        )
    }
}
