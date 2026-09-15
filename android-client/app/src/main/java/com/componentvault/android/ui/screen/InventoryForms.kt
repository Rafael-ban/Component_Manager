package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord

@Composable
internal fun ComponentEditorSurface(
    existing: ComponentRecord?,
    initialDraft: ComponentDraft? = null,
    layoutMode: InventoryLayoutMode,
    onDismiss: () -> Unit,
    onSave: (ComponentDraft, (OperationResult) -> Unit) -> Unit,
    onSaveAndGenerateLabel: ((ComponentDraft, (OperationResult) -> Unit) -> Unit)? = null,
) {
    val strings = vaultStrings()
    val stateKey = existing?.id ?: initialDraft?.sku.orEmpty()
    var sku by remember(stateKey) { mutableStateOf(existing?.sku ?: initialDraft?.sku.orEmpty()) }
    var name by remember(stateKey) { mutableStateOf(existing?.name ?: initialDraft?.name.orEmpty()) }
    var category by remember(stateKey) { mutableStateOf(existing?.category ?: initialDraft?.category.orEmpty()) }
    var packageName by remember(stateKey) {
        mutableStateOf(existing?.packageName ?: initialDraft?.packageName.orEmpty())
    }
    var location by remember(stateKey) { mutableStateOf(existing?.location ?: initialDraft?.location.orEmpty()) }
    var description by remember(stateKey) {
        mutableStateOf(existing?.description ?: initialDraft?.description.orEmpty())
    }
    var quantityText by remember(stateKey) {
        mutableStateOf((existing?.quantity ?: initialDraft?.quantity ?: 0).toString())
    }
    var minStockText by remember(stateKey) {
        mutableStateOf((existing?.minStock ?: initialDraft?.minStock ?: 0).toString())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = if (existing == null) {
        strings.forms.addComponentTitle
    } else {
        strings.forms.editComponentTitle
    }

    AdaptiveFormSurface(
        title = title,
        layoutMode = layoutMode,
        onDismiss = onDismiss,
        saveLabel = if (onSaveAndGenerateLabel == null) {
            null
        } else {
            strings.common.actionSaveAndGenerateLabel
        },
        onSave = {
            validateComponentDraft(
                strings = strings,
                existingId = existing?.id,
                sku = sku,
                name = name,
                category = category,
                packageName = packageName,
                location = location,
                description = description,
                quantityText = quantityText,
                minStockText = minStockText,
                onError = { errorMessage = it },
            )?.let { draft ->
                errorMessage = null
                onSave(draft) { result ->
                    if (!result.isSuccess) {
                        errorMessage = result.message
                    }
                }
            }
        },
        onSecondarySave = {
            validateComponentDraft(
                strings = strings,
                existingId = existing?.id,
                sku = sku,
                name = name,
                category = category,
                packageName = packageName,
                location = location,
                description = description,
                quantityText = quantityText,
                minStockText = minStockText,
                onError = { errorMessage = it },
            )?.let { draft ->
                errorMessage = null
                onSaveAndGenerateLabel?.invoke(draft) { result ->
                    if (!result.isSuccess) {
                        errorMessage = result.message
                    }
                }
            }
        },
    ) {
        item {
            SectionPane(
                title = strings.forms.componentBasicTitle,
                supporting = strings.forms.componentBasicSubtitle,
            ) {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldSku) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldName) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldCategory) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldPackage) },
                    singleLine = true,
                )
            }
        }
        item {
            SectionPane(title = strings.forms.componentStockTitle) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldQuantity) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = minStockText,
                    onValueChange = { minStockText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldMinimumStock) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        item {
            SectionPane(title = strings.forms.componentStorageTitle) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldLocation) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.common.fieldDescription) },
                    minLines = 3,
                )
            }
        }
        if (!errorMessage.isNullOrBlank()) {
            item {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun validateComponentDraft(
    strings: ComponentVaultStrings,
    existingId: String?,
    sku: String,
    name: String,
    category: String,
    packageName: String,
    location: String,
    description: String,
    quantityText: String,
    minStockText: String,
    onError: (String) -> Unit,
): ComponentDraft? {
    val quantity = quantityText.toIntOrNull()
    val minStock = minStockText.toIntOrNull()
    if (sku.isBlank() || name.isBlank() || category.isBlank() || packageName.isBlank() || location.isBlank()) {
        onError(strings.forms.componentRequiredFields)
        return null
    }
    if (quantity == null || minStock == null) {
        onError(strings.forms.invalidQuantityMinStock)
        return null
    }
    if (quantity < 0 || minStock < 0) {
        onError(strings.forms.componentNonNegative)
        return null
    }

    return ComponentDraft(
        id = existingId,
        sku = sku,
        name = name,
        category = category,
        packageName = packageName,
        location = location,
        description = description,
        quantity = quantity,
        minStock = minStock,
    )
}

internal data class MovementEditorState(
    val movementType: String = "inbound",
    val quantityText: String = "1",
    val reason: String = "",
    val note: String = "",
    val locationId: String = "",
    val destinationLocationId: String = "",
    val errorMessage: String? = null,
)

internal fun validateMovementEditorState(
    strings: ComponentVaultStrings,
    selectedComponentId: String?,
    state: MovementEditorState,
): Result<MovementEntryDraft> {
    return runCatching {
        val quantity = state.quantityText.toIntOrNull()
        if (selectedComponentId.isNullOrBlank() || state.reason.isBlank() || quantity == null) {
            throw IllegalStateException(strings.forms.chooseComponentTypeReason)
        }
        if (state.movementType == "adjustment" && quantity == 0) {
            throw IllegalStateException(strings.forms.adjustmentNonZero)
        }
        if (state.movementType != "adjustment" && quantity <= 0) {
            throw IllegalStateException(strings.forms.movementQuantityPositive)
        }
        if (state.locationId.isBlank()) throw IllegalStateException("请选择发生库存变动的库位。")
        if (state.movementType == "transfer" &&
            (state.destinationLocationId.isBlank() || state.destinationLocationId == state.locationId)
        ) throw IllegalStateException("调拨必须选择两个不同库位。")
        MovementEntryDraft(
            componentId = selectedComponentId,
            movementType = state.movementType,
            quantity = quantity,
            reason = state.reason,
            note = state.note,
            locationId = state.locationId,
            destinationLocationId = state.destinationLocationId.takeIf { state.movementType == "transfer" },
        )
    }
}

@Composable
internal fun MovementTypeSelector(
    movementType: String,
    onMovementTypeChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("inbound", "outbound", "adjustment", "transfer").forEach { type ->
            FilterChip(
                selected = movementType == type,
                onClick = { onMovementTypeChange(type) },
                label = { Text(movementTypeLabel(type)) },
            )
        }
    }
}

@Composable
internal fun MovementEntryFields(
    state: MovementEditorState,
    onStateChange: (MovementEditorState) -> Unit,
    storageLocations: List<StorageLocationRecord> = emptyList(),
) {
    val strings = vaultStrings()
    var sourceMenu by remember { mutableStateOf(false) }
    var destinationMenu by remember { mutableStateOf(false) }

    SectionPane(title = strings.forms.movementEntryTitle) {
        OutlinedTextField(
            value = state.locationId,
            onValueChange = { onStateChange(state.copy(locationId = it.trim(), errorMessage = null)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("源库位编码") },
            singleLine = true,
        )
        if (storageLocations.isNotEmpty()) {
            OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("选择已有源库位")
            }
            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                storageLocations.forEach { location ->
                    DropdownMenuItem(text = { Text("${location.name} (${location.id})") }, onClick = {
                        onStateChange(state.copy(locationId = location.id, errorMessage = null)); sourceMenu = false
                    })
                }
                DropdownMenuItem(text = { Text("新建库位…") }, onClick = { sourceMenu = false })
            }
        }
        if (state.movementType == "transfer") {
            OutlinedTextField(
                value = state.destinationLocationId,
                onValueChange = { onStateChange(state.copy(destinationLocationId = it.trim(), errorMessage = null)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("目标库位编码") },
                singleLine = true,
            )
            if (storageLocations.isNotEmpty()) {
                OutlinedButton(onClick = { destinationMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("选择已有目标库位")
                }
                DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                    storageLocations.filterNot { it.id == state.locationId }.forEach { location ->
                        DropdownMenuItem(text = { Text("${location.name} (${location.id})") }, onClick = {
                            onStateChange(state.copy(destinationLocationId = location.id, errorMessage = null)); destinationMenu = false
                        })
                    }
                    DropdownMenuItem(text = { Text("新建库位…") }, onClick = { destinationMenu = false })
                }
            }
        }
        OutlinedTextField(
            value = state.quantityText,
            onValueChange = {
                onStateChange(state.copy(quantityText = it, errorMessage = null))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldQuantity) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = state.reason,
            onValueChange = {
                onStateChange(state.copy(reason = it, errorMessage = null))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldReason) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = {
                onStateChange(state.copy(note = it, errorMessage = null))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.common.fieldNote) },
            minLines = 2,
        )
        Text(
            text = strings.forms.movementEditorInstruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun MovementEditorSurface(
    components: List<ComponentRecord>,
    selectedComponentId: String?,
    layoutMode: InventoryLayoutMode,
    initialMovementType: String? = null,
    allowManualComponentSelection: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (MovementEntryDraft, (OperationResult) -> Unit) -> Unit,
    storageLocations: List<StorageLocationRecord> = emptyList(),
) {
    val strings = vaultStrings()
    var componentMenuExpanded by remember { mutableStateOf(false) }
    var selectedComponent by remember(selectedComponentId, components) {
        mutableStateOf(
            components.firstOrNull { it.id == selectedComponentId } ?: components.firstOrNull(),
        )
    }
    var editorState by remember(initialMovementType) {
        mutableStateOf(
            MovementEditorState(
                movementType = initialMovementType ?: "inbound",
                locationId = components.firstOrNull { it.id == selectedComponentId }?.location.orEmpty(),
            ),
        )
    }

    AdaptiveFormSurface(
        title = strings.forms.recordStockMovementTitle,
        layoutMode = layoutMode,
        onDismiss = onDismiss,
        onSave = {
            validateMovementEditorState(
                strings = strings,
                selectedComponentId = selectedComponent?.id,
                state = editorState,
            ).onSuccess { draft ->
                editorState = editorState.copy(errorMessage = null)
                onSave(draft) { result ->
                    if (!result.isSuccess) {
                        editorState = editorState.copy(errorMessage = result.message)
                    }
                }
            }.onFailure { throwable ->
                editorState = editorState.copy(
                    errorMessage = throwable.message ?: strings.forms.chooseComponentTypeReason,
                )
            }
        },
    ) {
        item {
            SectionPane(
                title = strings.forms.movementScopeTitle,
                supporting = strings.forms.movementScopeSubtitle,
            ) {
                if (allowManualComponentSelection) {
                    Column {
                        OutlinedButton(onClick = { componentMenuExpanded = true }) {
                            Text(
                                selectedComponent?.let { "${it.name} (${it.sku})" }
                                    ?: strings.common.fieldComponent,
                            )
                        }
                        DropdownMenu(
                            expanded = componentMenuExpanded,
                            onDismissRequest = { componentMenuExpanded = false },
                        ) {
                            components.forEach { component ->
                                DropdownMenuItem(
                                    text = { Text("${component.name} (${component.sku})") },
                                    onClick = {
                                        selectedComponent = component
                                        componentMenuExpanded = false
                                        editorState = editorState.copy(locationId = component.location, errorMessage = null)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    selectedComponent?.let { component ->
                        ValueBlock(label = strings.common.fieldName, value = component.name)
                        ValueBlock(label = strings.common.fieldSku, value = component.sku)
                        ValueBlock(label = strings.common.fieldCategory, value = localizedCategoryLabel(component.category))
                        ValueBlock(label = strings.common.fieldPackage, value = component.packageName)
                        ValueBlock(label = strings.common.fieldLocation, value = component.location)
                        ValueBlock(
                            label = strings.movements.currentStockLabel,
                            value = component.quantity.toString(),
                        )
                    }
                }
                MovementTypeSelector(
                    movementType = editorState.movementType,
                    onMovementTypeChange = { movementType ->
                        editorState = editorState.copy(
                            movementType = movementType,
                            errorMessage = null,
                        )
                    },
                )
            }
        }
        item {
            MovementEntryFields(
                state = editorState,
                onStateChange = { editorState = it },
                storageLocations = storageLocations,
            )
        }
    }
}

@Composable
internal fun DeleteComponentConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = vaultStrings()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.forms.deleteConfirmTitle) },
        text = { Text(strings.forms.deleteConfirmMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.common.actionDelete)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.actionCancel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveFormSurface(
    title: String,
    layoutMode: InventoryLayoutMode,
    onDismiss: () -> Unit,
    saveLabel: String? = null,
    onSave: () -> Unit,
    onSecondarySave: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val strings = vaultStrings()

    if (layoutMode.prefersDialogForms) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .widthIn(max = 760.dp)
                    .heightIn(max = 860.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            TextButton(onClick = onDismiss) {
                                Text(strings.common.actionCancel)
                            }
                        },
                        actions = {
                            if (saveLabel != null && onSecondarySave != null) {
                                TextButton(onClick = onSecondarySave) {
                                    Text(saveLabel)
                                }
                            }
                            TextButton(onClick = onSave) {
                                Text(strings.common.actionSave)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text(strings.common.actionBack)
                        }
                    },
                    actions = {
                        if (saveLabel != null && onSecondarySave != null) {
                            TextButton(onClick = onSecondarySave) {
                                Text(saveLabel)
                            }
                        }
                        TextButton(onClick = onSave) {
                            Text(strings.common.actionSave)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
