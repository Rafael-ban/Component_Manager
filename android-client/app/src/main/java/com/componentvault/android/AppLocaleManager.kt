package com.componentvault.android

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.componentvault.android.model.AppLanguage

internal object AppLocaleManager {
    private const val prefsName = "component_vault_sync"
    private const val keyAppLanguage = "app_language"

    fun ensureInitialized(context: Context): AppLanguage {
        val preferences = context.applicationContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )
        val language = AppLanguage.fromStorageValue(
            preferences.getString(keyAppLanguage, null),
        )
        if (!preferences.contains(keyAppLanguage)) {
            preferences.edit()
                .putString(keyAppLanguage, language.storageValue)
                .apply()
        }
        apply(language)
        return language
    }

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.storageValue),
        )
    }
}
