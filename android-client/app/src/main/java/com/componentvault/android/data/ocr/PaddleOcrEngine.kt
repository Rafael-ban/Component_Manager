package com.componentvault.android.data.ocr

import android.graphics.Bitmap
import com.componentvault.android.model.OcrEngineMode

internal class PaddleOcrEngine : OcrEngine {
    override val resolvedMode: OcrEngineMode = OcrEngineMode.PaddleExperimental
    override val engineLabel: String = "PaddleOCR experimental"

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        throw OcrEngineUnavailableException(
            "PaddleOCR experimental mode is not bundled in this build yet.",
        )
    }
}
