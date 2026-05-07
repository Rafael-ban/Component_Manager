package com.componentvault.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VaultColorScheme: ColorScheme = lightColorScheme(
    primary = VaultPrimary,
    onPrimary = VaultOnPrimary,
    primaryContainer = VaultPrimaryContainer,
    onPrimaryContainer = VaultOnPrimaryContainer,
    secondary = VaultSecondary,
    onSecondary = VaultOnSecondary,
    secondaryContainer = VaultSecondaryContainer,
    onSecondaryContainer = VaultOnSecondaryContainer,
    tertiary = VaultTertiary,
    tertiaryContainer = VaultTertiaryContainer,
    background = VaultBackground,
    surface = VaultSurface,
    surfaceVariant = VaultSurfaceVariant,
    outline = VaultOutline,
    error = VaultError,
    errorContainer = VaultErrorContainer,
)

@Composable
fun ComponentVaultTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = VaultTypography,
        content = content,
    )
}
