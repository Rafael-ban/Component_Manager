package com.componentvault.android.model

data class OperationResult(
    val isSuccess: Boolean,
    val message: String,
    val entityId: String? = null,
)
