package com.componentvault.android.ui.screen

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.ComponentLabelCodec
import com.componentvault.android.data.ComponentLabelRenderer
import com.componentvault.android.model.ComponentLabelSeed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ComponentLabelPreviewSurface(
    seed: ComponentLabelSeed,
    layoutMode: InventoryLayoutMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val strings = vaultStrings()
    val qrPayload = remember(seed) { ComponentLabelCodec.buildQrPayload(seed) }
    val bitmap = remember(seed, qrPayload) { ComponentLabelRenderer.renderBitmap(seed, qrPayload) }
    var copiesText by remember { mutableStateOf("1") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val pngExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        feedbackMessage = runCatching {
            context.contentResolver.openOutputStream(uri).use { stream ->
                requireNotNull(stream) { "Unable to open the selected file." }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            strings.importer.exportPngSuccess
        }.getOrElse { throwable ->
            strings.importer.exportFailed(throwable.message ?: strings.importer.exportGenericError)
        }
    }

    val pdfExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val copies = copiesText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        feedbackMessage = runCatching {
            context.contentResolver.openOutputStream(uri).use { stream ->
                requireNotNull(stream) { "Unable to open the selected file." }
                ComponentLabelRenderer.writePdf(
                    outputStream = stream,
                    seed = seed,
                    qrPayload = qrPayload,
                    copies = copies,
                )
            }
            strings.importer.exportPdfSuccess
        }.getOrElse { throwable ->
            strings.importer.exportFailed(throwable.message ?: strings.importer.exportGenericError)
        }
    }

    AdaptiveFormSurface(
        title = strings.importer.labelPreviewTitle,
        layoutMode = layoutMode,
        onDismiss = onDismiss,
        onSave = {
            val filename = suggestedFileName(seed.sku, "png")
            pngExporter.launch(filename)
        },
    ) {
        item {
            SectionPane(
                title = strings.importer.labelPreviewTitle,
                supporting = strings.importer.labelPreviewSubtitle,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = strings.importer.labelPreviewTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            SectionPane(
                title = strings.importer.labelPayloadTitle,
                supporting = strings.importer.labelPayloadSubtitle,
            ) {
                ValueBlock(label = strings.common.fieldSku, value = seed.sku)
                ValueBlock(label = strings.common.fieldQuantity, value = seed.quantity.toString())
                ValueBlock(label = strings.common.fieldLocation, value = seed.location)
                Text(
                    text = qrPayload,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionPane(title = strings.importer.exportSectionTitle) {
                OutlinedTextField(
                    value = copiesText,
                    onValueChange = { copiesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.importer.labelCopiesLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = { pngExporter.launch(suggestedFileName(seed.sku, "png")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionExportPng)
                }
                OutlinedButton(
                    onClick = { pdfExporter.launch(suggestedFileName(seed.sku, "pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionExportPdf)
                }
            }
        }
        if (!feedbackMessage.isNullOrBlank()) {
            item {
                Text(
                    text = feedbackMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun suggestedFileName(
    sku: String,
    extension: String,
): String {
    val safeSku = sku.ifBlank { "component-label" }
        .replace(Regex("[^A-Za-z0-9._-]"), "-")
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "$safeSku-$timestamp.$extension"
}
