package com.itexpert120.yomu.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * The few theme values Material has no role for: the highlight palette (tuned separately for light
 * and dark themes, so highlights read on either) and the reader's monospace/sample text styles.
 * Everything else comes straight from `MaterialTheme.colorScheme` / `typography`.
 */
@Immutable
data class YomuReadingTokens(
    val highlightYellow: Color,
    val highlightGreen: Color,
    val highlightBlue: Color,
    val highlightPink: Color,
) {
    companion object {
        /** Monospace for figures that must not jitter as they change (clock, progress). */
        val Mono = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        /** Serif body text that previews the reading page outside the reader. */
        val ReaderSample: TextStyle get() = readerSampleStyle

        internal fun forMode(mode: YomuThemeMode): YomuReadingTokens = if (mode == YomuThemeMode.Light) Light else Dark

        private val Light = YomuReadingTokens(
            highlightYellow = Color(0xFFE8C55A),
            highlightGreen = Color(0xFF8FBE79),
            highlightBlue = Color(0xFF79ADD8),
            highlightPink = Color(0xFFD989A4),
        )
        private val Dark = YomuReadingTokens(
            highlightYellow = Color(0xFFE7C75B),
            highlightGreen = Color(0xFF93CE80),
            highlightBlue = Color(0xFF7CB8E8),
            highlightPink = Color(0xFFE690AA),
        )
    }
}

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

/** The full M3 Expressive corner scale (adds large-increased, extra-large-increased, 2XL). */
private val YomuShapes = Shapes()

/**
 * Baseline M3 type scale plus the paired `*Emphasized` styles call sites opt into for headlines,
 * hero figures, actions, and selected states. Material's emphasized tokens lean on a variable-font
 * weight axis the system Roboto doesn't apply, so each emphasized style gets an explicit heavier
 * weight at the same size and line height.
 */
private val YomuTypography = Typography().let { base ->
    base.copy(
        displayLargeEmphasized = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        displayMediumEmphasized = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        displaySmallEmphasized = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLargeEmphasized = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMediumEmphasized = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmallEmphasized = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLargeEmphasized = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMediumEmphasized = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmallEmphasized = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLargeEmphasized = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMediumEmphasized = base.bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmallEmphasized = base.bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLargeEmphasized = base.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMediumEmphasized = base.labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmallEmphasized = base.labelSmall.copy(fontWeight = FontWeight.Bold),
    )
}

/**
 * Resolves the app [ColorScheme] for a theme mode and colour source. Seeds are expanded with the
 * 2025 Material colour spec so presets produce the same tonal roles Android's own palette does.
 */
@Composable
fun yomuColorScheme(
    mode: YomuThemeMode,
    accent: AccentSelection,
    style: ColorStyle,
): ColorScheme {
    val context = LocalContext.current
    val dark = mode != YomuThemeMode.Light
    val wallpaper = accent == AccentSelection.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = remember(mode, accent, style, wallpaper) {
        if (wallpaper) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            seededColorScheme(accent.seedArgb(), dark, style)
        }
    }
    val oled = mode == YomuThemeMode.Oled
    return remember(base, oled) { if (oled) base.toOled() else base }
}

/** Preview/picker helper: a seeded scheme without composition. */
fun yomuSeededColorScheme(seedArgb: Long, dark: Boolean, style: ColorStyle): ColorScheme = seededColorScheme(seedArgb, dark, style)

private fun seededColorScheme(seedArgb: Long, dark: Boolean, style: ColorStyle): ColorScheme = dynamicColorScheme(
    seedColor = Color(seedArgb),
    isDark = dark,
    style = style.toPaletteStyle(),
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)

private fun AccentSelection.seedArgb(): Long = when (this) {
    is AccentSelection.Preset -> accent.seed
    is AccentSelection.Custom -> argb
    AccentSelection.Wallpaper -> (AccentSelection.FallbackPreset as AccentSelection.Preset).accent.seed
}

private fun ColorStyle.toPaletteStyle(): PaletteStyle = when (this) {
    ColorStyle.Balanced -> PaletteStyle.TonalSpot
    ColorStyle.Vibrant -> PaletteStyle.Vibrant
    ColorStyle.Expressive -> PaletteStyle.Expressive
    ColorStyle.Neutral -> PaletteStyle.Neutral
}

/** Pure-black surfaces for OLED; containers keep a faint tonal step so grouping stays legible. */
internal fun ColorScheme.toOled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF242424),
)

val LocalYomuReadingTokens = staticCompositionLocalOf { YomuReadingTokens.forMode(YomuThemeMode.Light) }
val LocalYomuSpacing = staticCompositionLocalOf { YomuSpacing() }
val LocalYomuRadius = staticCompositionLocalOf { YomuRadius() }

object YomuTheme {
    val reading: YomuReadingTokens
        @Composable get() = LocalYomuReadingTokens.current
    val space: YomuSpacing
        @Composable get() = LocalYomuSpacing.current
    val radius: YomuRadius
        @Composable get() = LocalYomuRadius.current
}

/**
 * Material 3 Expressive is the app theme; colour and type come from `MaterialTheme`. The Yomu locals
 * add only what Material has no tokens for: spacing, radii and [YomuReadingTokens].
 */
@Composable
fun YomuDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: YomuThemeMode? = null,
    accent: AccentSelection = AccentSelection.FallbackPreset,
    colorStyle: ColorStyle = ColorStyle.Balanced,
    content: @Composable () -> Unit,
) {
    val mode = themeMode ?: if (darkTheme) YomuThemeMode.Dark else YomuThemeMode.Light
    val colorScheme = yomuColorScheme(mode, accent, colorStyle)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = YomuTypography,
        shapes = YomuShapes,
        motionScheme = YomuMotion.scheme,
    ) {
        CompositionLocalProvider(
            LocalYomuReadingTokens provides YomuReadingTokens.forMode(mode),
            LocalYomuSpacing provides YomuSpacing(),
            LocalYomuRadius provides YomuRadius(),
            content = content,
        )
    }
}

private val readerSampleStyle = YomuTypography.bodyLarge.copy(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 33.sp,
    letterSpacing = 0.1.sp,
)
