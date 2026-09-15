package com.ash.axis.admin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Primary = Color(0xFF6E90C0)
private val OnPrimary = Color.White
private val PrimaryContainer = Color(0xFF16202C)
private val OnPrimaryContainer = Color(0xFFB4C6DA)
private val Secondary = Color(0xFF8FA9CC)

private val DarkBg = Color(0xFF000000)
private val DarkOnBg = Color(0xFFF2F2F4)
private val DarkSurface = Color(0xFF0C0C0E)
private val DarkSurfaceVariant = Color(0xFF151517)
private val DarkOnSurfaceVariant = Color(0xFF8A8A90)
private val DarkOutline = Color(0xFF262628)

private val DarkScheme =
    darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary,
        onSecondary = OnPrimary,
        background = DarkBg,
        onBackground = DarkOnBg,
        surface = DarkSurface,
        onSurface = DarkOnBg,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = DarkOutline,
        secondaryContainer = PrimaryContainer,
        onSecondaryContainer = OnPrimaryContainer,
        surfaceContainerLowest = DarkBg,
        surfaceContainerLow = DarkSurface,
        surfaceContainer = DarkSurfaceVariant,
        surfaceContainerHigh = Color(0xFF1A1A1D),
        surfaceContainerHighest = Color(0xFF1F1F22),
        error = Color(0xFFEF4444),
        onError = Color.White,
    )

private val LightScheme =
    lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = Color(0xFFE0E8F2),
        onPrimaryContainer = Primary,
        secondary = Secondary,
        onSecondary = OnPrimary,
        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFF7F9FC),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFEBEFF5),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFE0E4EA),
        secondaryContainer = Color(0xFFE0E8F2),
        onSecondaryContainer = Primary,
        error = Color(0xFFD32F2F),
        onError = Color.White,
    )

private val AdminTypography =
    Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
            ),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.1.sp,
            ),
        titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
    )

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = colorScheme, typography = AdminTypography, content = content)
}
