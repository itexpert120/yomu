package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderSettingsSyncTest {
    private val phone = ReaderSettings(
        theme = ReaderThemeMode.Sepia,
        font = ReaderFont.Karla,
        fontScale = 1.4f,
        lineHeight = 1.8f,
        pageMargins = 0.6f,
        paragraphSpacing = 1.0f,
        brightness = 0.9f,
    )
    private val tablet = ReaderSettings(
        fontScale = 0.9f,
        lineHeight = 1.4f,
        pageMargins = 2.0f,
        paragraphSpacing = 0.3f,
        brightness = 0.2f,
    )

    @Test fun payloadLeavesOutSizeAndSpacing() {
        val payload = ReaderSettingsSync.toPayload(phone)
        listOf("fontScale", "lineHeight", "pageMargins", "paragraphSpacing", "brightness").forEach {
            assertFalse("$it should not sync", payload.contains("\"$it\""))
        }
    }

    @Test fun mergeKeepsThisDevicesSizeAndSpacing() {
        val merged = ReaderSettingsSync.merge(tablet, ReaderSettingsSync.toPayload(phone)) { null }
        assertEquals(
            tablet.copy(theme = ReaderThemeMode.Sepia, font = ReaderFont.Karla),
            merged,
        )
    }

    @Test fun mergeIgnoresSizeAndSpacingSentByOlderVersions() {
        val legacy = """{"theme":"Sepia","fontScale":1.4,"lineHeight":1.8,"pageMargins":0.6,"paragraphSpacing":1.0}"""
        val merged = ReaderSettingsSync.merge(tablet, legacy) { null }
        assertEquals(tablet.copy(theme = ReaderThemeMode.Sepia), merged)
    }

    @Test fun sizeChangesAloneDoNotChangeThePayload() {
        assertEquals(
            ReaderSettingsSync.toPayload(phone),
            ReaderSettingsSync.toPayload(phone.copy(fontScale = 2.0f, lineHeight = null, pageMargins = 1.5f)),
        )
    }
}
