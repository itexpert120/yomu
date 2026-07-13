package com.itexpert120.yomu.app

import com.itexpert120.yomu.core.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashThemeTest {
    @Test
    fun `system theme follows system and preserves oled preference`() {
        assertEquals(YomuSplashTheme.System, ThemePreference.System.toSplashTheme(oledDark = false))
        assertEquals(YomuSplashTheme.SystemOled, ThemePreference.System.toSplashTheme(oledDark = true))
    }

    @Test
    fun `light theme ignores oled preference`() {
        assertEquals(YomuSplashTheme.Light, ThemePreference.Light.toSplashTheme(oledDark = false))
        assertEquals(YomuSplashTheme.Light, ThemePreference.Light.toSplashTheme(oledDark = true))
    }

    @Test
    fun `dark theme uses oled splash when enabled`() {
        assertEquals(YomuSplashTheme.Dark, ThemePreference.Dark.toSplashTheme(oledDark = false))
        assertEquals(YomuSplashTheme.Oled, ThemePreference.Dark.toSplashTheme(oledDark = true))
    }
}
