package com.cse3310.projecthealthmonitoringsystem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PhmsLightColorScheme = lightColorScheme(
    primary = LeafPrimary,
    onPrimary = LeafOnPrimary,
    primaryContainer = LeafPrimaryContainer,
    onPrimaryContainer = LeafOnPrimaryContainer,
    secondary = LeafSecondaryText,
    onSecondary = LeafOnPrimary,
    secondaryContainer = LeafSecondaryContainer,
    onSecondaryContainer = LeafPrimaryText,
    tertiary = PhmsChartAccent,
    onTertiary = LeafPrimaryText,
    tertiaryContainer = LeafPrimaryContainer,
    onTertiaryContainer = LeafOnPrimaryContainer,
    error = LeafError,
    onError = androidx.compose.ui.graphics.Color.White,
    background = LeafBackground,
    onBackground = LeafPrimaryText,
    surface = LeafSurface,
    onSurface = LeafPrimaryText,
    surfaceVariant = LeafSecondaryContainer,
    onSurfaceVariant = LeafSecondaryText,
    outline = LeafOutline,
    outlineVariant = LeafOutline
)

private val PhmsDarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFB8D8C3),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF102F30),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF102F30),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCEDE5),
    secondary = androidx.compose.ui.graphics.Color(0xFFB8D8C3),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF102F30),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF102F30),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFF5F7F6),
    tertiary = androidx.compose.ui.graphics.Color(0xFFB8D8C3),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF102F30),
    error = LeafError,
    onError = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFF101818),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E8E6),
    surface = androidx.compose.ui.graphics.Color(0xFF171F1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E8E6),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF293332),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFBFCAC7),
    outline = androidx.compose.ui.graphics.Color(0xFF899492),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3A4543)
)

@Composable
fun ProjectHealthMonitoringSystemTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PhmsDarkColorScheme else PhmsLightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
