package com.componentvault.android.model

data class StorageLocationRecord(
    val id: String,
    val name: String,
    val updatedAt: String,
    val deleted: Boolean = false,
)

data class ComponentAllocationRecord(
    val componentId: String,
    val locationId: String,
    val locationName: String,
    val quantity: Int,
)
