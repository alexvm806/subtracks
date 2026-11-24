package com.subtracks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Night =
    darkColorScheme(
        primary = Color(0xFFFFB591),
        onPrimary = Color(0xFF52260F),
        background = Color(0xFF181715),
        onBackground = Color(0xFFF6EFE6),
        surface = Color(0xFF181715),
        onSurface = Color(0xFFF6EFE6),
        surfaceContainer = Color(0xFF24211F),
        surfaceContainerHigh = Color(0xFF302B27),
        onSurfaceVariant = Color(0xFFD2C4B9),
        secondary = Color(0xFFE2BFA9),
    )

private val Day =
    lightColorScheme(
        primary = Color(0xFF883C1C),
        onPrimary = Color.White,
        background = Color(0xFFFFF9F2),
        onBackground = Color(0xFF24201D),
        surface = Color(0xFFFFF9F2),
        onSurface = Color(0xFF24201D),
        surfaceContainer = Color(0xFFF4EDE5),
        surfaceContainerHigh = Color(0xFFEDE4DA),
        onSurfaceVariant = Color(0xFF5C4C42),
        secondary = Color(0xFF735442),
    )

@Composable
fun SubtracksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else Day,
        typography = SubtracksTypography,
        content = content,
    )
}
