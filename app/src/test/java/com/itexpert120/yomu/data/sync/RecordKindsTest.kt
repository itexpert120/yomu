package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.BookCollectionEntity
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.ChapterProgressEntity
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.database.CollectionEntity
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.database.ReaderSettingsEntity
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each kind at its interface: a record snapshotted on one device and applied on another (and
 * re-applied on the first) snapshots back to the same payload, so nothing echoes. Sessions are
 * append-only (their snapshot lists only sessions missing from the ledger) and not covered here.
 */
class RecordKindsTest {
    private val a = FakeSyncWorld()
    private val b = FakeSyncWorld()
    private val hlc = Hlc(1_700_000_000_000, 0, "a")

    @Test
    fun kindKeysAreTheWireKeys() {
        assertEquals(
            listOf("bookSettings", "bookmark", "chapter", "collection", "font", "highlight", "meta", "progress", "settings", "shelf", "theme"),
            a.kinds.all.map { it.key },
        )
        assertTrue(a.kinds.byKey("progress")!!.carriesDeviceLabel)
        assertEquals(0, a.kinds.byKey("font")!!.applyOrder)
    }

    /** Snapshots [key] on device a, applies every record on b and on a, and checks both are fixed points. */
    private fun assertFixedPoint(key: String, firstContact: Boolean = true) = runBlocking {
        val records = a.kinds.byKey(key)!!.snapshot()
        assertTrue("$key: nothing to sync", records.isNotEmpty())
        for (world in listOf(b, a)) {
            val kind = world.kinds.byKey(key)!!
            records.forEach { record ->
                val applied = world.kinds.apply(kind, record.entityId, record.payload, firstContact, "Phone", hlc)
                assertEquals("$key/${record.entityId}", record.payload, applied.ledgerPayload)
                assertFalse(applied.dirty)
                assertFalse(applied.awaitingBook)
            }
            val again = kind.snapshot().associate { it.name to it.payload }
            records.forEach { assertEquals(it.name, it.payload, again[it.name]) }
        }
    }

    private fun bothHaveBook() {
        a.addBook("a1", "sha")
        b.addBook("b1", "sha")
    }

    @Test
    fun meta() {
        bothHaveBook()
        a.addBook("a1", "sha") { copy(title = "Dune", subtitle = "Book one", description = "Spice.", metadataEditedAt = 5) }
        assertFixedPoint("meta")
        assertEquals("Dune", b.books.getValue("b1").title)
    }

    @Test
    fun progress() {
        bothHaveBook()
        a.addBook("a1", "sha") { copy(progress = 0.4f, totalProgression = 0.41, locatorJson = "{\"href\":\"ch2\"}", currentChapterId = "ch2", lastOpenedAt = 10, startedAt = 5) }
        assertFixedPoint("progress")
        assertEquals("{\"href\":\"ch2\"}", b.books.getValue("b1").locatorJson)
    }

    @Test
    fun progressFromAnotherDeviceWhileReadingHereBecomesAnOffer() = runBlocking {
        bothHaveBook()
        a.addBook("a1", "sha") { copy(progress = 0.4f, locatorJson = "{\"href\":\"ch2\"}", lastOpenedAt = 10) }
        b.addBook("b1", "sha") { copy(progress = 0.1f, locatorJson = "{\"href\":\"ch1\"}", lastOpenedAt = 20) }
        val record = a.kinds.byKey("progress")!!.snapshot().single()
        val kind = b.kinds.byKey("progress")!!
        val applied = b.kinds.apply(kind, record.entityId, record.payload, false, "Phone", hlc)

        assertEquals(kind.snapshot().single().payload, applied.ledgerPayload)
        assertEquals("{\"href\":\"ch1\"}", b.books.getValue("b1").locatorJson)
        val offer = Json.decodeFromString(ProgressOffer.serializer(), b.store.metadata("offer/sha")!!)
        assertEquals(ProgressOffer("sha", "{\"href\":\"ch2\"}", 0.4f, "Phone", hlc.encode()), offer)
    }

    @Test
    fun chapter() {
        bothHaveBook()
        // Chapter ids may contain the record id's separator.
        a.chapterReads += ChapterReadEntity("a1", "OEBPS/ch|1.xhtml#part/2")
        a.chapterProgress["a1" to "ch2"] = ChapterProgressEntity("a1", "ch2", 0.5f, 100, manuallyRead = true)
        assertFixedPoint("chapter")
        assertTrue(ChapterReadEntity("b1", "OEBPS/ch|1.xhtml#part/2") in b.chapterReads)
    }

    @Test
    fun chapterFirstContactKeepsReadFromEitherSideAndFurthestProgress() = runBlocking {
        bothHaveBook()
        a.chapterProgress["a1" to "ch"] = ChapterProgressEntity("a1", "ch", 0.9f, 100, manuallyRead = true)
        b.chapterReads += ChapterReadEntity("b1", "ch")
        b.chapterProgress["b1" to "ch"] = ChapterProgressEntity("b1", "ch", 0.4f, 200)
        val record = a.kinds.byKey("chapter")!!.snapshot().single()
        val kind = b.kinds.byKey("chapter")!!

        val applied = b.kinds.apply(kind, record.entityId, record.payload, firstContact = true, device = null, hlc = hlc)
        val merged = ChapterPayload(read = true, progress = 0.9f, updatedAt = 100, manual = true)
        assertEquals(SyncJson.encodeToString(ChapterPayload.serializer(), merged), applied.ledgerPayload)
        assertTrue("a merge differing from the incoming record is pushed back", applied.dirty)
        assertEquals(applied.ledgerPayload, kind.snapshot().single().payload)
    }

    @Test
    fun chapterFirstContactWithEqualProgressPrefersTheNewerState() = runBlocking {
        bothHaveBook()
        a.chapterProgress["a1" to "ch"] = ChapterProgressEntity("a1", "ch", 0.5f, 20, manuallyRead = true)
        b.chapterProgress["b1" to "ch"] = ChapterProgressEntity("b1", "ch", 0.5f, 10)
        val record = a.kinds.byKey("chapter")!!.snapshot().single()
        val kind = b.kinds.byKey("chapter")!!

        val applied = b.kinds.apply(kind, record.entityId, record.payload, firstContact = true, device = null, hlc = hlc)
        assertEquals(record.payload, applied.ledgerPayload)
        assertFalse(applied.dirty)
    }

    @Test
    fun chapterFirstContactWithoutProgressOnOneSideKeepsTheOther() = runBlocking {
        bothHaveBook()
        a.chapterProgress["a1" to "ch"] = ChapterProgressEntity("a1", "ch", 0.3f, 50)
        b.chapterReads += ChapterReadEntity("b1", "ch")
        val record = a.kinds.byKey("chapter")!!.snapshot().single()
        val kind = b.kinds.byKey("chapter")!!

        val applied = b.kinds.apply(kind, record.entityId, record.payload, firstContact = true, device = null, hlc = hlc)
        assertEquals(SyncJson.encodeToString(ChapterPayload.serializer(), ChapterPayload(read = true, progress = 0.3f, updatedAt = 50, manual = false)), applied.ledgerPayload)
        assertTrue(applied.dirty)
    }

    @Test
    fun aChapterAtItsEndIsStoredReadByTheSharedRule() = runBlocking {
        bothHaveBook()
        val kind = b.kinds.byKey("chapter")!!
        val incoming = SyncJson.encodeToString(ChapterPayload.serializer(), ChapterPayload(read = false, progress = 1f, updatedAt = 50, manual = false))
        val record = ChapterRecordId.of("sha", "ch")

        val applied = b.kinds.apply(kind, record, incoming, firstContact = false, device = null, hlc = hlc)
        assertTrue(ChapterReadEntity("b1", "ch") in b.chapterReads)
        assertEquals(SyncJson.encodeToString(ChapterPayload.serializer(), ChapterPayload(read = true, progress = 1f, updatedAt = 50, manual = false)), applied.ledgerPayload)
    }

    @Test
    fun bookmark() {
        bothHaveBook()
        a.bookmarks["bm1"] = BookmarkEntity("bm1", "a1", "{}", "ch1.xhtml", "One", 0.25, 7)
        assertFixedPoint("bookmark")
        assertEquals("b1", b.bookmarks.getValue("bm1").bookId)
    }

    @Test
    fun highlight() {
        bothHaveBook()
        a.highlights["h1"] = HighlightEntity("h1", "a1", "{}", "Fear is the mind-killer.", -256, 7)
        assertFixedPoint("highlight")
    }

    @Test
    fun highlightWithNoteAndStyle() {
        bothHaveBook()
        a.highlights["h1"] = HighlightEntity("h1", "a1", "{}", "Fear is the mind-killer.", -256, 7, note = "Litany.", style = "squiggly", modifiedAt = 9)
        assertFixedPoint("highlight")
        val synced = b.highlights.getValue("h1")
        assertEquals("Litany.", synced.note)
        assertEquals("squiggly", synced.style)
        assertEquals(9L, synced.modifiedAt)
    }

    @Test
    fun aStyleFromANewerBuildRoundTrips() {
        bothHaveBook()
        a.highlights["h1"] = HighlightEntity("h1", "a1", "{}", "Fear", -256, 7, style = "glow")
        assertFixedPoint("highlight")
        assertEquals("glow", b.highlights.getValue("h1").style)
    }

    @Test
    fun recordForABookNotHereWaits() = runBlocking {
        a.addBook("a1", "sha")
        a.bookmarks["bm1"] = BookmarkEntity("bm1", "a1", "{}", null, null, 0.25, 7)
        val record = a.kinds.byKey("bookmark")!!.snapshot().single()
        val applied = b.kinds.apply(b.kinds.byKey("bookmark")!!, record.entityId, record.payload, true, null, hlc)

        assertEquals(Applied(record.payload, sha256 = "sha", awaitingBook = true), applied)
        assertTrue(b.bookmarks.isEmpty())
    }

    @Test
    fun bookSettings() {
        bothHaveBook()
        val settings = ReaderSettings(theme = ReaderThemeMode.Sepia, font = ReaderFont.Karla, fontScale = 1.3f, customFont = CustomFontRef("Inter", "/a/Inter.woff2"))
        a.readerSettingsRows.value = mapOf("a1" to ReaderSettingsEntity("a1", Json.encodeToString(ReaderSettings.serializer(), settings)))
        a.fonts.families += "Inter"
        b.fonts.families += "Inter"
        assertFixedPoint("bookSettings")
        assertNotNull(b.readerSettingsRows.value["b1"])
    }

    @Test
    fun bookCollections() {
        bothHaveBook()
        a.memberships += BookCollectionEntity("a1", "c2")
        a.memberships += BookCollectionEntity("a1", "c1")
        a.addBook("a1", "sha") { copy(manualStatus = "OnHold", shelfEditedAt = 3) }
        assertFixedPoint("shelf")
        assertEquals("OnHold", b.books.getValue("b1").manualStatus)
    }

    @Test
    fun collection() {
        a.collections["c1"] = CollectionEntity("c1", "Favourites", 2, 9)
        assertFixedPoint("collection")
        assertEquals("Favourites", b.collections.getValue("c1").name)
    }

    @Test
    fun settings() {
        runBlocking {
            a.appSettings.setThemePreference(ThemePreference.Dark)
            a.appSettings.setColorStyle(ColorStyle.Vibrant)
            a.libraryPrefs.setSortMode(SortMode.Title)
            a.libraryPrefs.setViewMode(LibraryViewMode.List)
            a.libraryPrefs.setCoverCrop(false)
            a.readerSettings.setGlobal(ReaderSettings(theme = ReaderThemeMode.Black, fontScale = 1.4f, customFont = CustomFontRef("Inter", "/a/Inter.woff2")))
        }
        a.fonts.families += "Inter"
        b.fonts.families += "Inter"
        assertFixedPoint("settings")
    }

    @Test
    fun theme() {
        runBlocking { a.readerSettings.saveCustomTheme(CustomReaderTheme("t1", "Night", 0xFF000000, 0xFFFFFFFF)) }
        assertFixedPoint("theme")
    }

    @Test
    fun font() {
        a.fonts.families += "Inter"
        assertFixedPoint("font")
        assertTrue("Inter" in b.fonts.families)
    }

    @Test
    fun fontStillDownloadingIsNotARemoval() = runBlocking {
        a.fonts.families += "Inter"
        val record = a.kinds.byKey("font")!!.snapshot().single()
        b.fonts.online = false
        val kind = b.kinds.byKey("font")!!
        b.kinds.apply(kind, record.entityId, record.payload, true, null, hlc)

        assertEquals(listOf(record), kind.snapshot())
        b.fonts.online = true
        b.kinds.retryPending()
        assertTrue("Inter" in b.fonts.families)
        assertEquals(listOf(record), kind.snapshot())
    }

    @Test
    fun removedFontStopsDownloading() = runBlocking {
        b.fonts.online = false
        val kind = b.kinds.byKey("font")!!
        b.kinds.apply(kind, "Inter", SyncJson.encodeToString(FontPayload.serializer(), FontPayload("Inter")), true, null, hlc)
        b.kinds.apply(kind, "Inter", null, false, null, hlc)

        assertEquals(emptyList<LocalRecord>(), kind.snapshot())
        b.fonts.online = true
        b.kinds.retryPending()
        assertTrue(b.fonts.families.isEmpty())
    }
}
