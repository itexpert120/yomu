package com.itexpert120.yomu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun builtInThemesUseCompleteMinimalPalettes() {
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFFF7F7F5,
                textArgb = 0xFF222426,
                secondaryTextArgb = 0xFF62676D,
                selectionArgb = 0xFFDCE8F7,
                linkArgb = 0xFF326EA8,
                borderArgb = 0xFFE1E3E5,
            ),
            ReaderSettings(theme = ReaderThemeMode.Light).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFFF3EBDD,
                textArgb = 0xFF302B26,
                secondaryTextArgb = 0xFF71685E,
                selectionArgb = 0xFFDED3C2,
                linkArgb = 0xFF456F91,
                borderArgb = 0xFFD8CDBD,
            ),
            ReaderSettings(theme = ReaderThemeMode.Sepia).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFF17191C,
                textArgb = 0xFFD7DADE,
                secondaryTextArgb = 0xFFAEB4BC,
                selectionArgb = 0xFF30343A,
                linkArgb = 0xFF82B7F5,
                borderArgb = 0xFF2A2E34,
            ),
            ReaderSettings(theme = ReaderThemeMode.Dark).colorPalette,
        )
        assertEquals(
            ReaderColorPalette(
                backgroundArgb = 0xFF000000,
                textArgb = 0xFFD4D7DB,
                secondaryTextArgb = 0xFF9DA3AB,
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
