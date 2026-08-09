package com.itexpert120.yomu.core.model

/**
 * Persisted base theme choice. [System] follows the device light/dark setting. Pure-black surfaces
 * and Android's dynamic palette are separate preferences; OLED only applies in dark mode, while
 * dynamic colors apply to both light and dark themes.
 */
enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}
