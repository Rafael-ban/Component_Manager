package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class LabelPrintQueueTest {
    private fun seed(sku: String) = ComponentLabelSeed(
        sku = sku, name = "Part $sku", category = "IC", packageName = "SOIC-8",
        location = "A1", quantity = 12, minStock = 3,
    )

    @Test
    fun createPreservesSelectionAndCopyOrder() {
        val first = seed("A")
        val second = seed("B")
        val queue = LabelPrintQueue.create(listOf(first to 2, second to 3))

        assertEquals(listOf(first, first, second, second, second), queue.items.map { it.seed })
        assertEquals(listOf(1, 2, 1, 2, 3), queue.items.map { it.copyNumber })
        assertEquals(listOf(2, 2, 3, 3, 3), queue.items.map { it.copies })
        assertEquals(5, queue.items.map { it.id }.toSet().size)
        assertEquals(List(5) { LabelPrintItemState.Pending }, queue.items.map { it.state })
    }

    @Test
    fun createRejectsEmptyInvalidCopiesAndOversizedJob() {
        assertFailsWith<IllegalArgumentException> { LabelPrintQueue.create(emptyList()) }
        assertFailsWith<IllegalArgumentException> { LabelPrintQueue.create(listOf(seed("A") to 0)) }
        assertFailsWith<IllegalArgumentException> { LabelPrintQueue.create(listOf(seed("A") to 100)) }
        val max = LabelPrintQueue.create(List(5) { seed("$it") to 99 } + (seed("last") to 5))
        assertEquals(500, max.items.size)
        assertFailsWith<IllegalArgumentException> {
            LabelPrintQueue.create(List(5) { seed("$it") to 99 } + (seed("last") to 6))
        }
    }

    @Test
    fun recoveryOnlyMarksInFlightItemUncertain() {
        val queue = LabelPrintQueue.create(listOf(seed("A") to 5))
        val states = listOf(
            LabelPrintItemState.Sending, LabelPrintItemState.Sent, LabelPrintItemState.Skipped,
            LabelPrintItemState.Uncertain, LabelPrintItemState.Failed,
        )
        val updated = queue.copy(items = queue.items.zip(states).map { (item, state) ->
            item.copy(state = state, detail = state.name)
        })
        val recovered = updated.recovered()

        assertEquals(LabelPrintItemState.Uncertain, recovered.items.first().state)
        assertEquals(states.drop(1), recovered.items.drop(1).map { it.state })
        assertEquals(updated.items.map { it.detail }, recovered.items.map { it.detail })
        assertEquals(recovered, recovered.recovered())
        assertNotEquals(updated, recovered)
    }

    @Test
    fun updateChangesOnlyNamedItemAndDoesNotRetryAutomatically() {
        val queue = LabelPrintQueue.create(listOf(seed("A") to 2))
        val id = queue.items.first().id
        val updated = queue.update(id, LabelPrintItemState.Uncertain, "Printer disconnected")
        assertEquals(LabelPrintItemState.Uncertain, updated.items.first().state)
        assertEquals("Printer disconnected", updated.items.first().detail)
        assertEquals(queue.items.last(), updated.items.last())
        assertFailsWith<IllegalArgumentException> { queue.update("missing", LabelPrintItemState.Sent) }
    }
}
