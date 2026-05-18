package com.componentvault.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.ComponentImportParser
import com.componentvault.android.data.InventoryRepository
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportFieldOrigin
import com.componentvault.android.model.ComponentImportLearningMatchType
import com.componentvault.android.model.ComponentOfficialLookupOutcome
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.SyncConfiguration
import com.componentvault.android.model.isJlcSource
import com.componentvault.android.model.referenceDisplayName

private enum class ImportScannerMode {
    Qr,
    SupplierText,
}

@Composable
internal fun JlcImportSurface(
    layoutMode: InventoryLayoutMode,
    syncConfiguration: SyncConfiguration,
    appPreferences: AppPreferences,
    onDismiss: () -> Unit,
    onSaveImportedComponent: (ComponentDraft, ComponentImportCandidate, (OperationResult) -> Unit) -> Unit,
    onOpenFullEditor: (ComponentDraft, ComponentImportCandidate) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { InventoryRepository(context) }
    val strings = vaultStrings()

    var rawInput by remember { mutableStateOf("") }
    var baseCandidate by remember { mutableStateOf<ComponentImportCandidate?>(null) }
    var displayedCandidate by remember { mutableStateOf<ComponentImportCandidate?>(null) }
    var scannerMode by rememberSaveable { mutableStateOf<ImportScannerMode?>(null) }
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var location by remember(appPreferences.suggestedImportLocation) {
        mutableStateOf(appPreferences.suggestedImportLocation)
    }
    var minStockText by remember(appPreferences.defaultImportMinStock) {
        mutableStateOf(appPreferences.defaultImportMinStock.toString())
    }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    var lookupIsError by remember { mutableStateOf(false) }
    var lookupInProgress by remember { mutableStateOf(false) }
    var learningMatchType by remember { mutableStateOf<ComponentImportLearningMatchType?>(null) }
    var skuEdited by remember { mutableStateOf(false) }
    var nameEdited by remember { mutableStateOf(false) }
    var categoryEdited by remember { mutableStateOf(false) }
    var packageEdited by remember { mutableStateOf(false) }
    var modelEdited by remember { mutableStateOf(false) }
    var brandEdited by remember { mutableStateOf(false) }
    var skuOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }
    var nameOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }
    var categoryOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }
    var packageOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }
    var modelOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }
    var brandOrigin by remember { mutableStateOf(ComponentImportFieldOrigin.Parsed) }

    fun applyDisplayedCandidate(
        candidate: ComponentImportCandidate,
        preserveUserEdits: Boolean,
    ) {
        displayedCandidate = candidate
        rawInput = candidate.rawPayload
        if (!preserveUserEdits || !skuEdited) {
            sku = candidate.sku
            skuOrigin = candidate.fieldOrigins.sku
        }
        if (!preserveUserEdits || !nameEdited) {
            name = candidate.name
            nameOrigin = candidate.fieldOrigins.name
        }
        if (!preserveUserEdits || !categoryEdited) {
            category = candidate.category
            categoryOrigin = candidate.fieldOrigins.category
        }
        if (!preserveUserEdits || !packageEdited) {
            packageName = candidate.packageName
            packageOrigin = candidate.fieldOrigins.packageName
        }
        if (!preserveUserEdits || !modelEdited) {
            model = candidate.model.orEmpty()
            modelOrigin = candidate.fieldOrigins.model
        }
        if (!preserveUserEdits || !brandEdited) {
            brand = candidate.brand.orEmpty()
            brandOrigin = candidate.fieldOrigins.brand
        }
        if (!preserveUserEdits) {
            quantityText = (candidate.suggestedQuantity ?: 1).coerceAtLeast(1).toString()
            candidate.notes.firstOrNull {
                it.startsWith("Warehouse location: ") || it.startsWith("仓位：")
            }?.let { note ->
                location = note
                    .removePrefix("Warehouse location: ")
                    .removePrefix("仓位：")
                    .trim()
            }
            candidate.notes.firstOrNull {
                it.startsWith("Minimum stock: ") || it.startsWith("最低库存：")
            }?.let { note ->
                note
                    .removePrefix("Minimum stock: ")
                    .removePrefix("最低库存：")
                    .trim()
                    .toIntOrNull()
                    ?.let { parsedMinStock ->
                    minStockText = parsedMinStock.toString()
                }
            }
            skuEdited = false
            nameEdited = false
            categoryEdited = false
            packageEdited = false
            modelEdited = false
            brandEdited = false
        }
        feedbackMessage = null
    }

    fun setBaseCandidate(candidate: ComponentImportCandidate) {
        baseCandidate = candidate
        learningMatchType = null
        lookupMessage = null
        lookupIsError = false
        lookupInProgress = false
        applyDisplayedCandidate(candidate, preserveUserEdits = false)
    }

    fun buildQuickSaveDraftOrNull(): ComponentDraft? {
        val candidate = displayedCandidate
        if (candidate == null) {
            feedbackMessage = strings.importer.parseFirstError
            return null
        }

        val quantity = quantityText.toIntOrNull()
        val minStock = minStockText.toIntOrNull()
        if (sku.isBlank() || name.isBlank() || category.isBlank() || packageName.isBlank() || location.isBlank()) {
            feedbackMessage = strings.forms.componentRequiredFields
            return null
        }
        if (quantity == null || minStock == null) {
            feedbackMessage = strings.forms.invalidQuantityMinStock
            return null
        }
        if (quantity < 0 || minStock < 0) {
            feedbackMessage = strings.forms.componentNonNegative
            return null
        }
        feedbackMessage = null
        return candidate.toComponentDraft(
            quantity = quantity,
            location = location,
            minStock = minStock,
            skuOverride = sku,
            nameOverride = name,
            categoryOverride = category,
            packageNameOverride = packageName,
            modelOverride = model.blankToNull(),
            brandOverride = brand.blankToNull(),
        )
    }

    fun buildEditorDraftOrNull(): ComponentDraft? {
        val candidate = displayedCandidate
        if (candidate == null) {
            feedbackMessage = strings.importer.parseFirstError
            return null
        }

        feedbackMessage = null
        return candidate.toComponentDraft(
            quantity = quantityText.toIntOrNull()?.takeIf { it >= 0 }
                ?: (candidate.suggestedQuantity ?: 0).coerceAtLeast(0),
            location = location.ifBlank { appPreferences.suggestedImportLocation },
            minStock = minStockText.toIntOrNull()?.takeIf { it >= 0 }
                ?: appPreferences.defaultImportMinStock.coerceAtLeast(0),
            skuOverride = sku,
            nameOverride = name,
            categoryOverride = category,
            packageNameOverride = packageName,
            modelOverride = model.blankToNull(),
            brandOverride = brand.blankToNull(),
        )
    }

    fun buildPreviewDraft(): ComponentDraft? {
        val candidate = displayedCandidate ?: return null
        val quantity = quantityText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val minStock = minStockText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return candidate.toComponentDraft(
            quantity = quantity,
            location = location.ifBlank { appPreferences.suggestedImportLocation },
            minStock = minStock,
            skuOverride = sku.ifBlank { candidate.sku },
            nameOverride = name.ifBlank { candidate.name.ifBlank { candidate.referenceDisplayName().orEmpty() } },
            categoryOverride = category.ifBlank { candidate.category },
            packageNameOverride = packageName.ifBlank { candidate.packageName },
            modelOverride = model.blankToNull() ?: candidate.model,
            brandOverride = brand.blankToNull() ?: candidate.brand,
        )
    }

    when (scannerMode) {
        ImportScannerMode.Qr -> {
            BackHandler(onBack = { scannerMode = null })
            JlcQrScannerSurface(
                onDismiss = { scannerMode = null },
                onScanResult = { result ->
                    scannerMode = null
                    runCatching { ComponentImportParser.parseScannedQr(result) }
                        .onSuccess(::setBaseCandidate)
                        .onFailure {
                            rawInput = result
                            feedbackMessage = it.message ?: strings.importer.scannerFailedDescription
                        }
                },
            )
            return
        }

        ImportScannerMode.SupplierText -> {
            BackHandler(onBack = { scannerMode = null })
            ImportTextScannerSurface(
                onDismiss = { scannerMode = null },
                preferredOcrEngineMode = appPreferences.ocrEngineMode,
                onOcrScanned = { result ->
                    scannerMode = null
                    runCatching { ComponentImportParser.parseSupplierOcr(result) }
                        .onSuccess(::setBaseCandidate)
                        .onFailure {
                            rawInput = result.fullText
                            feedbackMessage = it.message ?: strings.importer.supplierScanFailedDescription
                        }
                },
            )
            return
        }

        null -> Unit
    }

    LaunchedEffect(
        baseCandidate?.rawPayload,
        appPreferences.enableLocalAutoRecognition,
        appPreferences.preferAggressiveAutoRecognition,
        appPreferences.enableLocalImportLearning,
        appPreferences.enableServerJlcLookup,
        syncConfiguration.serverBaseUrl,
        syncConfiguration.apiToken,
    ) {
        val candidate = baseCandidate ?: return@LaunchedEffect
        lookupInProgress = candidate.sourceType != com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel &&
            appPreferences.enableServerJlcLookup
        val resolution = repository.enrichImportCandidate(
            candidate = candidate,
            appPreferences = appPreferences,
            syncConfiguration = syncConfiguration,
        )
        learningMatchType = resolution.learningMatch?.matchedBy
        applyDisplayedCandidate(
            candidate = resolution.candidate,
            preserveUserEdits = true,
        )

        val lookupResult = resolution.officialLookupResult
        if (lookupResult == null) {
            lookupInProgress = false
            lookupIsError = false
            lookupMessage = null
            return@LaunchedEffect
        }

        when (lookupResult.outcome) {
            ComponentOfficialLookupOutcome.Success -> {
                lookupInProgress = false
                lookupIsError = false
                lookupMessage = lookupResult.message ?: strings.importer.lookupSuccess
            }

            ComponentOfficialLookupOutcome.NoMatch -> {
                lookupInProgress = false
                lookupIsError = false
                lookupMessage = lookupResult.message ?: strings.importer.lookupNoMatch
            }

            ComponentOfficialLookupOutcome.NotConfigured -> {
                lookupInProgress = false
                lookupIsError = false
                lookupMessage = lookupResult.message ?: strings.importer.lookupNotConfigured
            }

            ComponentOfficialLookupOutcome.Failed -> {
                lookupInProgress = false
                lookupIsError = true
                lookupMessage = lookupResult.message ?: strings.importer.lookupFailed(strings.importer.exportGenericError)
            }
        }
    }

    AdaptiveFormSurface(
        title = strings.importer.title,
        layoutMode = layoutMode,
        onDismiss = onDismiss,
        onSave = {
            baseCandidate?.let { sourceCandidate ->
                buildQuickSaveDraftOrNull()?.let { draft ->
                    onSaveImportedComponent(draft, sourceCandidate) { result ->
                        if (!result.isSuccess) {
                            feedbackMessage = result.message
                        }
                    }
                }
            }
        },
    ) {
        item {
            SectionPane(
                title = strings.importer.subtitle,
                supporting = strings.importer.supportedFormatHint,
            ) {
                FilledTonalButton(
                    onClick = { scannerMode = ImportScannerMode.Qr },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionScanQr)
                }
                OutlinedButton(
                    onClick = { scannerMode = ImportScannerMode.SupplierText },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionScanSupplierText)
                }
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.importer.rawInputLabel) },
                    placeholder = { Text(strings.importer.rawInputPlaceholder) },
                    minLines = 6,
                )
                OutlinedButton(
                    onClick = {
                        runCatching { ComponentImportParser.parseJlcText(rawInput) }
                            .onSuccess(::setBaseCandidate)
                            .onFailure { feedbackMessage = it.message ?: strings.importer.parseFirstError }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionParseText)
                }
                OutlinedButton(
                    onClick = {
                        runCatching { ComponentImportParser.parseSupplierText(rawInput) }
                            .onSuccess(::setBaseCandidate)
                            .onFailure { feedbackMessage = it.message ?: strings.importer.parseSupplierTextError }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionParseSupplierText)
                }
            }
        }

        displayedCandidate?.let { candidate ->
            val referenceName = candidate.referenceDisplayName()?.takeIf { reference ->
                !reference.equals(name.trim(), ignoreCase = true)
            }
            item {
                SectionPane(
                    title = strings.importer.enrichmentTitle,
                    supporting = strings.importer.enrichmentSubtitle,
                ) {
                    ValueBlock(label = strings.importer.sourceLabel, value = candidate.sourceLabel)
                    candidate.vendor?.takeIf { it.isNotBlank() }?.let {
                        ValueBlock(label = strings.importer.vendorLabel, value = it)
                    }
                    candidate.modelFamily?.takeIf { it.isNotBlank() }?.let {
                        ValueBlock(label = strings.importer.modelFamilyLabel, value = it)
                    }
                    candidate.recognitionConfidence?.takeIf { it.isNotBlank() }?.let {
                        ValueBlock(label = strings.importer.recognitionConfidenceLabel, value = it)
                    }
                    candidate.matchedBy?.takeIf { it.isNotBlank() }?.let {
                        ValueBlock(label = strings.importer.matchedByLabel, value = it)
                    }
                    Text(
                        text = when (learningMatchType) {
                            ComponentImportLearningMatchType.Sku -> strings.importer.learningMatchSku
                            ComponentImportLearningMatchType.Mpn -> strings.importer.learningMatchMpn
                            null -> strings.importer.localRulesOnly
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (candidate.sourceType != com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel) {
                        val serverMessage = when {
                            lookupInProgress -> strings.importer.lookupLoading
                            appPreferences.enableServerJlcLookup -> lookupMessage ?: strings.importer.lookupOnlyFillsMissing
                            else -> strings.importer.serverLookupDisabled
                        }
                        Text(
                            text = serverMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lookupIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            item {
                SectionPane(
                    title = strings.importer.recognizedTitle,
                    supporting = strings.importer.recognizedSubtitle,
                ) {
                    ImportOriginField(
                        value = sku,
                        onValueChange = {
                            sku = it
                            skuEdited = true
                            skuOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.common.fieldSku,
                        origin = skuOrigin,
                        strings = strings,
                    )
                    ImportOriginField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameEdited = true
                            nameOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.common.fieldName,
                        origin = nameOrigin,
                        strings = strings,
                    )
                    if (referenceName != null) {
                        ValueBlock(
                            label = strings.importer.referenceNameLabel,
                            value = referenceName,
                        )
                        OutlinedButton(
                            onClick = {
                                name = referenceName
                                nameEdited = true
                                nameOrigin = ComponentImportFieldOrigin.User
                                feedbackMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.importer.actionUseReferenceName)
                        }
                    }
                    if (name.isBlank()) {
                        Text(
                            text = strings.importer.nameConfirmationHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ImportOriginField(
                        value = category,
                        onValueChange = {
                            category = it
                            categoryEdited = true
                            categoryOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.common.fieldCategory,
                        origin = categoryOrigin,
                        strings = strings,
                    )
                    ImportOriginField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            packageEdited = true
                            packageOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.common.fieldPackage,
                        origin = packageOrigin,
                        strings = strings,
                    )
                    ImportOriginField(
                        value = model,
                        onValueChange = {
                            model = it
                            modelEdited = true
                            modelOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.importer.modelLabel,
                        origin = modelOrigin,
                        strings = strings,
                    )
                    ImportOriginField(
                        value = brand,
                        onValueChange = {
                            brand = it
                            brandEdited = true
                            brandOrigin = ComponentImportFieldOrigin.User
                        },
                        label = strings.importer.brandLabel,
                        origin = brandOrigin,
                        strings = strings,
                    )
                }
            }
            item {
                SectionPane(
                    title = strings.importer.importDetailTitle,
                    supporting = strings.importer.quantityHint,
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.common.fieldQuantity) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.common.fieldLocation) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.common.fieldMinimumStock) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(
                        onClick = {
                            baseCandidate?.let { sourceCandidate ->
                                buildEditorDraftOrNull()?.let { draft ->
                                    onOpenFullEditor(draft, sourceCandidate)
                                }
                            }
                        },
                    ) {
                        Text(strings.importer.actionOpenFullEditor)
                    }
                }
            }
            item {
                SectionPane(title = strings.importer.importPreviewTitle) {
                    Text(
                        text = buildPreviewDraft()?.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!feedbackMessage.isNullOrBlank()) {
            item {
                Text(
                    text = feedbackMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImportOriginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    origin: ComponentImportFieldOrigin,
    strings: ComponentVaultStrings,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        supportingText = {
            Text(strings.importer.fieldOrigin(origin))
        },
    )
}

private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
