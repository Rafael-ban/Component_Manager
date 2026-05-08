package com.componentvault.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultLightColorScheme: ColorScheme = lightColorScheme(
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

private val VaultDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF72D7CB),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFFD5F3EE),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF183734),
    secondaryContainer = Color(0xFF304B47),
    onSecondaryContainer = Color(0xFFCDE8E2),
    tertiary = Color(0xFFA9C7FF),
    tertiaryContainer = Color(0xFF20456F),
    background = Color(0xFF0F1413),
    surface = Color(0xFF171C1B),
    surfaceVariant = Color(0xFF2D3634),
    outline = Color(0xFF889693),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

@Composable
fun ComponentVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            VaultDarkColorScheme
        } else {
            VaultLightColorScheme
        },
        typography = VaultTypography,
        content = content,
    )
}
