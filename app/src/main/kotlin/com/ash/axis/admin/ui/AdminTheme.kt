package com.ash.axis.admin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Primary = Color(0xFFF4F4F5)
private val OnPrimary = Color.Black
private val PrimaryContainer = Color(0xFF19191B)
private val OnPrimaryContainer = Color(0xFFE4E4E7)
private val Secondary = Color(0xFFC6C6CC)

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
    MaterialTheme(colorScheme = DarkScheme, typography = AdminTypography, content = content)
}
