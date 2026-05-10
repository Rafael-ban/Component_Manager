package com.componentvault.android.data.ocr

import android.graphics.Bitmap
import com.componentvault.android.model.OcrEngineMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class MlKitOcrEngine : OcrEngine {
    override val resolvedMode: OcrEngineMode = OcrEngineMode.MlKit
    override val engineLabel: String = "ML Kit Chinese OCR"

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        return suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build(),
            )
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.toOcrResult())
                    }
                }
                .addOnFailureListener { throwable ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
                .addOnCompleteListener {
                    recognizer.close()
                }

            continuation.invokeOnCancellation {
                recognizer.close()
            }
        }
    }

    private fun Text.toOcrResult(): OcrResult {
        val sortedBlocks = textBlocks
            .map { block ->
                val sortedLines = block.lines
                    .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
                    .map { line ->
                        OcrTextLine(
                            text = line.text.trim(),
                            bounds = line.boundingBox?.toOcrTextBounds(),
                        )
                    }
                    .filter { it.text.isNotBlank() }

                OcrTextBlock(
                    text = sortedLines.joinToString(separator = "\n", transform = OcrTextLine::text),
                    lines = sortedLines,
                    bounds = block.boundingBox?.toOcrTextBounds(),
                )
            }
            .filter { it.lines.isNotEmpty() }
            .sortedWith(compareBy({ it.bounds?.top ?: Int.MAX_VALUE }, { it.bounds?.left ?: Int.MAX_VALUE }))

        return OcrResult(
            engineMode = resolvedMode,
            engineLabel = engineLabel,
            fullText = sortedBlocks.joinToString(separator = "\n", transform = OcrTextBlock::text).trim(),
            blocks = sortedBlocks,
        )
    }
}
