package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import kotlin.math.max
import kotlin.math.min

/** Editable boxes in physical paper coordinates, before the printer's paper offset. */
internal enum class LabelElementType { Text, Qr }

internal data class LabelElement(
    val id: String,
    val type: LabelElementType,
    val text: String,
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val heightMm: Float,
)

internal data class LabelDesign(val elements: List<LabelElement>) {
    fun withText(id: String, text: String): LabelDesign = copy(elements = elements.map {
        if (it.id == id && it.type == LabelElementType.Text) it.copy(text = text) else it
    })

    fun moved(id: String, xMm: Float, yMm: Float, paper: M1TestPaperProfile): LabelDesign = copy(
        elements = elements.map { element ->
            if (element.id != id) element else element.copy(
                xMm = xMm.coerceIn(0f, max(0f, paper.widthMm - element.widthMm)),
                yMm = yMm.coerceIn(0f, max(0f, paper.heightMm - element.heightMm)),
            )
        },
    )

    fun validate(paper: M1TestPaperProfile) {
        require(elements.isNotEmpty() && elements.map { it.id }.toSet().size == elements.size) {
            "标签元素缺失或重复。"
        }
        elements.forEach {
            require(it.id.isNotBlank() && it.xMm.isFinite() && it.yMm.isFinite() &&
                it.widthMm.isFinite() && it.heightMm.isFinite() &&
                it.widthMm > 0f && it.heightMm > 0f &&
                it.xMm >= 0f && it.yMm >= 0f &&
                it.xMm + it.widthMm <= paper.widthMm + 0.001f &&
                it.yMm + it.heightMm <= paper.heightMm + 0.001f) {
                "标签元素超出纸张，请调整位置或尺寸。"
            }
        }
    }

    companion object {
        fun default(
            seed: ComponentLabelSeed,
            template: ComponentLabelTemplate,
            textTemplate: ComponentTextLabelTemplate,
            paper: M1TestPaperProfile,
        ): LabelDesign {
            val turn = paper.rotationDegrees == 90 || paper.rotationDegrees == 270
            val logicalW = if (turn) paper.heightMm else paper.widthMm
            val logicalH = if (turn) paper.widthMm else paper.heightMm
            val margin = 1f
            val gap = 0.8f
            val left = margin
            val top = margin
            val w = logicalW - 2 * margin
            val h = logicalH - 2 * margin
            fun box(id: String, type: LabelElementType, text: String,
                    x: Float, y: Float, width: Float, height: Float): LabelElement {
                val (paperX, paperY, paperW, paperH) = when (paper.rotationDegrees) {
                    90 -> listOf(paper.widthMm - y - height, x, height, width)
                    180 -> listOf(paper.widthMm - x - width, paper.heightMm - y - height, width, height)
                    270 -> listOf(y, paper.heightMm - x - width, height, width)
                    else -> listOf(x, y, width, height)
                }
                return LabelElement(id, type, text, paperX, paperY, paperW, paperH)
            }
            if (!template.isQrLabel) {
                return LabelDesign(listOf(box("text", LabelElementType.Text,
                    ComponentLabelRenderer.buildTextLabelContent(seed, textTemplate), left, top, w, h)))
            }
            val horizontal = w >= h * 1.2f
            val reserve = if (horizontal) 4.5f else 3f
            val side = min(if (horizontal) h else w, if (horizontal) w - reserve - gap else h - reserve - gap)
            require(side > 0f) { "纸张不足以同时放置文字和二维码。" }
            val qrX = if (horizontal) left + w - side else left + (w - side) / 2f
            val qrY = if (horizontal) top + (h - side) / 2f else top + h - side
            val textX = left
            val textY = top
            val textW = if (horizontal) qrX - gap - left else w
            val textH = if (horizontal) h else qrY - gap - top
            val lines = listOf(
                "name" to seed.name.ifBlank { seed.model.orEmpty().ifBlank { seed.sku } },
                "sku" to seed.sku,
                "details" to "${seed.packageName}  QTY ${seed.quantity}",
            )
            val rowH = textH / lines.size
            return LabelDesign(lines.mapIndexed { index, (id, value) ->
                box(id, LabelElementType.Text, value, textX, textY + index * rowH, textW, rowH)
            } + box("qr", LabelElementType.Qr, "", qrX, qrY, side, side))
        }
    }
}
