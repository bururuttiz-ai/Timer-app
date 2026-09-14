package com.familystudytimer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF00B37E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F5DD),
    onPrimaryContainer = Color(0xFF003826),
    secondary = Color(0xFFFF7A59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D6),
    onSecondaryContainer = Color(0xFF5C1F0B),
    tertiary = Color(0xFFFFC93C),
    onTertiary = Color(0xFF4A3800),
    background = Color(0xFFFFFBF3),
    onBackground = Color(0xFF2A2620),
    surface = Color.White,
    onSurface = Color(0xFF2A2620),
    surfaceVariant = Color(0xFFFFF1E4),
    error = Color(0xFFE5484D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAB9),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00543D),
    onPrimaryContainer = Color(0xFFB6F5DD),
    secondary = Color(0xFFFF9B80),
    onSecondary = Color(0xFF4A1C0C),
    secondaryContainer = Color(0xFF7A3016),
    onSecondaryContainer = Color(0xFFFFE0D6),
    tertiary = Color(0xFFFFD873),
    onTertiary = Color(0xFF4A3800),
    background = Color(0xFF16181D),
    onBackground = Color(0xFFEDECE8),
    surface = Color(0xFF1E2128),
    onSurface = Color(0xFFEDECE8),
    surfaceVariant = Color(0xFF33291F),
    error = Color(0xFFFF6B6E),
)

private val PopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun StudyTimerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = PopShapes, content = content)
}
