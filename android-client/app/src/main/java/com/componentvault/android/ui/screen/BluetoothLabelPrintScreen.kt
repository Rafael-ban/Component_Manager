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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
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
import com.componentvault.android.data.LabelDesign
import com.componentvault.android.data.LabelElement
import com.componentvault.android.data.LabelElementType
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
import org.json.JSONArray
import org.json.JSONObject

private data class PrintableComponent(val id: String, val seed: ComponentLabelSeed)
private data class PreviewRequest(
    val seeds: List<ComponentLabelSeed>,
    val templateId: String,
    val textTemplateId: String,
    val paper: M1TestPaperProfile?,
    val designs: Map<String, LabelDesign>,
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
    val selected = rememberSaveable(initiallySelected, saver = mapSaver(
        save = { it.toMap() },
        restore = { saved -> mutableStateMapOf<String, Boolean>().apply {
            saved.forEach { (key, value) -> put(key, value as Boolean) }
        } },
    )) {
        mutableStateMapOf<String, Boolean>().apply { initiallySelected?.let { put(it, true) } }
    }
    val copies = rememberSaveable(saver = mapSaver(
        save = { it.toMap() },
        restore = { saved -> mutableStateMapOf<String, String>().apply {
            saved.forEach { (key, value) -> put(key, value as String) }
        } },
    )) { mutableStateMapOf<String, String>() }
    var search by rememberSaveable { mutableStateOf("") }
    var troubleshootingExpanded by remember { mutableStateOf(false) }
    var choosingComponents by rememberSaveable { mutableStateOf(initialSeed == null) }
    var calibrationExpanded by rememberSaveable { mutableStateOf(false) }
    var editingSku by rememberSaveable { mutableStateOf<String?>(initialSeed?.sku) }
    var selectedElementId by rememberSaveable { mutableStateOf<String?>(null) }
    var invalidGeometryFields by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var draftDesignJson by rememberSaveable { mutableStateOf("{}") }
    var pendingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTextTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    val draftDesigns = remember(draftDesignJson) { readLabelDrafts(draftDesignJson) }
    var templateId by rememberSaveable(initialTemplateId) { mutableStateOf(initialTemplateId) }
    var textTemplateId by rememberSaveable(initialTextTemplateId) { mutableStateOf(initialTextTemplateId) }
    var widthText by rememberSaveable { mutableStateOf("40") }
    var heightText by rememberSaveable { mutableStateOf("60") }
    var rotationText by rememberSaveable { mutableStateOf("0") }
    var offsetXText by rememberSaveable { mutableStateOf("0") }
    var offsetYText by rememberSaveable { mutableStateOf("0") }
    var restoredPaper by rememberSaveable { mutableStateOf(false) }
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
        if (controller.loaded && !hasQueue && !restoredPaper) {
            widthText = queue.paper.widthMm.toString()
            heightText = queue.paper.heightMm.toString()
            rotationText = queue.paper.rotationDegrees.toString()
            offsetXText = queue.paper.offsetXmm.toString()
            offsetYText = queue.paper.offsetYmm.toString()
            if (initialSeed == null) {
                templateId = queue.templateId
                textTemplateId = queue.textTemplateId
            }
            restoredPaper = true
        }
    }
    val editable = !hasQueue && controller.loaded && !controller.running && !controller.working
    val previewSeeds = if (hasQueue) {
        queue.items.firstOrNull { it.id == previewQueueItemId }?.let { listOf(it.seed) }
            ?: queue.items.firstOrNull()?.let { listOf(it.seed) }.orEmpty()
    } else {
        draftSelections.map { it.first }.distinct().let { seeds ->
            val focused = seeds.firstOrNull { it.sku == editingSku } ?: seeds.firstOrNull()
            if (focused == null) emptyList() else listOf(focused) + seeds.filterNot { it.sku == focused.sku }
        }
    }
    val previewSeed = previewSeeds.firstOrNull()
    val previewPaper = if (hasQueue) queue.paper else paper
    val previewTemplate = if (hasQueue) ComponentLabelTemplate.fromId(queue.templateId)
        else ComponentLabelTemplate.fromId(templateId)
    val previewTextTemplate = if (hasQueue) ComponentTextLabelTemplate.fromId(queue.textTemplateId)
        else ComponentTextLabelTemplate.fromId(textTemplateId)
    val activeDesign = if (previewSeed != null && previewPaper != null) {
        if (hasQueue) queue.items.firstOrNull { it.seed.sku == previewSeed.sku }?.design
        else draftDesigns[previewSeed.sku]
            ?: runCatching { LabelDesign.default(previewSeed, previewTemplate, previewTextTemplate, previewPaper) }.getOrNull()
    } else null
    fun updateDesign(design: LabelDesign) {
        val seed = previewSeed ?: return
        draftDesignJson = writeLabelDrafts(draftDesigns + (seed.sku to design))
    }
    fun recordGeometryValidity(field: String, invalid: String?) {
        invalidGeometryFields = ArrayList(invalidGeometryFields.toMutableSet().apply {
            if (invalid != null) add(field) else remove(field)
        })
    }
    val printDesigns = if (hasQueue) queue.items.mapNotNull { it.design?.let { design -> it.seed.sku to design } }.toMap()
        else draftSelections.mapNotNull { (seed, _) ->
            val design = draftDesigns[seed.sku]
                ?: paper?.let { runCatching { LabelDesign.default(seed, previewTemplate, previewTextTemplate, it) }.getOrNull() }
            design?.let { seed.sku to it }
        }.toMap()
    val previewRequest = PreviewRequest(previewSeeds, previewTemplate.id, previewTextTemplate.id, previewPaper, printDesigns)
    var preview by remember { mutableStateOf(PrintPreview()) }
    LaunchedEffect(previewRequest) {
        if (previewSeed != null && previewPaper != null) {
            var generated: Bitmap? = null
            preview = try {
                val bitmap = withContext(Dispatchers.Default) {
                    M1ComponentLabelRenderer.render(previewSeed, previewTemplate, previewTextTemplate, previewPaper,
                        printDesigns[previewSeed.sku]).also {
                        generated = it
                    }
                    previewSeeds.drop(1).forEach { seed ->
                        currentCoroutineContext().ensureActive()
                        M1ComponentLabelRenderer.render(seed, previewTemplate, previewTextTemplate, previewPaper,
                            printDesigns[seed.sku]).recycle()
                    }
                    generated!!
                }
                val result = PrintPreview(request = previewRequest, bitmap = bitmap)
                generated = null
                result
            } catch (error: Exception) {
                generated?.recycle()
                if (error is CancellationException) throw error
                PrintPreview(request = previewRequest, bitmap = preview.bitmap,
                    error = error.message ?: context.getString(R.string.bluetooth_label_print_preview_unavailable))
            }
        } else preview = PrintPreview()
    }
    // Once published to Compose, the RenderThread may retain this bitmap past disposal.
    // Let GC release displayed previews; only recycle temporary, never-published rasters.

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
                        onClick = { paper?.let { controller.create(draftSelections, templateId, textTemplateId, it, printDesigns) } },
                        modifier = Modifier.weight(1f),
                        enabled = editable && validSelection && paper != null && invalidGeometryFields.isEmpty() &&
                            printDesigns.size == draftSelections.size && preview.request == previewRequest &&
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
            localMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!controller.loaded || controller.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!hasQueue) {
                PrintSection(stringResource(R.string.bluetooth_label_print_components)) {
                    if (!choosingComponents) {
                        draftSelections.forEach { (seed, count) ->
                            Text("${seed.name.ifBlank { seed.sku }} · ${seed.sku} · $count", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { choosingComponents = true }) {
                            Text(stringResource(R.string.label_editor_change_components))
                        }
                    } else {
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
                    TextButton(onClick = { choosingComponents = false }) {
                        Text(stringResource(R.string.label_editor_done_components))
                    }
                    }
                    Text(stringResource(R.string.bluetooth_label_print_count, draftSelections.sumOf { it.second }))
                    if (!validSelection && draftSelections.isNotEmpty()) {
                        Text(stringResource(R.string.bluetooth_label_print_count_invalid), color = MaterialTheme.colorScheme.error)
                    }
                }
                PrintSection(stringResource(R.string.bluetooth_label_print_preview)) {
                    if (draftSelections.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        draftSelections.forEach { (seed, _) ->
                            FilterChip(
                                selected = previewSeed?.sku == seed.sku,
                                onClick = {
                                    if (editingSku != seed.sku) {
                                        editingSku = seed.sku
                                        selectedElementId = null
                                        invalidGeometryFields = arrayListOf()
                                    }
                                },
                                label = { Text(seed.name.ifBlank { seed.sku }) },
                            )
                        }
                        }
                    }
                    if (preview.bitmap != null && previewPaper != null && previewSeed != null) {
                        LabelEditorCanvas(preview.bitmap!!, activeDesign, requireNotNull(previewPaper),
                            selectedElementId, editable,
                            onSelect = {
                                if (selectedElementId != it) {
                                    selectedElementId = it
                                    invalidGeometryFields = arrayListOf()
                                }
                            },
                            onMove = { id, x, y ->
                                val design = activeDesign ?: return@LabelEditorCanvas
                                val sheet = previewPaper ?: return@LabelEditorCanvas
                                val moved = design.moved(id, x, y, sheet)
                                updateDesign(moved)
                                val before = design.elements.firstOrNull { it.id == id }
                                val after = moved.elements.firstOrNull { it.id == id }
                                val prefix = "${previewSeed.sku}:$id:"
                                invalidGeometryFields = ArrayList(invalidGeometryFields.filterNot {
                                    (it == "${prefix}x" && before?.xMm != after?.xMm) ||
                                        (it == "${prefix}y" && before?.yMm != after?.yMm)
                                })
                            })
                        Text(stringResource(R.string.label_editor_preview_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.error != null) {
                        Text(stringResource(R.string.label_editor_invalid), color = MaterialTheme.colorScheme.error)
                        Text(preview.error!!, color = MaterialTheme.colorScheme.error)
                    } else if (preview.bitmap == null) {
                        Text(stringResource(R.string.bluetooth_label_print_preview_unavailable))
                    }
                    LabelRasterExportActions(
                        bitmap = preview.bitmap,
                        paper = previewPaper,
                        copies = draftSelections.firstOrNull { it.first.sku == previewSeed?.sku }?.second ?: 1,
                        sku = previewSeed?.sku.orEmpty(),
                        enabled = validSelection && preview.request == previewRequest && preview.error == null &&
                            invalidGeometryFields.isEmpty() && preview.bitmap != null,
                        onFeedback = { localMessage = it },
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeDesign?.elements?.forEach { element ->
                        FilterChip(
                            selected = selectedElementId == element.id,
                            onClick = {
                                if (selectedElementId != element.id) {
                                    selectedElementId = element.id
                                    invalidGeometryFields = arrayListOf()
                                }
                            },
                            label = { Text(element.text.take(24).ifBlank { element.id }) },
                            enabled = editable,
                        )
                    }
                    }
                    val editableDesign = activeDesign
                    val selectedElement = editableDesign?.elements?.firstOrNull { it.id == selectedElementId }
                    if (selectedElement != null && editableDesign != null) {
                            Text(stringResource(R.string.label_editor_element), style = MaterialTheme.typography.titleSmall)
                            if (selectedElement.type == LabelElementType.Text) {
                                OutlinedTextField(
                                    value = selectedElement.text,
                                    onValueChange = { updateDesign(editableDesign.withText(selectedElement.id, it)) },
                                    label = { Text(stringResource(R.string.label_editor_text)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = editable,
                                )
                            } else Text(stringResource(R.string.label_editor_qr_identity), style = MaterialTheme.typography.bodySmall)
                            LabelElementNumberField("${previewSeed?.sku}:${selectedElement.id}:x", stringResource(R.string.label_editor_x),
                                selectedElement.xMm, editable,
                                { recordGeometryValidity("${previewSeed?.sku}:${selectedElement.id}:x", it) }) { value ->
                                updateDesign(editableDesign.withElement(selectedElement.copy(xMm = value)))
                            }
                            LabelElementNumberField("${previewSeed?.sku}:${selectedElement.id}:y", stringResource(R.string.label_editor_y),
                                selectedElement.yMm, editable,
                                { recordGeometryValidity("${previewSeed?.sku}:${selectedElement.id}:y", it) }) { value ->
                                updateDesign(editableDesign.withElement(selectedElement.copy(yMm = value)))
                            }
                            LabelElementNumberField("${previewSeed?.sku}:${selectedElement.id}:width", stringResource(R.string.label_editor_width),
                                selectedElement.widthMm, editable,
                                { recordGeometryValidity("${previewSeed?.sku}:${selectedElement.id}:width", it) }) { value ->
                                updateDesign(editableDesign.withElement(selectedElement.copy(widthMm = value)))
                            }
                            LabelElementNumberField("${previewSeed?.sku}:${selectedElement.id}:height", stringResource(R.string.label_editor_height),
                                selectedElement.heightMm, editable,
                                { recordGeometryValidity("${previewSeed?.sku}:${selectedElement.id}:height", it) }) { value ->
                                updateDesign(editableDesign.withElement(selectedElement.copy(heightMm = value)))
                            }
                    }
                    if (invalidGeometryFields.isNotEmpty()) Text(stringResource(R.string.label_editor_invalid), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.label_editor_draft_only), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.bluetooth_label_print_layout), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ComponentLabelTemplate.entries.forEach { template ->
                            FilterChip(
                                selected = templateId == template.id,
                                onClick = {
                                    if (template.id != templateId) {
                                        if (draftDesigns.isNotEmpty()) pendingTemplateId = template.id
                                        else {
                                            templateId = template.id
                                            selectedElementId = null
                                            invalidGeometryFields = arrayListOf()
                                        }
                                    }
                                },
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
                                onClick = {
                                    if (textTemplate.id != textTemplateId) {
                                        if (draftDesigns.isNotEmpty()) pendingTextTemplateId = textTemplate.id
                                        else {
                                            textTemplateId = textTemplate.id
                                            selectedElementId = null
                                            invalidGeometryFields = arrayListOf()
                                        }
                                    }
                                },
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
                    TextButton(onClick = { calibrationExpanded = !calibrationExpanded }) {
                        Text(stringResource(R.string.label_editor_calibration))
                    }
                    if (calibrationExpanded) {
                        PrintNumberField(stringResource(R.string.bluetooth_label_print_rotation), rotationText, { rotationText = it }, editable)
                        PrintNumberField(stringResource(R.string.bluetooth_label_print_offset_x), offsetXText, { offsetXText = it }, editable)
                        PrintNumberField(stringResource(R.string.bluetooth_label_print_offset_y), offsetYText, { offsetYText = it }, editable)
                    }
                    if (paper == null) Text(stringResource(R.string.bluetooth_label_print_paper_invalid), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.bluetooth_label_print_paper_hint), style = MaterialTheme.typography.bodySmall)

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
                        if (preview.bitmap != null && previewPaper != null && previewSeed != null) LabelEditorCanvas(preview.bitmap!!, activeDesign, previewPaper,
                            selectedElementId, false, {}, { _, _, _ -> })
                        else Text(preview.error ?: stringResource(R.string.bluetooth_label_print_preview_unavailable))
                        LabelRasterExportActions(
                            bitmap = preview.bitmap,
                            paper = previewPaper,
                            copies = queue.items.count { it.seed.sku == previewSeed?.sku }.coerceAtLeast(1),
                            sku = previewSeed?.sku.orEmpty(),
                            enabled = preview.request == previewRequest && preview.error == null && preview.bitmap != null,
                            onFeedback = { localMessage = it },
                        )
                    }
                }
                OutlinedButton(onClick = { confirmNewQueue = true }, enabled = !controller.running && !controller.working, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bluetooth_label_print_new_queue))
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
                    TextButton(
                        onClick = { troubleshootingExpanded = !troubleshootingExpanded },
                        enabled = !controller.running && !controller.working,
                    ) { Text(stringResource(R.string.label_editor_troubleshooting)) }
                    if (troubleshootingExpanded) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(controller.transport.report))
                        }, enabled = controller.transport.report.isNotBlank() && !controller.running && !controller.working) {
                            Text(stringResource(R.string.bluetooth_label_print_copy_report))
                        }
                    }
                    if (controller.pauseRequested) Text(stringResource(R.string.bluetooth_label_print_pausing))
                }
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
    if (pendingTemplateId != null || pendingTextTemplateId != null) AlertDialog(
        onDismissRequest = { pendingTemplateId = null; pendingTextTemplateId = null },
        title = { Text(stringResource(R.string.label_editor_reset_title)) },
        text = { Text(stringResource(R.string.label_editor_reset_warning)) },
        confirmButton = { TextButton(onClick = {
            pendingTemplateId?.let { templateId = it }
            pendingTextTemplateId?.let { textTemplateId = it }
            draftDesignJson = "{}"
            selectedElementId = null
            invalidGeometryFields = arrayListOf()
            pendingTemplateId = null
            pendingTextTemplateId = null
        }) { Text(stringResource(R.string.label_editor_reset_confirm)) } },
        dismissButton = { TextButton(onClick = { pendingTemplateId = null; pendingTextTemplateId = null }) {
            Text(stringResource(R.string.bluetooth_label_print_cancel))
        } },
    )
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

private fun LabelDesign.withElement(replacement: LabelElement): LabelDesign =
    copy(elements = elements.map { if (it.id == replacement.id) replacement else it })

@Composable
private fun LabelElementNumberField(
    fieldId: String,
    label: String,
    value: Float,
    enabled: Boolean,
    onInvalidChange: (String?) -> Unit,
    onChange: (Float) -> Unit,
) {
    var raw by rememberSaveable(fieldId) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (raw.toFloatOrNull() != value) raw = value.toString() }
    OutlinedTextField(
        value = raw,
        onValueChange = { input ->
            raw = input
            val parsed = input.toFloatOrNull()?.takeIf { it.isFinite() }
            if (parsed == null) onInvalidChange(fieldId)
            else {
                onInvalidChange(null)
                onChange(parsed)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
    )
}

private fun readLabelDrafts(encoded: String): Map<String, LabelDesign> = runCatching {
    val root = JSONObject(encoded)
    buildMap {
        root.keys().forEach { sku ->
            val items = root.getJSONArray(sku)
            val elements = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                LabelElement(
                    id = item.getString("id"),
                    type = LabelElementType.valueOf(item.getString("type")),
                    text = item.getString("text"),
                    xMm = item.getDouble("x").toFloat(),
                    yMm = item.getDouble("y").toFloat(),
                    widthMm = item.getDouble("width").toFloat(),
                    heightMm = item.getDouble("height").toFloat(),
                )
            }
            put(sku, LabelDesign(elements))
        }
    }
}.getOrDefault(emptyMap())

private fun writeLabelDrafts(drafts: Map<String, LabelDesign>): String = JSONObject().apply {
    drafts.forEach { (sku, design) ->
        put(sku, JSONArray().apply {
            design.elements.forEach { element ->
                put(JSONObject().apply {
                    put("id", element.id)
                    put("type", element.type.name)
                    put("text", element.text)
                    put("x", element.xMm.toDouble())
                    put("y", element.yMm.toDouble())
                    put("width", element.widthMm.toDouble())
                    put("height", element.heightMm.toDouble())
                })
            }
        })
    }
}.toString()
