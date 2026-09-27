package com.componentvault.android.data

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerOfficialDetailsTest {
    @Test fun keepsOfficialDescriptionAndParameterValues() {
        val details = parseServerOfficialDetails(JSONObject("""{
            "description":"贴片电容 100nF 50V",
            "parameters":{"容量":"100nF","耐压":"50V","empty":"  "}
        }"""))
        assertEquals("贴片电容 100nF 50V", details.description)
        assertEquals(mapOf("容量" to "100nF", "耐压" to "50V"), details.parameters)
    }

    @Test fun nullableDetailsRemainAbsent() {
        val details = parseServerOfficialDetails(JSONObject("""{"description":null,"parameters":null}"""))
        assertNull(details.description)
        assertEquals(emptyMap(), details.parameters)
    }
}
