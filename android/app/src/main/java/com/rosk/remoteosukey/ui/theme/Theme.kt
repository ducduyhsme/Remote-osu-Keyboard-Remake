package com.rosk.remoteosukey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

// ====== Color Palette ======
// Inspired by osu!'s pink/purple aesthetic with a modern dark theme

val OsuPink = Color(0xFFFF66AB)
val OsuPinkDark = Color(0xFFCC3377)
val OsuPurple = Color(0xFF7B5EA7)
val OsuPurpleDark = Color(0xFF5A3D8A)

val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00E676)

val DarkBg = Color(0xFF0D0D1A)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF252540)
val DarkCard = Color(0xFF16213E)

val TextPrimary = Color(0xFFF0F0FF)
val TextSecondary = Color(0xFFB0B0CC)
val TextMuted = Color(0xFF666688)

val ErrorRed = Color(0xFFFF4444)
val WarningYellow = Color(0xFFFFAA00)
val SuccessGreen = Color(0xFF44FF88)

// Key press visual colors
val Key1Color = Color(0xFFFF66AB)    // Pink for key 1
val Key2Color = Color(0xFF00E5FF)    // Cyan for key 2
val Key1Pressed = Color(0xFFFF99CC)
val Key2Pressed = Color(0xFF66F0FF)

// ====== Material 3 Color Scheme ======
private val DarkColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color.White,
    primaryContainer = OsuPinkDark,
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003D4D),
    onSecondaryContainer = NeonCyan,
    tertiary = OsuPurple,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = TextMuted,
)

// ====== Typography ======
val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        lineHeight = 24.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
)

// ====== Theme Composable ======
@Composable
fun RemoteOsuKeyboardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
