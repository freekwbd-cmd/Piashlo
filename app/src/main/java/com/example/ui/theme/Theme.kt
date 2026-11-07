package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun PiashAgentTheme(
    darkTheme: Boolean = true,
    amoledMode: Boolean = true,
    accentPreset: AccentPreset = AccentPreset.CYAN,
    content: @Composable () -> Unit
) {
    val primaryColor = accentPreset.primary
    val secondaryColor = accentPreset.secondary

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.16f),
            onPrimaryContainer = primaryColor,
            secondary = secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = secondaryColor.copy(alpha = 0.18f),
            onSecondaryContainer = secondaryColor,
            tertiary = EmeraldAccent,
            background = if (amoledMode) AmoledBlack else SecondaryBackground,
            onBackground = TextPrimaryDark,
            surface = if (amoledMode) SurfaceDark else ElevatedSurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = ElevatedSurfaceDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = BorderDark,
            outlineVariant = BorderSubtle,
            error = ErrorRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = TextPrimaryLight,
            secondary = secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = secondaryColor.copy(alpha = 0.15f),
            onSecondaryContainer = TextPrimaryLight,
            tertiary = EmeraldAccent,
            background = BackgroundLight,
            onBackground = TextPrimaryLight,
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SecondaryBackgroundLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = BorderLight,
            outlineVariant = ElevatedSurfaceLight,
            error = ErrorRed,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    PiashAgentTheme(darkTheme = darkTheme, content = content)
}
