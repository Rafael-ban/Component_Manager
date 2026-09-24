package com.componentvault.android.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelPrintOutcomeTest {
    private fun reply(finished: Int = 0, errors: List<Int> = emptyList(), overflow: Boolean = false) =
        M1QueryReply("M1".toByteArray(), errors, overflow, finished)
    private fun confirmation(reply: M1QueryReply) = confirmPostPrintModel(query = { reply }, drain = { M1QueryReply(byteArrayOf(), emptyList()) })

    @Test fun aModelReplyWithoutProcessingNotificationCannotAdvanceTheQueue() {
        val feed = M1QueryReply(byteArrayOf(), emptyList())
        assertFalse(m1BatchReady(feed, confirmation(reply())))
        assertTrue(m1BatchReady(feed, confirmation(reply(finished = 1))))
        assertTrue(m1BatchReady(feed.copy(asyncPrintFinishCount = 1), confirmation(reply())))
    }

    @Test fun latePrinterErrorOrOverflowPausesEvenWithAnM1Reply() {
        val feed = M1QueryReply(byteArrayOf(), emptyList())
        assertFalse(m1BatchReady(feed, confirmation(reply(finished = 1, errors = listOf(0x01)))))
        assertFalse(m1BatchReady(feed.copy(overflowed = true), confirmation(reply(finished = 1))))
        assertFalse(m1BatchReady(feed, confirmation(reply(finished = 1, overflow = true))))
    }

    @Test fun attemptedOrUnacknowledgedWritesRequireExplicitReview() {
        assertEquals(LabelPrintItemState.Sent, labelPrintOutcomeState(M1TestPrintResult.SentUnconfirmed, true))
        for (result in listOf(M1TestPrintResult.SentConnectionLost, M1TestPrintResult.Partial, M1TestPrintResult.SentUnconfirmed)) {
            assertEquals(LabelPrintItemState.Uncertain, labelPrintOutcomeState(result, false))
        }
        assertEquals(LabelPrintItemState.Failed, labelPrintOutcomeState(M1TestPrintResult.Rejected, false))
        assertEquals(LabelPrintItemState.Failed, labelPrintOutcomeState(M1TestPrintResult.Interrupted, false))
    }
}
