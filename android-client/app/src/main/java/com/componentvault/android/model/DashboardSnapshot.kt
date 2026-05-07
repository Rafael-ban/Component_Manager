package com.componentvault.android.model

data class DashboardSnapshot(
    val componentCount: Int,
    val totalUnits: Int,
    val lowStockCount: Int,
    val movementCount: Int,
)

