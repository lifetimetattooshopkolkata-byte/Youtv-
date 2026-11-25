package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val YouTvDarkColorScheme = darkColorScheme(
    primary = YouTvRed,
    onPrimary = YouTvWhite,
    primaryContainer = YouTvRedContainer,
    onPrimaryContainer = YouTvWhite,
    secondary = YouTvRedBright,
    onSecondary = YouTvWhite,
    secondaryContainer = YouTvSurfaceBright,
    onSecondaryContainer = YouTvWhite,
    tertiary = YouTvInfo,
    onTertiary = YouTvBlack,
    background = YouTvBlack,
    onBackground = YouTvWhite,
    surface = YouTvSurface,
    onSurface = YouTvWhite,
    surfaceVariant = YouTvSurfaceElevated,
    onSurfaceVariant = YouTvTextSecondary,
    surfaceContainerLowest = YouTvBlack,
    surfaceContainerLow = YouTvSurface,
    surfaceContainer = YouTvSurfaceElevated,
    surfaceContainerHigh = YouTvSurfaceBright,
    outline = YouTvBorder,
    outlineVariant = YouTvBorder,
    error = YouTvRedBright,
    onError = YouTvWhite
)

private val AdmitDarkColorScheme = darkColorScheme(
    primary = AdmitAccentCrimson,
    onPrimary = AdmitTextPrimary,
    primaryContainer = Color(0xFF450A0A),
    onPrimaryContainer = AdmitTextPrimary,
    secondary = AdmitAccentAmber,
    onSecondary = AdmitObsidian,
    secondaryContainer = AdmitCardElevated,
    onSecondaryContainer = AdmitTextPrimary,
    tertiary = AdmitAccentEmerald,
    onTertiary = AdmitObsidian,
    background = AdmitObsidian,
    onBackground = AdmitTextPrimary,
    surface = AdmitSlate,
    onSurface = AdmitTextPrimary,
    surfaceVariant = AdmitCard,
    onSurfaceVariant = AdmitTextMuted,
    surfaceContainerLowest = AdmitObsidian,
    surfaceContainerLow = AdmitSlate,
    surfaceContainer = AdmitCard,
    surfaceContainerHigh = AdmitCardElevated,
    outline = AdmitBorder,
    outlineVariant = AdmitBorder,
    error = AdmitAccentCrimson,
    onError = AdmitTextPrimary
)

val YouTvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YouTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YouTvDarkColorScheme,
        typography = YouTvTypography,
        shapes = YouTvShapes,
        content = content
    )
}

@Composable
fun AdmitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdmitDarkColorScheme,
        typography = YouTvTypography,
        shapes = YouTvShapes,
        content = content
    )
}
