package com.componentvault.android.model

data class StockMovementRecord(
    val id: String,
    val componentId: String,
    val componentSku: String,
    val componentName: String,
    val movementType: String,
    val quantity: Int,
    val reason: String,
    val note: String,
    val happenedAt: String,
    val updatedAt: String,
    val deleted: Boolean,
)
