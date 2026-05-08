package com.componentvault.android.ui.screen.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Phone / Light",
    group = "Phone",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
annotation class InventoryPhonePreview

@Preview(
    name = "Tablet / Light",
    group = "Tablet",
    showBackground = true,
    widthDp = 1280,
    heightDp = 800,
)
annotation class InventoryTabletPreview

@Preview(
    name = "Phone / Dark",
    group = "Theme",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class InventoryDarkPreview

@Preview(
    name = "Phone / zh-CN",
    group = "Locale",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    locale = "zh-rCN",
)
annotation class InventoryZhCnPreview

@Preview(
    name = "Phone / Large Font",
    group = "Accessibility",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
)
annotation class InventoryLargeFontPreview

@Preview(
    name = "Shell / Phone / Light",
    group = "Shell",
    showBackground = true,
    showSystemUi = true,
    widthDp = 412,
    heightDp = 915,
)
annotation class InventoryShellPhonePreview

@Preview(
    name = "Shell / Tablet / Light",
    group = "Shell",
    showBackground = true,
    showSystemUi = true,
    widthDp = 1280,
    heightDp = 800,
)
annotation class InventoryShellTabletPreview

@Preview(
    name = "Dialog / Light",
    group = "Dialogs",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
annotation class InventoryDialogPreview
