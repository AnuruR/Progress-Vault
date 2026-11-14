package com.anuru.progressvault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VaultColorScheme = darkColorScheme(
    primary = VaultColors.Cyan,
    onPrimary = VaultColors.OnAccent,
    primaryContainer = VaultColors.SurfaceRaised,
    onPrimaryContainer = VaultColors.Cyan,
    secondary = VaultColors.Purple,
    onSecondary = VaultColors.OnAccent,
    secondaryContainer = VaultColors.SurfaceRaised,
    onSecondaryContainer = VaultColors.TextPrimary,
    tertiary = VaultColors.Green,
    onTertiary = VaultColors.OnAccent,
    background = VaultColors.Background,
    onBackground = VaultColors.TextPrimary,
    surface = VaultColors.Surface,
    onSurface = VaultColors.TextPrimary,
    surfaceVariant = VaultColors.SurfaceRaised,
    onSurfaceVariant = VaultColors.TextSecondary,
    surfaceContainerLowest = VaultColors.Background,
    surfaceContainerLow = VaultColors.Surface,
    surfaceContainer = VaultColors.Surface,
    surfaceContainerHigh = VaultColors.SurfaceRaised,
    surfaceContainerHighest = VaultColors.SurfaceRaised,
    outline = VaultColors.BorderStrong,
    outlineVariant = VaultColors.Border,
    error = VaultColors.Error,
    onError = VaultColors.OnAccent,
    scrim = VaultColors.Background,
)

private val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Progress Vault is always dark — there is intentionally no light scheme. */
@Composable
fun ProgressVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content,
    )
}
