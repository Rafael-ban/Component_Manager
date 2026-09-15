package com.componentvault.android.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StockMovementRecordTest {
    @Test
    fun outboundHistoryUsesANegativeChangeWithoutChangingStoredMagnitude() {
        val movement = record("outbound", 1)
        assertEquals(-1L, movement.quantityChange)
        assertEquals(1, movement.quantity)
    }

    @Test
    fun inboundAndSignedAdjustmentsKeepTheirDirections() {
        assertEquals(1L, record("inbound", 1).quantityChange)
        assertEquals(-2L, record("adjustment", -2).quantityChange)
        assertEquals(2L, record("adjustment", 2).quantityChange)
        assertEquals(0L, record("adjustment", 0).quantityChange)
    }

    @Test
    fun importedSignedOutboundDoesNotBecomePositive() {
        assertEquals(-1L, record("OUTBOUND", -1).quantityChange)
        assertEquals(-2147483648L, record("outbound", Int.MIN_VALUE).quantityChange)
    }

    private fun record(type: String, quantity: Int) = StockMovementRecord(
        id = "movement", componentId = "component", componentSku = "C70565",
        componentName = "Crystal", movementType = type, quantity = quantity,
        reason = "项目", note = "", happenedAt = "", updatedAt = "", deleted = false,
    )
}
