package com.componentvault.android.ui.screen

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupFailureDetailsTest {
    @Test
    fun startupReportKeepsErrorTypesButExcludesMessagesAndFilePaths() {
        val cause = IllegalArgumentException("private QR and order contents")
        cause.stackTrace = arrayOf(StackTraceElement(
            "com.componentvault.android.data.InventoryDatabaseHelper",
            "onUpgrade", "/private/user/inventory.db", 42,
        ))
        val report = startupFailureDetails(IllegalStateException("secret token", cause))
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("InventoryDatabaseHelper.onUpgrade:42"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("secret token"))
    }
}
