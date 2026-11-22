package com.itexpert120.yomu.app

import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveThemeModeTest {
    private fun mode(preference: ThemePreference, oled: Boolean, systemDark: Boolean) = AppAppearance(themePreference = preference, oledDark = oled).resolveThemeMode(systemDark)

    @Test
    fun `system preference follows the system setting`() {
        assertEquals(YomuThemeMode.Light, mode(ThemePreference.System, oled = false, systemDark = false))
        assertEquals(YomuThemeMode.Dark, mode(ThemePreference.System, oled = false, systemDark = true))
        assertEquals(YomuThemeMode.Oled, mode(ThemePreference.System, oled = true, systemDark = true))
    }

    @Test
    fun `oled applies only when the result is dark`() {
        assertEquals(YomuThemeMode.Light, mode(ThemePreference.System, oled = true, systemDark = false))
        assertEquals(YomuThemeMode.Light, mode(ThemePreference.Light, oled = true, systemDark = true))
        assertEquals(YomuThemeMode.Oled, mode(ThemePreference.Dark, oled = true, systemDark = false))
        assertEquals(YomuThemeMode.Dark, mode(ThemePreference.Dark, oled = false, systemDark = false))
    }
}
