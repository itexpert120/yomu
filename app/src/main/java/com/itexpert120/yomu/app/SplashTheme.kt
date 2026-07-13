package com.itexpert120.yomu.app

import android.content.Context
import androidx.annotation.StyleRes
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.model.ThemePreference

internal enum class YomuSplashTheme(@StyleRes val styleRes: Int) {
    System(R.style.Theme_Yomu_Starting),
    SystemOled(R.style.Theme_Yomu_Starting_SystemOled),
    Light(R.style.Theme_Yomu_Starting_Light),
    Dark(R.style.Theme_Yomu_Starting_Dark),
    Oled(R.style.Theme_Yomu_Starting_Oled),
}

internal fun ThemePreference.toSplashTheme(oledDark: Boolean): YomuSplashTheme = when (this) {
    ThemePreference.System -> if (oledDark) YomuSplashTheme.SystemOled else YomuSplashTheme.System
    ThemePreference.Light -> YomuSplashTheme.Light
    ThemePreference.Dark -> if (oledDark) YomuSplashTheme.Oled else YomuSplashTheme.Dark
}

/**
 * A tiny synchronous cache of the DataStore-backed theme choice. Android needs the starting theme
 * before Compose and DataStore can load, so this cache is updated whenever the app theme resolves.
 */
internal class SplashThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun read(): YomuSplashTheme = preferences.getString(KeyTheme, null)
        ?.let { name -> runCatching { YomuSplashTheme.valueOf(name) }.getOrNull() }
        ?: YomuSplashTheme.System

    fun write(theme: YomuSplashTheme): Boolean {
        if (read() == theme) return false
        preferences.edit().putString(KeyTheme, theme.name).apply()
        return true
    }

    private companion object {
        const val PreferencesName = "yomu_startup"
        const val KeyTheme = "splash_theme"
    }
}
