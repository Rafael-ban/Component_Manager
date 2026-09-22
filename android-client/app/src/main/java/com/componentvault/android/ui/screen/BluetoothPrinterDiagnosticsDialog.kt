package com.componentvault.android.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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

@Composable
internal fun BluetoothPrinterDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val probe = remember { BluetoothPrinterDiagnostics(context) }
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
        val permissions = BluetoothPrinterDiagnostics.permissions()
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) probe.scan()
        else permissionRequest.launch(permissions)
    }
    val statusText = stringResource(when (probe.status) {
        PrinterProbeStatus.Idle -> R.string.printer_probe_idle
        PrinterProbeStatus.Scanning -> R.string.printer_probe_scanning
        PrinterProbeStatus.ScanComplete -> R.string.printer_probe_scan_complete
        PrinterProbeStatus.Connecting -> R.string.printer_probe_connecting
        PrinterProbeStatus.Complete -> R.string.printer_probe_complete
        PrinterProbeStatus.BluetoothOff -> R.string.printer_probe_bluetooth_off
        PrinterProbeStatus.Unsupported -> R.string.printer_probe_unsupported
        PrinterProbeStatus.PermissionRequired -> R.string.printer_probe_permission_required
        PrinterProbeStatus.ScanFailed -> R.string.printer_probe_scan_failed
        PrinterProbeStatus.ConnectionFailed -> R.string.printer_probe_connection_failed
        PrinterProbeStatus.TimedOut -> R.string.printer_probe_timeout
        PrinterProbeStatus.Stopped -> R.string.printer_probe_stopped
    })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.printer_probe_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.printer_probe_intro)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(statusText, style = MaterialTheme.typography.titleSmall)
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
                    }
                }
                if (probe.status == PrinterProbeStatus.ScanComplete && probe.candidates.isEmpty()) {
                    item { Text(stringResource(R.string.printer_probe_empty)) }
                }
                items(probe.candidates, key = { it.address }) { candidate ->
                    OutlinedButton(
                        onClick = { probe.inspect(candidate) },
                        enabled = probe.status != PrinterProbeStatus.Connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(candidate.name ?: stringResource(R.string.printer_probe_unnamed))
                            Text("…${candidate.address.takeLast(5)} · " + stringResource(if (candidate.paired) R.string.printer_probe_paired else R.string.printer_probe_nearby), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.printer_probe_inspect), style = MaterialTheme.typography.labelSmall)
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
}
