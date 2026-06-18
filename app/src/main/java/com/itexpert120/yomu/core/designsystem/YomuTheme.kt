package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class YomuColors(
    val appBackground: Color,
    val appBackgroundAccent: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val panel: Color,
    val panelStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val link: Color,
    val danger: Color,
    val readerPaper: Color,
    val readerInk: Color,
    val readerMuted: Color,
    val highlightYellow: Color,
    val highlightGreen: Color,
    val highlightBlue: Color,
    val highlightPink: Color,
)

@Immutable
data class YomuType(
    val display: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val reader: TextStyle,
    val caption: TextStyle,
    val control: TextStyle,
    val mono: TextStyle,
)

@Immutable
data class YomuSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

@Immutable
data class YomuRadius(
    val xs: Dp = 6.dp,
    val sm: Dp = 10.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val panel: Dp = 24.dp,
    val pill: Dp = 999.dp,
)

enum class YomuThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Oled("OLED"),
}

private val LightYomuColors = YomuColors(
    appBackground = Color.White,
    appBackgroundAccent = Color.White,
    surface = Color(0xFFF8F8F6),
    surfaceRaised = Color.White,
    surfaceSunken = Color(0xFFEFEFEB),
    panel = Color.White,
    panelStrong = Color(0xFFF6F6F2),
    textPrimary = Color(0xFF10110F),
    textSecondary = Color(0xFF4A4D46),
    textMuted = Color(0xFF82857C),
    border = Color(0xFFE2E2DD),
    accent = Color(0xFF1D4F3A),
    accentSoft = Color(0x141D4F3A),
    link = Color(0xFF245C89),
    danger = Color(0xFFB13A32),
    readerPaper = Color(0xFFFFFCF4),
    readerInk = Color(0xFF201B15),
    readerMuted = Color(0xFF81796B),
    highlightYellow = Color(0xFFECCB5B),
    highlightGreen = Color(0xFF9FCB82),
    highlightBlue = Color(0xFF8DBFE8),
    highlightPink = Color(0xFFE7A0B8),
)

private val DarkYomuColors = YomuColors(
    appBackground = Color(0xFF111210),
    appBackgroundAccent = Color(0xFF111210),
    surface = Color(0xFF171815),
    surfaceRaised = Color(0xFF1D1F1B),
    surfaceSunken = Color(0xFF0C0D0B),
    panel = Color(0xFF171815),
    panelStrong = Color(0xFF1D1F1B),
    textPrimary = Color(0xFFF4F4EF),
    textSecondary = Color(0xFFBFC2B8),
    textMuted = Color(0xFF858A7E),
    border = Color(0xFF2C2F29),
    accent = Color(0xFFB8D88F),
    accentSoft = Color(0x24B8D88F),
    link = Color(0xFF99C8F2),
    danger = Color(0xFFFF8A80),
    readerPaper = Color(0xFF171814),
    readerInk = Color(0xFFE8E3D6),
    readerMuted = Color(0xFF8A8577),
    highlightYellow = Color(0xFFE7C75B),
    highlightGreen = Color(0xFF93CE80),
    highlightBlue = Color(0xFF7CB8E8),
    highlightPink = Color(0xFFE690AA),
)

private val OledYomuColors = DarkYomuColors.copy(
    appBackground = Color.Black,
    appBackgroundAccent = Color.Black,
    surface = Color(0xFF050505),
    surfaceRaised = Color(0xFF0B0B0B),
    surfaceSunken = Color.Black,
    panel = Color(0xFF060606),
    panelStrong = Color(0xFF0C0C0C),
    border = Color(0xFF222222),
    readerPaper = Color.Black,
    readerInk = Color(0xFFEDE9DE),
    readerMuted = Color(0xFF8C887E),
)

private val YomuTypography = YomuType(
    display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp,
    ),
    title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.25).sp,
    ),
    section = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    reader = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.1.sp,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    control = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val LocalYomuColors = staticCompositionLocalOf { DarkYomuColors }
val LocalYomuType = staticCompositionLocalOf { YomuTypography }
val LocalYomuSpacing = staticCompositionLocalOf { YomuSpacing() }
val LocalYomuRadius = staticCompositionLocalOf { YomuRadius() }

object YomuTheme {
    val colors: YomuColors
        @Composable get() = LocalYomuColors.current
    val type: YomuType
        @Composable get() = LocalYomuType.current
    val space: YomuSpacing
        @Composable get() = LocalYomuSpacing.current
    val radius: YomuRadius
        @Composable get() = LocalYomuRadius.current
}

@Composable
fun YomuDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: YomuThemeMode? = null,
    content: @Composable () -> Unit,
) {
    val mode = themeMode ?: if (darkTheme) YomuThemeMode.Dark else YomuThemeMode.Light
    val colors = when (mode) {
        YomuThemeMode.Light -> LightYomuColors
        YomuThemeMode.Dark -> DarkYomuColors
        YomuThemeMode.Oled -> OledYomuColors
    }
    CompositionLocalProvider(
        LocalYomuColors provides colors,
        LocalYomuType provides YomuTypography,
        LocalYomuSpacing provides YomuSpacing(),
        LocalYomuRadius provides YomuRadius(),
        content = content,
    )
}
