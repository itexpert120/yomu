package com.itexpert120.yomu.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Reflowable content flow. */
@Serializable
enum class ReaderLayout { Scroll, Paged }

/** Reader colour theme. [Dark] is the default (a soft dark, not pure-black OLED). */
@Serializable
enum class ReaderThemeMode { Light, Dark, Sepia, Black, Custom }

/** Text alignment. [Default] leaves it to the engine; the rest force a concrete alignment. */
@Serializable
enum class ReaderTextAlign(val label: String) { Default("Auto"), Left("Left"), Justify("Justify") }

/** Bundled reading fonts. [Lora] is the default. Users may also install custom fonts; see
 *  [ReaderSettings.customFont]. */
@Serializable
enum class ReaderFont(val displayName: String, val cssFamily: String) {
    Lora("Lora", "Lora"),
    Karla("Karla", "Karla"),
    Rubik("Rubik", "Rubik"),
    Cardo("Cardo", "Cardo"),
    Nunito("Nunito", "Nunito"),
    Merriweather("Merriweather", "Merriweather"),
}

/**
 * A user-installed custom reading font (e.g. downloaded from Google Fonts). [family] is the CSS font
 * family the engine renders; [regularPath]/[italicPath] are absolute paths to the app-private font
 * files (woff2) the engine embeds as @font-face. When [ReaderSettings.customFont] is set it takes
 * precedence over the bundled [ReaderSettings.font].
 */
@Serializable
data class CustomFontRef(
    val family: String,
    val regularPath: String,
    val italicPath: String? = null,
)

/** Semantic colours shared by EPUB content and reader chrome. */
data class ReaderColorPalette(
    val backgroundArgb: Long,
    val textArgb: Long,
    val secondaryTextArgb: Long,
    val selectionArgb: Long,
    val linkArgb: Long,
    val borderArgb: Long,
)

/**
 * Resolved reading preferences applied to the reader. A single global instance is the default;
 * each book may carry an override that, when present, fully supersedes the global one. Brightness
 * is a window attribute (not an engine preference); the rest map onto the EPUB engine.
 */
@Serializable
data class ReaderSettings(
    val layout: ReaderLayout = ReaderLayout.Scroll,
    val theme: ReaderThemeMode = ReaderThemeMode.Dark,
    val customBackground: Long? = null,
    val customText: Long? = null,
    val font: ReaderFont = ReaderFont.Lora,
    // A user-installed custom font; when non-null it supersedes [font]. Selecting a bundled font
    // clears it. Round-trips in the settings JSON blob like everything else here.
    val customFont: CustomFontRef? = null,
    val fontScale: Float = 1.0f,
    // Advanced typography. null means "leave to the engine" (Auto); a value forces the setting.
    val lineHeight: Float? = null,
    val pageMargins: Float? = null,
    val paragraphSpacing: Float? = null,
    val textAlign: ReaderTextAlign = ReaderTextAlign.Default,
    val useSystemBrightness: Boolean = true,
    val brightness: Float = 0.5f,
    // Extra dimming below the device minimum: a black overlay (0 = off .. 1 = darkest allowed).
    val dimLevel: Float = 0f,
    // Tapping the left/right edge turns the page in paged mode (centre/scroll toggles controls).
    val tapNavigation: Boolean = true,
    // Chrome appearance. The top bar is always present (sleek + compact); the footer is optional.
    val showFooter: Boolean = true,
    // Footer contents (battery on the left, reading progress on the right, clock between).
    val footerShowBattery: Boolean = true,
    val footerShowClock: Boolean = true,
    val footerShowProgress: Boolean = true,
    // Remaining chapter amount in the footer: visual pages in paged mode, percentage in scroll mode.
    val footerShowPagesLeft: Boolean = false,
    // Keep the display awake while reading (on by default).
    val keepScreenOn: Boolean = true,
    // Show the native vertical scrollbar in scroll mode (on by default; scroll-mode only).
    val showScrollbar: Boolean = true,
    // Immersive reading: hide the top bar + footer (with the controls) on a centre tap. Off keeps the
    // top bar visible at all times.
    val immersiveChrome: Boolean = false,
) {
    /** Complete palette for the active theme. Custom themes derive supporting colours from the
     *  user's background/text pair so links, selections and chrome remain coherent. */
    val colorPalette: ReaderColorPalette
        get() {
            when (theme) {
                ReaderThemeMode.Light -> return LIGHT_PALETTE
                ReaderThemeMode.Sepia -> return SEPIA_PALETTE
                ReaderThemeMode.Dark -> return DARK_PALETTE
                ReaderThemeMode.Black -> return BLACK_PALETTE
                ReaderThemeMode.Custom -> Unit
            }

            val background = customBackground ?: DARK_PALETTE.backgroundArgb
            val requested = customText ?: DARK_PALETTE.textArgb
            val darkInk = 0xFF171717
            val lightInk = 0xFFF1F3F5
            val text = if (contrastRatio(requested, background) >= 4.5) {
                requested
            } else if (contrastRatio(darkInk, background) >= contrastRatio(lightInk, background)) {
                darkInk
            } else {
                lightInk
            }
            val lightBackground = relativeLuminance(background) >= 0.45
            return ReaderColorPalette(
                backgroundArgb = background,
                textArgb = text,
                secondaryTextArgb = blendArgb(text, background, 0.72f),
                selectionArgb = blendArgb(text, background, 0.16f),
                linkArgb = if (lightBackground) LIGHT_PALETTE.linkArgb else DARK_PALETTE.linkArgb,
                borderArgb = blendArgb(text, background, 0.14f),
            )
        }

    /** Page background shared by the EPUB engine, chrome and system bars. */
    val backgroundArgb: Long get() = colorPalette.backgroundArgb

    /** Main reading text colour. */
    val textArgb: Long get() = colorPalette.textArgb

    val isLightBackground: Boolean
        get() = relativeLuminance(backgroundArgb) >= 0.45

    companion object {
        val LIGHT_PALETTE = ReaderColorPalette(
            backgroundArgb = 0xFFF7F7F5,
            textArgb = 0xFF222426,
            secondaryTextArgb = 0xFF62676D,
            selectionArgb = 0xFFDCE8F7,
            linkArgb = 0xFF326EA8,
            borderArgb = 0xFFE1E3E5,
        )
        val SEPIA_PALETTE = ReaderColorPalette(
            backgroundArgb = 0xFFF3EBDD,
            textArgb = 0xFF302B26,
            secondaryTextArgb = 0xFF71685E,
            selectionArgb = 0xFFDED3C2,
            linkArgb = 0xFF456F91,
            borderArgb = 0xFFD8CDBD,
        )
        val DARK_PALETTE = ReaderColorPalette(
            backgroundArgb = 0xFF17191C,
            textArgb = 0xFFD7DADE,
            secondaryTextArgb = 0xFFAEB4BC,
            selectionArgb = 0xFF30343A,
            linkArgb = 0xFF82B7F5,
            borderArgb = 0xFF2A2E34,
        )
        val BLACK_PALETTE = ReaderColorPalette(
            backgroundArgb = 0xFF000000,
            textArgb = 0xFFD4D7DB,
            secondaryTextArgb = 0xFF9DA3AB,
            selectionArgb = 0xFF25282D,
            linkArgb = 0xFF76ADEF,
            borderArgb = 0xFF202328,
        )

        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 2.5f
        const val FONT_SCALE_STEP = 0.05f
        const val DEFAULT_FONT_SCALE = 1.0f

        // Advanced typography ranges (min, max, the "Auto" fallback shown on the slider, step).
        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.4f
        const val DEFAULT_LINE_HEIGHT = 1.5f
        const val LINE_HEIGHT_STEP = 0.05f

        const val MIN_PAGE_MARGINS = 0.5f
        const val MAX_PAGE_MARGINS = 3.0f
        const val DEFAULT_PAGE_MARGINS = 1.0f
        const val PAGE_MARGINS_STEP = 0.1f

        const val MIN_PARAGRAPH_SPACING = 0.0f
        const val MAX_PARAGRAPH_SPACING = 2.0f
        const val DEFAULT_PARAGRAPH_SPACING = 0.5f
        const val PARAGRAPH_SPACING_STEP = 0.1f

        // The darkest the extra-dim overlay may get; kept under 1.0 so the screen never goes fully black.
        const val MAX_DIM_ALPHA = 0.85f
    }
}

private fun contrastRatio(foreground: Long, background: Long): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val raw = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (raw <= 0.04045) raw / 12.92 else Math.pow((raw + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private fun blendArgb(foreground: Long, background: Long, amount: Float): Long {
    fun channel(shift: Int): Long {
        val foregroundChannel = (foreground shr shift) and 0xFF
        val backgroundChannel = (background shr shift) and 0xFF
        return (backgroundChannel + (foregroundChannel - backgroundChannel) * amount)
            .roundToInt()
            .coerceIn(0, 255)
            .toLong()
    }
    return 0xFF000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
