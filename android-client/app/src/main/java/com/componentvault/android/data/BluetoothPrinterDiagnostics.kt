package com.componentvault.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class PrinterDeviceCandidate(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val type: Int,
    val paired: Boolean,
    val advertisedServices: List<String> = emptyList(),
)

internal enum class PrinterProbeStatus {
    Idle, Scanning, ScanComplete, Connecting, Complete, BluetoothOff, Unsupported,
    PermissionRequired, ScanFailed, ConnectionFailed, TimedOut, Stopped,
}

/** Builds the shareable portion of a probe report without device identity or label data. */
internal fun buildPrinterProbeReportHeader(
    sdk: Int,
    type: Int,
    paired: Boolean,
    advertisedServices: List<String>,
): String = buildString {
    appendLine("android_sdk=$sdk")
    appendLine("device_type=$type (0=unknown, 1=Classic, 2=BLE, 3=dual)")
    appendLine("paired=$paired")
    advertisedServices.forEach { appendLine("advertised_service=$it") }
}

/** A foreground-only transport probe. It never sends a print command or reads characteristic values. */
@SuppressLint("MissingPermission") // The UI requests permissions; revocation is also handled at every entry point.
internal class BluetoothPrinterDiagnostics(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanCallback: ScanCallback? = null
    private var stopScanTask: Runnable? = null
    private var timeoutTask: Runnable? = null
    private var gatt: BluetoothGatt? = null
    private var generation = 0

    var candidates by mutableStateOf<List<PrinterDeviceCandidate>>(emptyList())
        private set
    var status by mutableStateOf(PrinterProbeStatus.Idle)
        private set
    var report by mutableStateOf("")
        private set
    val busy get() = status == PrinterProbeStatus.Scanning || status == PrinterProbeStatus.Connecting

    fun scan() {
        stop()
        candidates = emptyList()
        report = ""
        val currentGeneration = generation
        try {
            val bluetooth = adapter ?: run { status = PrinterProbeStatus.Unsupported; return }
            if (!bluetooth.isEnabled) { status = PrinterProbeStatus.BluetoothOff; return }
            bluetooth.bondedDevices.forEach { addCandidate(it, emptyList()) }
            val scanner = bluetooth.bluetoothLeScanner
            if (scanner == null) { status = PrinterProbeStatus.Unsupported; return }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handler.post {
                        if (generation == currentGeneration && scanCallback === this && status == PrinterProbeStatus.Scanning) {
                            try {
                                addCandidate(result.device, result.scanRecord?.serviceUuids.orEmpty().map { it.toString() })
                            } catch (_: SecurityException) { permissionDenied() }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    handler.post {
                        if (generation == currentGeneration && scanCallback === this) {
                            stopScan()
                            status = PrinterProbeStatus.ScanFailed
                            report = "scan_error=$errorCode"
                        }
                    }
                }
            }
            scanCallback = callback
            status = PrinterProbeStatus.Scanning
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            stopScanTask = Runnable {
                if (generation == currentGeneration && status == PrinterProbeStatus.Scanning) {
                    stopScan()
                    status = PrinterProbeStatus.ScanComplete
                }
            }.also { handler.postDelayed(it, 10_000) }
        } catch (_: SecurityException) {
            permissionDenied()
        } catch (_: IllegalStateException) {
            stop()
            status = PrinterProbeStatus.BluetoothOff
        }
    }

    private fun addCandidate(device: BluetoothDevice, advertised: List<String>) {
        val address = device.address
        val previous = candidates.firstOrNull { it.address == address }
        val candidate = PrinterDeviceCandidate(
            device, device.name, address, device.type, device.bondState == BluetoothDevice.BOND_BONDED,
            (previous?.advertisedServices.orEmpty() + advertised).distinct().sorted(),
        )
        // Keep a bounded, stable list while nearby devices advertise repeatedly.
        candidates = if (previous != null) candidates.map { if (it.address == address) candidate else it }
        else if (candidates.size < 80) candidates + candidate else candidates
    }

    fun inspect(candidate: PrinterDeviceCandidate) {
        stop()
        val currentGeneration = generation
        report = buildPrinterProbeReportHeader(
            sdk = Build.VERSION.SDK_INT,
            type = candidate.type,
            paired = candidate.paired,
            advertisedServices = candidate.advertisedServices,
        )
        try {
            if (adapter?.isEnabled != true) { status = PrinterProbeStatus.BluetoothOff; return }
            candidate.device.uuids.orEmpty().forEach { report += "cached_service=$it\n" }
            if (candidate.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                report += "probe=cached_classic_services_only; print_protocol=unknown\n"
                status = PrinterProbeStatus.Complete
                return
            }
            status = PrinterProbeStatus.Connecting
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(connection: BluetoothGatt, code: Int, newState: Int) {
                    handler.post {
                        if (generation != currentGeneration || gatt !== connection) return@post
                        try {
                            when {
                                code != BluetoothGatt.GATT_SUCCESS -> finish(PrinterProbeStatus.ConnectionFailed, "gatt_error=$code")
                                newState == BluetoothProfile.STATE_CONNECTED -> {
                                    if (!connection.discoverServices()) finish(PrinterProbeStatus.ConnectionFailed, "service_discovery=not_started")
                                }
                                newState == BluetoothProfile.STATE_DISCONNECTED -> finish(PrinterProbeStatus.ConnectionFailed, "connection=disconnected")
                            }
                        } catch (_: SecurityException) { permissionDenied() }
                    }
                }

                override fun onServicesDiscovered(connection: BluetoothGatt, code: Int) {
                    handler.post {
                        if (generation != currentGeneration || gatt !== connection) return@post
                        if (code != BluetoothGatt.GATT_SUCCESS) {
                            finish(PrinterProbeStatus.ConnectionFailed, "service_error=$code")
                            return@post
                        }
                        report += buildString {
                            connection.services.forEach { service ->
                                appendLine("service=${service.uuid}")
                                service.characteristics.forEach { characteristic ->
                                    appendLine("  characteristic=${characteristic.uuid} properties=${characteristic.properties}")
                                }
                            }
                        }
                        finish(PrinterProbeStatus.Complete, "probe=services_only; print_protocol=unknown")
                    }
                }
            }
            gatt = candidate.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) { finish(PrinterProbeStatus.ConnectionFailed, "connection=not_started"); return }
            timeoutTask = Runnable {
                if (generation == currentGeneration) finish(PrinterProbeStatus.TimedOut, "connection=timeout")
            }.also { handler.postDelayed(it, 15_000) }
        } catch (_: SecurityException) {
            permissionDenied()
        } catch (_: IllegalStateException) {
            finish(PrinterProbeStatus.ConnectionFailed, "connection=unavailable")
        }
    }

    fun permissionDenied() {
        stop()
        status = PrinterProbeStatus.PermissionRequired
    }

    private fun finish(result: PrinterProbeStatus, detail: String) {
        report += "$detail\n"
        stop()
        status = result
    }

    private fun stopScan() {
        stopScanTask?.let(handler::removeCallbacks)
        stopScanTask = null
        scanCallback?.let { callback -> runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) } }
        scanCallback = null
    }

    fun stop() {
        generation++
        stopScan()
        timeoutTask?.let(handler::removeCallbacks)
        timeoutTask = null
        val connection = gatt
        gatt = null
        runCatching { connection?.disconnect() }
        runCatching { connection?.close() }
        if (busy) status = PrinterProbeStatus.Stopped
    }

    companion object {
        fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
