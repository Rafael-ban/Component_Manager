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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.componentvault.android.data.bom.ComponentHubParseResult
import com.componentvault.android.data.bom.LocalImportLimits
import java.io.ByteArrayOutputStream
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
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var productionSets by remember { mutableStateOf("1") }
    var sheets by remember { mutableStateOf<List<BomSheet>>(emptyList()) }
    var selectedSheet by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BomReleasePreview?>(null) }
    var hubPreview by remember { mutableStateOf<ComponentHubParseResult?>(null) }
    var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var releaseId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var batchId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var releaseApplied by rememberSaveable { mutableStateOf(false) }
    var confirmKind by remember { mutableStateOf<String?>(null) }
    var selectedMode by rememberSaveable { mutableStateOf(initialMode) }

    fun resetFile() {
        fileBytes = null
        fileName = ""
        sheets = emptyList()
        selectedSheet = null
        preview = null
        hubPreview = null
        selections = emptyMap()
        message = ""
        messageIsError = false
        releaseApplied = false
    }

    fun requestPreview() {
        val bytes = fileBytes ?: return
        if (selectedMode == BomImportMode.Migration) {
            busy = true
            viewModel.previewComponentHub(bytes, skipDuplicates = false) { result ->
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
        busy = true
        viewModel.previewBom(
            bytes = bytes,
            fileName = fileName,
            projectName = projectName,
            productionSets = sets,
            sheetName = selectedSheet,
            selections = selections,
        ) { result ->
            busy = false
            result.onSuccess { preview = it; message = "预览完成，共 ${it.lines.size} 项。"; messageIsError = false }
                .onFailure { message = it.message ?: "BOM 解析失败。"; messageIsError = true }
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
            fileBytes = bytes
            preview = null
            hubPreview = null
            selections = emptyMap()
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
                    }.onFailure { message = it.message ?: "无法读取工作表。"; messageIsError = true }
                }
            } else {
                busy = false
                sheets = emptyList()
                selectedSheet = null
            }
        }.onFailure { busy = false; message = it.message ?: "无法读取文件。"; messageIsError = true }
        }
    }

    SecondaryPageScaffold(
        title = stringResource(R.string.bom_import_title),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding).imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BomImportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { if (fileBytes == null && !busy) selectedMode = mode },
                                enabled = fileBytes == null && !busy,
                                label = { Text(stringResource(if (mode == BomImportMode.Bom) R.string.bom_task_bom else R.string.bom_task_migration)) },
                            )
                        }
                    }
                }
                if (fileBytes == null) item {
                    Text(
                        stringResource(if (selectedMode == BomImportMode.Bom) R.string.bom_task_bom_description else R.string.bom_task_migration_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    ) { Text(if (fileName.isBlank()) stringResource(R.string.bom_choose_file) else fileName) }
                }
                if (selectedMode == BomImportMode.Bom && fileBytes != null) {
                    item {
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            label = { Text(stringResource(R.string.bom_project_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !releaseApplied,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = productionSets,
                            onValueChange = { productionSets = it },
                            label = { Text(stringResource(R.string.bom_production_sets)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !releaseApplied,
                        )
                    }
                }
                if (sheets.size > 1) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sheets.filterNot { it.hidden }, key = { it.name }) { sheet ->
                            FilterChip(
                                selected = selectedSheet == sheet.name,
                                enabled = !busy && !releaseApplied,
                                onClick = { selectedSheet = sheet.name; selections = emptyMap() },
                                label = { Text(sheet.name) },
                            )
                        }
                    }
                }
                if (fileBytes != null) item {
                    Button(
                        onClick = ::requestPreview,
                        enabled = !busy && !releaseApplied,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.bom_generate_preview)) }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (message.isNotBlank()) item { StatusMessage(message, messageIsError) }
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current.lines, key = { it.requirement.identity.canonicalKey }) { line ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                            Text(line.requirement.sku ?: line.requirement.model ?: "未识别物料")
                            Text("需求 ${line.requirement.requiredQuantity} / 库存 ${line.availableQuantity}")
                            if (line.componentId == null) {
                                Text(if (line.candidates.isEmpty()) "未匹配" else "请选择匹配项")
                                line.candidates.forEach { candidate ->
                                    TextButton(enabled = !busy, onClick = {
                                        selections = selections +
                                            (line.requirement.identity.canonicalKey to candidate.inventoryId)
                                        requestPreview()
                                    }) { Text("${candidate.sku} ${candidate.displayName.orEmpty()}") }
                                }
                            } else {
                                Text("匹配：${line.componentSku}")
                                line.allocationPlan.forEach { allocation ->
                                    Text("库位 ${allocation.locationId}：扣减 ${allocation.quantity}")
                                }
                            }
                        }
                    }
                }
            }
            Button(
                enabled = current.canCommit && !busy && !releaseApplied,
                onClick = { confirmKind = "bom" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("核对并批量出库") }
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
                Text(
                    if (kind == "bom" && bom != null) {
                        "项目：${bom.parsed.projectName}\n生产套数：${bom.parsed.productionSets}\n总扣减：${bom.lines.sumOf { it.requirement.requiredQuantity.toLong() }}"
                    } else {
                        "将导入 ${hub?.components?.size ?: 0} 项，跳过 ${hub?.skippedDuplicateCount ?: 0} 项。"
                    },
                )
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
                            messageIsError = !result.isSuccess
                        }
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmKind = null }) { Text("取消") } },
        )
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
