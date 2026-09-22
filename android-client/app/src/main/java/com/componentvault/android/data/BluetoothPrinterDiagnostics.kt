package com.componentvault.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
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
import java.io.ByteArrayOutputStream

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
    @Volatile private var sppSession: SppSession? = null
    @Volatile private var generation = 0

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

    fun inspectM1Spp(candidate: PrinterDeviceCandidate) {
        stop()
        val currentGeneration = generation
        report = buildString {
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("transport=spp")
            appendLine("probe=m1_model_status")
            appendLine("print_tested=false")
            appendLine("paired=${candidate.paired}")
            appendLine("device_type=${candidate.type}")
        }
        if (!candidate.paired || candidate.type !in setOf(
                BluetoothDevice.DEVICE_TYPE_CLASSIC,
                BluetoothDevice.DEVICE_TYPE_DUAL,
            )
        ) {
            report += "error=unsupported_device\n"
            status = PrinterProbeStatus.Unsupported
            return
        }
        try {
            if (adapter?.isEnabled != true) { status = PrinterProbeStatus.BluetoothOff; return }
            status = PrinterProbeStatus.Connecting
            val session = SppSession(currentGeneration)
            sppSession = session
            Thread({ runM1SppProbe(candidate, session) }, "m1-spp-probe").start()
        } catch (_: SecurityException) {
            permissionDenied()
        } catch (_: RuntimeException) {
            finish(PrinterProbeStatus.ConnectionFailed, "error=worker_start")
        }
    }

    private fun runM1SppProbe(candidate: PrinterDeviceCandidate, session: SppSession) {
        try {
            val connectedSocket = candidate.device.createInsecureRfcommSocketToServiceRecord(M1SppProtocol.sppUuid)
            if (!session.sockets.attach(connectedSocket) || !isCurrentSpp(session)) {
                session.sockets.cancel()
                return
            }
            runCatching { adapter?.cancelDiscovery() }
            scheduleSppDeadline(session, CONNECT_TIMEOUT_MS, "connect")
            connectedSocket.connect()
            cancelSppDeadline(session)
            if (!isCurrentSpp(session)) return
            postSpp(session) { report += "stage=connected\n" }

            val output = connectedSocket.outputStream
            val input = connectedSocket.inputStream
            scheduleSppDeadline(session, MODEL_STAGE_TIMEOUT_MS, "model_query")
            output.write(M1SppProtocol.queryModel)
            output.flush()
            var modelReply = readAvailable(input, MODEL_QUERY_TIMEOUT_MS, session)
            if (modelReply.isEmpty() && isCurrentSpp(session)) {
                output.write(M1SppProtocol.queryModelFallback)
                output.flush()
                modelReply = readAvailable(input, FALLBACK_QUERY_TIMEOUT_MS, session)
            }
            cancelSppDeadline(session)
            val modelResult = M1SppProtocol.parseModelReply(modelReply)
            postSpp(session) {
                report += "query_model=${modelResult.name.lowercase()}\n"
                report += "model_reply_bytes=${modelReply.size}\n"
                if (modelResult == M1SppProtocol.ModelResult.Matched) report += "model=M1\n"
            }
            if (modelResult != M1SppProtocol.ModelResult.Matched) {
                val detail = if (modelResult == M1SppProtocol.ModelResult.NoResponse) {
                    "error=model_no_response"
                } else "error=model_unrecognized_response"
                val terminalStatus = if (modelResult == M1SppProtocol.ModelResult.NoResponse) {
                    PrinterProbeStatus.TimedOut
                } else PrinterProbeStatus.ConnectionFailed
                postSpp(session) { finish(terminalStatus, detail) }
                return
            }

            scheduleSppDeadline(session, STATUS_STAGE_TIMEOUT_MS, "status_query")
            output.write(M1SppProtocol.queryStatus)
            output.flush()
            val statusReply = readAvailable(input, STATUS_QUERY_TIMEOUT_MS, session)
            cancelSppDeadline(session)
            val statusResult = M1SppProtocol.classifyStatusReply(statusReply)
            postSpp(session) {
                report += "status_reply_bytes=${statusReply.size}\n"
                when (statusResult) {
                    M1SppProtocol.StatusResult.NoResponse ->
                        finish(PrinterProbeStatus.TimedOut, "status_query=no_response")
                    M1SppProtocol.StatusResult.Invalid ->
                        finish(PrinterProbeStatus.ConnectionFailed, "status_query=invalid")
                    is M1SppProtocol.StatusResult.Received -> {
                        report += "status_result=received\n"
                        report += "status_code=${statusResult.code}\n"
                        report += "status_source=${statusResult.source.name.lowercase()}\n"
                        M1SppProtocol.statusBits(statusResult.code).forEach { report += "status_bit=$it\n" }
                        finish(PrinterProbeStatus.Complete, "status_query=received")
                    }
                }
            }
        } catch (_: SecurityException) {
            postSpp(session) { permissionDenied() }
        } catch (_: Exception) {
            postSpp(session) { finish(PrinterProbeStatus.ConnectionFailed, "error=connection_or_io") }
        } finally {
            cancelSppDeadline(session)
            session.sockets.closeCurrent()
        }
    }

    private fun readAvailable(
        input: java.io.InputStream,
        timeoutMs: Long,
        session: SppSession,
    ): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        var quietSince = 0L
        while (System.nanoTime() < deadline && isCurrentSpp(session)) {
            val available = input.available()
            if (available > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, available))
                if (count < 0) break
                if (result.size() < MAX_REPLY_BYTES) result.write(buffer, 0, minOf(count, MAX_REPLY_BYTES - result.size()))
                quietSince = System.nanoTime()
            } else {
                if (result.size() > 0 && System.nanoTime() - quietSince >= READ_QUIET_MS * 1_000_000) break
                Thread.sleep(READ_POLL_MS)
            }
        }
        return result.toByteArray()
    }

    private fun scheduleSppDeadline(session: SppSession, delayMs: Long, stage: String) {
        cancelSppDeadline(session)
        lateinit var deadline: Runnable
        deadline = Runnable {
            if (session.deadline === deadline && isCurrentSpp(session)) {
                session.deadline = null
                session.sockets.closeCurrent()
                finish(PrinterProbeStatus.TimedOut, "error=${stage}_timeout")
            }
        }
        session.deadline = deadline
        handler.postDelayed(deadline, delayMs)
    }

    private fun cancelSppDeadline(session: SppSession) {
        session.deadline?.let(handler::removeCallbacks)
        session.deadline = null
    }

    private fun isCurrentSpp(session: SppSession): Boolean =
        sessionMayDeliver(session.generation, generation, sppSession === session)

    private fun postSpp(session: SppSession, block: () -> Unit) {
        handler.post { if (isCurrentSpp(session)) block() }
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
        val session = sppSession
        sppSession = null
        session?.let(::cancelSppDeadline)
        session?.sockets?.cancel()
        if (busy) status = PrinterProbeStatus.Stopped
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MODEL_QUERY_TIMEOUT_MS = 5_000L
        private const val FALLBACK_QUERY_TIMEOUT_MS = 1_000L
        private const val STATUS_QUERY_TIMEOUT_MS = 3_000L
        private const val MODEL_STAGE_TIMEOUT_MS = 7_000L
        private const val STATUS_STAGE_TIMEOUT_MS = 4_000L
        private const val READ_QUIET_MS = 150L
        private const val READ_POLL_MS = 20L
        private const val MAX_REPLY_BYTES = 512

        private class SppSession(val generation: Int) {
            val sockets = CancelableResourceSlot<BluetoothSocket> { socket -> runCatching { socket.close() } }
            @Volatile var deadline: Runnable? = null
        }

        fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
