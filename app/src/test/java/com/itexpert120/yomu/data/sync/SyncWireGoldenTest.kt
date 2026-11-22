package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.model.ReaderThemeMode
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Payloads exactly as this build writes them. Older and newer app versions in the same sync group
 * decode these bytes, so a change here is a wire-protocol change, not a refactor.
 */
class SyncWireGoldenTest {
    private fun <T> assertRoundTrip(serializer: KSerializer<T>, golden: String) {
        val decoded = SyncJson.decodeFromString(serializer, golden)
        assertEquals(golden, SyncJson.encodeToString(serializer, decoded))
    }

    @Test
    fun recordNames() {
        // Kind keys are pinned where the kinds are built: RecordKindsTest.
        assertEquals("abc|OEBPS/ch1.xhtml", ChapterRecordId.of("abc", "OEBPS/ch1.xhtml"))
        assertEquals("progress/abc", LocalRecord("progress", "abc", "abc", "{}", 1).name)
        assertEquals(listOf("app", "library", "readerDefaults"), listOf(SettingsId.APP, SettingsId.LIBRARY, SettingsId.READER_DEFAULTS))
    }

    @Test
    fun envelope() {
        assertRoundTrip(SyncEnvelope.serializer(), """{"name":"progress/abc","payload":"{\"x\":1}","device":"Pixel 9"}""")
        assertRoundTrip(SyncEnvelope.serializer(), """{"name":"bookmark/b1","payload":null,"device":null}""")
        assertEquals(
            """{"name":"theme/t","payload":null,"device":null}""",
            SyncJson.encodeToString(SyncEnvelope.serializer(), SyncEnvelope("theme/t")),
        )
    }

    @Test
    fun hlc() {
        assertEquals("1700000000000:00003:dev1", Hlc(1_700_000_000_000, 3, "dev1").encode())
    }

    @Test
    fun meta() = assertRoundTrip(
        MetaPayload.serializer(),
        """{"title":"Dune","subtitle":null,"author":"Frank Herbert","description":"Spice."}""",
    )

    @Test
    fun progress() = assertRoundTrip(
        ProgressPayload.serializer(),
        """{"locator":"{\"href\":\"ch1.xhtml\"}","progress":0.25,"totalProgression":0.2512,"chapterId":"ch1","lastOpenedAt":1700000000000,"startedAt":1690000000000,"finishedAt":0}""",
    )

    @Test
    fun chapter() {
        assertRoundTrip(ChapterPayload.serializer(), """{"read":true,"progress":0.5,"updatedAt":1700000000000,"manual":false}""")
        assertRoundTrip(ChapterPayload.serializer(), """{"read":true,"progress":null,"updatedAt":0,"manual":false}""")
    }

    @Test
    fun bookmark() = assertRoundTrip(
        BookmarkPayload.serializer(),
        """{"sha256":"abc","locator":"{}","href":"ch2.xhtml","chapterTitle":"Two","progression":0.75,"createdAt":1700000000000}""",
    )

    @Test
    fun highlight() = assertRoundTrip(
        HighlightPayload.serializer(),
        """{"sha256":"abc","locator":"{}","text":"Fear is the mind-killer.","color":-256,"createdAt":1700000000000}""",
    )

    @Test
    fun shelf() {
        assertRoundTrip(ShelfPayload.serializer(), """{"collections":["c1","c2"],"status":"OnHold"}""")
        assertRoundTrip(ShelfPayload.serializer(), """{"collections":[],"status":null}""")
    }

    @Test
    fun session() = assertRoundTrip(SessionPayload.serializer(), """{"sha256":"abc","startedAt":1700000000000,"seconds":600}""")

    @Test
    fun appSettings() = assertRoundTrip(AppSettingsPayload.serializer(), """{"theme":"Dark","colorStyle":"Vibrant"}""")

    @Test
    fun librarySettings() {
        val golden = """{"sort":"Title","group":"None","view":"List","coverCrop":false}"""
        assertRoundTrip(LibraryPayload.serializer(), golden)
        // Older versions require `group`, so it is always sent.
        assertEquals(golden, SyncJson.encodeToString(LibraryPayload.serializer(), LibraryPayload(sort = "Title", view = "List", coverCrop = false)))
    }

    @Test
    fun librarySettingsFromOlderVersionsStillDecode() {
        val older = SyncJson.decodeFromString(LibraryPayload.serializer(), """{"sort":"Author","group":"Author","view":"Grid","coverCrop":true}""")
        assertEquals(LibraryPayload("Author", "Author", "Grid", true), older)
    }

    @Test
    fun theme() = assertRoundTrip(CustomReaderTheme.serializer(), """{"id":"t1","name":"Night","background":4278190080,"text":4294967295}""")

    @Test
    fun font() = assertRoundTrip(FontPayload.serializer(), """{"family":"Inter"}""")

    @Test
    fun collection() = assertRoundTrip(CollectionPayload.serializer(), """{"name":"Favourites","position":2}""")

    private val readerGolden =
        """{"layout":"Paged","theme":"Custom","customBackground":4294967295,"customText":4278190080,"font":"Karla","customFont":"Inter",""" +
            """"textAlign":"Justify","tapNavigation":false,"showFooter":true,"footerShowBattery":false,"footerShowClock":true,""" +
            """"footerShowProgress":true,"footerShowPagesLeft":true,"showScrollbar":false,"immersiveChrome":true}"""

    @Test
    fun readerSettings() {
        val settings = ReaderSettings(
            layout = ReaderLayout.Paged,
            theme = ReaderThemeMode.Custom,
            customBackground = 0xFFFFFFFF,
            customText = 0xFF000000,
            font = ReaderFont.Karla,
            customFont = CustomFontRef("Inter", "/fonts/Inter.woff2"),
            fontScale = 1.3f,
            lineHeight = 1.6f,
            textAlign = ReaderTextAlign.Justify,
            brightness = 0.1f,
            tapNavigation = false,
            footerShowBattery = false,
            footerShowPagesLeft = true,
            keepScreenOn = false,
            showScrollbar = false,
            immersiveChrome = true,
        )
        assertEquals(readerGolden, ReaderSettingsSync.toPayload(settings))
        val applied = ReaderSettingsSync.merge(ReaderSettings(), readerGolden) { CustomFontRef(it, "/fonts/$it.woff2") }
        assertEquals(readerGolden, ReaderSettingsSync.toPayload(applied))
    }

    @Test
    fun readerSettingsFromOlderVersionsStillDecode() {
        val older = """{"layout":"Scroll","theme":"Sepia","font":"Merriweather","fontScale":1.4,"lineHeight":1.8,"customFont":null}"""
        val applied = ReaderSettingsSync.merge(ReaderSettings(fontScale = 0.9f), older) { null }
        assertEquals(ReaderSettings(theme = ReaderThemeMode.Sepia, fontScale = 0.9f), applied)
    }
}
