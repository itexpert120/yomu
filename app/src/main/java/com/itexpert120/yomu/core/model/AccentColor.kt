package com.itexpert120.yomu.core.model

/**
 * User-selectable accent. Each preset carries a [light] variant (dark enough for white text on
 * it) and a [dark] variant (light enough for dark text), so the accent reads well in both themes.
 * Stored as the enum name.
 */
enum class AccentColor(val label: String, val light: Long, val dark: Long) {
    Forest("Forest", 0xFF1D4F3A, 0xFFB8D88F),
    Ocean("Ocean", 0xFF1C5A78, 0xFF8FC6E8),
    Violet("Violet", 0xFF5B3E86, 0xFFC4A9E8),
    Amber("Amber", 0xFF8A5414, 0xFFE7BD7E),
    Rose("Rose", 0xFF8E2F4C, 0xFFE89BB0),
    Slate("Slate", 0xFF3C4A57, 0xFFAEC0CE),
}

/** The chosen accent: one of the [AccentColor] presets, or a custom ARGB color. */
sealed interface AccentSelection {
    data class Preset(val accent: AccentColor) : AccentSelection
    data class Custom(val argb: Long) : AccentSelection

    /** Resolves to the ARGB value for the current theme brightness. */
    fun resolve(dark: Boolean): Long = when (this) {
        is Preset -> if (dark) accent.dark else accent.light
        is Custom -> argb
    }

    companion object {
        val Default: AccentSelection = Preset(AccentColor.Forest)

        fun deserialize(value: String?): AccentSelection = when {
            value == null -> Default
            value.startsWith("#") -> value.drop(1).toLongOrNull(16)?.let { Custom(it) } ?: Default
            else -> runCatching { Preset(AccentColor.valueOf(value)) }.getOrDefault(Default)
        }

        fun serialize(selection: AccentSelection): String = when (selection) {
            is Preset -> selection.accent.name
            is Custom -> "#" + selection.argb.toString(16).uppercase()
        }
    }
}
