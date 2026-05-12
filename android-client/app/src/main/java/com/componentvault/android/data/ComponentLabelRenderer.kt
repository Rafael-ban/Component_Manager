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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal object ComponentLabelRenderer {
    private const val DefaultFooterText = "Scan to import or update inventory."
    private const val PreviewUnitsPerMm = 36f
    private const val PdfUnitsPerMm = 72f / 25.4f
    private const val CompanionPreviewGapMm = 2f
    private const val TextOnlyHorizontalPaddingMm = 1.4f
    private const val TextOnlyMinWidthMm = 14f
    private const val TextOnlyMaxWidthMm = 120f
    private const val TextOnlyFontScale = 1.7f

    fun buildTextLabelContent(
        seed: ComponentLabelSeed,
        template: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ): String {
        val primaryName = seed.name.ifBlank {
            seed.model?.takeUnless(String::isBlank) ?: seed.sku
        }
        val segments = when (template) {
            ComponentTextLabelTemplate.NameSku -> listOf(
                primaryName,
                seed.sku,
            )

            ComponentTextLabelTemplate.NamePackageSku -> listOf(
                primaryName,
                seed.packageName,
                seed.sku,
            )

            ComponentTextLabelTemplate.NameModel -> listOf(
                primaryName,
                seed.model?.takeUnless(String::isBlank) ?: seed.sku,
            )
        }

        return segments
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = " | ")
    }

    fun renderBitmap(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ): Bitmap {
        return renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = false,
            textTemplate = textTemplate,
        ).first()
    }

    fun renderBitmaps(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ): List<Bitmap> {
        return resolvePages(
            seed = seed,
            template = template,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
        ).map { page ->
            renderPageBitmap(
                seed = seed,
                page = page,
                footerText = footerText,
                unitsPerMm = PreviewUnitsPerMm,
            )
        }
    }

    fun renderCombinedBitmap(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ): Bitmap {
        val pages = renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
        )
        if (pages.size == 1) {
            return pages.first()
        }

        val gapPx = mmToUnits(CompanionPreviewGapMm, PreviewUnitsPerMm)
        val width = pages.maxOf(Bitmap::getWidth)
        val height = pages.sumOf(Bitmap::getHeight) + gapPx * (pages.size - 1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F4F1EA"))

        var top = 0f
        pages.forEachIndexed { index, page ->
            val left = ((width - page.width) / 2f)
            canvas.drawBitmap(page, left, top, null)
            top += page.height
            if (index != pages.lastIndex) {
                top += gapPx
            }
        }
        return bitmap
    }

    fun writePng(
        outputStream: OutputStream,
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ) {
        renderCombinedBitmap(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
        ).compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    }

    fun writePdf(
        outputStream: OutputStream,
        seed: ComponentLabelSeed,
        copies: Int,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
    ) {
        val pages = resolvePages(
            seed = seed,
            template = template,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
        )

        val document = PdfDocument()
        try {
            var pageNumber = 1
            repeat(copies.coerceAtLeast(1)) {
                pages.forEach { page ->
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        mmToUnits(page.widthMm, PdfUnitsPerMm),
                        mmToUnits(page.heightMm, PdfUnitsPerMm),
                        pageNumber++,
                    ).create()
                    val pdfPage = document.startPage(pageInfo)
                    drawPage(
                        canvas = pdfPage.canvas,
                        seed = seed,
                        page = page,
                        footerText = footerText,
                        unitsPerMm = PdfUnitsPerMm,
                    )
                    document.finishPage(pdfPage)
                }
            }
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    private fun resolvePages(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate,
        includeCompanionTextLabel: Boolean,
        textTemplate: ComponentTextLabelTemplate,
    ): List<LabelPageSpec> {
        val pages = mutableListOf<LabelPageSpec>()
        when {
            template.isQrLabel -> {
                val qrPayload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, template))
                pages += LabelPageSpec(
                    template = template,
                    widthMm = requireNotNull(template.widthMm),
                    heightMm = template.heightMm,
                    qrPayload = qrPayload,
                )
                if (includeCompanionTextLabel) {
                    pages += createTextOnlyPage(seed, textTemplate)
                }
            }

            else -> {
                pages += createTextOnlyPage(seed, textTemplate)
            }
        }
        return pages
    }

    private fun createTextOnlyPage(
        seed: ComponentLabelSeed,
        textTemplate: ComponentTextLabelTemplate,
    ): LabelPageSpec {
        val content = buildTextLabelContent(seed, textTemplate)
        return LabelPageSpec(
            template = ComponentLabelTemplate.TextOnly,
            widthMm = measureTextOnlyWidthMm(content),
            heightMm = ComponentLabelTemplate.TextOnly.heightMm,
            textOnlyContent = content,
        )
    }

    private fun renderPageBitmap(
        seed: ComponentLabelSeed,
        page: LabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
    ): Bitmap {
        val width = mmToUnits(page.widthMm, unitsPerMm)
        val height = mmToUnits(page.heightMm, unitsPerMm)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPage(
            canvas = canvas,
            seed = seed,
            page = page,
            footerText = footerText,
            unitsPerMm = unitsPerMm,
        )
        return bitmap
    }

    private fun drawPage(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: LabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
    ) {
        when (page.template.layoutMode) {
            ComponentLabelLayoutMode.CompactQr -> drawCompactQrLabel(
                canvas = canvas,
                seed = seed,
                page = page,
                unitsPerMm = unitsPerMm,
            )

            ComponentLabelLayoutMode.StandardQr -> drawStandardQrLabel(
                canvas = canvas,
                seed = seed,
                page = page,
                footerText = footerText,
                unitsPerMm = unitsPerMm,
            )

            ComponentLabelLayoutMode.TextOnly -> drawTextOnlyLabel(
                canvas = canvas,
                page = page,
                unitsPerMm = unitsPerMm,
            )
        }
    }

    private fun drawCompactQrLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: LabelPageSpec,
        unitsPerMm: Float,
    ) {
        val width = mmToUnits(page.widthMm, unitsPerMm)
        val height = mmToUnits(page.heightMm, unitsPerMm)
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        drawCardBackground(canvas, cardRect, unitsPerMm)

        val quietZone = mm(page.template.quietZoneMm, unitsPerMm)
        val qrSize = mm(requireNotNull(page.template.qrSizeMm), unitsPerMm)
        val qrFootprint = qrSize + quietZone * 2f
        val qrLeft = ((width - qrFootprint) / 2f) + quietZone
        val qrTop = mm(1.4f, unitsPerMm) + quietZone
        val qrBitmap = createQrBitmap(requireNotNull(page.qrPayload).rawValue, qrSize.roundToInt())
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        val contentLeft = mm(0.8f, unitsPerMm)
        val contentWidth = (width - contentLeft * 2f).roundToInt().coerceAtLeast(1)
        var cursorTop = qrTop + qrSize + mm(1.0f, unitsPerMm)

        val titlePaint = buildTextPaint(
            color = "#111827",
            fontSizeMm = 1.25f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val metaPaint = buildTextPaint(
            color = "#334155",
            fontSizeMm = 0.95f,
            unitsPerMm = unitsPerMm,
        )
        val emphasisPaint = buildTextPaint(
            color = "#7C3AED",
            fontSizeMm = 1.05f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )

        cursorTop += drawTextBlock(
            canvas = canvas,
            text = seed.name.ifBlank { seed.sku },
            paint = titlePaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
            maxLines = 2,
        )
        cursorTop += mm(0.7f, unitsPerMm)
        cursorTop += drawSingleLine(
            canvas = canvas,
            text = "SKU ${seed.sku}",
            paint = metaPaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
        )
        cursorTop += mm(0.5f, unitsPerMm)
        cursorTop += drawSingleLine(
            canvas = canvas,
            text = "PKG ${seed.packageName}",
            paint = metaPaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
        )
        cursorTop += mm(0.5f, unitsPerMm)
        drawSingleLine(
            canvas = canvas,
            text = "QTY ${seed.quantity}",
            paint = emphasisPaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
        )
    }

    private fun drawStandardQrLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: LabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
    ) {
        val width = mmToUnits(page.widthMm, unitsPerMm)
        val height = mmToUnits(page.heightMm, unitsPerMm)
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        drawCardBackground(canvas, cardRect, unitsPerMm)

        val quietZone = mm(page.template.quietZoneMm, unitsPerMm)
        val qrSize = mm(requireNotNull(page.template.qrSizeMm), unitsPerMm)
        val qrFootprint = qrSize + quietZone * 2f
        val qrLeft = ((width - qrFootprint) / 2f) + quietZone
        val qrTop = mm(1.8f, unitsPerMm) + quietZone
        val qrBitmap = createQrBitmap(requireNotNull(page.qrPayload).rawValue, qrSize.roundToInt())
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        val contentLeft = mm(1.4f, unitsPerMm)
        val contentWidth = (width - contentLeft * 2f).roundToInt().coerceAtLeast(1)
        var cursorTop = qrTop + qrSize + mm(1.4f, unitsPerMm)

        val titlePaint = buildTextPaint(
            color = "#111827",
            fontSizeMm = 1.9f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val subtitlePaint = buildTextPaint(
            color = "#475569",
            fontSizeMm = 1.2f,
            unitsPerMm = unitsPerMm,
        )
        val fieldPaint = buildTextPaint(
            color = "#334155",
            fontSizeMm = 1.1f,
            unitsPerMm = unitsPerMm,
        )
        val footerPaint = buildTextPaint(
            color = "#64748B",
            fontSizeMm = 0.95f,
            unitsPerMm = unitsPerMm,
        )

        cursorTop += drawTextBlock(
            canvas = canvas,
            text = seed.name.ifBlank { seed.sku },
            paint = titlePaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
            maxLines = 2,
        )
        cursorTop += mm(0.8f, unitsPerMm)

        buildSecondaryHeadline(seed)?.let { headline ->
            cursorTop += drawTextBlock(
                canvas = canvas,
                text = headline,
                paint = subtitlePaint,
                x = contentLeft,
                y = cursorTop,
                width = contentWidth,
                maxLines = 1,
            )
            cursorTop += mm(0.8f, unitsPerMm)
        }

        buildStandardFieldLines(seed).forEach { line ->
            cursorTop += drawSingleLine(
                canvas = canvas,
                text = line,
                paint = fieldPaint,
                x = contentLeft,
                y = cursorTop,
                width = contentWidth,
            )
            cursorTop += mm(0.45f, unitsPerMm)
        }

        val footerTop = height - mm(2.6f, unitsPerMm)
        drawSingleLine(
            canvas = canvas,
            text = footerText,
            paint = footerPaint,
            x = contentLeft,
            y = footerTop,
            width = contentWidth,
        )
    }

    private fun drawTextOnlyLabel(
        canvas: Canvas,
        page: LabelPageSpec,
        unitsPerMm: Float,
    ) {
        val width = mmToUnits(page.widthMm, unitsPerMm)
        val height = mmToUnits(page.heightMm, unitsPerMm)
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        drawCardBackground(canvas, cardRect, unitsPerMm)

        val padding = mm(TextOnlyHorizontalPaddingMm, unitsPerMm)
        val contentWidth = (width - padding * 2f).coerceAtLeast(1f)
        val paint = buildTextPaint(
            color = "#111827",
            fontSizeMm = requireNotNull(page.template.textHeightMm) * TextOnlyFontScale,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val text = TextUtils.ellipsize(
            page.textOnlyContent.orEmpty(),
            paint,
            contentWidth,
            TextUtils.TruncateAt.END,
        ).toString()

        val metrics = paint.fontMetrics
        val baseline = (height / 2f) - ((metrics.ascent + metrics.descent) / 2f)
        canvas.drawText(text, padding, baseline, paint)
    }

    private fun drawCardBackground(
        canvas: Canvas,
        rect: RectF,
        unitsPerMm: Float,
    ) {
        canvas.drawColor(Color.WHITE)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = mm(0.18f, unitsPerMm)
            color = Color.parseColor("#D6D3D1")
        }
        val radius = mm(0.9f, unitsPerMm)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
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

    private fun drawSingleLine(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
    ): Float {
        val ellipsized = TextUtils.ellipsize(
            text,
            paint,
            width.toFloat(),
            TextUtils.TruncateAt.END,
        )
        canvas.drawText(ellipsized, 0, ellipsized.length, x, y - paint.fontMetrics.ascent, paint)
        return paint.fontSpacing
    }

    private fun buildStandardFieldLines(seed: ComponentLabelSeed): List<String> {
        return buildList {
            add("SKU  ${seed.sku}")
            add("PKG  ${seed.packageName}")
            seed.category.takeIf(String::isNotBlank)?.let { add("CAT  $it") }
            seed.location.takeIf(String::isNotBlank)?.let { add("LOC  $it") }
            add("QTY  ${seed.quantity}")
        }
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

    private fun buildTextPaint(
        color: String,
        fontSizeMm: Float,
        unitsPerMm: Float,
        bold: Boolean = false,
    ): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor(color)
            textSize = mm(fontSizeMm, unitsPerMm)
            typeface = if (bold) {
                Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
        }
    }

    private fun measureTextOnlyWidthMm(content: String): Float {
        val paint = buildTextPaint(
            color = "#111827",
            fontSizeMm = requireNotNull(ComponentLabelTemplate.TextOnly.textHeightMm) * TextOnlyFontScale,
            unitsPerMm = PreviewUnitsPerMm,
            bold = true,
        )
        val contentWidthUnits = paint.measureText(content)
        val totalWidthUnits = contentWidthUnits + (mm(TextOnlyHorizontalPaddingMm, PreviewUnitsPerMm) * 2f)
        val widthMm = totalWidthUnits / PreviewUnitsPerMm
        return widthMm.coerceIn(TextOnlyMinWidthMm, TextOnlyMaxWidthMm)
    }

    private fun createQrBitmap(
        payload: String,
        size: Int,
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 0,
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

    private fun mm(valueMm: Float, unitsPerMm: Float): Float = valueMm * unitsPerMm

    private fun mmToUnits(valueMm: Float, unitsPerMm: Float): Int =
        max(1, ceil(valueMm * unitsPerMm.toDouble()).toInt())

    private data class LabelPageSpec(
        val template: ComponentLabelTemplate,
        val widthMm: Float,
        val heightMm: Float,
        val qrPayload: ComponentQrPayload? = null,
        val textOnlyContent: String? = null,
    )
}
