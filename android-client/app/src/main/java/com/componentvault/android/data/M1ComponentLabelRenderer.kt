package com.componentvault.android.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.componentvault.android.model.ComponentLabelSeed
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Renders one complete M1 paper canvas. The caller owns and recycles the returned bitmap. */
internal object M1ComponentLabelRenderer {
    private const val Dpi = 203
    private const val EdgeInsetDots = 8f // Approximately 1 mm at 203 dpi; not a printed frame.
    private const val GapDots = 6f
    private const val MinQrModuleDots = 2
    private const val MinTextDots = 8f

    fun render(
        seed: ComponentLabelSeed,
        template: ComponentLabelTemplate,
        textTemplate: ComponentTextLabelTemplate,
        paper: M1TestPaperProfile,
    ): Bitmap {
        val width = dots(paper.widthMm)
        val height = dots(paper.heightMm)
        val offsetX = dots(paper.offsetXmm)
        val offsetY = dots(paper.offsetYmm)
        val quarterTurn = paper.rotationDegrees == 90 || paper.rotationDegrees == 270
        val logicalWidth = if (quarterTurn) height else width
        val logicalHeight = if (quarterTurn) width else height
        val safeWidth = width - 2f * EdgeInsetDots
        val safeHeight = height - 2f * EdgeInsetDots
        val contentWidth = if (quarterTurn) safeHeight else safeWidth
        val contentHeight = if (quarterTurn) safeWidth else safeHeight
        val bounds = RectF(
            (logicalWidth - contentWidth) / 2f,
            (logicalHeight - contentHeight) / 2f,
            (logicalWidth + contentWidth) / 2f,
            (logicalHeight + contentHeight) / 2f,
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            when (paper.rotationDegrees) {
                90 -> { canvas.translate(width.toFloat(), 0f); canvas.rotate(90f) }
                180 -> { canvas.translate(width.toFloat(), height.toFloat()); canvas.rotate(180f) }
                270 -> { canvas.translate(0f, height.toFloat()); canvas.rotate(-90f) }
            }
            val qrBounds = if (template.isQrLabel) {
                val payload = requireNotNull(ComponentLabelCodec.buildQrPayload(seed, template))
                drawQrLabel(canvas, bounds, seed, payload.rawValue).also { canvas.matrix.mapRect(it) }
            } else {
                drawTextOnly(canvas, bounds, ComponentLabelRenderer.buildTextLabelContent(seed, textTemplate))
                null
            }
            canvas.restore()
            // Offset is a literal paper-axis translation, not a layout resize. Reject clipped ink
            // rather than silently changing QR module size or dropping part of the label.
            if (offsetX != 0 || offsetY != 0) {
                qrBounds?.let {
                    require(it.left + offsetX >= 0 && it.top + offsetY >= 0 &&
                        it.right + offsetX <= width && it.bottom + offsetY <= height) {
                        "偏移后二维码静区不完整，请减小偏移。"
                    }
                }
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                for (y in 0 until height) for (x in 0 until width) {
                    if (pixels[y * width + x] != Color.WHITE) {
                        require(x + offsetX in 0 until width && y + offsetY in 0 until height) {
                            "偏移后会裁掉标签内容，请减小偏移或调整纸张尺寸。"
                        }
                    }
                }
                val shifted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(shifted).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, offsetX.toFloat(), offsetY.toFloat(), null)
                }
                bitmap.recycle()
                return shifted
            }
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    private fun drawQrLabel(canvas: Canvas, bounds: RectF, seed: ComponentLabelSeed, payload: String): RectF {
        val qr = try {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"),
            )
        } catch (error: Exception) {
            throw IllegalArgumentException("库存二维码内容无法编码，请缩短数据后重试。", error)
        }
        val horizontal = bounds.width() >= bounds.height() * 1.2f
        val textReserve = if (horizontal) 36f else 24f
        val qrBudget = if (horizontal) {
            min(bounds.height(), bounds.width() - textReserve - GapDots)
        } else {
            min(bounds.width(), bounds.height() - textReserve - GapDots)
        }
        val moduleDots = floor(qrBudget / qr.width).toInt()
        require(moduleDots >= MinQrModuleDots) {
            "纸张或偏移后可用空间不足：二维码需要至少 $MinQrModuleDots 点/模块和完整静区，请增大纸张或改用短二维码模板。"
        }
        val side = qr.width * moduleDots
        val qrLeft: Int
        val qrTop: Int
        val textBounds: RectF
        if (horizontal) {
            qrLeft = floor(bounds.right - side).toInt()
            qrTop = floor(bounds.centerY() - side / 2f).toInt()
            textBounds = RectF(bounds.left, bounds.top, qrLeft - GapDots, bounds.bottom)
        } else {
            qrLeft = floor(bounds.centerX() - side / 2f).toInt()
            qrTop = floor(bounds.bottom - side).toInt()
            textBounds = RectF(bounds.left, bounds.top, bounds.right, qrTop - GapDots)
        }
        require(textBounds.width() >= 24f && textBounds.height() >= 16f) {
            "纸张没有足够空间同时放置库存文字和可扫描二维码，请增大纸张。"
        }
        val qrPaint = Paint().apply { color = Color.BLACK; isAntiAlias = false; style = Paint.Style.FILL }
        for (y in 0 until qr.height) {
            for (x in 0 until qr.width) {
                if (qr[x, y]) {
                    canvas.drawRect(
                        (qrLeft + x * moduleDots).toFloat(),
                        (qrTop + y * moduleDots).toFloat(),
                        (qrLeft + (x + 1) * moduleDots).toFloat(),
                        (qrTop + (y + 1) * moduleDots).toFloat(),
                        qrPaint,
                    )
                }
            }
        }
        drawLines(
            canvas,
            textBounds,
            listOf(seed.name.ifBlank { seed.model.orEmpty().ifBlank { seed.sku } }, seed.sku,
                "${seed.packageName}  QTY ${seed.quantity}"),
            allowEllipsis = true,
        )
        return RectF(qrLeft.toFloat(), qrTop.toFloat(), (qrLeft + side).toFloat(), (qrTop + side).toFloat())
    }

    private fun drawTextOnly(canvas: Canvas, bounds: RectF, content: String) {
        require(content.isNotBlank()) { "文字标签没有可打印内容。" }
        drawLines(canvas, bounds, content.split(" | "), allowEllipsis = false)
    }

    private fun drawLines(canvas: Canvas, bounds: RectF, lines: List<String>, allowEllipsis: Boolean) {
        val visible = lines.filter(String::isNotBlank)
        require(visible.isNotEmpty()) { "标签没有可打印内容。" }
        val maxSize = min(16f, (bounds.height() / visible.size) * 0.74f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        var size = maxSize
        if (!allowEllipsis) {
            while (size >= MinTextDots) {
                paint.textSize = size
                if (visible.all { paint.measureText(it) <= bounds.width() }) break
                size -= 0.5f
            }
        }
        require(size >= MinTextDots) { "文字内容无法完整放入当前纸张，请增大纸张或缩短文字。" }
        paint.textSize = size
        val lineHeight = size * 1.24f
        val totalHeight = lineHeight * visible.size
        require(totalHeight <= bounds.height()) { "纸张高度不足以容纳标签文字。" }
        var baseline = bounds.centerY() - totalHeight / 2f - paint.fontMetrics.ascent
        visible.forEach { text ->
            val shown = if (allowEllipsis) fitWithEllipsis(text, paint, bounds.width()) else text
            require(shown.isNotEmpty()) { "纸张宽度不足以容纳标签文字。" }
            canvas.drawText(shown, bounds.left, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun fitWithEllipsis(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > width) end--
        return if (end > 0) text.substring(0, end) + ellipsis else ""
    }

    private fun dots(mm: Float): Int = (mm * Dpi / 25.4f).roundToInt()
}
