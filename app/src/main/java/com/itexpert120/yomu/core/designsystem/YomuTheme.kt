package com.itexpert120.yomu.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val panel: Dp = 28.dp,
    val pill: Dp = 999.dp,
)

enum class YomuThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Oled("OLED"),
}

private val FallbackYomuColors = YomuColors(
    appBackground = Color(0xFFFFFBFE),
    appBackgroundAccent = Color(0xFFE7E0EC),
    surface = Color(0xFFFFFBFE),
    surfaceRaised = Color(0xFFF7F2FA),
    surfaceSunken = Color(0xFFFFFBFE),
    panel = Color(0xFFF3EDF7),
    panelStrong = Color(0xFFECE6F0),
    textPrimary = Color(0xFF1C1B1F),
    textSecondary = Color(0xFF49454F),
    textMuted = Color(0xFF49454F).copy(alpha = 0.72f),
    border = Color(0xFFCAC4D0),
    accent = Color(0xFF6750A4),
    accentSoft = Color(0xFFEADDFF),
    link = Color(0xFF6750A4),
    danger = Color(0xFFBA1A1A),
    readerPaper = Color(0xFFFFFBFE),
    readerInk = Color(0xFF1C1B1F),
    readerMuted = Color(0xFF49454F),
    highlightYellow = Color(0xFFFFD54F),
    highlightGreen = Color(0xFFA5D6A7),
    highlightBlue = Color(0xFF90CAF9),
    highlightPink = Color(0xFFF48FB1),
)

private val MaterialShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val MaterialTypography = Typography()

val LocalYomuColors = staticCompositionLocalOf { FallbackYomuColors }
val LocalYomuType = staticCompositionLocalOf { yomuType(MaterialTypography) }
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

/**
 * Material 3 Expressive is the app theme. The Yomu locals remain as a migration bridge so feature
 * call sites can move incrementally without changing reader and library behavior at the same time.
 */
@Composable
fun YomuDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: YomuThemeMode? = null,
    accent: Color? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mode = themeMode ?: if (darkTheme) YomuThemeMode.Dark else YomuThemeMode.Light
    val isDark = mode != YomuThemeMode.Light
    val systemScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> {
            dynamicDarkColorScheme(context)
        }

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(context)
        }

        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val baseScheme = if (mode == YomuThemeMode.Oled) {
        systemScheme.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        systemScheme
    }
    val safeAccent = accent?.takeIf { contrastRatio(it, baseScheme.background) >= 4.5f }
    val colorScheme = safeAccent?.let { baseScheme.copy(primary = it) } ?: baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTypography,
        shapes = MaterialShapes,
    ) {
        CompositionLocalProvider(
            LocalYomuColors provides yomuColors(MaterialTheme.colorScheme, mode),
            LocalYomuType provides yomuType(MaterialTheme.typography),
            LocalYomuSpacing provides YomuSpacing(),
            LocalYomuRadius provides YomuRadius(),
            content = content,
        )
    }
}

private fun yomuColors(scheme: ColorScheme, mode: YomuThemeMode): YomuColors = YomuColors(
    appBackground = scheme.background,
    appBackgroundAccent = scheme.surfaceVariant,
    surface = scheme.surface,
    surfaceRaised = scheme.surfaceContainerLow,
    surfaceSunken = scheme.surfaceContainerLowest,
    panel = scheme.surfaceContainer,
    panelStrong = scheme.surfaceContainerHigh,
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textMuted = scheme.onSurfaceVariant.copy(alpha = 0.72f),
    border = scheme.outlineVariant,
    accent = scheme.primary,
    accentSoft = scheme.primaryContainer,
    link = scheme.primary,
    danger = scheme.error,
    readerPaper = if (mode == YomuThemeMode.Oled) Color.Black else scheme.surface,
    readerInk = scheme.onSurface,
    readerMuted = scheme.onSurfaceVariant,
    highlightYellow = if (mode == YomuThemeMode.Light) Color(0xFFE8C55A) else Color(0xFFE7C75B),
    highlightGreen = if (mode == YomuThemeMode.Light) Color(0xFF8FBE79) else Color(0xFF93CE80),
    highlightBlue = if (mode == YomuThemeMode.Light) Color(0xFF79ADD8) else Color(0xFF7CB8E8),
    highlightPink = if (mode == YomuThemeMode.Light) Color(0xFFD989A4) else Color(0xFFE690AA),
)

private fun yomuType(typography: Typography): YomuType = YomuType(
    display = typography.displaySmall,
    title = typography.headlineSmall,
    section = typography.titleMedium,
    body = typography.bodyLarge,
    reader = typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.1.sp,
    ),
    caption = typography.bodySmall,
    control = typography.labelLarge,
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance())
    val darker = minOf(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
