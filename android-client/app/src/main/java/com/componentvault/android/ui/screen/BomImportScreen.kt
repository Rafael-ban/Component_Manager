package com.componentvault.android.ui.screen

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import com.componentvault.android.data.bom.BomReleasePreview
import com.componentvault.android.data.bom.BomSheet
import com.componentvault.android.data.bom.BomColumnMapping
import com.componentvault.android.data.bom.BomTableInspection
import com.componentvault.android.data.bom.BomShortageCsvExporter
import com.componentvault.android.data.bom.BomPreset
import com.componentvault.android.data.bom.BomPresetStore
import com.componentvault.android.data.bom.ComponentHubParseResult
import com.componentvault.android.data.bom.LocalImportLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class BomImportMode { Bom, Migration }

@Composable
internal fun BomImportScreen(
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    initialMode: BomImportMode? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember(context) { BomPresetStore(File(context.filesDir, "bom-presets.json")) }
    val initialPresets = remember(presetStore) { runCatching { presetStore.list() } }
    var presets by remember { mutableStateOf(initialPresets.getOrDefault(emptyList())) }
    var activePreset by remember { mutableStateOf<BomPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<BomPreset?>(null) }
    var presetToSave by remember { mutableStateOf<BomReleasePreview?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var productionSets by remember { mutableStateOf("1") }
    var sheets by remember { mutableStateOf<List<BomSheet>>(emptyList()) }
    var selectedSheet by remember { mutableStateOf<String?>(null) }
    var inspection by remember { mutableStateOf<BomTableInspection?>(null) }
    var columnMapping by remember { mutableStateOf<BomColumnMapping?>(null) }
    var preview by remember { mutableStateOf<BomReleasePreview?>(null) }
    var hubPreview by remember { mutableStateOf<ComponentHubParseResult?>(null) }
    var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var searchQueries by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedSearches by remember { mutableStateOf<Set<String>>(emptySet()) }
    var excludedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mappingExpanded by rememberSaveable { mutableStateOf(false) }
    var showAllLines by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialPresets.exceptionOrNull()?.message.orEmpty()) }
    var messageIsError by remember { mutableStateOf(initialPresets.isFailure) }
    var busy by remember { mutableStateOf(false) }
    var releaseId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var batchId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var releaseApplied by rememberSaveable { mutableStateOf(false) }
    var confirmKind by remember { mutableStateOf<String?>(null) }
    var selectedMode by rememberSaveable { mutableStateOf(initialMode) }

    fun resetFile() {
        fileBytes = null
        activePreset = null
        fileName = ""
        sheets = emptyList()
        selectedSheet = null
        inspection = null
        columnMapping = null
        preview = null
        hubPreview = null
        selections = emptyMap()
        searchQueries = emptyMap()
        expandedSearches = emptySet()
        excludedKeys = emptySet()
        mappingExpanded = false
        showAllLines = false
        message = ""
        messageIsError = false
        releaseApplied = false
    }

    fun requestPreview() {
        val bytes = fileBytes
        if (selectedMode == BomImportMode.Migration) {
            val migrationBytes = bytes ?: return
            busy = true
            viewModel.previewComponentHub(migrationBytes, skipDuplicates = false) { result ->
                busy = false
                result.onSuccess {
                    hubPreview = it
                    message = "迁移预览：可导入 ${it.components.size} 项，冲突 ${it.conflicts.size} 项。"
                    messageIsError = false
                }.onFailure { message = it.message ?: "Component Hub 解析失败。"; messageIsError = true }
            }
            return
        }
        val sets = productionSets.toIntOrNull()
        if (projectName.isBlank() || sets == null || sets <= 0) {
            message = "请输入项目名和正整数生产套数。"
            messageIsError = true
            return
        }
        val mapping = columnMapping
        if (activePreset == null && inspection != null &&
            (mapping?.quantity == null || mapping.sku == null && mapping.model == null && mapping.name == null)) {
            mappingExpanded = true
            message = context.getString(R.string.bom_mapping_required)
            messageIsError = true
            return
        }
        busy = true
        val onResult: (Result<BomReleasePreview>) -> Unit = { result ->
            busy = false
            result.onSuccess { preview = it; message = context.getString(R.string.bom_matching_done); messageIsError = false }
                .onFailure {
                    preview = null
                    message = it.message ?: "BOM 解析失败。"
                    messageIsError = true
                }
        }
        val preset = activePreset
        if (preset != null) {
            runCatching { preset.reconfigure(sets).parsed.copy(projectName = projectName.trim()) }
                .onSuccess { viewModel.previewBomPreset(it, selections, searchQueries, excludedKeys, onResult) }
                .onFailure { onResult(Result.failure(it)) }
        } else {
            val sourceBytes = bytes ?: run { busy = false; return }
            viewModel.previewBom(
                bytes = sourceBytes, fileName = fileName, projectName = projectName,
                productionSets = sets, sheetName = selectedSheet, mapping = columnMapping,
                selections = selections, searchQueries = searchQueries, excludedKeys = excludedKeys,
                onComplete = onResult,
            )
        }
    }

    fun openPreset(preset: BomPreset) {
        activePreset = preset
        fileBytes = null
        fileName = preset.name
        projectName = preset.parsed.projectName
        productionSets = preset.parsed.productionSets.toString()
        selectedSheet = preset.parsed.selectedSheet.name
        inspection = null
        columnMapping = null
        selections = preset.selections
        searchQueries = emptyMap()
        excludedKeys = preset.excludedKeys
        expandedSearches = emptySet()
        showAllLines = false
        releaseId = UUID.randomUUID().toString()
        batchId = UUID.randomUUID().toString()
        releaseApplied = false
        preview = null
        requestPreview()
    }

    fun inspectSelected(bytes: ByteArray? = fileBytes) {
        val selectedBytes = bytes ?: return
        busy = true
        viewModel.inspectBom(selectedBytes, fileName, selectedSheet) { result ->
            busy = false
            result.onSuccess {
                inspection = it
                columnMapping = it.automaticMapping
                mappingExpanded = it.automaticMapping.quantity == null ||
                    it.automaticMapping.sku == null && it.automaticMapping.model == null && it.automaticMapping.name == null
                message = ""
                messageIsError = false
            }
                .onFailure { message = it.message ?: "无法读取 BOM 表头。"; messageIsError = true }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val current = preview
        if (uri == null || current == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(BomShortageCsvExporter.export(current)) }
            } }.onSuccess {
                val count = BomShortageCsvExporter.shortageCount(current)
                message = if (count == 0) "已导出 CSV；当前预览无缺料。" else "已导出 CSV，含 $count 项缺料或未匹配记录。"
                messageIsError = false
            }.onFailure { message = it.message ?: "CSV 导出失败。"; messageIsError = true }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
        runCatching { withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            } ?: "bom.csv"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= LocalImportLimits.MAX_FILE_BYTES) {
                        "文件超过 10 MiB 限制。"
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("无法读取文件。")
            name to bytes
        } }.onSuccess { (name, bytes) ->
            fileName = name
            if (projectName.isBlank()) projectName = name.substringBeforeLast('.').ifBlank { name }
            fileBytes = bytes
            activePreset = null
            preview = null
            hubPreview = null
            selections = emptyMap()
            searchQueries = emptyMap()
            expandedSearches = emptySet()
            excludedKeys = emptySet()
            mappingExpanded = false
            showAllLines = false
            inspection = null
            columnMapping = null
            releaseId = UUID.randomUUID().toString()
            batchId = UUID.randomUUID().toString()
            releaseApplied = false
            sheets = emptyList()
            selectedSheet = null
            if (name.endsWith(".xlsx", true)) {
                viewModel.listBomSheets(bytes) { result ->
                    busy = false
                    result.onSuccess {
                        sheets = it
                        selectedSheet = it.firstOrNull { sheet -> !sheet.hidden }?.name
                        inspectSelected(bytes)
                    }.onFailure { message = it.message ?: "无法读取工作表。"; messageIsError = true }
                }
            } else {
                sheets = emptyList()
                selectedSheet = null
                inspectSelected(bytes)
            }
        }.onFailure { busy = false; message = it.message ?: "无法读取文件。"; messageIsError = true }
        }
    }

    SecondaryPageScaffold(
        title = stringResource(when (selectedMode) {
            BomImportMode.Bom -> R.string.bom_task_bom
            BomImportMode.Migration -> R.string.bom_task_migration
            null -> R.string.bom_workflow_title
        }),
        onBack = {
            when {
                preview != null || hubPreview != null -> {
                    preview = null
                    hubPreview = null
                    message = ""
                    messageIsError = false
                }
                fileBytes != null -> resetFile()
                initialMode == null && selectedMode != null -> selectedMode = null
                else -> onDismiss()
            }
        },
        navigationEnabled = !busy,
    ) { scaffoldPadding ->
        if (selectedMode == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(R.string.bom_task_prompt), style = MaterialTheme.typography.bodyLarge) }
                item {
                    ImportTaskCard(
                        title = stringResource(R.string.bom_task_bom),
                        description = stringResource(R.string.bom_task_bom_description),
                        onClick = { selectedMode = BomImportMode.Bom },
                    )
                }
                item {
                    ImportTaskCard(
                        title = stringResource(R.string.bom_task_migration),
                        description = stringResource(R.string.bom_task_migration_description),
                        onClick = { selectedMode = BomImportMode.Migration },
                    )
                }
            }
            return@SecondaryPageScaffold
        }
        if (preview == null && hubPreview == null) {
            Column(Modifier.fillMaxSize().padding(scaffoldPadding).imePadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (fileBytes == null && activePreset == null) item {
                    Text(
                        stringResource(if (selectedMode == BomImportMode.Bom) R.string.bom_task_bom_description else R.string.bom_task_migration_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedMode == BomImportMode.Bom && fileBytes == null && activePreset == null && presets.isNotEmpty()) {
                    item { Text(stringResource(R.string.bom_saved_projects), style = MaterialTheme.typography.titleMedium) }
                    items(presets, key = { "preset:${it.id}" }) { preset ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.bom_saved_project_summary, preset.parsed.requirements.size, preset.parsed.productionSets), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy, onClick = { openPreset(preset) }) { Text(stringResource(R.string.bom_open_preset)) }
                                    TextButton(enabled = !busy, onClick = { presetToDelete = preset }) { Text(stringResource(R.string.bom_delete)) }
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy,
                        onClick = {
                            picker.launch(if (selectedMode == BomImportMode.Bom) {
                                arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            } else arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (fileName.isBlank()) stringResource(R.string.bom_choose_file) else "${stringResource(R.string.bom_choose_file)} · $fileName") }
                }
                if (selectedMode == BomImportMode.Bom && (fileBytes != null || activePreset != null)) {
                    item {
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it; preview = null },
                            label = { Text(stringResource(R.string.bom_project_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !releaseApplied,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = productionSets,
                            onValueChange = { productionSets = it; preview = null },
                            label = { Text(stringResource(R.string.bom_production_sets)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !releaseApplied,
                        )
                    }
                }
                if (activePreset != null) item {
                    Text(stringResource(R.string.bom_preset_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sheets.size > 1) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sheets.filterNot { it.hidden }, key = { it.name }) { sheet ->
                            FilterChip(
                                selected = selectedSheet == sheet.name,
                                enabled = !busy && !releaseApplied,
                                onClick = {
                                    selectedSheet = sheet.name
                                    inspection = null
                                    columnMapping = null
                                    selections = emptyMap()
                                    searchQueries = emptyMap()
                                    expandedSearches = emptySet()
                                    excludedKeys = emptySet()
                                    inspectSelected()
                                },
                                label = { Text(sheet.name) },
                            )
                        }
                    }
                }
                if (selectedMode == BomImportMode.Bom && inspection != null) item {
                    val table = inspection!!
                    val mapping = columnMapping ?: table.automaticMapping
                    val ready = mapping.quantity != null && (mapping.sku != null || mapping.model != null || mapping.name != null)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(if (ready) R.string.bom_detected else R.string.bom_detection_needs_review), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.bom_table_summary, table.sheet.name, table.dataRowCount, table.headerRowNumber),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (ready) stringResource(
                                    R.string.bom_mapping_summary,
                                    table.headers.getOrNull(mapping.quantity ?: -1).orEmpty(),
                                    listOfNotNull(mapping.sku, mapping.model, mapping.name).mapNotNull { table.headers.getOrNull(it) }.joinToString("、"),
                                ) else stringResource(R.string.bom_mapping_missing),
                                color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { mappingExpanded = !mappingExpanded }) {
                                Text(stringResource(if (mappingExpanded) R.string.bom_mapping_collapse else R.string.bom_mapping_expand))
                            }
                            if (mappingExpanded) {
                                BomColumnMappingEditor(
                                    inspection = table,
                                    mapping = mapping,
                                    onChange = { columnMapping = it; preview = null },
                                )
                            }
                        }
                    }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (message.isNotBlank()) item { StatusMessage(message, messageIsError) }
            }
            if (fileBytes != null || activePreset != null) {
                Button(
                    onClick = ::requestPreview,
                    enabled = !busy && !releaseApplied && (selectedMode != BomImportMode.Bom || inspection != null),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(if (selectedMode == BomImportMode.Bom) R.string.bom_match_inventory else R.string.bom_generate_preview)) }
            }
            }
            return@SecondaryPageScaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (selectedMode == BomImportMode.Bom) {
                    stringResource(R.string.bom_preview_summary, fileName, projectName, productionSets)
                } else {
                    stringResource(R.string.migration_preview_summary, fileName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.isNotBlank()) StatusMessage(message, messageIsError)
            preview?.let { current ->
            val skippedRequirements = current.parsed.requirements.filter { it.identity.canonicalKey in excludedKeys }
            val shortageIds = current.lines.filter {
                it.componentId != null && (it.availableQuantity < it.requirement.requiredQuantity ||
                    it.allocationPlan.sumOf { allocation -> allocation.quantity } != it.requirement.requiredQuantity)
            }.mapNotNull { it.componentId }.toSet()
            val problemLines = current.matchingLines.filter { it.componentId == null || it.componentId in shortageIds }
            val visibleLines = if (showAllLines) current.matchingLines else problemLines
            val readyCount = current.matchingLines.size - problemLines.size
            Text(stringResource(R.string.bom_match_counts, readyCount, problemLines.size, skippedRequirements.size), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (current.canCommit) R.string.bom_ready_to_review else if (current.lines.isEmpty()) R.string.bom_restore_to_release else R.string.bom_resolve_hint),
                color = if (current.canCommit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { showAllLines = !showAllLines }) {
                Text(if (showAllLines) stringResource(R.string.bom_show_issues) else stringResource(R.string.bom_show_all, current.matchingLines.size))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    TextButton(enabled = !busy && !releaseApplied, onClick = { presetToSave = current }) {
                        Text(stringResource(R.string.bom_save_preset))
                    }
                }
                if (visibleLines.isEmpty()) item {
                    Text(stringResource(R.string.bom_no_issues), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(visibleLines, key = { "match:${it.requirement.identity.canonicalKey}" }) { line ->
                    val requirementKey = line.requirement.identity.canonicalKey
                    val selectionKeys = line.selectionKeys
                    val searchExpanded = requirementKey in expandedSearches
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                            Text(line.requirement.sku ?: line.requirement.model ?: "未识别物料")
                            val details = listOfNotNull(
                                line.requirement.name?.takeIf(String::isNotBlank),
                                line.requirement.model?.takeIf { it.isNotBlank() && it != line.requirement.sku },
                                line.requirement.packageName?.takeIf(String::isNotBlank),
                                columnMapping?.reference?.let { index -> inspection?.headers?.getOrNull(index) }
                                    ?.let { header -> line.requirement.sourceRows.flatMap { it.fields[header].orEmpty().split(',', ';') }.filter(String::isNotBlank).distinct().joinToString("、").takeIf(String::isNotBlank) },
                            )
                            if (details.isNotEmpty()) Text(details.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.bom_line_quantity, line.requirement.quantityPerSet, line.requirement.requiredQuantity, line.availableQuantity))
                            if (line.componentId != null && line.componentId in shortageIds) {
                                Text(stringResource(R.string.bom_stock_shortage), color = MaterialTheme.colorScheme.error)
                            }
                            if (line.componentId == null) {
                                Text(
                                    stringResource(
                                        if (line.candidates.isEmpty()) R.string.bom_match_none
                                        else R.string.bom_match_choose,
                                    ),
                                )
                                if (!searchExpanded) line.candidates.forEach { candidate ->
                                    TextButton(enabled = !busy, onClick = {
                                        selections = selections + selectionKeys.associateWith { candidate.inventoryId }
                                        requestPreview()
                                    }) { Text(candidate.matchingLabel()) }
                                }
                            } else {
                                Text(stringResource(R.string.bom_match_selected, line.componentSku.orEmpty()))
                            }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    expandedSearches = if (searchExpanded) {
                                        expandedSearches - requirementKey
                                    } else {
                                        expandedSearches + requirementKey
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.bom_match_manual))
                            }
                            if (searchExpanded) {
                                OutlinedTextField(
                                    value = selectionKeys.firstNotNullOfOrNull(searchQueries::get).orEmpty(),
                                    onValueChange = { query ->
                                        searchQueries = searchQueries + selectionKeys.associateWith { query }
                                    },
                                    label = { Text(stringResource(R.string.bom_match_search_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    enabled = !busy && selectionKeys.firstNotNullOfOrNull(searchQueries::get).orEmpty().isNotBlank(),
                                    onClick = ::requestPreview,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.bom_match_search_action)) }
                                line.candidates
                                    .filterNot { it.inventoryId == line.componentId }
                                    .forEach { candidate ->
                                        TextButton(
                                            enabled = !busy,
                                            onClick = {
                                                selections = selections + selectionKeys.associateWith { candidate.inventoryId }
                                                requestPreview()
                                            },
                                        ) { Text(candidate.matchingLabel()) }
                                    }
                                if (selectionKeys.firstNotNullOfOrNull(searchQueries::get).orEmpty().isNotBlank() &&
                                    line.candidates.none { it.inventoryId != line.componentId }
                                ) {
                                    Text(stringResource(R.string.bom_match_search_empty))
                                }
                            }
                            TextButton(enabled = !busy, onClick = {
                                excludedKeys = excludedKeys + requirementKey
                                requestPreview()
                            }) { Text(stringResource(R.string.bom_skip_item)) }
                        }
                    }
                }
                items(skippedRequirements, key = { "skip:${it.identity.canonicalKey}" }) { requirement ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.bom_skipped_item, requirement.sku ?: requirement.model ?: requirement.name ?: stringResource(R.string.bom_mapping_unnamed)))
                            Text(stringResource(R.string.bom_skipped_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(enabled = !busy, onClick = {
                                excludedKeys = excludedKeys - requirement.identity.canonicalKey
                                requestPreview()
                            }) { Text(stringResource(R.string.bom_restore_item)) }
                        }
                    }
                }
            }
            Button(
                enabled = current.canCommit && !busy && !releaseApplied,
                onClick = { confirmKind = "bom" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.bom_review_release, current.lines.size)) }
            OutlinedButton(
                enabled = !busy,
                onClick = { exportPicker.launch("${fileName.substringBeforeLast('.').ifBlank { "bom" }}-缺料.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (BomShortageCsvExporter.shortageCount(current) == 0) "导出 CSV（无缺料）" else "导出缺料 CSV") }
            if (releaseApplied) {
                OutlinedButton(
                    onClick = {
                        releaseId = UUID.randomUUID().toString()
                        batchId = UUID.randomUUID().toString()
                        releaseApplied = false
                        preview = null
                        message = "已创建新生产批次，请重新生成预览。"
                        messageIsError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("新建生产批次") }
            }
            }
            hubPreview?.let { current ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(current.components, key = { it.sourceIndex }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${item.sku} · ${item.name}")
                            Text("库存 ${item.quantity} · ${item.category}")
                        }
                    }
                }
                items(current.conflicts, key = { "conflict-${it.sourceIndex}" }) { conflict ->
                    Text(conflict.message, color = MaterialTheme.colorScheme.error)
                }
                items(current.issues, key = { "issue-${it.sourceIndex}" }) { issue ->
                    Text(issue.message, color = MaterialTheme.colorScheme.error)
                }
            }
            if (current.conflicts.any { !it.skipped }) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val bytes = fileBytes ?: return@OutlinedButton
                        busy = true
                        viewModel.previewComponentHub(bytes, skipDuplicates = true) { result ->
                            busy = false
                            result.onSuccess { hubPreview = it; message = "已明确跳过 ${it.skippedDuplicateCount} 个冲突。"; messageIsError = false }
                                .onFailure { message = it.message ?: "无法应用跳过策略。"; messageIsError = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("明确跳过重复项") }
            }
            Button(
                enabled = current.canConfirm && !busy,
                onClick = { confirmKind = "hub" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("确认迁移") }
            }
        }
    }
    confirmKind?.let { kind ->
        val bom = preview
        val hub = hubPreview
        AlertDialog(
            onDismissRequest = { if (!busy) confirmKind = null },
            title = { Text(if (kind == "bom") "确认批量出库" else "确认 Component Hub 迁移") },
            text = {
                if (kind == "bom" && bom != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.bom_confirm_summary, bom.parsed.projectName, bom.parsed.productionSets, bom.lines.size, excludedKeys.size, bom.lines.sumOf { it.requirement.requiredQuantity.toLong() }))
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(bom.lines, key = { it.requirement.identity.canonicalKey }) { line ->
                                Text(
                                    "${line.componentSku ?: "未匹配"} × ${line.requirement.requiredQuantity}" +
                                        line.allocationPlan.joinToString(prefix = "（", postfix = "）") { "${it.locationId} ${it.quantity}" },
                                )
                            }
                        }
                    }
                } else {
                    Text("将导入 ${hub?.components?.size ?: 0} 项，跳过 ${hub?.skippedDuplicateCount ?: 0} 项。")
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true
                    if (kind == "bom" && bom != null) {
                        viewModel.commitBomRelease(bom, releaseId, batchId) { result ->
                            busy = false
                            confirmKind = null
                            message = result.message
                            messageIsError = result.outcome == com.componentvault.android.data.bom.BomReleaseOutcome.REJECTED
                            releaseApplied = result.outcome != com.componentvault.android.data.bom.BomReleaseOutcome.REJECTED
                        }
                    } else if (hub != null) {
                        viewModel.importComponentHub(hub) { result ->
                            busy = false
                            confirmKind = null
                            message = result.message
                            messageIsError = result.outcome == com.componentvault.android.data.bom.ComponentHubImportOutcome.REJECTED
                        }
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmKind = null }) { Text("取消") } },
        )
    }
    presetToSave?.let { current ->
        val replacesExisting = presets.any { it.name.equals(current.parsed.projectName, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { presetToSave = null },
            title = { Text(stringResource(R.string.bom_save_preset)) },
            text = { Text(stringResource(if (replacesExisting) R.string.bom_preset_overwrite_notice else R.string.bom_preset_save_notice, current.parsed.projectName)) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val saved = presetStore.save(BomPreset(
                            id = activePreset?.id ?: UUID.randomUUID().toString(),
                            name = current.parsed.projectName,
                            parsed = current.parsed,
                            selections = selections,
                            excludedKeys = excludedKeys,
                        ))
                        saved to presetStore.list()
                    }.onSuccess { (saved, updated) ->
                        if (activePreset != null) activePreset = saved
                        presets = updated
                        message = context.getString(R.string.bom_preset_saved)
                        messageIsError = false
                    }.onFailure {
                        message = it.message ?: context.getString(R.string.bom_preset_save_failed)
                        messageIsError = true
                    }
                    presetToSave = null
                }) { Text(stringResource(R.string.bom_save_preset)) }
            },
            dismissButton = { TextButton(onClick = { presetToSave = null }) { Text("取消") } },
        )
    }
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text(stringResource(R.string.bom_delete_preset_title)) },
            text = { Text(stringResource(R.string.bom_delete_preset_message, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        presetStore.delete(preset.id)
                        presetStore.list()
                    }.onSuccess {
                        presets = it
                        message = context.getString(R.string.bom_preset_deleted)
                        messageIsError = false
                    }.onFailure {
                        message = it.message ?: context.getString(R.string.bom_preset_delete_failed)
                        messageIsError = true
                    }
                    presetToDelete = null
                }) { Text(stringResource(R.string.bom_delete)) }
            },
            dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text("取消") } },
        )
    }
}

private fun com.componentvault.android.data.bom.InventoryMatchCandidate.matchingLabel(): String =
    listOfNotNull(
        sku,
        model?.takeIf(String::isNotBlank),
        packageName?.takeIf(String::isNotBlank),
        displayName?.takeIf(String::isNotBlank),
    ).distinct().joinToString(" · ")

@Composable
private fun BomColumnMappingEditor(
    inspection: BomTableInspection,
    mapping: BomColumnMapping,
    onChange: (BomColumnMapping) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.bom_mapping_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            MappingRow(stringResource(R.string.bom_mapping_part_number), inspection, mapping.sku, true) { onChange(mapping.copy(sku = it)) }
            MappingRow(stringResource(R.string.bom_mapping_model), inspection, mapping.model, true) { onChange(mapping.copy(model = it)) }
            MappingRow(stringResource(R.string.bom_mapping_quantity), inspection, mapping.quantity, false) { onChange(mapping.copy(quantity = it)) }
            MappingRow(stringResource(R.string.bom_mapping_package), inspection, mapping.packageName, true) { onChange(mapping.copy(packageName = it)) }
            MappingRow(stringResource(R.string.bom_mapping_name), inspection, mapping.name, true) { onChange(mapping.copy(name = it)) }
            MappingRow(stringResource(R.string.bom_mapping_reference), inspection, mapping.reference, true) { onChange(mapping.copy(reference = it)) }
            if (mapping.quantity == null || mapping.sku == null && mapping.model == null && mapping.name == null) {
                Text(stringResource(R.string.bom_mapping_missing_fields), color = MaterialTheme.colorScheme.error)
            }
    }
}

@Composable
private fun MappingRow(label: String, inspection: BomTableInspection, selected: Int?, optional: Boolean, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val headers = inspection.headers
    val unnamed = stringResource(R.string.bom_mapping_unnamed)
    val unused = stringResource(R.string.bom_mapping_unused)
    fun option(index: Int): String {
        val sample = inspection.sampleRows.firstNotNullOfOrNull { row -> row.getOrNull(index)?.takeIf(String::isNotBlank) }
        return "${index + 1}. ${headers[index].ifBlank { unnamed }}" +
            (sample?.let { "（${it.take(28)}）" } ?: "")
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Column {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let(::option) ?: unused)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (optional) DropdownMenuItem(text = { Text(unused) }, onClick = { onSelect(null); expanded = false })
                headers.indices.forEach { index ->
                    DropdownMenuItem(text = { Text(option(index)) }, onClick = { onSelect(index); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ImportTaskCard(title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bom_task_open))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (isError) error(message)
        },
    )
}
