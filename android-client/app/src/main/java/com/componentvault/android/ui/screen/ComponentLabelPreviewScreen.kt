package com.componentvault.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.ComponentLabelCodec
import com.componentvault.android.data.ComponentLabelPrintCanvasTemplate
import com.componentvault.android.data.ComponentLabelRenderer
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.model.ComponentLabelSeed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComponentLabelPreviewSurface(
    seed: ComponentLabelSeed,
    layoutMode: InventoryLayoutMode,
    onDismiss: () -> Unit,
    selectedTemplate: ComponentLabelTemplate = ComponentLabelTemplate.default,
    selectedCanvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
    includeCompanionTextLabel: Boolean = false,
    selectedTextTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    onTemplateChange: (ComponentLabelTemplate) -> Unit = {},
    onCanvasTemplateChange: (ComponentLabelPrintCanvasTemplate) -> Unit = {},
    onIncludeCompanionTextLabelChange: (Boolean) -> Unit = {},
    onTextTemplateChange: (ComponentTextLabelTemplate) -> Unit = {},
) {
    val context = LocalContext.current
    val strings = vaultStrings()
    val qrPayload = remember(seed, selectedTemplate.id) {
        ComponentLabelCodec.buildQrPayload(seed, selectedTemplate)
    }
    val textLabelContent = remember(seed, selectedTextTemplate) {
        ComponentLabelRenderer.buildTextLabelContent(seed, selectedTextTemplate)
    }
    val pageBitmaps = remember(
        seed,
        selectedTemplate.id,
        selectedCanvasTemplate.id,
        includeCompanionTextLabel,
        selectedTextTemplate.id,
        strings.importer.labelFooterText,
    ) {
        ComponentLabelRenderer.renderBitmaps(
            seed = seed,
            template = selectedTemplate,
            footerText = strings.importer.labelFooterText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = selectedTextTemplate,
            canvasTemplate = selectedCanvasTemplate,
            showCanvasOutline = true,
        )
    }
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
                ComponentLabelRenderer.writePng(
                    outputStream = stream,
                    seed = seed,
                    template = selectedTemplate,
                    footerText = strings.importer.labelFooterText,
                    includeCompanionTextLabel = includeCompanionTextLabel,
                    textTemplate = selectedTextTemplate,
                    canvasTemplate = selectedCanvasTemplate,
                )
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
                    copies = copies,
                    template = selectedTemplate,
                    footerText = strings.importer.labelFooterText,
                    includeCompanionTextLabel = includeCompanionTextLabel,
                    textTemplate = selectedTextTemplate,
                    canvasTemplate = selectedCanvasTemplate,
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
            val filename = suggestedFileName(
                sku = seed.sku,
                template = selectedTemplate,
                canvasTemplate = selectedCanvasTemplate,
                includeCompanionTextLabel = includeCompanionTextLabel,
                textTemplate = selectedTextTemplate,
                extension = "png",
            )
            pngExporter.launch(filename)
        },
    ) {
        item {
            SectionPane(
                title = strings.importer.labelPreviewTitle,
                supporting = strings.importer.labelPreviewSubtitle,
            ) {
                Text(
                    text = strings.importer.labelFormatTitle,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = strings.importer.labelFormatSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComponentLabelTemplate.entries.forEach { template ->
                        FilterChip(
                            selected = selectedTemplate.id == template.id,
                            onClick = {
                                onTemplateChange(template)
                                feedbackMessage = null
                            },
                            label = { Text(strings.importer.labelTemplateLabel(template)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.importer.printCanvasTitle,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = strings.importer.printCanvasSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComponentLabelPrintCanvasTemplate.entries.forEach { canvasTemplate ->
                        FilterChip(
                            selected = selectedCanvasTemplate.id == canvasTemplate.id,
                            onClick = {
                                onCanvasTemplateChange(canvasTemplate)
                                feedbackMessage = null
                            },
                            label = { Text(strings.importer.printCanvasLabel(canvasTemplate)) },
                        )
                    }
                }
                if (selectedTemplate.supportsCompanionTextLabel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.importer.labelCompanionToggle,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Switch(
                            checked = includeCompanionTextLabel,
                            onCheckedChange = {
                                onIncludeCompanionTextLabelChange(it)
                                feedbackMessage = null
                            },
                        )
                    }
                    Text(
                        text = strings.importer.labelCompanionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selectedTemplate.isQrLabel || includeCompanionTextLabel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.importer.textLabelTemplateTitle,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = strings.importer.textLabelTemplateSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComponentTextLabelTemplate.entries.forEach { template ->
                            FilterChip(
                                selected = selectedTextTemplate == template,
                                onClick = {
                                    onTextTemplateChange(template)
                                    feedbackMessage = null
                                },
                                label = { Text(strings.importer.textLabelTemplateLabel(template)) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.importer.labelFormatSummary(selectedTemplate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = strings.importer.printCanvasSummary(selectedCanvasTemplate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pageBitmaps.forEachIndexed { index, bitmap ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = strings.importer.labelPreviewTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                ValueBlock(label = strings.importer.labelFormatTitle, value = strings.importer.labelFormatSummary(selectedTemplate))
                ValueBlock(label = strings.importer.printCanvasTitle, value = strings.importer.printCanvasSummary(selectedCanvasTemplate))
                ValueBlock(
                    label = strings.importer.labelPayloadModeTitle,
                    value = strings.importer.payloadModeLabel(qrPayload?.mode ?: selectedTemplate.payloadMode),
                )
                qrPayload?.rawValue?.let { payload ->
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selectedTemplate.isQrLabel || includeCompanionTextLabel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ValueBlock(
                        label = strings.importer.textLabelTemplateTitle,
                        value = strings.importer.textLabelTemplateLabel(selectedTextTemplate),
                    )
                    Text(
                        text = strings.importer.labelTextContentTitle,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = textLabelContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    onClick = {
                        pngExporter.launch(
                            suggestedFileName(
                                sku = seed.sku,
                                template = selectedTemplate,
                                canvasTemplate = selectedCanvasTemplate,
                                includeCompanionTextLabel = includeCompanionTextLabel,
                                textTemplate = selectedTextTemplate,
                                extension = "png",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.importer.actionExportPng)
                }
                OutlinedButton(
                    onClick = {
                        pdfExporter.launch(
                            suggestedFileName(
                                sku = seed.sku,
                                template = selectedTemplate,
                                canvasTemplate = selectedCanvasTemplate,
                                includeCompanionTextLabel = includeCompanionTextLabel,
                                textTemplate = selectedTextTemplate,
                                extension = "pdf",
                            ),
                        )
                    },
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
    template: ComponentLabelTemplate,
    canvasTemplate: ComponentLabelPrintCanvasTemplate,
    includeCompanionTextLabel: Boolean,
    textTemplate: ComponentTextLabelTemplate,
    extension: String,
): String {
    val safeSku = sku.ifBlank { "component-label" }
        .replace(Regex("[^A-Za-z0-9._-]"), "-")
    val suffix = buildString {
        append(template.fileSuffix)
        append("-")
        append(canvasTemplate.fileSuffix)
        if (includeCompanionTextLabel && template.isQrLabel) {
            append("-with-")
            append(textTemplate.fileSuffix)
        } else if (!template.isQrLabel) {
            append("-")
            append(textTemplate.fileSuffix)
        }
    }
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "$safeSku-$suffix-$timestamp.$extension"
}
