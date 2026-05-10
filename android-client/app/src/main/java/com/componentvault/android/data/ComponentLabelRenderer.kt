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
import android.text.TextUtils
import com.componentvault.android.model.ComponentLabelSeed
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream

internal object ComponentLabelRenderer {
    private const val DefaultFooterText = "Scan to import or update inventory."

    fun renderBitmap(
        seed: ComponentLabelSeed,
        qrPayload: String,
        template: ComponentLabelTemplate = ComponentLabelTemplate.Standard,
        footerText: String = DefaultFooterText,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(template.width, template.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawLabel(canvas, seed, qrPayload, template, footerText)
        return bitmap
    }

    fun writePdf(
        outputStream: OutputStream,
        seed: ComponentLabelSeed,
        qrPayload: String,
        copies: Int,
        template: ComponentLabelTemplate = ComponentLabelTemplate.Standard,
        footerText: String = DefaultFooterText,
    ) {
        val document = PdfDocument()
        try {
            repeat(copies.coerceAtLeast(1)) { pageIndex ->
                val pageInfo = PdfDocument.PageInfo.Builder(template.width, template.height, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                drawLabel(page.canvas, seed, qrPayload, template, footerText)
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
        template: ComponentLabelTemplate,
        footerText: String,
    ) {
        val layout = template.layout()
        canvas.drawColor(Color.parseColor("#F6F1E7"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(layout.shadowBlur, 0f, layout.shadowDy, Color.argb(18, 31, 41, 55))
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = layout.borderWidth
            color = Color.parseColor("#D4C6AF")
        }
        val cardRect = RectF(
            layout.outerPadding,
            layout.outerPadding,
            template.width - layout.outerPadding,
            template.height - layout.outerPadding,
        )
        canvas.drawRoundRect(cardRect, layout.cardCornerRadius, layout.cardCornerRadius, cardPaint)
        canvas.drawRoundRect(cardRect, layout.cardCornerRadius, layout.cardCornerRadius, borderPaint)

        val qrBitmap = createQrBitmap(qrPayload, template.qrSize)
        canvas.drawBitmap(qrBitmap, layout.qrLeft, layout.qrTop, null)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = layout.titleTextSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = layout.subtitleTextSize
        }
        val fieldValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = layout.fieldValueTextSize
            typeface = Typeface.MONOSPACE
        }
        val fieldLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C5F37")
            textSize = layout.fieldLabelTextSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = layout.footerTextSize
        }

        val titleHeight = drawTextBlock(
            canvas = canvas,
            text = seed.name.ifBlank { seed.sku },
            paint = titlePaint,
            x = layout.contentLeft,
            y = layout.contentTop,
            width = layout.contentWidth,
            maxLines = 2,
        )

        var fieldTop = layout.contentTop + titleHeight + layout.titleBottomSpacing
        buildSecondaryHeadline(seed)?.let { secondaryHeadline ->
            val subtitleHeight = drawTextBlock(
                canvas = canvas,
                text = secondaryHeadline,
                paint = subtitlePaint,
                x = layout.contentLeft,
                y = fieldTop,
                width = layout.contentWidth,
                maxLines = 1,
            )
            fieldTop += subtitleHeight + layout.sectionSpacing
        }

        buildFieldRows(seed, template).forEachIndexed { rowIndex, row ->
            val rowTop = fieldTop + rowIndex * (layout.fieldRowHeight + layout.fieldRowGap)
            drawFieldCell(
                canvas = canvas,
                labelPaint = fieldLabelPaint,
                valuePaint = fieldValuePaint,
                field = row.first,
                x = layout.contentLeft,
                y = rowTop,
                width = layout.fieldColumnWidth,
                valueTopOffset = layout.fieldValueTopOffset,
            )
            row.second?.let { field ->
                drawFieldCell(
                    canvas = canvas,
                    labelPaint = fieldLabelPaint,
                    valuePaint = fieldValuePaint,
                    field = field,
                    x = layout.contentLeft + layout.fieldColumnWidth + layout.fieldColumnGap,
                    y = rowTop,
                    width = layout.fieldColumnWidth,
                    valueTopOffset = layout.fieldValueTopOffset,
                )
            }
        }

        drawTextBlock(
            canvas = canvas,
            text = footerText,
            paint = footerPaint,
            x = layout.contentLeft,
            y = layout.footerTop,
            width = layout.contentWidth,
            maxLines = 1,
        )
    }

    private fun drawFieldCell(
        canvas: Canvas,
        labelPaint: TextPaint,
        valuePaint: TextPaint,
        field: LabelField,
        x: Float,
        y: Float,
        width: Int,
        valueTopOffset: Float,
    ) {
        canvas.drawText(field.label, x, y, labelPaint)
        drawTextBlock(
            canvas = canvas,
            text = field.value.ifBlank { "-" },
            paint = valuePaint,
            x = x,
            y = y + valueTopOffset,
            width = width,
            maxLines = 1,
        )
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        maxLines: Int,
    ): Float {
        canvas.save()
        canvas.translate(x, y)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun buildSecondaryHeadline(seed: ComponentLabelSeed): String? {
        val primary = listOfNotNull(
            seed.brand?.trim().takeUnless { it.isNullOrBlank() },
            seed.model?.trim().takeUnless { it.isNullOrBlank() },
        )
        if (primary.isNotEmpty()) {
            return primary.joinToString(" / ")
        }

        val fallback = listOfNotNull(
            seed.category.trim().takeUnless { it.isBlank() },
            seed.packageName.trim().takeUnless { it.isBlank() },
        )
        return fallback.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }

    private fun buildFieldRows(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate,
    ): List<Pair<LabelField, LabelField?>> {
        val rows = mutableListOf<Pair<LabelField, LabelField?>>(
            LabelField("SKU", seed.sku) to LabelField("QTY", seed.quantity.toString()),
            LabelField("PKG", seed.packageName) to LabelField("LOC", seed.location),
        )

        if (template.showsSecondaryFields) {
            val secondaryLeft = seed.category.takeUnless { it.isBlank() }?.let { LabelField("CAT", it) }
            val secondaryRight = seed.brand?.takeUnless { it.isBlank() }?.let { LabelField("BRAND", it) }
            if (secondaryLeft != null || secondaryRight != null) {
                rows += (secondaryLeft ?: LabelField("CAT", "-")) to secondaryRight
            }
        }

        return rows
    }

    private fun ComponentLabelTemplate.layout(): LabelLayout {
        val scale = width / 960f
        val outerPadding = 24f * scale
        val contentLeft = 58f * scale
        val contentTop = 54f * scale
        val qrTop = 54f * scale
        val qrRightInset = 52f * scale
        val columnGap = 18f * scale
        val contentRight = width - qrRightInset - qrSize - (34f * scale)
        val contentWidth = (contentRight - contentLeft).toInt().coerceAtLeast(120)
        return LabelLayout(
            outerPadding = outerPadding,
            cardCornerRadius = 30f * scale,
            borderWidth = 3f * scale,
            shadowBlur = 12f * scale,
            shadowDy = 4f * scale,
            contentLeft = contentLeft,
            contentTop = contentTop,
            contentWidth = contentWidth,
            qrLeft = width - qrRightInset - qrSize,
            qrTop = qrTop,
            titleTextSize = 42f * scale,
            subtitleTextSize = 22f * scale,
            fieldLabelTextSize = 18f * scale,
            fieldValueTextSize = 26f * scale,
            footerTextSize = 18f * scale,
            titleBottomSpacing = 12f * scale,
            sectionSpacing = 22f * scale,
            fieldColumnWidth = ((contentWidth - columnGap) / 2f).toInt().coerceAtLeast(96),
            fieldColumnGap = columnGap,
            fieldRowHeight = 56f * scale,
            fieldRowGap = 16f * scale,
            fieldValueTopOffset = 24f * scale,
            footerTop = height - (72f * scale),
        )
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

    private data class LabelField(
        val label: String,
        val value: String,
    )

    private data class LabelLayout(
        val outerPadding: Float,
        val cardCornerRadius: Float,
        val borderWidth: Float,
        val shadowBlur: Float,
        val shadowDy: Float,
        val contentLeft: Float,
        val contentTop: Float,
        val contentWidth: Int,
        val qrLeft: Float,
        val qrTop: Float,
        val titleTextSize: Float,
        val subtitleTextSize: Float,
        val fieldLabelTextSize: Float,
        val fieldValueTextSize: Float,
        val footerTextSize: Float,
        val titleBottomSpacing: Float,
        val sectionSpacing: Float,
        val fieldColumnWidth: Int,
        val fieldColumnGap: Float,
        val fieldRowHeight: Float,
        val fieldRowGap: Float,
        val fieldValueTopOffset: Float,
        val footerTop: Float,
    )
}
