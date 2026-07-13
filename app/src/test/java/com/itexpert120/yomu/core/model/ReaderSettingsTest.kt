package com.itexpert120.yomu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun darkThemeUsesWarmLowGlarePalette() {
        val settings = ReaderSettings(theme = ReaderThemeMode.Dark)

        assertEquals(0xFF1C1B1A, settings.backgroundArgb)
        assertEquals(0xFFE9E3D8, settings.textArgb)
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
    }
}
