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

internal data class ResolvedLabelPageSpec(
    val template: ComponentLabelTemplate,
    val canvasTemplate: ComponentLabelPrintCanvasTemplate,
    val canvasWidthMm: Float,
    val canvasHeightMm: Float,
    val contentLeftMm: Float,
    val contentTopMm: Float,
    val contentWidthMm: Float,
    val contentHeightMm: Float,
    val qrPayload: ComponentQrPayload? = null,
    val textOnlyContent: String? = null,
)

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
        canvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
        showCanvasOutline: Boolean = false,
    ): Bitmap {
        return renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = false,
            textTemplate = textTemplate,
            canvasTemplate = canvasTemplate,
            showCanvasOutline = showCanvasOutline,
        ).first()
    }

    fun renderBitmaps(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
        canvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
        showCanvasOutline: Boolean = false,
    ): List<Bitmap> {
        return resolvePageSpecs(
            seed = seed,
            template = template,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
            canvasTemplate = canvasTemplate,
        ).map { page ->
            renderPageBitmap(
                seed = seed,
                page = page,
                footerText = footerText,
                unitsPerMm = PreviewUnitsPerMm,
                showCanvasOutline = showCanvasOutline,
            )
        }
    }

    fun renderCombinedBitmap(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
        canvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
        showCanvasOutline: Boolean = false,
    ): Bitmap {
        val pages = renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
            canvasTemplate = canvasTemplate,
            showCanvasOutline = showCanvasOutline,
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
        canvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
    ) {
        renderCombinedBitmap(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
            canvasTemplate = canvasTemplate,
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
        canvasTemplate: ComponentLabelPrintCanvasTemplate = ComponentLabelPrintCanvasTemplate.default,
    ) {
        val pages = resolvePageSpecs(
            seed = seed,
            template = template,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
            canvasTemplate = canvasTemplate,
        )

        val document = PdfDocument()
        try {
            var pageNumber = 1
            repeat(copies.coerceAtLeast(1)) {
                pages.forEach { page ->
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        mmToUnits(page.canvasWidthMm, PdfUnitsPerMm),
                        mmToUnits(page.canvasHeightMm, PdfUnitsPerMm),
                        pageNumber++,
                    ).create()
                    val pdfPage = document.startPage(pageInfo)
                    drawPage(
                        canvas = pdfPage.canvas,
                        seed = seed,
                        page = page,
                        footerText = footerText,
                        unitsPerMm = PdfUnitsPerMm,
                        showCanvasOutline = false,
                    )
                    document.finishPage(pdfPage)
                }
            }
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    internal fun resolvePageSpecs(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate,
        includeCompanionTextLabel: Boolean,
        textTemplate: ComponentTextLabelTemplate,
        canvasTemplate: ComponentLabelPrintCanvasTemplate,
    ): List<ResolvedLabelPageSpec> {
        val pages = mutableListOf<ResolvedLabelPageSpec>()
        when {
            template.isQrLabel -> {
                val qrPayload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, template))
                pages += wrapPageInCanvas(
                    template = template,
                    contentWidthMm = requireNotNull(template.widthMm),
                    contentHeightMm = template.heightMm,
                    canvasTemplate = canvasTemplate,
                    qrPayload = qrPayload,
                )
                if (includeCompanionTextLabel) {
                    pages += createTextOnlyPage(seed, textTemplate, canvasTemplate)
                }
            }

            else -> {
                pages += createTextOnlyPage(seed, textTemplate, canvasTemplate)
            }
        }
        return pages
    }

    private fun createTextOnlyPage(
        seed: ComponentLabelSeed,
        textTemplate: ComponentTextLabelTemplate,
        canvasTemplate: ComponentLabelPrintCanvasTemplate,
    ): ResolvedLabelPageSpec {
        val content = buildTextLabelContent(seed, textTemplate)
        val textWidthMm = measureTextOnlyWidthMm(content)
        return wrapPageInCanvas(
            template = ComponentLabelTemplate.TextOnly,
            contentWidthMm = textWidthMm,
            contentHeightMm = ComponentLabelTemplate.TextOnly.heightMm,
            canvasTemplate = canvasTemplate,
            textOnlyContent = content,
        )
    }

    private fun wrapPageInCanvas(
        template: ComponentLabelTemplate,
        contentWidthMm: Float,
        contentHeightMm: Float,
        canvasTemplate: ComponentLabelPrintCanvasTemplate,
        qrPayload: ComponentQrPayload? = null,
        textOnlyContent: String? = null,
    ): ResolvedLabelPageSpec {
        val canvasWidthMm = canvasTemplate.widthMm ?: contentWidthMm
        val canvasHeightMm = canvasTemplate.heightMm ?: contentHeightMm
        val contentLeftMm = ((canvasWidthMm - contentWidthMm) / 2f).coerceAtLeast(0f)
        val contentTopMm = ((canvasHeightMm - contentHeightMm) / 2f).coerceAtLeast(0f)
        return ResolvedLabelPageSpec(
            template = template,
            canvasTemplate = canvasTemplate,
            canvasWidthMm = canvasWidthMm,
            canvasHeightMm = canvasHeightMm,
            contentLeftMm = contentLeftMm,
            contentTopMm = contentTopMm,
            contentWidthMm = contentWidthMm,
            contentHeightMm = contentHeightMm,
            qrPayload = qrPayload,
            textOnlyContent = textOnlyContent,
        )
    }

    private fun renderPageBitmap(
        seed: ComponentLabelSeed,
        page: ResolvedLabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
        showCanvasOutline: Boolean,
    ): Bitmap {
        val width = mmToUnits(page.canvasWidthMm, unitsPerMm)
        val height = mmToUnits(page.canvasHeightMm, unitsPerMm)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPage(
            canvas = canvas,
            seed = seed,
            page = page,
            footerText = footerText,
            unitsPerMm = unitsPerMm,
            showCanvasOutline = showCanvasOutline,
        )
        return bitmap
    }

    private fun drawPage(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: ResolvedLabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
        showCanvasOutline: Boolean,
    ) {
        drawCanvasBackground(
            canvas = canvas,
            page = page,
            unitsPerMm = unitsPerMm,
            showCanvasOutline = showCanvasOutline,
        )
        when (page.template.layoutMode) {
            ComponentLabelLayoutMode.LandscapeRightQr -> drawLandscapeRightQrLabel(
                canvas = canvas,
                seed = seed,
                page = page,
                unitsPerMm = unitsPerMm,
            )

            ComponentLabelLayoutMode.TopLeftQrDetails -> drawTopLeftQrDetailsLabel(
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

    private fun drawCanvasBackground(
        canvas: Canvas,
        page: ResolvedLabelPageSpec,
        unitsPerMm: Float,
        showCanvasOutline: Boolean,
    ) {
        val pageRect = RectF(
            0f,
            0f,
            mm(page.canvasWidthMm, unitsPerMm),
            mm(page.canvasHeightMm, unitsPerMm),
        )
        canvas.drawColor(Color.WHITE)
        if (!showCanvasOutline) {
            return
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = mm(0.22f, unitsPerMm)
            color = Color.parseColor("#CBD5E1")
        }
        canvas.drawRect(pageRect, borderPaint)
    }

    private fun drawLandscapeRightQrLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: ResolvedLabelPageSpec,
        unitsPerMm: Float,
    ) {
        val labelRect = page.contentRect(unitsPerMm)
        drawCardBackground(canvas, labelRect, unitsPerMm)

        val horizontalPadding = mm(0.8f, unitsPerMm)
        val verticalPadding = mm(0.6f, unitsPerMm)
        val gap = mm(0.8f, unitsPerMm)
        val quietZone = mm(page.template.quietZoneMm, unitsPerMm)
        val qrSize = mm(requireNotNull(page.template.qrSizeMm), unitsPerMm)
        val qrFootprint = qrSize + quietZone * 2f
        val qrZoneLeft = labelRect.right - horizontalPadding - qrFootprint
        val qrLeft = qrZoneLeft + quietZone
        val qrTop = labelRect.top + ((labelRect.height() - qrSize) / 2f)
        val qrBitmap = createQrBitmap(requireNotNull(page.qrPayload).rawValue, qrSize.roundToInt())
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        val contentLeft = labelRect.left + horizontalPadding
        val contentWidth = (qrZoneLeft - gap - contentLeft).roundToInt().coerceAtLeast(1)
        var cursorTop = labelRect.top + verticalPadding

        val titlePaint = buildTextPaint(
            color = "#111827",
            fontSizeMm = 1.2f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val metaPaint = buildTextPaint(
            color = "#334155",
            fontSizeMm = 0.86f,
            unitsPerMm = unitsPerMm,
        )
        val emphasisPaint = buildTextPaint(
            color = "#0F766E",
            fontSizeMm = 0.9f,
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
        cursorTop += mm(0.35f, unitsPerMm)
        cursorTop += drawSingleLine(
            canvas = canvas,
            text = "SKU ${seed.sku}",
            paint = metaPaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
        )
        cursorTop += mm(0.2f, unitsPerMm)
        drawSingleLine(
            canvas = canvas,
            text = "QTY ${seed.quantity}",
            paint = emphasisPaint,
            x = contentLeft,
            y = cursorTop,
            width = contentWidth,
        )
    }

    private fun drawTopLeftQrDetailsLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: ResolvedLabelPageSpec,
        footerText: String,
        unitsPerMm: Float,
    ) {
        val labelRect = page.contentRect(unitsPerMm)
        drawCardBackground(canvas, labelRect, unitsPerMm)

        val outerPadding = mm(1.2f, unitsPerMm)
        val columnGap = mm(1.1f, unitsPerMm)
        val quietZone = mm(page.template.quietZoneMm, unitsPerMm)
        val qrSize = mm(requireNotNull(page.template.qrSizeMm), unitsPerMm)
        val qrFootprint = qrSize + quietZone * 2f
        val qrZoneLeft = labelRect.left + outerPadding
        val qrZoneTop = labelRect.top + outerPadding
        val qrLeft = qrZoneLeft + quietZone
        val qrTop = qrZoneTop + quietZone
        val qrBitmap = createQrBitmap(requireNotNull(page.qrPayload).rawValue, qrSize.roundToInt())
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        val infoLeft = qrZoneLeft + qrFootprint + columnGap
        val infoTop = labelRect.top + outerPadding
        val infoWidth = (labelRect.right - outerPadding - infoLeft).roundToInt().coerceAtLeast(1)

        val titlePaint = buildTextPaint(
            color = "#111827",
            fontSizeMm = 0.95f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val subtitlePaint = buildTextPaint(
            color = "#475569",
            fontSizeMm = 0.78f,
            unitsPerMm = unitsPerMm,
        )
        val fieldPaint = buildTextPaint(
            color = "#334155",
            fontSizeMm = 0.96f,
            unitsPerMm = unitsPerMm,
        )
        val footerPaint = buildTextPaint(
            color = "#64748B",
            fontSizeMm = 0.74f,
            unitsPerMm = unitsPerMm,
        )

        var infoBottom = infoTop
        infoBottom += drawTextBlock(
            canvas = canvas,
            text = seed.name.ifBlank { seed.sku },
            paint = titlePaint,
            x = infoLeft,
            y = infoBottom,
            width = infoWidth,
            maxLines = 5,
        )
        buildTopLeftInfoHeadline(seed)?.let { headline ->
            infoBottom += mm(0.55f, unitsPerMm)
            infoBottom += drawTextBlock(
                canvas = canvas,
                text = headline,
                paint = subtitlePaint,
                x = infoLeft,
                y = infoBottom,
                width = infoWidth,
                maxLines = 4,
            )
        }

        var cursorTop = max(infoBottom, qrZoneTop + qrFootprint) + mm(1f, unitsPerMm)
        val contentLeft = labelRect.left + outerPadding
        val contentWidth = (labelRect.width() - outerPadding * 2f).roundToInt().coerceAtLeast(1)
        buildStandardFieldLines(seed).forEach { line ->
            cursorTop += drawSingleLine(
                canvas = canvas,
                text = line,
                paint = fieldPaint,
                x = contentLeft,
                y = cursorTop,
                width = contentWidth,
            )
            cursorTop += mm(0.32f, unitsPerMm)
        }

        val footerTop = labelRect.bottom - outerPadding - footerPaint.fontSpacing
        if (footerTop > cursorTop) {
            drawSingleLine(
                canvas = canvas,
                text = footerText,
                paint = footerPaint,
                x = contentLeft,
                y = footerTop,
                width = contentWidth,
            )
        }
    }

    private fun drawTextOnlyLabel(
        canvas: Canvas,
        page: ResolvedLabelPageSpec,
        unitsPerMm: Float,
    ) {
        val labelRect = page.contentRect(unitsPerMm)
        drawCardBackground(canvas, labelRect, unitsPerMm)

        val padding = mm(TextOnlyHorizontalPaddingMm, unitsPerMm)
        val contentWidth = (labelRect.width() - padding * 2f).coerceAtLeast(1f)
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
        val baseline = labelRect.centerY() - ((metrics.ascent + metrics.descent) / 2f)
        canvas.drawText(text, labelRect.left + padding, baseline, paint)
    }

    private fun drawCardBackground(
        canvas: Canvas,
        rect: RectF,
        unitsPerMm: Float,
    ) {
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
            seed.packageName.takeIf(String::isNotBlank)?.let { add("PKG  $it") }
            seed.category.takeIf(String::isNotBlank)?.let { add("CAT  $it") }
            seed.location.takeIf(String::isNotBlank)?.let { add("LOC  $it") }
            add("QTY  ${seed.quantity}")
        }
    }

    private fun buildTopLeftInfoHeadline(seed: ComponentLabelSeed): String? {
        val preferred = listOfNotNull(
            seed.model?.trim().takeUnless { it.isNullOrBlank() },
            seed.brand?.trim().takeUnless { it.isNullOrBlank() },
            seed.packageName.trim().takeUnless { it.isBlank() },
        )
        return preferred.takeIf { it.isNotEmpty() }?.joinToString(" / ")
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

    private fun ResolvedLabelPageSpec.contentRect(unitsPerMm: Float): RectF {
        val left = mm(contentLeftMm, unitsPerMm)
        val top = mm(contentTopMm, unitsPerMm)
        return RectF(
            left,
            top,
            left + mm(contentWidthMm, unitsPerMm),
            top + mm(contentHeightMm, unitsPerMm),
        )
    }
}
