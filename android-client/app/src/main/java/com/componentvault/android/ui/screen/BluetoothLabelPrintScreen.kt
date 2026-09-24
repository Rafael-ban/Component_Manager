package com.componentvault.android.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.componentvault.android.R
import com.componentvault.android.data.BluetoothPrinterDiagnostics
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.data.LabelPrintController
import com.componentvault.android.data.LabelPrintItemState
import com.componentvault.android.data.M1ComponentLabelRenderer
import com.componentvault.android.data.M1TestPaperProfile
import com.componentvault.android.model.ComponentLabelSeed
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.toLabelSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class PrintableComponent(val id: String, val seed: ComponentLabelSeed)
private data class PreviewRequest(
    val seeds: List<ComponentLabelSeed>,
    val templateId: String,
    val textTemplateId: String,
    val paper: M1TestPaperProfile?,
)
private data class PrintPreview(
    val request: PreviewRequest? = null,
    val bitmap: Bitmap? = null,
    val error: String? = null,
)

@Composable
internal fun BluetoothLabelPrintScreen(
    components: List<ComponentRecord>,
    initialSeed: ComponentLabelSeed?,
    initialTemplateId: String = ComponentLabelTemplate.default.id,
    initialTextTemplateId: String = ComponentTextLabelTemplate.default.id,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { LabelPrintController(context) }
    val choices = remember(components, initialSeed) {
        buildList {
            if (initialSeed != null) add(PrintableComponent("initial:${initialSeed.sku}", initialSeed))
            components.filterNot(ComponentRecord::deleted).filter { initialSeed == null || it.sku != initialSeed.sku }
                .forEach { add(PrintableComponent(it.id, it.toLabelSeed())) }
        }
    }
    val initiallySelected = remember(choices, initialSeed) {
        if (initialSeed == null) null else "initial:${initialSeed.sku}"
    }
    val selected = remember(initiallySelected) {
        mutableStateMapOf<String, Boolean>().apply { initiallySelected?.let { put(it, true) } }
    }
    val copies = remember { mutableStateMapOf<String, String>() }
    var search by remember { mutableStateOf("") }
    var templateId by remember(initialTemplateId) { mutableStateOf(initialTemplateId) }
    var textTemplateId by remember(initialTextTemplateId) { mutableStateOf(initialTextTemplateId) }
    var widthText by remember { mutableStateOf("40") }
    var heightText by remember { mutableStateOf("60") }
    var rotationText by remember { mutableStateOf("0") }
    var offsetXText by remember { mutableStateOf("0") }
    var offsetYText by remember { mutableStateOf("0") }
    var confirmNewQueue by remember { mutableStateOf(false) }
    var reviewItemId by remember { mutableStateOf<String?>(null) }
    var previewQueueItemId by remember { mutableStateOf<String?>(null) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var leaveAfterStop by remember { mutableStateOf(false) }

    fun requestBack() {
        if (controller.running) {
            leaveAfterStop = true
            confirmStop = true
        } else onDismiss()
    }

    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.close()
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (BluetoothPrinterDiagnostics.permissions().all { grants[it] == true }) {
            controller.transport.refreshPaired()
        } else {
            localMessage = context.getString(R.string.bluetooth_label_print_permission)
        }
    }
    fun refreshDevices() {
        val permissions = BluetoothPrinterDiagnostics.permissions()
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            controller.transport.refreshPaired()
        } else {
            permissionRequest.launch(permissions)
        }
    }
    val paper = remember(widthText, heightText, rotationText, offsetXText, offsetYText) {
        runCatching {
            M1TestPaperProfile(
                widthMm = widthText.toFloat(),
                heightMm = heightText.toFloat(),
                rotationDegrees = rotationText.toInt(),
                offsetXmm = offsetXText.toFloat(),
                offsetYmm = offsetYText.toFloat(),
            )
        }.getOrNull()
    }
    val draftSelections = choices.mapNotNull { choice ->
        if (selected[choice.id] != true) return@mapNotNull null
        val count = (copies[choice.id] ?: "1").toIntOrNull() ?: 0
        choice.seed to count
    }
    val validSelection = draftSelections.isNotEmpty() &&
        draftSelections.all { it.second in 1..99 } && draftSelections.sumOf { it.second } <= 500
    val queue = controller.queue
    val hasQueue = queue.items.isNotEmpty()
    LaunchedEffect(controller.loaded, hasQueue) {
        if (controller.loaded && !hasQueue) {
            widthText = queue.paper.widthMm.toString()
            heightText = queue.paper.heightMm.toString()
            rotationText = queue.paper.rotationDegrees.toString()
            offsetXText = queue.paper.offsetXmm.toString()
            offsetYText = queue.paper.offsetYmm.toString()
            if (initialSeed == null) {
                templateId = queue.templateId
                textTemplateId = queue.textTemplateId
            }
        }
    }
    val editable = !hasQueue && controller.loaded && !controller.running && !controller.working
    val previewSeeds = if (hasQueue) {
        queue.items.firstOrNull { it.id == previewQueueItemId }?.let { listOf(it.seed) }
            ?: queue.items.firstOrNull()?.let { listOf(it.seed) }.orEmpty()
    } else {
        draftSelections.map { it.first }.distinct()
    }
    val previewSeed = previewSeeds.firstOrNull()
    val previewPaper = if (hasQueue) queue.paper else paper
    val previewTemplate = if (hasQueue) ComponentLabelTemplate.fromId(queue.templateId)
        else ComponentLabelTemplate.fromId(templateId)
    val previewTextTemplate = if (hasQueue) ComponentTextLabelTemplate.fromId(queue.textTemplateId)
        else ComponentTextLabelTemplate.fromId(textTemplateId)
    val previewRequest = PreviewRequest(previewSeeds, previewTemplate.id, previewTextTemplate.id, previewPaper)
    val preview by produceState(
        initialValue = PrintPreview(),
        previewRequest,
    ) {
        value = PrintPreview()
        if (previewSeed != null && previewPaper != null) {
            var generated: Bitmap? = null
            value = try {
                val bitmap = withContext(Dispatchers.Default) {
                    M1ComponentLabelRenderer.render(previewSeed, previewTemplate, previewTextTemplate, previewPaper).also {
                        generated = it
                    }
                    previewSeeds.drop(1).forEach { seed ->
                        currentCoroutineContext().ensureActive()
                        M1ComponentLabelRenderer.render(seed, previewTemplate, previewTextTemplate, previewPaper).recycle()
                    }
                    generated!!
                }
                val result = PrintPreview(request = previewRequest, bitmap = bitmap)
                generated = null
                result
            } catch (error: Exception) {
                generated?.recycle()
                if (error is CancellationException) throw error
                PrintPreview(request = previewRequest,
                    error = error.message ?: context.getString(R.string.bluetooth_label_print_preview_unavailable))
            }
        }
    }
    DisposableEffect(preview) { onDispose { preview.bitmap?.recycle() } }

    SecondaryPageScaffold(
        title = stringResource(R.string.bluetooth_label_print_title),
        onBack = ::requestBack,
        bottomBar = {
            val selectedCandidate = controller.transport.candidates.firstOrNull {
                it.paired && it.type in setOf(BluetoothDevice.DEVICE_TYPE_CLASSIC, BluetoothDevice.DEVICE_TYPE_DUAL) &&
                    it.address == controller.selectedAddress
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasQueue) {
                    Button(
                        onClick = { paper?.let { controller.create(draftSelections, templateId, textTemplateId, it) } },
                        modifier = Modifier.weight(1f),
                        enabled = editable && validSelection && paper != null && preview.request == previewRequest &&
                            preview.bitmap != null && preview.error == null,
                    ) { Text(stringResource(R.string.bluetooth_label_print_create)) }
                } else if (controller.running) {
                    OutlinedButton(onClick = controller::pause, enabled = !controller.pauseRequested, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bluetooth_label_print_pause))
                    }
                    Button(onClick = { leaveAfterStop = false; confirmStop = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bluetooth_label_print_stop))
                    }
                } else {
                    Button(
                        onClick = { selectedCandidate?.let(controller::start) },
                        enabled = !controller.working && selectedCandidate != null &&
                            queue.items.any { it.state == LabelPrintItemState.Pending } &&
                            queue.items.none { it.state in listOf(LabelPrintItemState.Uncertain, LabelPrintItemState.Failed, LabelPrintItemState.Sending) },
                        modifier = Modifier.weight(1f).testTag("print_queue_start"),
                    ) { Text(stringResource(R.string.bluetooth_label_print_start)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.bluetooth_label_print_intro), style = MaterialTheme.typography.bodyMedium)
            if (!controller.loaded || controller.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!hasQueue) {
                PrintSection(stringResource(R.string.bluetooth_label_print_components)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text(stringResource(R.string.bluetooth_label_print_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val visible = choices.filter {
                        search.isBlank() || it.seed.name.contains(search, true) || it.seed.sku.contains(search, true)
                    }
                    if (visible.isEmpty()) Text(stringResource(R.string.bluetooth_label_print_empty))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(visible, key = PrintableComponent::id) { choice ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = selected[choice.id] == true,
                                onCheckedChange = { selected[choice.id] = it },
                                enabled = editable,
                            )
                            Column(Modifier.weight(1f).clickable(enabled = editable) {
                                selected[choice.id] = selected[choice.id] != true
                            }) {
                                Text(choice.seed.name.ifBlank { choice.seed.sku }, style = MaterialTheme.typography.bodyMedium)
                                Text(choice.seed.sku, style = MaterialTheme.typography.bodySmall)
                            }
                            if (selected[choice.id] == true) {
                                OutlinedTextField(
                                    value = copies[choice.id] ?: "1",
                                    onValueChange = { copies[choice.id] = it.filter(Char::isDigit).take(2) },
                                    label = { Text(stringResource(R.string.bluetooth_label_print_copies)) },
                                    modifier = Modifier.weight(0.45f),
                                    enabled = editable,
                                    singleLine = true,
                                )
                            }
                        }
                        }
                    }
                    Text(stringResource(R.string.bluetooth_label_print_count, draftSelections.sumOf { it.second }))
                    if (!validSelection && draftSelections.isNotEmpty()) {
                        Text(stringResource(R.string.bluetooth_label_print_count_invalid), color = MaterialTheme.colorScheme.error)
                    }
                }
                PrintSection(stringResource(R.string.bluetooth_label_print_layout)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ComponentLabelTemplate.entries.forEach { template ->
                            FilterChip(
                                selected = templateId == template.id,
                                onClick = { templateId = template.id },
                                label = { Text(stringResource(when (template) {
                                    ComponentLabelTemplate.Qr10x40 -> R.string.bluetooth_label_print_compact_qr
                                    ComponentLabelTemplate.Qr30x40 -> R.string.bluetooth_label_print_full_qr
                                    else -> R.string.bluetooth_label_print_text_only
                                })) },
                                enabled = editable,
                            )
                        }
                    }
                    if (ComponentLabelTemplate.fromId(templateId) == ComponentLabelTemplate.TextOnly) {
                        ComponentTextLabelTemplate.entries.forEach { textTemplate ->
                            FilterChip(
                                selected = textTemplateId == textTemplate.id,
                                onClick = { textTemplateId = textTemplate.id },
                                label = { Text(stringResource(when (textTemplate) {
                                    ComponentTextLabelTemplate.NameSku -> R.string.bluetooth_label_print_text_name_sku
                                    ComponentTextLabelTemplate.NamePackageSku -> R.string.bluetooth_label_print_text_name_package_sku
                                    ComponentTextLabelTemplate.NameModel -> R.string.bluetooth_label_print_text_name_model
                                })) },
                            )
                        }
                    }
                    Text(stringResource(R.string.bluetooth_label_print_qr_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.bluetooth_label_print_paper), style = MaterialTheme.typography.titleSmall)
                    PrintNumberField(stringResource(R.string.bluetooth_label_print_width), widthText, { widthText = it }, editable)
                    PrintNumberField(stringResource(R.string.bluetooth_label_print_height), heightText, { heightText = it }, editable)
                    PrintNumberField(stringResource(R.string.bluetooth_label_print_rotation), rotationText, { rotationText = it }, editable)
                    PrintNumberField(stringResource(R.string.bluetooth_label_print_offset_x), offsetXText, { offsetXText = it }, editable)
                    PrintNumberField(stringResource(R.string.bluetooth_label_print_offset_y), offsetYText, { offsetYText = it }, editable)
                    if (paper == null) Text(stringResource(R.string.bluetooth_label_print_paper_invalid), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.bluetooth_label_print_paper_hint), style = MaterialTheme.typography.bodySmall)
                }
                PrintSection(stringResource(R.string.bluetooth_label_print_preview)) {
                    if (preview.bitmap != null) {
                        Image(preview.bitmap!!.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 300.dp))
                    } else {
                        Text(preview.error ?: stringResource(R.string.bluetooth_label_print_preview_unavailable),
                            color = if (preview.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                val sent = queue.items.count { it.state == LabelPrintItemState.Sent }
                val skipped = queue.items.count { it.state == LabelPrintItemState.Skipped }
                PrintSection(stringResource(R.string.bluetooth_label_print_queue)) {
                    Text(stringResource(R.string.bluetooth_label_print_progress, sent + skipped, queue.items.size))
                    LinearProgressIndicator(
                        progress = { if (queue.items.isEmpty()) 0f else (sent + skipped).toFloat() / queue.items.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.bluetooth_label_print_locked, queue.paper.widthMm.toString(), queue.paper.heightMm.toString()))
                    Text(stringResource(
                        R.string.bluetooth_label_print_locked_detail,
                        labelTemplateText(ComponentLabelTemplate.fromId(queue.templateId), context),
                        queue.paper.rotationDegrees,
                        queue.paper.offsetXmm.toString(),
                        queue.paper.offsetYmm.toString(),
                    ), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("print_queue_list")) {
                        items(queue.items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().testTag("print_queue_item_${item.id}"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.seed.name.ifBlank { item.seed.sku })
                                Text("${item.seed.sku} · ${item.copyNumber}/${item.copies} · ${labelStateText(item.state, context)}", style = MaterialTheme.typography.bodySmall)
                                if (item.detail.isNotBlank()) Text(printErrorText(item.detail, context), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!controller.running && !controller.working && item.state in listOf(LabelPrintItemState.Uncertain, LabelPrintItemState.Failed)) {
                                TextButton(onClick = { reviewItemId = item.id }) {
                                    Text(stringResource(R.string.bluetooth_label_print_review))
                                }
                            }
                        }
                        }
                    }
                    PrintSection(stringResource(R.string.bluetooth_label_print_preview)) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 128.dp)) {
                            items(queue.items.distinctBy { it.seed }, key = { it.id }) { item ->
                                Row(Modifier.fillMaxWidth().clickable { previewQueueItemId = item.id }) {
                                    RadioButton(
                                        selected = (previewQueueItemId ?: queue.items.firstOrNull()?.id) == item.id,
                                        onClick = { previewQueueItemId = item.id },
                                    )
                                    Column {
                                        Text(item.seed.name.ifBlank { item.seed.sku })
                                        Text(item.seed.sku, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (preview.bitmap != null) Image(preview.bitmap!!.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 300.dp))
                        else Text(preview.error ?: stringResource(R.string.bluetooth_label_print_preview_unavailable))
                    }
                }
                PrintSection(stringResource(R.string.bluetooth_label_print_device)) {
                    OutlinedButton(onClick = ::refreshDevices, enabled = !controller.running && !controller.working) {
                        Text(stringResource(R.string.bluetooth_label_print_refresh_devices))
                    }
                    if (controller.transport.candidates.isEmpty()) Text(stringResource(R.string.bluetooth_label_print_pair_hint))
                    controller.transport.candidates.filter {
                        it.paired && it.type in setOf(BluetoothDevice.DEVICE_TYPE_CLASSIC, BluetoothDevice.DEVICE_TYPE_DUAL)
                    }.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !controller.running && !controller.working) {
                                controller.selectDevice(candidate.address)
                            },
                        ) {
                            RadioButton(
                                selected = controller.selectedAddress == candidate.address,
                                onClick = { controller.selectDevice(candidate.address) },
                                enabled = !controller.running && !controller.working,
                            )
                            Column {
                                Text(candidate.name ?: stringResource(R.string.bluetooth_label_print_unknown_device))
                                Text("…${candidate.address.takeLast(5)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { Text(stringResource(R.string.bluetooth_label_print_pair_settings)) }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(controller.transport.report))
                    }, enabled = controller.transport.report.isNotBlank()) {
                        Text(stringResource(R.string.bluetooth_label_print_copy_report))
                    }
                    if (controller.pauseRequested) Text(stringResource(R.string.bluetooth_label_print_pausing))
                }
                OutlinedButton(onClick = { confirmNewQueue = true }, enabled = !controller.running && !controller.working, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bluetooth_label_print_new_queue))
                }
            }
            localMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    controller.error?.let { error ->
        AlertDialog(
            onDismissRequest = controller::clearError,
            title = { Text(stringResource(R.string.bluetooth_label_print_error_title)) },
            text = { Text(printErrorText(error, context)) },
            confirmButton = {
                if (error == "queue_load_failed") {
                    TextButton(onClick = { controller.clear() }) {
                        Text(stringResource(R.string.bluetooth_label_print_clear_corrupt))
                    }
                } else {
                    TextButton(onClick = controller::clearError) { Text(stringResource(R.string.bluetooth_label_print_dismiss_error)) }
                }
            },
            dismissButton = {
                TextButton(onClick = controller::clearError) { Text(stringResource(R.string.bluetooth_label_print_cancel)) }
            },
        )
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.bluetooth_label_print_stop)) },
        text = { Text(stringResource(R.string.bluetooth_label_print_stop_warning)) },
        confirmButton = { TextButton(onClick = {
            controller.stop()
            confirmStop = false
            if (leaveAfterStop) onDismiss()
            leaveAfterStop = false
        }) { Text(stringResource(R.string.bluetooth_label_print_stop)) } },
        dismissButton = { TextButton(onClick = { confirmStop = false; leaveAfterStop = false }) {
            Text(stringResource(R.string.bluetooth_label_print_cancel))
        } },
    )
    if (confirmNewQueue) AlertDialog(
        onDismissRequest = { confirmNewQueue = false },
        title = { Text(stringResource(R.string.bluetooth_label_print_new_queue)) },
        text = { Text(stringResource(R.string.bluetooth_label_print_replace_warning)) },
        confirmButton = { TextButton(onClick = { controller.clear(); confirmNewQueue = false }) {
            Text(stringResource(R.string.bluetooth_label_print_clear_confirm))
        } },
        dismissButton = { TextButton(onClick = { confirmNewQueue = false }) { Text(stringResource(R.string.bluetooth_label_print_cancel)) } },
    )
    val reviewItem = queue.items.firstOrNull { it.id == reviewItemId }
    if (reviewItem != null) AlertDialog(
        onDismissRequest = { reviewItemId = null },
        title = { Text(stringResource(R.string.bluetooth_label_print_review)) },
        text = { Text(stringResource(R.string.bluetooth_label_print_review_warning, reviewItem.seed.sku)) },
        confirmButton = {
            Column {
                TextButton(onClick = { controller.resolve(reviewItem.id, LabelPrintItemState.Pending); reviewItemId = null }) {
                    Text(stringResource(R.string.bluetooth_label_print_retry))
                }
                TextButton(onClick = { controller.resolve(reviewItem.id, LabelPrintItemState.Sent); reviewItemId = null }) {
                    Text(stringResource(R.string.bluetooth_label_print_mark_printed))
                }
                TextButton(onClick = { controller.resolve(reviewItem.id, LabelPrintItemState.Skipped); reviewItemId = null }) {
                    Text(stringResource(R.string.bluetooth_label_print_skip))
                }
            }
        },
        dismissButton = { TextButton(onClick = { reviewItemId = null }) { Text(stringResource(R.string.bluetooth_label_print_cancel)) } },
    )
}

private fun labelStateText(state: LabelPrintItemState, context: android.content.Context): String = context.getString(when (state) {
    LabelPrintItemState.Pending -> R.string.bluetooth_label_print_state_pending
    LabelPrintItemState.Sending -> R.string.bluetooth_label_print_state_sending
    LabelPrintItemState.Sent -> R.string.bluetooth_label_print_state_sent
    LabelPrintItemState.Uncertain -> R.string.bluetooth_label_print_state_uncertain
    LabelPrintItemState.Failed -> R.string.bluetooth_label_print_state_failed
    LabelPrintItemState.Skipped -> R.string.bluetooth_label_print_state_skipped
})

private fun labelTemplateText(template: ComponentLabelTemplate, context: android.content.Context): String = context.getString(when (template) {
    ComponentLabelTemplate.Qr10x40 -> R.string.bluetooth_label_print_compact_qr
    ComponentLabelTemplate.Qr30x40 -> R.string.bluetooth_label_print_full_qr
    else -> R.string.bluetooth_label_print_text_only
})

private fun printErrorText(code: String, context: android.content.Context): String = context.getString(when (code) {
    "queue_load_failed" -> R.string.bluetooth_label_print_error_load
    "queue_save_failed", "queue_save_or_print_failed" -> R.string.bluetooth_label_print_error_save
    "queue_invalid_selection" -> R.string.bluetooth_label_print_count_invalid
    "queue_review_required", "print_check_label" -> R.string.bluetooth_label_print_error_review
    "label_does_not_fit" -> R.string.bluetooth_label_print_error_fit
    "printer_paper_out" -> R.string.bluetooth_label_print_error_paper_out
    "printer_cover_open" -> R.string.bluetooth_label_print_error_cover_open
    "printer_locate_failed" -> R.string.bluetooth_label_print_error_locate
    "printer_bluetooth_off" -> R.string.bluetooth_label_print_error_bluetooth_off
    "printer_permission_required" -> R.string.bluetooth_label_print_permission
    "printer_not_m1" -> R.string.bluetooth_label_print_error_not_m1
    "printer_no_response" -> R.string.bluetooth_label_print_error_no_response
    else -> R.string.bluetooth_label_print_error_not_ready
})

@Composable
private fun PrintSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PrintNumberField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
    )
}
