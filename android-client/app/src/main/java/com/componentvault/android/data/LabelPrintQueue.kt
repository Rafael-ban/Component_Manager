package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import java.util.UUID

internal enum class LabelPrintItemState { Pending, Sending, Sent, Uncertain, Failed, Skipped }

internal data class LabelPrintItem(
    val id: String,
    val seed: ComponentLabelSeed,
    val copyNumber: Int,
    val copies: Int,
    val state: LabelPrintItemState = LabelPrintItemState.Pending,
    val detail: String = "",
)

internal data class LabelPrintQueue(
    val items: List<LabelPrintItem> = emptyList(),
    val templateId: String = ComponentLabelTemplate.default.id,
    val textTemplateId: String = ComponentTextLabelTemplate.default.id,
    val paper: M1TestPaperProfile = M1TestPaperProfile(40f, 60f),
) {
    /** A send interrupted by process death may already have reached the printer. */
    fun recovered(): LabelPrintQueue = copy(items = items.map { item ->
        if (item.state == LabelPrintItemState.Sending) {
            item.copy(state = LabelPrintItemState.Uncertain)
        } else {
            item
        }
    })

    fun update(id: String, state: LabelPrintItemState, detail: String = ""): LabelPrintQueue {
        require(items.any { it.id == id }) { "Print item does not exist: $id" }
        return copy(items = items.map { item ->
            if (item.id == id) item.copy(state = state, detail = detail) else item
        })
    }

    companion object {
        const val MaxItems = 500
        const val MaxCopies = 99

        fun create(
            selections: List<Pair<ComponentLabelSeed, Int>>,
            templateId: String = ComponentLabelTemplate.default.id,
            textTemplateId: String = ComponentTextLabelTemplate.default.id,
            paper: M1TestPaperProfile = M1TestPaperProfile(40f, 60f),
        ): LabelPrintQueue {
            require(selections.isNotEmpty()) { "Select at least one component to print" }
            var total = 0
            val items = selections.flatMap { (seed, copies) ->
                require(copies in 1..MaxCopies) { "Copies must be between 1 and $MaxCopies" }
                total += copies
                require(total <= MaxItems) { "Print job may contain at most $MaxItems labels" }
                (1..copies).map { copyNumber ->
                    LabelPrintItem(UUID.randomUUID().toString(), seed, copyNumber, copies)
                }
            }
            return LabelPrintQueue(items, templateId, textTemplateId, paper)
        }
    }
}
