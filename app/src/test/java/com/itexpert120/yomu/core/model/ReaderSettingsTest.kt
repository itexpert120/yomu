package com.itexpert120.yomu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun builtInThemesUseCompleteMinimalPalettes() {
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFFFFFFFF,
                textArgb = 0xFF24282C,
                secondaryTextArgb = 0xFF5B636B,
                selectionArgb = 0xFFDCE8F7,
                linkArgb = 0xFF326EA8,
                borderArgb = 0xFFEDF0F2,
            ),
            ReaderSettings(theme = ReaderThemeMode.Light).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFFF5EBDA,
                textArgb = 0xFF3B2F22,
                secondaryTextArgb = 0xFF6E5D48,
                selectionArgb = 0xFFDED3C2,
                linkArgb = 0xFF456F91,
                borderArgb = 0xFFE3D3B6,
            ),
            ReaderSettings(theme = ReaderThemeMode.Sepia).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFF121416,
                textArgb = 0xFFC9CDD1,
                secondaryTextArgb = 0xFF9AA3AB,
                selectionArgb = 0xFF30343A,
                linkArgb = 0xFF82B7F5,
                borderArgb = 0xFF2A2F34,
            ),
            ReaderSettings(theme = ReaderThemeMode.Dark).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFF000000,
                textArgb = 0xFFB9BEC3,
                secondaryTextArgb = 0xFF8C959D,
                selectionArgb = 0xFF25282D,
                linkArgb = 0xFF76ADEF,
                borderArgb = 0xFF202328,
            ),
            ReaderSettings(theme = ReaderThemeMode.Black).colorPalette,
        )
    }

    @Test
    fun unreadableCustomTextIsCorrected() {
        val settings = ReaderSettings(
            theme = ReaderThemeMode.Custom,
            customBackground = 0xFFFFFFFF,
            customText = 0xFFFDFDFD,
        )

        assertNotEquals(0xFFFDFDFD, settings.textArgb)
        assertEquals(0xFF171717, settings.textArgb)
    }

    @Test
    fun readableCustomTextIsPreserved() {
        val settings = ReaderSettings(
            theme = ReaderThemeMode.Custom,
            customBackground = 0xFF101010,
            customText = 0xFFEAEAEA,
        )

        assertEquals(0xFFEAEAEA, settings.textArgb)
        assertNotEquals(settings.textArgb, settings.colorPalette.secondaryTextArgb)
        assertNotEquals(settings.backgroundArgb, settings.colorPalette.selectionArgb)
    }
}
