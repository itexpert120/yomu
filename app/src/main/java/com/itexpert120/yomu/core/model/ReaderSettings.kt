package com.itexpert120.yomu.core.model

import kotlinx.serialization.Serializable

/** Reflowable content flow. */
@Serializable
enum class ReaderLayout { Scroll, Paged }

/** Reader colour theme. [Dark] is the default (a soft dark, not pure-black OLED). */
@Serializable
enum class ReaderThemeMode { Light, Dark, Sepia, Black, Custom }

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
    val lineHeight: Float? = null,
    val useSystemBrightness: Boolean = true,
    val brightness: Float = 0.5f,
    // Chrome visibility/appearance — let the reader be either ultra-clean or fully framed.
    val showTopBar: Boolean = true,
    val showFooter: Boolean = true,
    val edgeShadows: Boolean = true,
    // Footer contents (battery on the left, reading progress on the right, clock between).
    val footerShowBattery: Boolean = true,
    val footerShowClock: Boolean = true,
    val footerShowProgress: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 2.5f
    }
}
