package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ProducerCyan,
    onPrimary = StudioVoid,
    primaryContainer = ProducerCyanSurface,
    onPrimaryContainer = ProducerCyan,
    secondary = EngineerAmber,
    onSecondary = StudioVoid,
    secondaryContainer = EngineerAmberSurface,
    onSecondaryContainer = EngineerAmber,
    tertiary = LyricistViolet,
    onTertiary = StudioVoid,
    tertiaryContainer = LyricistVioletSurface,
    onTertiaryContainer = LyricistViolet,
    background = StudioVoid,
    onBackground = StudioTextPrimary,
    surface = StudioConsoleBg,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioRackSurface,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainer = StudioRackSurface,
    surfaceContainerHigh = StudioCardElevated,
    outline = StudioBorder,
    error = MeterPeakRed,
    onError = StudioTextPrimary
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun HydrogenStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = HydrogenTypography,
        shapes = StudioShapes,
        content = content
    )
}
