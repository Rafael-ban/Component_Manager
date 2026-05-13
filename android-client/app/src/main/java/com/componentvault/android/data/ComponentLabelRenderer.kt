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
    val canvasWidthMm: Float,
    val canvasHeightMm: Float,
    val contentLeftMm: Float,
    val contentTopMm: Float,
    val contentWidthMm: Float,
    val contentHeightMm: Float,
    val qrPayload: ComponentQrPayload? = null,
    val textOnlyContent: String? = null,
    val textOnlyFontSizeMm: Float? = null,
)

private data class LandscapeStackTypography(
    val packageFontSizeMm: Float,
    val nameFontSizeMm: Float,
    val quantityFontSizeMm: Float,
    val metaFontSizeMm: Float,
    val mainGapMm: Float,
    val sectionGapMm: Float,
    val lineGapMm: Float,
)

private enum class SecondaryLineRole {
    Meta,
    Quantity,
}

private data class SecondaryLineSpec(
    val text: String,
    val role: SecondaryLineRole,
    val required: Boolean,
)

private data class MeasuredSecondaryLine(
    val text: CharSequence,
    val paint: TextPaint,
    val height: Float,
)

private data class LandscapeStackLayoutPlan(
    val packageLayout: StaticLayout?,
    val nameLayout: StaticLayout,
    val secondaryLines: List<MeasuredSecondaryLine>,
    val mainGap: Float,
    val sectionGap: Float,
    val lineGap: Float,
    val mainHeight: Float,
    val secondaryHeight: Float,
)

internal object ComponentLabelRenderer {
    private const val DefaultFooterText = "Scan to import or update inventory."
    private const val PreviewUnitsPerMm = 36f
    private const val PdfUnitsPerMm = 72f / 25.4f
    private const val CompanionPreviewGapMm = 2f
    private const val TextOnlyHorizontalPaddingMm = 1.1f
    private const val TextOnlyVerticalPaddingMm = 0.35f
    private const val TextOnlyMinWidthMm = 14f
    private const val TextOnlyMaxWidthMm = 120f
    private const val TextOnlyMinFontSizeMm = 1.3f
    private const val TextOnlyMaxFontSizeMm = 3.4f
    private const val TextOnlyFontStepMm = 0.05f

    private val LandscapeStackTypographyTiers = listOf(
        LandscapeStackTypography(
            packageFontSizeMm = 1.32f,
            nameFontSizeMm = 1.78f,
            quantityFontSizeMm = 1.42f,
            metaFontSizeMm = 1.08f,
            mainGapMm = 0.34f,
            sectionGapMm = 0.82f,
            lineGapMm = 0.18f,
        ),
        LandscapeStackTypography(
            packageFontSizeMm = 1.22f,
            nameFontSizeMm = 1.64f,
            quantityFontSizeMm = 1.3f,
            metaFontSizeMm = 1.02f,
            mainGapMm = 0.3f,
            sectionGapMm = 0.72f,
            lineGapMm = 0.16f,
        ),
        LandscapeStackTypography(
            packageFontSizeMm = 1.12f,
            nameFontSizeMm = 1.5f,
            quantityFontSizeMm = 1.2f,
            metaFontSizeMm = 0.96f,
            mainGapMm = 0.26f,
            sectionGapMm = 0.64f,
            lineGapMm = 0.14f,
        ),
    )

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
        showCanvasOutline: Boolean = false,
    ): Bitmap {
        return renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = false,
            textTemplate = textTemplate,
            showCanvasOutline = showCanvasOutline,
        ).first()
    }

    fun renderBitmaps(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate = ComponentLabelTemplate.default,
        footerText: String = DefaultFooterText,
        includeCompanionTextLabel: Boolean = false,
        textTemplate: ComponentTextLabelTemplate = ComponentTextLabelTemplate.default,
        showCanvasOutline: Boolean = false,
    ): List<Bitmap> {
        return resolvePageSpecs(
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
        showCanvasOutline: Boolean = false,
    ): Bitmap {
        val pages = renderBitmaps(
            seed = seed,
            template = template,
            footerText = footerText,
            includeCompanionTextLabel = includeCompanionTextLabel,
            textTemplate = textTemplate,
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
        val pages = resolvePageSpecs(
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
    ): List<ResolvedLabelPageSpec> {
        val pages = mutableListOf<ResolvedLabelPageSpec>()
        when {
            template.isQrLabel -> {
                val qrPayload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, template))
                pages += createPageSpec(
                    template = template,
                    contentWidthMm = requireNotNull(template.physicalWidthMm),
                    contentHeightMm = template.physicalHeightMm,
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
    ): ResolvedLabelPageSpec {
        val content = buildTextLabelContent(seed, textTemplate)
        val textFontSizeMm = resolveTextOnlyFontSizeMm(content)
        val textWidthMm = measureTextOnlyWidthMm(content, textFontSizeMm)
        return createPageSpec(
            template = ComponentLabelTemplate.TextOnly,
            contentWidthMm = textWidthMm,
            contentHeightMm = ComponentLabelTemplate.TextOnly.physicalHeightMm,
            textOnlyContent = content,
            textOnlyFontSizeMm = textFontSizeMm,
        )
    }

    private fun createPageSpec(
        template: ComponentLabelTemplate,
        contentWidthMm: Float,
        contentHeightMm: Float,
        qrPayload: ComponentQrPayload? = null,
        textOnlyContent: String? = null,
        textOnlyFontSizeMm: Float? = null,
    ): ResolvedLabelPageSpec {
        return ResolvedLabelPageSpec(
            template = template,
            canvasWidthMm = template.physicalWidthMm ?: contentWidthMm,
            canvasHeightMm = template.physicalHeightMm,
            contentLeftMm = 0f,
            contentTopMm = 0f,
            contentWidthMm = contentWidthMm,
            contentHeightMm = contentHeightMm,
            qrPayload = qrPayload,
            textOnlyContent = textOnlyContent,
            textOnlyFontSizeMm = textOnlyFontSizeMm,
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

            ComponentLabelLayoutMode.LandscapeLeftQrRightStack -> drawLandscapeLeftQrRightStackLabel(
                canvas = canvas,
                seed = seed,
                page = page,
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

        val horizontalPadding = mm(0.7f, unitsPerMm)
        val verticalPadding = mm(0.45f, unitsPerMm)
        val gap = mm(0.6f, unitsPerMm)
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
            fontSizeMm = 1.72f,
            unitsPerMm = unitsPerMm,
            bold = true,
        )
        val metaPaint = buildTextPaint(
            color = "#334155",
            fontSizeMm = 1.16f,
            unitsPerMm = unitsPerMm,
        )
        val emphasisPaint = buildTextPaint(
            color = "#0F766E",
            fontSizeMm = 1.3f,
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

    private fun drawLandscapeLeftQrRightStackLabel(
        canvas: Canvas,
        seed: ComponentLabelSeed,
        page: ResolvedLabelPageSpec,
        unitsPerMm: Float,
    ) {
        val labelRect = page.contentRect(unitsPerMm)
        drawCardBackground(canvas, labelRect, unitsPerMm)

        val outerPadding = mm(0.8f, unitsPerMm)
        val columnGap = mm(0.8f, unitsPerMm)
        val quietZone = mm(page.template.quietZoneMm, unitsPerMm)
        val qrSize = mm(requireNotNull(page.template.qrSizeMm), unitsPerMm)
        val qrFootprint = qrSize + quietZone * 2f
        val qrZoneLeft = labelRect.left + outerPadding
        val qrZoneTop = labelRect.top + ((labelRect.height() - qrFootprint) / 2f)
        val qrLeft = qrZoneLeft + quietZone
        val qrTop = qrZoneTop + quietZone
        val qrBitmap = createQrBitmap(requireNotNull(page.qrPayload).rawValue, qrSize.roundToInt())
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        val infoLeft = qrZoneLeft + qrFootprint + columnGap
        val infoRect = RectF(
            infoLeft,
            labelRect.top + outerPadding,
            labelRect.right - outerPadding,
            labelRect.bottom - outerPadding,
        )
        val infoWidth = infoRect.width().roundToInt().coerceAtLeast(1)
        val plan = resolveLandscapeStackLayoutPlan(
            seed = seed,
            width = infoWidth,
            maxHeight = infoRect.height(),
            unitsPerMm = unitsPerMm,
        )

        val mainTop = infoRect.top + max(
            0f,
            ((infoRect.height() - plan.secondaryHeight - plan.sectionGap) - plan.mainHeight) / 2f,
        )
        if (plan.packageLayout != null) {
            drawTextLayout(
                canvas = canvas,
                layout = plan.packageLayout,
                x = infoRect.left,
                y = mainTop,
            )
        }
        val nameTop = mainTop + (plan.packageLayout?.height?.toFloat() ?: 0f) + plan.mainGap
        drawTextLayout(
            canvas = canvas,
            layout = plan.nameLayout,
            x = infoRect.left,
            y = nameTop,
        )

        var secondaryTop = infoRect.bottom - plan.secondaryHeight
        plan.secondaryLines.forEachIndexed { index, line ->
            secondaryTop += drawSingleLine(
                canvas = canvas,
                text = line.text.toString(),
                paint = line.paint,
                x = infoRect.left,
                y = secondaryTop,
                width = infoWidth,
            )
            if (index != plan.secondaryLines.lastIndex) {
                secondaryTop += plan.lineGap
            }
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
            fontSizeMm = requireNotNull(page.textOnlyFontSizeMm),
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
        val layout = buildTextLayout(text, paint, width, maxLines)
        return drawTextLayout(canvas, layout, x, y)
    }

    private fun drawTextLayout(
        canvas: Canvas,
        layout: StaticLayout,
        x: Float,
        y: Float,
    ): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun buildTextLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
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

    private fun resolveLandscapeStackLayoutPlan(
        seed: ComponentLabelSeed,
        width: Int,
        maxHeight: Float,
        unitsPerMm: Float,
    ): LandscapeStackLayoutPlan {
        val packageText = seed.packageName.trim().takeIf { it.isNotBlank() }
        val nameText = seed.name.ifBlank { seed.sku }
        val secondaryCandidates = buildLandscapeStackSecondaryCandidates(seed)
        var fallbackPlan: LandscapeStackLayoutPlan? = null

        LandscapeStackTypographyTiers.forEach { typography ->
            val packagePaint = buildTextPaint(
                color = "#0F172A",
                fontSizeMm = typography.packageFontSizeMm,
                unitsPerMm = unitsPerMm,
                bold = true,
            )
            val namePaint = buildTextPaint(
                color = "#111827",
                fontSizeMm = typography.nameFontSizeMm,
                unitsPerMm = unitsPerMm,
                bold = true,
            )
            val quantityPaint = buildTextPaint(
                color = "#0F766E",
                fontSizeMm = typography.quantityFontSizeMm,
                unitsPerMm = unitsPerMm,
                bold = true,
            )
            val metaPaint = buildTextPaint(
                color = "#334155",
                fontSizeMm = typography.metaFontSizeMm,
                unitsPerMm = unitsPerMm,
            )

            secondaryCandidates.forEach { secondaryLines ->
                val plan = createLandscapeStackLayoutPlan(
                    packageText = packageText,
                    nameText = nameText,
                    secondaryLines = secondaryLines,
                    width = width,
                    unitsPerMm = unitsPerMm,
                    typography = typography,
                    packagePaint = packagePaint,
                    namePaint = namePaint,
                    quantityPaint = quantityPaint,
                    metaPaint = metaPaint,
                )
                fallbackPlan = plan
                if (plan.mainHeight + plan.sectionGap + plan.secondaryHeight <= maxHeight) {
                    return plan
                }
            }
        }

        return requireNotNull(fallbackPlan)
    }

    private fun createLandscapeStackLayoutPlan(
        packageText: String?,
        nameText: String,
        secondaryLines: List<SecondaryLineSpec>,
        width: Int,
        unitsPerMm: Float,
        typography: LandscapeStackTypography,
        packagePaint: TextPaint,
        namePaint: TextPaint,
        quantityPaint: TextPaint,
        metaPaint: TextPaint,
    ): LandscapeStackLayoutPlan {
        val packageLayout = packageText?.let {
            buildTextLayout(
                text = it,
                paint = packagePaint,
                width = width,
                maxLines = 1,
            )
        }
        val nameLayout = buildTextLayout(
            text = nameText,
            paint = namePaint,
            width = width,
            maxLines = 2,
        )
        val measuredSecondaryLines = secondaryLines.map { spec ->
            val paint = when (spec.role) {
                SecondaryLineRole.Meta -> metaPaint
                SecondaryLineRole.Quantity -> quantityPaint
            }
            MeasuredSecondaryLine(
                text = TextUtils.ellipsize(
                    spec.text,
                    paint,
                    width.toFloat(),
                    TextUtils.TruncateAt.END,
                ),
                paint = paint,
                height = paint.fontSpacing,
            )
        }
        val mainGap = if (packageLayout != null) {
            mm(typography.mainGapMm, unitsPerMm)
        } else {
            0f
        }
        val lineGap = mm(typography.lineGapMm, unitsPerMm)
        val sectionGap = if (measuredSecondaryLines.isNotEmpty()) {
            mm(typography.sectionGapMm, unitsPerMm)
        } else {
            0f
        }
        val mainHeight = (packageLayout?.height?.toFloat() ?: 0f) + mainGap + nameLayout.height.toFloat()
        val secondaryHeight = measuredSecondaryLines.foldIndexed(0f) { index, total, line ->
            total + line.height + if (index > 0) lineGap else 0f
        }
        return LandscapeStackLayoutPlan(
            packageLayout = packageLayout,
            nameLayout = nameLayout,
            secondaryLines = measuredSecondaryLines,
            mainGap = mainGap,
            sectionGap = sectionGap,
            lineGap = lineGap,
            mainHeight = mainHeight,
            secondaryHeight = secondaryHeight,
        )
    }

    private fun buildLandscapeStackSecondaryCandidates(
        seed: ComponentLabelSeed,
    ): List<List<SecondaryLineSpec>> {
        val all = buildList {
            add(SecondaryLineSpec(text = "QTY ${seed.quantity}", role = SecondaryLineRole.Quantity, required = true))
            add(SecondaryLineSpec(text = "SKU ${seed.sku}", role = SecondaryLineRole.Meta, required = true))
            seed.model?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(SecondaryLineSpec(text = "MODEL $it", role = SecondaryLineRole.Meta, required = false)) }
            seed.location.trim()
                .takeIf { it.isNotBlank() }
                ?.let { add(SecondaryLineSpec(text = "LOC $it", role = SecondaryLineRole.Meta, required = false)) }
        }
        val noLocation = all.filterNot { !it.required && it.text.startsWith("LOC ") }
        val requiredOnly = all.filter(SecondaryLineSpec::required)
        return listOf(all, noLocation, requiredOnly)
            .distinctBy { candidate -> candidate.joinToString(separator = "|") { it.text } }
    }

    private fun resolveTextOnlyFontSizeMm(content: String): Float {
        return runCatching {
            resolveTextOnlyFontSizeMmWithPaint(content)
        }.getOrElse {
            resolveTextOnlyFontSizeMmFallback(content)
        }
    }

    private fun measureTextOnlyWidthMm(
        content: String,
        fontSizeMm: Float,
    ): Float {
        return runCatching {
            measureTextOnlyWidthMmWithPaint(content, fontSizeMm)
        }.getOrElse {
            measureTextOnlyWidthMmFallback(content, fontSizeMm)
        }
    }

    private fun resolveTextOnlyFontSizeMmWithPaint(content: String): Float {
        val maxContentHeightUnits = mm(
            ComponentLabelTemplate.TextOnly.physicalHeightMm - (TextOnlyVerticalPaddingMm * 2f),
            PreviewUnitsPerMm,
        )
        var candidate = TextOnlyMaxFontSizeMm
        while (candidate >= TextOnlyMinFontSizeMm) {
            val paint = buildTextPaint(
                color = "#111827",
                fontSizeMm = candidate,
                unitsPerMm = PreviewUnitsPerMm,
                bold = true,
            )
            val totalWidthUnits = paint.measureText(content) +
                (mm(TextOnlyHorizontalPaddingMm, PreviewUnitsPerMm) * 2f)
            val totalWidthMm = totalWidthUnits / PreviewUnitsPerMm
            val fitsHeight = paint.fontSpacing <= maxContentHeightUnits
            if (fitsHeight && totalWidthMm <= TextOnlyMaxWidthMm) {
                return candidate
            }
            candidate -= TextOnlyFontStepMm
        }
        return TextOnlyMinFontSizeMm
    }

    private fun resolveTextOnlyFontSizeMmFallback(content: String): Float {
        val maxContentHeightMm =
            ComponentLabelTemplate.TextOnly.physicalHeightMm - (TextOnlyVerticalPaddingMm * 2f)
        var candidate = TextOnlyMaxFontSizeMm
        while (candidate >= TextOnlyMinFontSizeMm) {
            val estimatedFontSpacingMm = candidate * 1.12f
            val estimatedWidthMm = estimateTextOnlyWidthMm(content, candidate)
            if (estimatedFontSpacingMm <= maxContentHeightMm && estimatedWidthMm <= TextOnlyMaxWidthMm) {
                return candidate
            }
            candidate -= TextOnlyFontStepMm
        }
        return TextOnlyMinFontSizeMm
    }

    private fun measureTextOnlyWidthMmWithPaint(
        content: String,
        fontSizeMm: Float,
    ): Float {
        val paint = buildTextPaint(
            color = "#111827",
            fontSizeMm = fontSizeMm,
            unitsPerMm = PreviewUnitsPerMm,
            bold = true,
        )
        val contentWidthUnits = paint.measureText(content)
        val totalWidthUnits = contentWidthUnits + (mm(TextOnlyHorizontalPaddingMm, PreviewUnitsPerMm) * 2f)
        val widthMm = totalWidthUnits / PreviewUnitsPerMm
        return widthMm.coerceIn(TextOnlyMinWidthMm, TextOnlyMaxWidthMm)
    }

    private fun measureTextOnlyWidthMmFallback(
        content: String,
        fontSizeMm: Float,
    ): Float = estimateTextOnlyWidthMm(content, fontSizeMm).coerceIn(TextOnlyMinWidthMm, TextOnlyMaxWidthMm)

    private fun estimateTextOnlyWidthMm(
        content: String,
        fontSizeMm: Float,
    ): Float {
        val contentWidthMm = content.sumOf { character ->
            estimateCharacterWidthFactor(character).toDouble()
        }.toFloat() * fontSizeMm
        return contentWidthMm + (TextOnlyHorizontalPaddingMm * 2f)
    }

    private fun estimateCharacterWidthFactor(character: Char): Float = when {
        character.isWhitespace() -> 0.32f
        character.code in 0x4E00..0x9FFF -> 1f
        character.isDigit() -> 0.58f
        character.isUpperCase() -> 0.62f
        character.isLowerCase() -> 0.54f
        character in "-_./|:+()[]," -> 0.34f
        else -> 0.6f
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
