package com.componentvault.android.model

enum class AppLanguage(
    val storageValue: String,
) {
    ZhCn("zh-CN"),
    English("en"),
    ;

    companion object {
        fun fromStorageValue(value: String?): AppLanguage {
            return entries.firstOrNull { language ->
                language.storageValue.equals(value?.trim(), ignoreCase = true)
            } ?: ZhCn
        }
    }
}
