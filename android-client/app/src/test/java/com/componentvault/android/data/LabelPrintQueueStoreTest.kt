package com.componentvault.android.data

import com.componentvault.android.model.ComponentLabelSeed
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LabelPrintQueueStoreTest {
    private val seed = ComponentLabelSeed(
        sku = "C123", name = "Resistor", category = "Passive", packageName = "0603",
        location = "Drawer 2", quantity = 120, minStock = 10,
        model = "RC0603", brand = "Acme", sourceLabel = "supplier",
        rawPayload = "{raw:payload}", notes = listOf("batch 1", "fragile"),
    )

    @Test
    fun jsonRoundTripPreservesFullSnapshotAndPaper() {
        val queue = LabelPrintQueue.create(
            listOf(seed to 2), templateId = ComponentLabelTemplate.Qr10x40.id,
            textTemplateId = ComponentTextLabelTemplate.NameModel.id,
            paper = M1TestPaperProfile(25f, 40f, 270, -1.5f, 2.25f),
        )
        val updated = queue.update(queue.items.first().id, LabelPrintItemState.Sent, "Printed")
        assertEquals(updated, LabelPrintQueueCodec.decode(LabelPrintQueueCodec.encode(updated)))
        assertEquals(seed, LabelPrintQueueCodec.decode(LabelPrintQueueCodec.encode(updated)).items.first().seed)
    }

    @Test
    fun loadPersistsRecoveryAndPreservesCompletedStates() {
        val context = RuntimeEnvironment.getApplication()
        val store = LabelPrintQueueStore(context)
        store.clear()
        try {
            val queue = LabelPrintQueue.create(listOf(seed to 4))
            val items = queue.items.mapIndexed { index, item ->
                item.copy(state = listOf(
                    LabelPrintItemState.Sending, LabelPrintItemState.Sent,
                    LabelPrintItemState.Skipped, LabelPrintItemState.Uncertain,
                )[index])
            }
            store.save(queue.copy(items = items))
            val loaded = store.load()
            assertEquals(
                listOf(LabelPrintItemState.Uncertain, LabelPrintItemState.Sent,
                    LabelPrintItemState.Skipped, LabelPrintItemState.Uncertain),
                loaded.items.map { it.state },
            )
            assertEquals(loaded, LabelPrintQueueCodec.decode(
                context.filesDir.resolve("label-print-queue.json").readText(),
            ))
            store.clear()
            assertEquals(LabelPrintQueue(), store.load())
        } finally {
            store.clear()
        }
    }

    @Test
    fun corruptQueueIsDiagnosedRatherThanDiscarded() {
        val context = RuntimeEnvironment.getApplication()
        val store = LabelPrintQueueStore(context)
        store.clear()
        try {
            context.filesDir.resolve("label-print-queue.json").writeText("{broken")
            val error = assertFailsWith<IllegalStateException> { store.load() }
            assertTrue(error.message.orEmpty().contains("clear it explicitly"))
        } finally {
            store.clear()
        }
    }
}
