package com.itexpert120.yomu.app

import android.content.Context
import androidx.annotation.StyleRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
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

/** The splash as the platform draws it: the desk colour with the wordmark in ink, light | dark | black. */
@Preview(name = "Splash", widthDp = 900, heightDp = 600)
@Composable
private fun SplashPreview() {
    Row {
        listOf(
            0xFFE9EDF0 to R.drawable.ic_splash_wordmark_light,
            0xFF0F1215 to R.drawable.ic_splash_wordmark_dark,
            0xFF000000 to R.drawable.ic_splash_wordmark_dark,
        ).forEach { (desk, wordmark) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(desk)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painter = painterResource(wordmark), contentDescription = null)
            }
        }
    }
}
