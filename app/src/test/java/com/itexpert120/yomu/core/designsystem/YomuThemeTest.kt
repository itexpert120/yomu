package com.itexpert120.yomu.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YomuThemeTest {
    @Test
    fun seededSchemesExposeDistinctLightAndDarkRoles() {
        val light = yomuSeededColorScheme(AccentColor.Ocean.seed, dark = false, style = ColorStyle.Balanced)
        val dark = yomuSeededColorScheme(AccentColor.Ocean.seed, dark = true, style = ColorStyle.Balanced)

        assertTrue(light.background.luminance() > 0.5f)
        assertTrue(dark.background.luminance() < 0.5f)
        assertNotEquals(light.primary, dark.primary)
        assertNotEquals(light.surfaceContainer, dark.surfaceContainer)
    }

    @Test
    fun presetsProduceDifferentPrimaries() {
        val ocean = yomuSeededColorScheme(AccentColor.Ocean.seed, dark = false, style = ColorStyle.Balanced)
        val forest = yomuSeededColorScheme(AccentColor.Forest.seed, dark = false, style = ColorStyle.Balanced)
        assertNotEquals(ocean.primary, forest.primary)
    }

    @Test
    fun oledUsesBlackBaseAndTieredContainers() {
        val oled = yomuSeededColorScheme(AccentColor.Ocean.seed, dark = true, style = ColorStyle.Balanced).toOled()

        assertEquals(Color.Black, oled.background)
        assertEquals(Color.Black, oled.surface)
        assertEquals(Color.Black, oled.surfaceContainerLowest)
        assertEquals(Color(0xFF0C0C0C), oled.surfaceContainerLow)
        assertEquals(Color(0xFF131313), oled.surfaceContainer)
        assertEquals(Color(0xFF1B1B1B), oled.surfaceContainerHigh)
        assertEquals(Color(0xFF242424), oled.surfaceContainerHighest)
    }

    @Test
    fun accentSelectionRoundTrips() {
        listOf(
            AccentSelection.Wallpaper,
            AccentSelection.Preset(AccentColor.Rose),
            AccentSelection.Custom(0xFF336699),
        ).forEach { selection ->
            assertEquals(selection, AccentSelection.deserialize(AccentSelection.serialize(selection)))
        }
        assertNull(AccentSelection.deserialize(null))
        assertNull(AccentSelection.deserialize("NotAColour"))
    }
}
