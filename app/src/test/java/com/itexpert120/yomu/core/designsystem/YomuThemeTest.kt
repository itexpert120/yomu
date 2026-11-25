package com.itexpert120.yomu.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YomuThemeTest {
    private val all = listOf(StackColors.Light, StackColors.Dark, StackColors.Black)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun everyTextTokenMeetsItsContrastOnSheetAndDesk() {
        for (stack in all) {
            for (surface in listOf(stack.sheet, stack.desk)) {
                assertTrue("ink on $surface", contrast(stack.ink, surface) >= 7.0)
                assertTrue("body on $surface", contrast(stack.body, surface) >= 7.0)
                assertTrue("muted on $surface", contrast(stack.muted, surface) >= 4.5)
                assertTrue("faint on $surface", contrast(stack.faint, surface) >= 4.5)
                assertTrue("danger on $surface", contrast(stack.danger, surface) >= 4.5)
            }
            assertTrue(contrast(stack.onInk, stack.ink) >= 7.0)
        }
    }

    @Test
    fun themesMapStackTokensOntoMaterialRoles() {
        for (stack in all) {
            val scheme = stack.toColorScheme()
            assertEquals(stack.desk, scheme.background)
            assertEquals(stack.sheet, scheme.surface)
            assertEquals(stack.ink, scheme.primary)
            assertEquals(stack.onInk, scheme.onPrimary)
            assertEquals(stack.raised, scheme.surfaceContainer)
            assertEquals(stack.muted, scheme.onSurfaceVariant)
            assertEquals(stack.danger, scheme.error)
            // No tonal elevation: the tint is the surface itself.
            assertEquals(stack.sheet, scheme.surfaceTint)
        }
    }

    @Test
    fun modesPickTheirStack() {
        assertEquals(StackColors.Light, StackColors.forMode(YomuThemeMode.Light))
        assertEquals(StackColors.Dark, StackColors.forMode(YomuThemeMode.Dark))
        assertEquals(Color.Black, StackColors.forMode(YomuThemeMode.Oled).desk)
        assertTrue(StackColors.Light.desk.luminance() > 0.5f)
        assertTrue(StackColors.Dark.desk.luminance() < 0.1f)
    }

    @Test
    fun storedMonochromeStyleMigratesToNeutral() {
        assertEquals(ColorStyle.Neutral, ColorStyle.fromStoredName("Monochrome"))
        assertEquals(ColorStyle.Vibrant, ColorStyle.fromStoredName("Vibrant"))
        assertNull(ColorStyle.fromStoredName("NotAStyle"))
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
