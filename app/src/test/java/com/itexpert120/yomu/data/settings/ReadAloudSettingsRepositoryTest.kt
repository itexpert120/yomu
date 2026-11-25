package com.itexpert120.yomu.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudSettingsRepositoryTest {
    @Test
    fun voicesRoundTrip() {
        val voices = mapOf("en" to "en-us-x-sfg-local", "fr" to "fr-FR-language")

        assertEquals(voices, decodeVoices(encodeVoices(voices)))
    }

    @Test
    fun malformedVoiceLinesAreIgnored() {
        assertEquals(mapOf("en" to "a=b"), decodeVoices("en=a=b\n=x\nde=\nnoequals"))
        assertEquals(emptyMap<String, String>(), decodeVoices(null))
    }
}
