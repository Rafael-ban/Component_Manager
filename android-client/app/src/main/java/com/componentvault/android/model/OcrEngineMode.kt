package com.componentvault.android.model

enum class OcrEngineMode(
    val storageValue: String,
) {
    Auto("auto"),
    MlKit("mlkit"),
    PaddleExperimental("paddle_experimental"),
    ;

    companion object {
        fun fromStorageValue(value: String?): OcrEngineMode {
            return entries.firstOrNull { mode ->
                mode.storageValue.equals(value?.trim(), ignoreCase = true)
            }?.takeUnless { it == PaddleExperimental } ?: Auto
        }
    }
}
