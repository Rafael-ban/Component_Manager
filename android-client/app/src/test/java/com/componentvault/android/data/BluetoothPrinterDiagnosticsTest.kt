package com.componentvault.android.data

import android.app.Application
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BluetoothPrinterDiagnosticsTest {
    @Test
    fun permissionsOnAndroid11AndEarlierUseFineLocationOnly() {
        assertEquals(arrayOf("android.permission.ACCESS_FINE_LOCATION").toList(), BluetoothPrinterDiagnostics.permissions().toList())
    }

    @Test
    @Config(sdk = [31])
    fun permissionsOnAndroid12AndLaterUseBluetoothScanAndConnect() {
        assertEquals(
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            BluetoothPrinterDiagnostics.permissions().toList(),
        )
    }

    @Test
    fun reportHeaderDoesNotContainDeviceIdentityOrLabelContent() {
        val report = buildPrinterProbeReportHeader(
            sdk = Build.VERSION.SDK_INT,
            type = 3,
            paired = true,
            advertisedServices = listOf("000018f0-0000-1000-8000-00805f9b34fb"),
        )

        assertTrue(report.contains("advertised_service=000018f0-0000-1000-8000-00805f9b34fb"))
        assertFalse(report.contains("Secret Printer"))
        assertFalse(report.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(report.contains("label-content"))
    }

    @Test
    fun scanWithoutUsableBluetoothDoesNotClaimDevicesOrSuccess() {
        val diagnostics = BluetoothPrinterDiagnostics(RuntimeEnvironment.getApplication())

        diagnostics.scan()

        assertTrue(diagnostics.candidates.isEmpty())
        assertTrue(
            diagnostics.status == PrinterProbeStatus.Unsupported ||
                diagnostics.status == PrinterProbeStatus.BluetoothOff,
        )
        assertFalse(diagnostics.report.contains("success=true"))
        diagnostics.stop()
    }
}
