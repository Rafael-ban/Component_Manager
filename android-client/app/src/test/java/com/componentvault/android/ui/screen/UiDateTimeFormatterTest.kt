package com.componentvault.android.ui.screen

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UiDateTimeFormatterTest {
    @Test
    fun `utc timestamp is formatted in requested local zone`() {
        assertEquals(
            "2026-09-15 16:36",
            formatShortLocalTimestamp(
                value = "2026-09-15T08:36:00Z",
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun `invalid timestamp is returned unchanged`() {
        assertEquals(
            "legacy timestamp",
            formatShortLocalTimestamp("legacy timestamp", ZoneId.of("UTC")),
        )
    }
}
