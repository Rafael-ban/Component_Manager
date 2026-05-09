package com.componentvault.android.model

data class AppPreferences(
    val defaultImportLocation: String = "",
    val lastImportLocation: String = "",
    val defaultImportMinStock: Int = 0,
    val rememberLastImportLocation: Boolean = true,
    val syncAfterLocalChanges: Boolean = false,
    val scannerAutoZoomEnabled: Boolean = true,
) {
    val suggestedImportLocation: String
        get() = if (rememberLastImportLocation && lastImportLocation.isNotBlank()) {
            lastImportLocation
        } else {
            defaultImportLocation
        }
}
