package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            highlightYellow = Color(0xFFF6E27A),
            highlightGreen = Color(0xFFB8E3A8),
            highlightBlue = Color(0xFFB4D4F5),
            highlightPink = Color(0xFFF5C2D7),
        )
        private val Dark = Light
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

/** Stack radii: cover 4 · thumbnail 3 · row/inset 16 · card on desk 24 · sheet top 32 · pill. */
@Immutable
data class YomuRadius(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val panel: Dp = 32.dp,
    val pill: Dp = 999.dp,
    val cover: Dp = 4.dp,
    val thumbnail: Dp = 3.dp,
)

enum class YomuThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Oled("OLED"),
}

/** Stack's corners on Material's shape roles: rows 16, cards 24, sheets 32. */
private val YomuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val YomuTypography = StackTypography

val LocalYomuReadingTokens = staticCompositionLocalOf { YomuReadingTokens.forMode(YomuThemeMode.Light) }
val LocalYomuSpacing = staticCompositionLocalOf { YomuSpacing() }
val LocalYomuRadius = staticCompositionLocalOf { YomuRadius() }
val LocalStackColors = staticCompositionLocalOf { StackColors.Light }

object YomuTheme {
    val reading: YomuReadingTokens
        @Composable get() = LocalYomuReadingTokens.current
    val space: YomuSpacing
        @Composable get() = LocalYomuSpacing.current
    val radius: YomuRadius
        @Composable get() = LocalYomuRadius.current

    /** Stack's colours by their own names (desk, sheet, raised, ink, muted, …). */
    val stack: StackColors
        @Composable get() = LocalStackColors.current
}

/**
 * Runs [content] as the inside of a floating surface (a sheet or dialog over a dim): Stack's and
 * Material's `sheet`/`surface` become [StackColors.elevated], so everything that paints "the sheet"
 * (pinned headers, rows, menus) matches the surface it sits on. A no-op on light themes.
 */
@Composable
fun YomuFloatingSurface(content: @Composable () -> Unit) {
    val stack = YomuTheme.stack
    val floating = stack.floating()
    if (floating === stack) {
        content()
        return
    }
    // Material's theme provides its ripple; keep Stack's press highlight.
    val indication = LocalIndication.current
    MaterialExpressiveTheme(
        colorScheme = remember(floating) { floating.toColorScheme() },
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalStackColors provides floating, LocalIndication provides indication, content = content)
    }
}

/**
 * The app theme: Stack's colours, type and corners on Material 3 Expressive (which keeps the
 * behaviour: touch targets, springs, accessibility). Colour comes from [YomuThemeMode] alone — Stack
 * has no app accent. The Yomu locals add spacing, radii, [YomuReadingTokens] and [StackColors].
 */
@Composable
fun YomuDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: YomuThemeMode? = null,
    content: @Composable () -> Unit,
) {
    val mode = themeMode ?: if (darkTheme) YomuThemeMode.Dark else YomuThemeMode.Light
    val stack = StackColors.forMode(mode)
    val colorScheme = remember(stack) { stack.toColorScheme() }

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
            LocalStackColors provides stack,
            // Stack's soft, rounded press highlight for every plain `clickable` (no ripple).
            LocalIndication provides YomuPressIndication(),
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
