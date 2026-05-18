package com.componentvault.android.data.ocr

import android.graphics.Bitmap
import com.componentvault.android.model.OcrEngineMode

internal class PaddleOcrEngine : OcrEngine {
    override val resolvedMode: OcrEngineMode = OcrEngineMode.PaddleExperimental
    override val engineLabel: String = "PaddleOCR 实验模式"

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        throw OcrEngineUnavailableException(
            "当前构建尚未内置 PaddleOCR 实验模式。",
        )
    }
}
