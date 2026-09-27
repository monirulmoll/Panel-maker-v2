package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FloatConfigLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = TextDark,
    secondary = SecondaryIndigo,
    onSecondary = SurfaceWhite,
    secondaryContainer = SecondaryContainerIndigo,
    onSecondaryContainer = SecondaryIndigo,
    tertiary = SuccessGreen,
    onTertiary = SurfaceWhite,
    background = BackgroundLight,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = SurfaceVariantSoft,
    onSurfaceVariant = TextMedium,
    outline = BorderSoft,
    error = ErrorRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FloatConfigLightColorScheme,
        typography = Typography,
        content = content
    )
}
