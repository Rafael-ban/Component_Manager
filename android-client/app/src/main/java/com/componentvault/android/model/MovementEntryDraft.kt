package com.componentvault.android.model

data class MovementEntryDraft(
    val componentId: String,
    val movementType: String,
    val quantity: Int,
    val reason: String,
    val note: String,
)
