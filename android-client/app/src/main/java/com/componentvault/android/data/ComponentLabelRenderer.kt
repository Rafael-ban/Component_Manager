package com.componentvault.android.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.componentvault.android.model.ComponentLabelSeed
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream

internal object ComponentLabelRenderer {
    const val LabelWidth = 960
    const val LabelHeight = 480

    fun renderBitmap(
        seed: ComponentLabelSeed,
        qrPayload: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(LabelWidth, LabelHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawLabel(canvas, seed, qrPayload)
        return bitmap
    }

    fun writePdf(
        outputStream: OutputStream,
        seed: ComponentLabelSeed,
        qrPayload: String,
        copies: Int,
    ) {
        val document = PdfDocument()
        try {
            repeat(copies.coerceAtLeast(1)) { pageIndex ->
                val pageInfo = PdfDocument.PageInfo.Builder(LabelWidth, LabelHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                drawLabel(page.canvas, seed, qrPayload)
                document.finishPage(page)
            }
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        qrPayload: String,
    ) {
        canvas.drawColor(Color.parseColor("#F6F1E7"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(12f, 0f, 4f, Color.argb(18, 31, 41, 55))
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#D4C6AF")
        }
        val cardRect = RectF(24f, 24f, LabelWidth - 24f, LabelHeight - 24f)
        canvas.drawRoundRect(cardRect, 30f, 30f, cardPaint)
        canvas.drawRoundRect(cardRect, 30f, 30f, borderPaint)

        val qrBitmap = createQrBitmap(qrPayload, 260)
        canvas.drawBitmap(qrBitmap, LabelWidth - 312f, 54f, null)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C5F37")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        drawWrappedText(
            canvas = canvas,
            text = seed.name.ifBlank { seed.sku },
            paint = titlePaint,
            x = 58f,
            y = 54f,
            width = 560,
            maxLines = 2,
        )

        val metaTop = 170f
        val rowHeight = 46f
        drawLineItem(canvas, labelPaint, bodyPaint, "SKU", seed.sku, 58f, metaTop)
        drawLineItem(canvas, labelPaint, bodyPaint, "PKG", seed.packageName, 58f, metaTop + rowHeight)
        drawLineItem(canvas, labelPaint, bodyPaint, "CAT", seed.category, 58f, metaTop + rowHeight * 2)
        drawLineItem(canvas, labelPaint, bodyPaint, "QTY", seed.quantity.toString(), 58f, metaTop + rowHeight * 3)
        drawLineItem(canvas, labelPaint, bodyPaint, "LOC", seed.location, 58f, metaTop + rowHeight * 4)

        if (!seed.model.isNullOrBlank()) {
            drawLineItem(canvas, labelPaint, bodyPaint, "MODEL", seed.model, 360f, metaTop)
        }
        if (!seed.brand.isNullOrBlank()) {
            drawLineItem(canvas, labelPaint, bodyPaint, "BRAND", seed.brand, 360f, metaTop + rowHeight)
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 18f
        }
        drawWrappedText(
            canvas = canvas,
            text = "Machine-readable label for Component Vault inventory import.",
            paint = footerPaint,
            x = 58f,
            y = LabelHeight - 74f,
            width = 560,
            maxLines = 2,
        )
    }

    private fun drawLineItem(
        canvas: Canvas,
        labelPaint: TextPaint,
        bodyPaint: TextPaint,
        label: String,
        value: String,
        x: Float,
        y: Float,
    ) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value.ifBlank { "-" }, x + 116f, y, bodyPaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        maxLines: Int,
    ) {
        canvas.save()
        canvas.translate(x, y)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        layout.draw(canvas)
        canvas.restore()
    }

    private fun createQrBitmap(
        payload: String,
        size: Int,
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
