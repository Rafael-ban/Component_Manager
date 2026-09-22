package com.componentvault.android.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fixed calibration content; does not expose or print the selected component's data. */
internal object M1TestLabelRenderer {
    const val Dpi = 203
    const val QrContent = "M1-TEST"

    fun supports(template: ComponentLabelTemplate): Boolean =
        template == ComponentLabelTemplate.Qr10x40 || template == ComponentLabelTemplate.Qr30x40

    fun render(template: ComponentLabelTemplate): Bitmap {
        require(supports(template)) { "Choose an existing 40 x 10 or 40 x 30 mm QR template" }
        return render(M1TestPaperProfile.fromTemplate(template))
    }

    fun render(profile: M1TestPaperProfile): Bitmap {
        val width = mmToDots(profile.widthMm)
        val height = mmToDots(profile.heightMm)
        require(width <= MaxPrintHeadDots) { "Raster exceeds the $MaxPrintHeadDots-dot test limit" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(mmToDots(profile.offsetXmm).toFloat(), mmToDots(profile.offsetYmm).toFloat())
        val contentWidth: Int
        val contentHeight: Int
        when (profile.rotationDegrees) {
            0 -> {
                contentWidth = width
                contentHeight = height
            }
            90 -> {
                canvas.translate(width.toFloat(), 0f)
                canvas.rotate(90f)
                contentWidth = height
                contentHeight = width
            }
            180 -> {
                canvas.translate(width.toFloat(), height.toFloat())
                canvas.rotate(180f)
                contentWidth = width
                contentHeight = height
            }
            else -> {
                canvas.translate(0f, height.toFloat())
                canvas.rotate(-90f)
                contentWidth = height
                contentHeight = width
            }
        }
        drawContent(canvas, contentWidth, contentHeight, profile)
        canvas.restore()
        return bitmap
    }

    private fun drawContent(
        canvas: Canvas,
        width: Int,
        height: Int,
        profile: M1TestPaperProfile,
    ) {
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        // One millimetre inset makes clipping and paper alignment visible without edge printing.
        val inset = 8f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(inset, inset, width - inset - 1, height - inset - 1, paint)
        paint.style = Paint.Style.FILL
        // QR encoder includes a four-module quiet zone. Integer module scaling avoids blur.
        val qr = QRCodeWriter().encode(QrContent, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
        val horizontalLayout = width >= height * 1.35f
        val qrBudget = if (horizontalLayout) {
            min(height - 2 * inset.toInt(), (width * 0.42f).roundToInt())
        } else {
            min(width - 2 * inset.toInt(), (height * 0.58f).roundToInt())
        }
        val scale = max(1, min(6, qrBudget / qr.width))
        val qrPixels = qr.width * scale
        val left = if (horizontalLayout) width - inset.toInt() - qrPixels else (width - qrPixels) / 2
        val top = if (horizontalLayout) (height - qrPixels) / 2 else height - inset.toInt() - qrPixels

        val textLeft = if (horizontalLayout) 16f else inset + 4f
        val textRight = if (horizontalLayout) left - 6f else width - inset - 4f
        val maxTextWidth = max(1f, textRight - textLeft)
        val firstBaseline = if (horizontalLayout) {
            max(inset + 10f, height / 2f - 13f)
        } else {
            inset + 12f
        }
        val lineGap = if (horizontalLayout) 12f else min(14f, max(10f, (top - firstBaseline) / 3f))
        drawFittedText(canvas, paint, "M1 TEST", textLeft, firstBaseline, maxTextWidth, 16f)
        drawFittedText(
            canvas,
            paint,
            "W ${formatMm(profile.widthMm)} mm",
            textLeft,
            firstBaseline + lineGap,
            maxTextWidth,
            10f,
        )
        drawFittedText(
            canvas,
            paint,
            "H ${formatMm(profile.heightMm)} mm",
            textLeft,
            firstBaseline + lineGap * 2,
            maxTextWidth,
            10f,
        )
        if (!horizontalLayout) {
            drawFittedText(
                canvas,
                paint,
                "$Dpi dpi",
                textLeft,
                firstBaseline + lineGap * 3,
                maxTextWidth,
                9f,
            )
        }
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            if (qr[x, y]) canvas.drawRect(
                (left + x * scale).toFloat(), (top + y * scale).toFloat(),
                (left + (x + 1) * scale).toFloat(), (top + (y + 1) * scale).toFloat(), paint,
            )
        }
    }

    private fun drawFittedText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        left: Float,
        baseline: Float,
        maxWidth: Float,
        preferredSize: Float,
    ) {
        paint.textSize = preferredSize
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize = max(5f, preferredSize * maxWidth / measured)
        canvas.drawText(text, left, baseline, paint)
    }

    private fun mmToDots(mm: Float): Int = (mm * Dpi / 25.4f).roundToInt()

    private fun formatMm(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    private const val MaxPrintHeadDots = 384
}
