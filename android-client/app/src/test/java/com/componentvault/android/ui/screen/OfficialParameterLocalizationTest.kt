package com.componentvault.android.ui.screen

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialParameterLocalizationTest {
    @Test fun knownTypeValuesTranslateOnlyForChinese() {
        assertEquals("多层陶瓷电容", localizedOfficialParameterValue("type", "MLCC", Locale.SIMPLIFIED_CHINESE))
        assertEquals("MLCC", localizedOfficialParameterValue("type", "MLCC", Locale.ENGLISH))
        assertEquals("Proprietary C0G Blend",
            localizedOfficialParameterValue("type", "Proprietary C0G Blend", Locale.SIMPLIFIED_CHINESE))
        assertEquals("-55°C to 125°C",
            localizedOfficialParameterValue("operating_temperature", "-55°C to 125°C", Locale.SIMPLIFIED_CHINESE))
    }
}
