package com.componentvault.android.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

/** Fixed calibration content; does not expose or print the selected component's data. */
internal object M1TestLabelRenderer {
    const val Dpi = 203
    const val QrContent = "M1-TEST"

    fun supports(template: ComponentLabelTemplate): Boolean =
        template == ComponentLabelTemplate.Qr10x40 || template == ComponentLabelTemplate.Qr30x40

    fun render(template: ComponentLabelTemplate): Bitmap {
        require(supports(template)) { "Choose an existing 40 x 10 or 40 x 30 mm QR template" }
        val width = (requireNotNull(template.physicalWidthMm) * Dpi / 25.4f).roundToInt()
        val height = (template.physicalHeightMm * Dpi / 25.4f).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        // One millimetre inset makes clipping and paper alignment visible without edge printing.
        val inset = 8f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(inset, inset, width - inset - 1, height - inset - 1, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 16f
        canvas.drawText("M1 TEST", 16f, height / 2f - 4, paint)
        paint.textSize = 10f
        canvas.drawText("40 x ${template.physicalHeightMm.toInt()} mm / $Dpi dpi", 16f, height / 2f + 13, paint)
        // QR encoder includes a four-module quiet zone. Integer module scaling avoids blur.
        val qr = QRCodeWriter().encode(QrContent, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
        val scale = if (height < 100) 2 else 4
        val left = width - 16 - qr.width * scale
        val top = (height - qr.height * scale) / 2
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            if (qr[x, y]) canvas.drawRect(
                (left + x * scale).toFloat(), (top + y * scale).toFloat(),
                (left + (x + 1) * scale).toFloat(), (top + (y + 1) * scale).toFloat(), paint,
            )
        }
        return bitmap
    }
}
