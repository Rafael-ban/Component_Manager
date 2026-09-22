package com.componentvault.android.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.componentvault.android.BuildConfig
import com.componentvault.android.R
import com.componentvault.android.data.BluetoothPrinterDiagnostics
import com.componentvault.android.data.PrinterProbeStatus
import com.componentvault.android.data.PrinterDeviceCandidate
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.M1TestLabelRenderer
import com.componentvault.android.data.M1TestPaperProfile
import com.componentvault.android.data.M1TestPrintResult

@Composable
internal fun BluetoothPrinterDiagnosticsDialog(
    onDismiss: () -> Unit,
    labelTemplate: ComponentLabelTemplate = ComponentLabelTemplate.default,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val probe = remember { BluetoothPrinterDiagnostics(context) }
    val paperPreferences = remember { context.getSharedPreferences("m1_test_paper", android.content.Context.MODE_PRIVATE) }
    var paperProfile by remember(labelTemplate.id) { mutableStateOf(M1TestPaperProfile.load(paperPreferences, labelTemplate)) }
    var showPaperSettings by remember { mutableStateOf(false) }
    var m1Mode by remember { mutableStateOf(false) }
    var lastRunWasM1 by remember { mutableStateOf(false) }
    var printCandidate by remember { mutableStateOf<PrinterDeviceCandidate?>(null) }
    val listState = rememberLazyListState()
    var showPrintFailure by remember { mutableStateOf(false) }
    LaunchedEffect(probe.status, probe.printResult) {
        if (probe.status == PrinterProbeStatus.Connecting || probe.status == PrinterProbeStatus.Printing ||
            probe.printResult != M1TestPrintResult.None) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(probe.printResult) {
        showPrintFailure = probe.printResult in setOf(
            M1TestPrintResult.Rejected, M1TestPrintResult.Partial, M1TestPrintResult.Interrupted,
            M1TestPrintResult.SentConnectionLost,
        )
    }
    DisposableEffect(probe, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) probe.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            probe.stop()
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (BluetoothPrinterDiagnostics.permissions().all { grants[it] == true }) probe.scan()
        else probe.permissionDenied()
    }
    val startScan = {
        lastRunWasM1 = false
        val permissions = BluetoothPrinterDiagnostics.permissions()
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) probe.scan()
        else permissionRequest.launch(permissions)
    }
    val statusText = stringResource(when (probe.status) {
        PrinterProbeStatus.Idle -> R.string.printer_probe_idle
        PrinterProbeStatus.Scanning -> R.string.printer_probe_scanning
        PrinterProbeStatus.ScanComplete -> R.string.printer_probe_scan_complete
        PrinterProbeStatus.Connecting -> if (lastRunWasM1) R.string.printer_probe_m1_connecting else R.string.printer_probe_connecting
        PrinterProbeStatus.Printing -> R.string.printer_m1_printing
        PrinterProbeStatus.Complete -> if (lastRunWasM1) R.string.printer_probe_m1_complete else R.string.printer_probe_complete
        PrinterProbeStatus.BluetoothOff -> R.string.printer_probe_bluetooth_off
        PrinterProbeStatus.Unsupported -> R.string.printer_probe_unsupported
        PrinterProbeStatus.PermissionRequired -> R.string.printer_probe_permission_required
        PrinterProbeStatus.ScanFailed -> R.string.printer_probe_scan_failed
        PrinterProbeStatus.ConnectionFailed -> if (lastRunWasM1) R.string.printer_probe_m1_failed else R.string.printer_probe_connection_failed
        PrinterProbeStatus.TimedOut -> if (lastRunWasM1) R.string.printer_probe_m1_timeout else R.string.printer_probe_timeout
        PrinterProbeStatus.Stopped -> R.string.printer_probe_stopped
    })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.printer_probe_title)) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !m1Mode,
                            onClick = { m1Mode = false },
                            enabled = !probe.busy,
                            label = { Text(stringResource(R.string.printer_probe_service_mode)) },
                        )
                        FilterChip(
                            selected = m1Mode,
                            onClick = { m1Mode = true },
                            enabled = !probe.busy,
                            label = { Text(stringResource(R.string.printer_probe_m1_mode)) },
                        )
                    }
                }
                item { Text(stringResource(if (m1Mode) R.string.printer_probe_m1_intro else R.string.printer_probe_intro)) }
                if (m1Mode) {
                    item {
                        OutlinedButton(onClick = { showPaperSettings = true }, enabled = !probe.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.printer_m1_paper_summary, paperProfile.widthMm.toString(), paperProfile.heightMm.toString()))
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when (probe.printResult) {
                            M1TestPrintResult.SentUnconfirmed -> stringResource(R.string.printer_m1_sent)
                            M1TestPrintResult.SentConnectionLost -> stringResource(R.string.printer_m1_sent_connection_lost)
                            M1TestPrintResult.Partial, M1TestPrintResult.Interrupted -> stringResource(R.string.printer_m1_interrupted)
                            M1TestPrintResult.Rejected -> stringResource(R.string.printer_m1_rejected)
                            M1TestPrintResult.None -> statusText
                        }, style = MaterialTheme.typography.titleSmall)
                        if (probe.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { if (probe.busy) probe.stop() else startScan() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (probe.busy) R.string.printer_probe_stop else R.string.printer_probe_scan))
                        }
                        if (probe.status == PrinterProbeStatus.BluetoothOff || probe.status == PrinterProbeStatus.PermissionRequired) {
                            TextButton(onClick = {
                                val intent = if (probe.status == PrinterProbeStatus.PermissionRequired) {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                } else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                runCatching { context.startActivity(intent) }
                            }) { Text(stringResource(R.string.printer_probe_settings)) }
                        }
                        if (m1Mode && !probe.busy) {
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            }) { Text(stringResource(R.string.printer_probe_pair_settings)) }
                        }
                    }
                }
                if (probe.status == PrinterProbeStatus.ScanComplete && probe.candidates.isEmpty()) {
                    item { Text(stringResource(R.string.printer_probe_empty)) }
                }
                items(probe.candidates, key = { it.address }) { candidate ->
                    val supportsSpp = candidate.paired &&
                        (candidate.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || candidate.type == BluetoothDevice.DEVICE_TYPE_DUAL)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            lastRunWasM1 = m1Mode
                            if (m1Mode) probe.inspectM1Spp(candidate) else probe.inspect(candidate)
                        },
                        enabled = !probe.busy && (!m1Mode || supportsSpp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(candidate.name ?: stringResource(R.string.printer_probe_unnamed))
                            Text("…${candidate.address.takeLast(5)} · " + stringResource(if (candidate.paired) R.string.printer_probe_paired else R.string.printer_probe_nearby), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(when {
                                !m1Mode -> R.string.printer_probe_inspect
                                supportsSpp -> R.string.printer_probe_m1_inspect
                                else -> R.string.printer_probe_m1_pair_required
                            }), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (m1Mode && supportsSpp) {
                        OutlinedButton(
                            onClick = { printCandidate = candidate },
                            enabled = !probe.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.printer_m1_test_one)) }
                    }
                    }
                }
                if (probe.report.isNotBlank()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.printer_probe_report_hint))
                            Text(probe.report, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !probe.busy, onClick = {
                                clipboard.setText(AnnotatedString("Component Vault ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nstatus=${probe.status}\n${probe.report}"))
                            }) { Text(stringResource(R.string.printer_probe_copy)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.printer_probe_close)) } },
    )
    printCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { printCandidate = null },
            title = { Text(stringResource(R.string.printer_m1_test_one)) },
            text = { Text(stringResource(R.string.printer_m1_confirm_paper, paperProfile.widthMm.toString(), paperProfile.heightMm.toString())) },
            confirmButton = {
                TextButton(onClick = {
                    printCandidate = null
                    lastRunWasM1 = true
                    val bitmap = M1TestLabelRenderer.render(paperProfile)
                    try { probe.printM1Test(candidate, bitmap, paperProfile) } finally { bitmap.recycle() }
                }) { Text(stringResource(R.string.printer_m1_print_now)) }
            },
            dismissButton = { TextButton(onClick = { printCandidate = null }) { Text(stringResource(R.string.printer_probe_close)) } },
        )
    }
    if (showPaperSettings) {
        M1TestPaperSettingsDialog(
            current = paperProfile,
            onSave = {
                paperProfile = it
                it.save(paperPreferences)
                showPaperSettings = false
            },
            onDismiss = { showPaperSettings = false },
        )
    }
    if (showPrintFailure) {
        AlertDialog(
            onDismissRequest = { showPrintFailure = false },
            title = { Text(stringResource(R.string.printer_m1_failure_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(
                        when (probe.printResult) {
                            M1TestPrintResult.Rejected -> R.string.printer_m1_rejected
                            M1TestPrintResult.SentConnectionLost -> R.string.printer_m1_sent_connection_lost
                            else -> R.string.printer_m1_interrupted
                        },
                    )) }
                    item { Text(probe.report, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrintFailure = false }) { Text(stringResource(R.string.printer_probe_close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("Component Vault ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nstatus=${probe.status}\n${probe.report}"))
                }) { Text(stringResource(R.string.printer_probe_copy)) }
            },
        )
    }
}
