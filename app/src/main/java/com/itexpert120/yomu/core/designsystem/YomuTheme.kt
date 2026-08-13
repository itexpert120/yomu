package com.itexpert120.yomu.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
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

private val YomuShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val BaseTypography = Typography()
private val YomuTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
    displayMedium = BaseTypography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
)

private val YomuLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    inversePrimary = Color(0xFFD0BCFF),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    surfaceDim = Color(0xFFE4E1E6),
    surfaceBright = Color(0xFFFFFBFE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

private val YomuDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF6750A4),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343B),
)

private val YomuOledColorScheme = YomuDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF242424),
)

internal fun yomuStaticColorScheme(mode: YomuThemeMode): ColorScheme = when (mode) {
    YomuThemeMode.Light -> YomuLightColorScheme
    YomuThemeMode.Dark -> YomuDarkColorScheme
    YomuThemeMode.Oled -> YomuOledColorScheme
}

val LocalYomuColors = staticCompositionLocalOf { FallbackYomuColors }
val LocalYomuType = staticCompositionLocalOf { yomuType(YomuTypography) }
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
    dynamicColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mode = themeMode ?: if (darkTheme) YomuThemeMode.Dark else YomuThemeMode.Light
    val colorScheme = when {
        mode == YomuThemeMode.Oled -> yomuStaticColorScheme(mode)
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mode == YomuThemeMode.Light -> {
            dynamicLightColorScheme(context)
        }

        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        else -> yomuStaticColorScheme(mode)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = YomuTypography,
        shapes = YomuShapes,
    ) {
        CompositionLocalProvider(
            LocalYomuColors provides yomuColors(colorScheme, mode),
            LocalYomuType provides yomuType(YomuTypography),
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
