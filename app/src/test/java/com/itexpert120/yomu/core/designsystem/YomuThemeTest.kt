package com.itexpert120.yomu.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YomuThemeTest {
    @Test
    fun staticModesExposeDistinctLightAndDarkRoles() {
        val light = yomuStaticColorScheme(YomuThemeMode.Light)
        val dark = yomuStaticColorScheme(YomuThemeMode.Dark)

        assertEquals(Color(0xFFFFFBFE), light.background)
        assertEquals(Color(0xFF1C1B1F), dark.background)
        assertTrue(light.primary != dark.primary)
        assertTrue(light.surfaceContainer != dark.surfaceContainer)
    }

    @Test
    fun oledUsesBlackBaseAndTieredContainers() {
        val oled = yomuStaticColorScheme(YomuThemeMode.Oled)

        assertEquals(Color.Black, oled.background)
        assertEquals(Color.Black, oled.surface)
        assertEquals(Color.Black, oled.surfaceContainerLowest)
        assertEquals(Color(0xFF0C0C0C), oled.surfaceContainerLow)
        assertEquals(Color(0xFF131313), oled.surfaceContainer)
        assertEquals(Color(0xFF1B1B1B), oled.surfaceContainerHigh)
        assertEquals(Color(0xFF242424), oled.surfaceContainerHighest)
    }
}
