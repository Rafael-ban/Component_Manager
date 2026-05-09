package com.componentvault.android.ui.screen

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.JlcImportParser
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.JlcImportPayload
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
internal fun JlcImportSurface(
    layoutMode: InventoryLayoutMode,
    appPreferences: AppPreferences,
    onDismiss: () -> Unit,
    onSaveImportedComponent: (ComponentDraft) -> Unit,
    onOpenFullEditor: (ComponentDraft) -> Unit,
) {
    val strings = vaultStrings()
    val context = LocalContext.current
    var rawInput by remember { mutableStateOf("") }
    var parsedPayload by remember { mutableStateOf<JlcImportPayload?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    var location by remember(appPreferences.suggestedImportLocation) {
        mutableStateOf(appPreferences.suggestedImportLocation)
    }
    var minStockText by remember(appPreferences.defaultImportMinStock) {
        mutableStateOf(appPreferences.defaultImportMinStock.toString())
    }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    fun applyParsedPayload(payload: JlcImportPayload) {
        parsedPayload = payload
        quantityText = (payload.suggestedQuantity ?: 1).coerceAtLeast(1).toString()
        feedbackMessage = null
    }

    AdaptiveFormSurface(
        title = strings.importer.title,
        layoutMode = layoutMode,
        onDismiss = onDismiss,
        onSave = {
            val payload = parsedPayload
            if (payload == null) {
                feedbackMessage = strings.importer.parseFirstError
                return@AdaptiveFormSurface
            }

            val quantity = quantityText.toIntOrNull()
            val minStock = minStockText.toIntOrNull()
            if (quantity == null || quantity <= 0) {
                feedbackMessage = strings.forms.movementQuantityPositive
                return@AdaptiveFormSurface
            }
            if (minStock == null || minStock < 0) {
                feedbackMessage = strings.forms.componentNonNegative
                return@AdaptiveFormSurface
            }
            if (location.isBlank()) {
                feedbackMessage = strings.forms.componentRequiredFields
                return@AdaptiveFormSurface
            }

            onSaveImportedComponent(
                payload.toComponentDraft(
                    quantity = quantity,
                    location = location.trim(),
                    minStock = minStock,
                ),
            )
        },
    ) {
        item {
            SectionPane(
                title = strings.importer.subtitle,
                supporting = strings.importer.supportedFormatHint,
            ) {
                FilledTonalButton(
                    onClick = {
                        startJlcQrScan(
                            context = context,
                            enableAutoZoom = appPreferences.scannerAutoZoomEnabled,
                            onSuccess = { result ->
                                rawInput = result
                                runCatching { JlcImportParser.parseQr(result) }
                                    .onSuccess(::applyParsedPayload)
                                    .onFailure {
                                        feedbackMessage = it.message ?: strings.importer.scanCancelled
                                    }
                            },
                            onFailure = { error ->
                                feedbackMessage = if (error.isNullOrBlank()) {
                                    strings.importer.scanCancelled
                                } else {
                                    strings.importer.scanFailed(error)
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionScanQr)
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
                        val parser = if (rawInput.trim().startsWith("{")) {
                            runCatching { JlcImportParser.parseQr(rawInput) }
                        } else {
                            runCatching { JlcImportParser.parseText(rawInput) }
                        }
                        parser
                            .onSuccess(::applyParsedPayload)
                            .onFailure { feedbackMessage = it.message ?: strings.importer.parseFirstError }
                    },
                ) {
                    Text(strings.importer.actionParseText)
                }
            }
        }

        parsedPayload?.let { payload ->
            item {
                SectionPane(
                    title = strings.importer.recognizedTitle,
                    supporting = strings.importer.recognizedSubtitle,
                ) {
                    ValueBlock(label = strings.common.fieldSku, value = payload.sku)
                    ValueBlock(label = strings.common.fieldName, value = payload.name)
                    ValueBlock(label = strings.common.fieldPackage, value = payload.packageName)
                    ValueBlock(label = strings.common.fieldCategory, value = payload.category)
                    ValueBlock(label = strings.importer.sourceLabel, value = payload.sourceLabel)
                    payload.model?.let {
                        ValueBlock(label = strings.importer.modelLabel, value = it)
                    }
                    payload.brand?.let {
                        ValueBlock(label = strings.importer.brandLabel, value = it)
                    }
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
                            val quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val minStock = minStockText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            onOpenFullEditor(
                                payload.toComponentDraft(
                                    quantity = quantity,
                                    location = location.ifBlank { appPreferences.suggestedImportLocation },
                                    minStock = minStock,
                                ),
                            )
                        },
                    ) {
                        Text(strings.importer.actionOpenFullEditor)
                    }
                }
            }
            item {
                SectionPane(title = strings.importer.importPreviewTitle) {
                    Text(
                        text = payload.toComponentDraft(
                            quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            location = location,
                            minStock = minStockText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        ).description,
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

private fun startJlcQrScan(
    context: Context,
    enableAutoZoom: Boolean,
    onSuccess: (String) -> Unit,
    onFailure: (String?) -> Unit,
) {
    val optionsBuilder = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)

    if (enableAutoZoom) {
        optionsBuilder.enableAutoZoom()
    }

    val scanner = GmsBarcodeScanning.getClient(context, optionsBuilder.build())
    val scanTask: Task<Barcode> = scanner.startScan()
    scanTask
        .addOnSuccessListener { result ->
            onSuccess(result.rawValue.orEmpty())
        }
        .addOnCanceledListener {
            onFailure(null)
        }
        .addOnFailureListener { error ->
            onFailure(error.message)
        }
}
