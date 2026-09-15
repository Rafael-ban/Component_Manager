package com.componentvault.android.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.componentvault.android.R

internal enum class InventoryDestination(
    @StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    Overview(R.string.destination_overview, Icons.Outlined.Home),
    Inventory(R.string.destination_inventory, Icons.Outlined.Inventory2),
    Movements(R.string.destination_movements, Icons.Outlined.SwapHoriz),
    Settings(R.string.destination_settings, Icons.Outlined.Settings),
}
