package com.itexpert120.yomu.core.model

/**
 * User-selectable app colour theme presets. Each [seed] is expanded into a full Material 3 tonal
 * scheme (light and dark) at theme time, so one value drives every colour role. Stored as the enum
 * name.
 */
enum class AccentColor(val label: String, val seed: Long) {
    Ocean("Ocean", 0xFF1F6FEB),
    Teal("Teal", 0xFF00897B),
    Forest("Forest", 0xFF3B7D2E),
    Amber("Amber", 0xFFE0A030),
    Coral("Coral", 0xFFE8674A),
    Rose("Rose", 0xFFD1487A),
    Violet("Violet", 0xFF6750A4),
    Slate("Slate", 0xFF5B6B7A),
}

/**
 * The chosen app colour source: Android's wallpaper palette, one of the [AccentColor] presets, or
 * a custom ARGB seed.
 */
sealed interface AccentSelection {
    data object Wallpaper : AccentSelection
    data class Preset(val accent: AccentColor) : AccentSelection
    data class Custom(val argb: Long) : AccentSelection

    companion object {
        /** Used when wallpaper colours are unavailable (pre-Android 12). */
        val FallbackPreset: AccentSelection = Preset(AccentColor.Ocean)

        fun deserialize(value: String?): AccentSelection? = when {
            value == null -> null
            value == WallpaperKey -> Wallpaper
            value.startsWith("#") -> value.drop(1).toLongOrNull(16)?.let { Custom(it) }
            else -> runCatching { Preset(AccentColor.valueOf(value)) }.getOrNull()
        }

        fun serialize(selection: AccentSelection): String = when (selection) {
            Wallpaper -> WallpaperKey
            is Preset -> selection.accent.name
            is Custom -> "#" + selection.argb.toString(16).uppercase()
        }

        private const val WallpaperKey = "wallpaper"
    }
}

/** How strongly the seed colour is expressed across the generated scheme. */
enum class ColorStyle(val label: String) {
    Balanced("Balanced"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
    Monochrome("Mono"),
}
