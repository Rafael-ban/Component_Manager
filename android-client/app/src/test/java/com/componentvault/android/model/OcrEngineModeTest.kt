package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrEngineModeTest {
    @Test fun unavailableLegacyEngineFallsBackToWorkingLocalDefault() {
        assertEquals(OcrEngineMode.Auto, OcrEngineMode.fromStorageValue("paddle_experimental"))
        assertEquals(OcrEngineMode.MlKit, OcrEngineMode.fromStorageValue("MLKIT"))
    }
}
