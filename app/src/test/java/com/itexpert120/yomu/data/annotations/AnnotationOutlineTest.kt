package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationOutlineTest {
    private val readingOrder = listOf("OEBPS/cover.xhtml", "OEBPS/ch%201.xhtml", "OEBPS/ch2.xhtml")
    private val toc = listOf(
        ReaderTocItem("OEBPS/ch%201.xhtml", "One", """{"href":"OEBPS/ch%201.xhtml"}""", 0),
        ReaderTocItem("OEBPS/ch%201.xhtml#b", "One, part B", """{"href":"OEBPS/ch%201.xhtml#b"}""", 1, resourceHref = "OEBPS/ch%201.xhtml"),
        ReaderTocItem("OEBPS/ch2.xhtml", "Two", """{"href":"OEBPS/ch2.xhtml","locations":{"progression":0}}""", 0),
        ReaderTocItem("OEBPS/ch2.xhtml#late", "Two, late", """{"href":"OEBPS/ch2.xhtml#late","locations":{"progression":0.6}}""", 1, resourceHref = "OEBPS/ch2.xhtml"),
    )

    private fun highlight(id: String, href: String, progression: Double?, createdAt: Long = 1, title: String? = null): ReaderHighlight {
        val locations = progression?.let { ""","locations":{"progression":$it}""" }.orEmpty()
        val titled = title?.let { ""","title":"$it"""" }.orEmpty()
        return ReaderHighlight(id, """{"href":"$href"$locations$titled}""", id, -1, createdAt)
    }

    private fun titlesAndIds(chapters: List<AnnotationChapter>) = chapters.map { chapter -> chapter.title to chapter.items.map { it.id } }

    @Test
    fun ordersByReadingPositionAndFilesUnderTheLastChapterBefore() {
        val chapters = AnnotationOutline.chapters(
            highlights = listOf(
                highlight("late-two", "OEBPS/ch2.xhtml", 0.9),
                highlight("early-two", "/OEBPS/ch2.xhtml", 0.1),
                // foliate spells the same file decoded.
                highlight("one", "OEBPS/ch 1.xhtml", 0.5),
                highlight("cover", "OEBPS/cover.xhtml", 0.0, title = "Cover"),
            ),
            bookmarks = listOf(ReaderBookmark("bm", """{"href":"OEBPS/ch2.xhtml","locations":{"progression":0.5}}""", null, null, 0.7, 1)),
            toc = toc,
            readingOrder = readingOrder,
        )
        assertEquals(
            listOf(
                // Before the first TOC entry: Readium's locator title.
                "Cover" to listOf("cover"),
                // Several entries in one file without positions: the first of them.
                "One" to listOf("one"),
                "Two" to listOf("early-two", "bm"),
                "Two, late" to listOf("late-two"),
            ),
            titlesAndIds(chapters),
        )
    }

    @Test
    fun bookmarksNamingTheirChapterGoThere() {
        val bookmark = ReaderBookmark("bm", """{"href":"OEBPS/ch%201.xhtml","locations":{"progression":0.9}}""", "OEBPS/ch%201.xhtml#b", "One, part B", 0.4, 1)
        val chapters = AnnotationOutline.chapters(emptyList(), listOf(bookmark), toc, readingOrder)
        assertEquals(listOf("One, part B" to listOf("bm")), titlesAndIds(chapters))
    }

    @Test
    fun sameSpotOrdersByCreationThenUnknownPlacesGoLast() {
        val chapters = AnnotationOutline.chapters(
            highlights = listOf(
                highlight("nowhere", "missing.xhtml", null, createdAt = 0),
                highlight("second", "OEBPS/ch2.xhtml", 0.2, createdAt = 5),
                highlight("first", "OEBPS/ch2.xhtml", 0.2, createdAt = 3),
            ),
            bookmarks = emptyList(),
            toc = toc,
            readingOrder = readingOrder,
        )
        assertEquals(listOf("Two" to listOf("first", "second"), null to listOf("nowhere")), titlesAndIds(chapters))
    }

    @Test
    fun withoutAReadingOrderTheTocGivesIt() {
        val chapters = AnnotationOutline.chapters(
            highlights = listOf(highlight("b", "OEBPS/ch2.xhtml", 0.1), highlight("a", "OEBPS/ch%201.xhtml", 0.8)),
            bookmarks = emptyList(),
            toc = toc,
            readingOrder = emptyList(),
        )
        assertEquals(listOf("One" to listOf("a"), "Two" to listOf("b")), titlesAndIds(chapters))
    }

    @Test
    fun hrefsNormalise() {
        assertEquals("OEBPS/a b+c.xhtml", normalizedHref("/OEBPS/a%20b+c.xhtml#frag"))
        assertEquals("ü.xhtml", normalizedHref("%C3%BC.xhtml?x=1"))
        assertEquals(null, normalizedHref("#only"))
    }
}
