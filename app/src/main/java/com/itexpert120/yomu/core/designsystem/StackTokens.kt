package com.itexpert120.yomu.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.R

/**
 * Stack, Open Reader's design system: a grey desk, white sheets that slide up and stack, ink text,
 * real covers. Colour comes from covers and reading themes only — there is no app accent. These
 * tokens are mapped onto Material's colour roles ([toColorScheme]) so every component follows;
 * [YomuTheme.stack] exposes them by their Stack names for code written against the spec.
 */
@Immutable
data class StackColors(
    /** The screen background everything sits on. */
    val desk: Color,
    /** Sheets, cards and bars. */
    val sheet: Color,
    /** Rows, insets and fields inside a sheet. */
    val raised: Color,
    /** Primary text and the primary action. */
    val ink: Color,
    val onInk: Color,
    /** Secondary text. */
    val body: Color,
    /** Captions and inactive items (≥ 6:1). */
    val muted: Color,
    /** The least text (≥ 4.5:1). */
    val faint: Color,
    /** Dividers. */
    val rule: Color,
    /** Control tracks, "off" states and secondary borders. */
    val track: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    companion object {
        val Light = StackColors(
            desk = Color(0xFFE9EDF0),
            sheet = Color(0xFFFFFFFF),
            raised = Color(0xFFF1F4F6),
            ink = Color(0xFF16191C),
            onInk = Color(0xFFFFFFFF),
            body = Color(0xFF2E3439),
            muted = Color(0xFF5B636B),
            faint = Color(0xFF6A737B),
            rule = Color(0xFFEDF0F2),
            track = Color(0xFFD6DCE0),
            danger = Color(0xFFB3261E),
            isDark = false,
        )
        val Dark = StackColors(
            desk = Color(0xFF0F1215),
            sheet = Color(0xFF1A1E22),
            raised = Color(0xFF23282D),
            ink = Color(0xFFEEF1F3),
            onInk = Color(0xFF0F1215),
            body = Color(0xFFC9CFD4),
            muted = Color(0xFF9AA3AB),
            faint = Color(0xFF8C959D),
            rule = Color(0xFF2A2F34),
            track = Color(0xFF3A4046),
            danger = Color(0xFFF2B8B5),
            isDark = true,
        )

        /** Pure black: the desk goes black; sheets keep a faint step so they still read as sheets. */
        val Black = Dark.copy(
            desk = Color(0xFF000000),
            sheet = Color(0xFF0E1012),
            raised = Color(0xFF1A1D20),
            onInk = Color(0xFF000000),
            rule = Color(0xFF22262A),
            track = Color(0xFF33393E),
        )

        internal fun forMode(mode: YomuThemeMode): StackColors = when (mode) {
            YomuThemeMode.Light -> Light
            YomuThemeMode.Dark -> Dark
            YomuThemeMode.Oled -> Black
        }
    }
}

/** Stack's tokens on Material's roles. No tonal elevation: every container role is a Stack surface. */
internal fun StackColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = raised,
        onPrimaryContainer = ink,
        inversePrimary = sheet,
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = raised,
        onSecondaryContainer = ink,
        tertiary = ink,
        onTertiary = onInk,
        tertiaryContainer = raised,
        onTertiaryContainer = ink,
        background = desk,
        onBackground = ink,
        surface = sheet,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        surfaceTint = sheet,
        inverseSurface = ink,
        inverseOnSurface = onInk,
        error = danger,
        onError = if (isDark) Color(0xFF3B0A08) else Color.White,
        errorContainer = raised,
        onErrorContainer = danger,
        outline = track,
        outlineVariant = rule,
        scrim = Color.Black,
        surfaceBright = sheet,
        surfaceDim = desk,
        surfaceContainerLowest = sheet,
        surfaceContainerLow = sheet,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: Int, width: Float = 100f) = Font(
    resId = R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Instrument Sans, Stack's UI face (variable: weight and width). Reading fonts are separate. */
val InstrumentSans = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))

/** Instrument Sans at 80% width, for big figures. */
val InstrumentSansCondensed = FontFamily(instrument(600, width = 80f), instrument(700, width = 80f))

private fun style(size: Float, line: Float, weight: Int, tracking: TextUnit = 0.sp, family: FontFamily = InstrumentSans) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking,
)

/**
 * Stack's type scale, named as in the spec. Material's roles map onto it in [StackTypography]:
 * display → displaySmall, figure → displayLarge, title → headlineMedium, heading → titleMedium,
 * body → bodyLarge, bodySmall → bodyMedium, label → labelLarge, caption → bodySmall/labelSmall.
 */
object StackType {
    val Display = style(32f, 38f, 600, (-0.6 / 32).em)
    val Figure = style(44f, 44f, 600, (-1.5 / 44).em, InstrumentSansCondensed)
    val Title = style(26f, 28f, 600, (-0.6 / 26).em)
    val Heading = style(15f, 20f, 600)
    val Body = style(15f, 21f, 400)
    val BodySmall = style(14.5f, 21f, 400)
    val Label = style(13f, 17f, 600)
    val Caption = style(12f, 16f, 400)
}

internal val StackTypography: Typography = Typography(
    displayLarge = StackType.Figure,
    displayMedium = style(36f, 40f, 600, (-0.8 / 36).em),
    displaySmall = StackType.Display,
    headlineLarge = style(28f, 32f, 600, (-0.6 / 28).em),
    headlineMedium = StackType.Title,
    headlineSmall = style(22f, 26f, 600, (-0.4 / 22).em),
    titleLarge = style(20f, 24f, 600),
    titleMedium = StackType.Heading,
    titleSmall = style(14f, 18f, 600),
    bodyLarge = StackType.Body,
    bodyMedium = StackType.BodySmall,
    bodySmall = StackType.Caption,
    labelLarge = StackType.Label,
    labelMedium = style(12f, 16f, 600),
    labelSmall = style(12f, 16f, 500),
).let { base ->
    // Stack has one weight for emphasis; the emphasized roles step up to it (or past it).
    base.copy(
        displayLargeEmphasized = base.displayLarge.copy(fontWeight = FontWeight(700)),
        displayMediumEmphasized = base.displayMedium.copy(fontWeight = FontWeight(700)),
        displaySmallEmphasized = base.displaySmall.copy(fontWeight = FontWeight(700)),
        headlineLargeEmphasized = base.headlineLarge.copy(fontWeight = FontWeight(700)),
        headlineMediumEmphasized = base.headlineMedium.copy(fontWeight = FontWeight(700)),
        headlineSmallEmphasized = base.headlineSmall.copy(fontWeight = FontWeight(700)),
        titleLargeEmphasized = base.titleLarge.copy(fontWeight = FontWeight(700)),
        titleMediumEmphasized = base.titleMedium.copy(fontWeight = FontWeight(700)),
        titleSmallEmphasized = base.titleSmall.copy(fontWeight = FontWeight(700)),
        bodyLargeEmphasized = base.bodyLarge.copy(fontWeight = FontWeight(600)),
        bodyMediumEmphasized = base.bodyMedium.copy(fontWeight = FontWeight(600)),
        bodySmallEmphasized = base.bodySmall.copy(fontWeight = FontWeight(600)),
        labelLargeEmphasized = base.labelLarge.copy(fontWeight = FontWeight(700)),
        labelMediumEmphasized = base.labelMedium.copy(fontWeight = FontWeight(700)),
        labelSmallEmphasized = base.labelSmall.copy(fontWeight = FontWeight(600)),
    )
}
