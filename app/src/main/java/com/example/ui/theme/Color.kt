package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure AMOLED Black Palette (Claude Desktop & Modern Dev Workspace Inspired)
val AmoledBlack = Color(0xFF000000)
val SecondaryBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF111111)
val ElevatedSurfaceDark = Color(0xFF171717)
val BorderDark = Color(0xFF262626)
val BorderSubtle = Color(0xFF1F1F22)

// Accents
val CyanAccent = Color(0xFF00D9FF)
val VioletAccent = Color(0xFF8B5CF6)
val EmeraldAccent = Color(0xFF22C55E)
val AmberAccent = Color(0xFFF59E0B)
val RoseAccent = Color(0xFFF43F5E)

// Semantic Status
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

// Typography Colors
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFA1A1AA)
val TextMutedDark = Color(0xFF71717A)

// Optional Light Theme Palette
val BackgroundLight = Color(0xFFFAFAFA)
val SecondaryBackgroundLight = Color(0xFFF4F4F5)
val SurfaceLight = Color(0xFFFFFFFF)
val ElevatedSurfaceLight = Color(0xFFE4E4E7)
val BorderLight = Color(0xFFD4D4D8)
val TextPrimaryLight = Color(0xFF09090B)
val TextSecondaryLight = Color(0xFF52525B)

enum class AccentPreset(val label: String, val primary: Color, val secondary: Color) {
    CYAN("Cyber Cyan", CyanAccent, VioletAccent),
    VIOLET("Neural Violet", VioletAccent, CyanAccent),
    EMERALD("Matrix Emerald", EmeraldAccent, CyanAccent),
    AMBER("Claude Amber", AmberAccent, VioletAccent),
    ROSE("Crimson Pulse", RoseAccent, VioletAccent)
}
