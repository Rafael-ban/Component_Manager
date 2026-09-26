package com.componentvault.android.data

internal data class BatchTransferLine(
    val componentId: String,
    val quantity: Int,
    val expectedUpdatedAt: String,
)
