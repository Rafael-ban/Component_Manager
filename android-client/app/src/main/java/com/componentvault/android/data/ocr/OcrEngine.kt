package com.componentvault.android.data.ocr

import android.graphics.Bitmap
import com.componentvault.android.model.OcrEngineMode

internal interface OcrEngine {
    val resolvedMode: OcrEngineMode
    val engineLabel: String

    suspend fun recognize(bitmap: Bitmap): OcrResult
}

internal class OcrEngineUnavailableException(
    message: String,
) : IllegalStateException(message)

internal object OcrEngineFactory {
    fun create(preferredMode: OcrEngineMode): OcrEngine {
        return when (preferredMode) {
            OcrEngineMode.Auto,
            OcrEngineMode.MlKit,
            -> MlKitOcrEngine()

            // Older preferences may still request the never-bundled experimental engine.
            OcrEngineMode.PaddleExperimental -> MlKitOcrEngine()
        }
    }
}
