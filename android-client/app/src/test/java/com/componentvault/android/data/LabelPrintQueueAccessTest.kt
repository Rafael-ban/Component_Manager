package com.componentvault.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LabelPrintQueueAccessTest {
    @Test fun oldScreenCleanupCannotRestoreAQueueClearedByTheNewScreen() = runBlocking {
        val storage = LabelPrintQueueAccess()
        var saved = "Sending"
        val oldScreen = storage.claim()
        val newScreen = storage.claim()
        storage.access(newScreen) { saved = "empty" }
        assertFailsWith<CancellationException> {
            storage.access(oldScreen) { saved = "Uncertain" }
        }
        assertEquals("empty", storage.access(newScreen) { saved })
    }
}
