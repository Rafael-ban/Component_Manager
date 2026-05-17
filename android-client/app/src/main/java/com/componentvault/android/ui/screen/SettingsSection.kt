package com.componentvault.android.ui.screen

internal enum class SettingsSection {
    Sync,
    ImportAndOcr,
    App,
    About,
}

internal fun SettingsSection.title(strings: ComponentVaultStrings): String = when (this) {
    SettingsSection.Sync -> strings.settings.connectionTitle
    SettingsSection.ImportAndOcr -> strings.settings.importPreferencesTitle
    SettingsSection.App -> strings.settings.languageTitle
    SettingsSection.About -> strings.settings.aboutTitle
}

internal fun SettingsSection.supporting(strings: ComponentVaultStrings): String = when (this) {
    SettingsSection.Sync -> strings.settings.connectionSubtitle
    SettingsSection.ImportAndOcr -> strings.settings.importPreferencesSubtitle
    SettingsSection.App -> strings.settings.languageSubtitle
    SettingsSection.About -> strings.settings.aboutSubtitle
}
