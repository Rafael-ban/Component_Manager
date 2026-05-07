package com.componentvault.android.model

data class ComponentRecord(
    val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val packageName: String,
    val location: String,
    val description: String,
    val quantity: Int,
    val minStock: Int,
    val updatedAt: String,
    val deleted: Boolean,
) {
    val isLowStock: Boolean
        get() = !deleted && quantity <= minStock
}

