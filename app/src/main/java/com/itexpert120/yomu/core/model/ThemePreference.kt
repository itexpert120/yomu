package com.itexpert120.yomu.core.model

/**
 * Persisted base theme choice. [System] follows the device light/dark setting. Whether dark
 * renders as pure-black (OLED) is a separate toggle ([AppSettings.oledDark]) that only applies
 * when the resolved theme is dark.
 */
enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}
