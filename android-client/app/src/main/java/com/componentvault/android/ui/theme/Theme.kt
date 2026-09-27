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
    surfaceDim = Color(0xFFD4DEDB),
    surfaceBright = Color(0xFFF8FCFA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6F3),
    surfaceContainer = Color(0xFFEAF1EE),
    surfaceContainerHigh = Color(0xFFE4ECE8),
    surfaceContainerHighest = Color(0xFFDEE6E2),
    onBackground = Color(0xFF171D1B),
    onSurface = Color(0xFF171D1B),
    onSurfaceVariant = Color(0xFF414A47),
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
    surfaceDim = Color(0xFF0F1413),
    surfaceBright = Color(0xFF353C39),
    surfaceContainerLowest = Color(0xFF0A100E),
    surfaceContainerLow = Color(0xFF171C1B),
    surfaceContainer = Color(0xFF1B2220),
    surfaceContainerHigh = Color(0xFF252C29),
    surfaceContainerHighest = Color(0xFF303734),
    onBackground = Color(0xFFDEE5E1),
    onSurface = Color(0xFFDEE5E1),
    onSurfaceVariant = Color(0xFFBECAC4),
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
