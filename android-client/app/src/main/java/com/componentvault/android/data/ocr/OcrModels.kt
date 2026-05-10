package com.componentvault.android.data.ocr

import android.graphics.Rect
import com.componentvault.android.model.OcrEngineMode

internal data class OcrTextBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class OcrTextLine(
    val text: String,
    val bounds: OcrTextBounds? = null,
)

internal data class OcrTextBlock(
    val text: String,
    val lines: List<OcrTextLine>,
    val bounds: OcrTextBounds? = null,
)

internal data class OcrResult(
    val engineMode: OcrEngineMode,
    val engineLabel: String,
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val lines: List<OcrTextLine>
        get() = blocks.flatMap(OcrTextBlock::lines)
}

internal fun OcrResult.normalizedText(): String {
    return lines
        .map(OcrTextLine::text)
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n")
}

internal fun Rect.toOcrTextBounds(): OcrTextBounds {
    return OcrTextBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
}
