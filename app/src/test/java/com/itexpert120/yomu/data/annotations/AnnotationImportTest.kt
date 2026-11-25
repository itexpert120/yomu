package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.data.highlights.toReaderHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationImportTest {
    private val cfiLocator = """{"href":"ch1.xhtml","locations":{"cfi":"epubcfi(/6/4!/4/2,/1:0,/1:24)","progression":0.1}}"""
    private val local = HighlightEntity("local", "book", cfiLocator, "Fear is the mind-killer.", -256, 100)
    private val localBookmark = BookmarkEntity("bm", "book", """{"href":"ch2.xhtml"}""", "ch2", "Two", 0.5, 100)

    private var ids = 0
    private fun plan(file: AnnotationFile, highlights: List<HighlightEntity> = listOf(local), bookmarks: List<BookmarkEntity> = listOf(localBookmark)) = AnnotationMerge.plan("book", file, highlights, bookmarks, newId = { "new-${++ids}" }, now = 999)

    private fun exported(vararg highlights: HighlightEntity, bookmarks: List<ReaderBookmark> = emptyList()): AnnotationFile {
        val document = AnnotationDocument.of("Dune", "Frank Herbert", "sha", 1, highlights.map { it.toReaderHighlight() }, bookmarks, emptyList(), emptyList())
        return (AnnotationFileCodec.parse(AnnotationRenderer.render(document, AnnotationFormat.Json)) as AnnotationFileParse.Parsed).file
    }

    @Test
    fun reimportingAnExportChangesNothing() {
        val reader = ReaderBookmark("bm", localBookmark.locatorJson, "ch2", "Two", 0.5, 100)
        val file = exported(local, bookmarks = listOf(reader))
        val plan = plan(file)
        assertEquals(ImportPlan(emptyList(), emptyList(), emptyList()), plan)
        val preview = AnnotationMerge.preview(file, "SHA", plan)
        assertTrue(preview.bookMatches)
        assertTrue(preview.changesNothing)
        assertEquals(1, preview.highlights)
        assertEquals(1, preview.bookmarks)
    }

    @Test
    fun newAnnotationsGetFreshIdsAndKeepTheirFields() {
        val elsewhere = HighlightEntity("their-id", "other", """{"href":"ch3.xhtml"}""", "I must not fear.", 0xFFB4D4F5.toInt(), 50, "Litany", "underline", 60)
        val plan = plan(exported(elsewhere))
        assertEquals(listOf(elsewhere.copy(id = "new-1", bookId = "book")), plan.insertHighlights)
        assertTrue(plan.updateHighlights.isEmpty())
    }

    @Test
    fun theSameHighlightIsRecognisedByTextAndCfiEvenWithADifferentLocatorSpelling() {
        val sameSpot = local.copy(id = "x", locatorJson = """{"locations":{"progression":0.1,"cfi":"epubcfi(/6/4!/4/2,/1:0,/1:24)"},"href":"ch1.xhtml","title":"One"}""")
        assertTrue(plan(exported(sameSpot)).insertHighlights.isEmpty())
        // The same text somewhere else is another highlight.
        val elsewhere = local.copy(id = "y", locatorJson = """{"href":"ch1.xhtml","locations":{"cfi":"epubcfi(/6/4!/4/8,/1:0,/1:24)"}}""")
        assertEquals(1, plan(exported(elsewhere)).insertHighlights.size)
    }

    @Test
    fun aNewerEditWinsTheNoteAndAnOlderOneDoesNot() {
        val edited = local.copy(note = "Litany", style = HighlightStyle.Squiggly.key, modifiedAt = 500)
        assertEquals(listOf(edited), plan(exported(edited.copy(id = "theirs"))).updateHighlights)

        val localEdit = local.copy(note = "Mine", modifiedAt = 800)
        assertTrue(plan(exported(edited), highlights = listOf(localEdit)).updateHighlights.isEmpty())

        // An unedited copy never clears a local note.
        assertTrue(plan(exported(local), highlights = listOf(localEdit)).updateHighlights.isEmpty())
    }

    @Test
    fun duplicatesWithinAFileCollapse() {
        val other = HighlightEntity("o", "b", """{"href":"ch9.xhtml"}""", "Twice", -1, 1)
        val plan = plan(exported(other, other.copy(id = "o2")))
        assertEquals(1, plan.insertHighlights.size)
    }

    @Test
    fun bookmarksOnTheSamePageAreSkipped() {
        val samePage = ReaderBookmark("x", """{"href":"ch2.xhtml","locations":{"progression":0.3}}""", "ch2", "Two", 0.505, 1)
        val otherPage = ReaderBookmark("y", """{"href":"ch2.xhtml","locations":{"progression":0.9}}""", "ch2", "Two", 0.6, 1)
        val plan = plan(exported(bookmarks = listOf(samePage, otherPage)))
        assertEquals(listOf(BookmarkEntity("new-1", "book", otherPage.locatorJson, "ch2", "Two", 0.6, 1)), plan.insertBookmarks)
    }

    @Test
    fun anotherBooksFileIsFlagged() {
        val file = exported(local)
        assertFalse(AnnotationMerge.preview(file, "different", plan(file)).bookMatches)
        assertFalse(AnnotationMerge.preview(file.copy(book = file.book.copy(sha256 = null)), null, plan(file)).bookMatches)
    }

    @Test
    fun previewCountsNotes() {
        val file = exported(local.copy(note = "n", modifiedAt = 101), local.copy(id = "2", text = "Other"))
        val preview = AnnotationMerge.preview(file, "sha", plan(file))
        assertEquals(2, preview.highlights)
        assertEquals(1, preview.notes)
        assertEquals(1, preview.newHighlights)
        assertEquals(1, preview.updatedHighlights)
        assertEquals("Dune", preview.fileBookTitle)
    }

    @Test
    fun parseRejectsOtherFilesAndNewerVersions() {
        assertEquals(AnnotationFileParse.NotAnnotationFile, AnnotationFileCodec.parse("not json"))
        assertEquals(AnnotationFileParse.NotAnnotationFile, AnnotationFileCodec.parse("""{"format":"something-else","version":1}"""))
        assertEquals(AnnotationFileParse.NewerVersion(2), AnnotationFileCodec.parse("""{"format":"open-reader-annotations","version":2}"""))
    }

    @Test
    fun parseToleratesUnknownAndMissingFields() {
        val parsed = AnnotationFileCodec.parse(
            """{"format":"open-reader-annotations","version":1,"future":true,
               "highlights":[{"text":"Hi","locator":{"href":"a.xhtml"},"style":"glow","extra":1}]}""",
        ) as AnnotationFileParse.Parsed
        val plan = plan(parsed.file, highlights = emptyList())
        val row = plan.insertHighlights.single()
        assertEquals("glow", row.style)
        assertEquals(DEFAULT_HIGHLIGHT_ARGB, row.colorArgb)
        assertEquals(999L, row.createdAt)
        assertEquals("""{"href":"a.xhtml"}""", row.locatorJson)
    }
}
