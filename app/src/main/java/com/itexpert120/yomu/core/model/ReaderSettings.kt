package com.itexpert120.yomu.core.model

import kotlinx.serialization.Serializable

/** Reflowable content flow. */
@Serializable
enum class ReaderLayout { Scroll, Paged }

/** Reader colour theme. [Dark] is the default (a soft dark, not pure-black OLED). */
@Serializable
enum class ReaderThemeMode { Light, Dark, Sepia, Black, Custom }

/** Text alignment. [Default] leaves it to the engine; the rest force a concrete alignment. */
@Serializable
enum class ReaderTextAlign(val label: String) { Default("Auto"), Left("Left"), Justify("Justify") }

/** Bundled reading fonts (custom user fonts are out of scope for now). [Lora] is the default. */
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
) {
    /** Page background for the active theme (ARGB). Shared by the engine and the reader chrome
     *  so the area behind the system bars matches the page with no seam. Dark is a soft, non-OLED grey. */
    val backgroundArgb: Long
        get() = when (theme) {
            ReaderThemeMode.Light -> 0xFFFFFFFF
            ReaderThemeMode.Sepia -> 0xFFFAF4E8
            ReaderThemeMode.Dark -> 0xFF16181D
            ReaderThemeMode.Black -> 0xFF000000
            ReaderThemeMode.Custom -> customBackground ?: 0xFF16181D
        }

    /** Text colour for the active theme (ARGB). */
    val textArgb: Long
        get() = when (theme) {
            ReaderThemeMode.Light -> 0xFF1A1A1A
            ReaderThemeMode.Sepia -> 0xFF2A2520
            ReaderThemeMode.Dark, ReaderThemeMode.Black -> 0xFFE6E6E6
            ReaderThemeMode.Custom -> customText ?: 0xFFE6E6E6
        }

    val isLightBackground: Boolean
        get() = theme == ReaderThemeMode.Light || theme == ReaderThemeMode.Sepia

    companion object {
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
