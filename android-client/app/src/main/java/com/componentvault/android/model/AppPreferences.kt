package com.componentvault.android.model

data class AppPreferences(
    val defaultImportLocation: String = "",
    val lastImportLocation: String = "",
    val defaultImportMinStock: Int = 0,
    val rememberLastImportLocation: Boolean = true,
    val syncAfterLocalChanges: Boolean = false,
    val enableLocalAutoRecognition: Boolean = true,
    val preferAggressiveAutoRecognition: Boolean = true,
    val enableLocalImportLearning: Boolean = true,
    val enableServerJlcLookup: Boolean = false,
    val ocrEngineMode: OcrEngineMode = OcrEngineMode.Auto,
    val appLanguage: AppLanguage = AppLanguage.ZhCn,
) {
    val suggestedImportLocation: String
        get() = if (rememberLastImportLocation && lastImportLocation.isNotBlank()) {
            lastImportLocation
        } else {
            defaultImportLocation
        }
}
