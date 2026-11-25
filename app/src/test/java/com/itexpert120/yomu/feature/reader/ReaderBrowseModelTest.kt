package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.ChapterReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBrowseModelTest {
    private fun item(id: String, depth: Int = 0, jumpable: Boolean = true) = ReaderTocItem(id = id, title = "T $id", locatorJson = if (jumpable) "{}" else null, depth = depth)

    // Two volumes of two chapters; the volumes have their own (title) pages.
    private val volumes = listOf(
        item("v1"),
        item("a", 1),
        item("b", 1),
        item("v2"),
        item("c", 1),
        item("d", 1),
    )

    @Test
    fun volumesBecomeSectionsWithTheirTitlePageFirst() {
        val outline = contentsOutline(volumes, "c")
        assertTrue(outline.grouped)
        assertEquals(listOf(ContentsSection(0, listOf(0, 1, 2)), ContentsSection(3, listOf(3, 4, 5))), outline.sections)
        assertEquals(4, outline.current)
        assertEquals(1, outline.currentSection)
    }

    @Test
    fun aSingleVolumeIsAFlatList() {
        val outline = contentsOutline(listOf(item("book"), item("a", 1), item("b", 1)), null)
        assertFalse(outline.grouped)
        assertEquals(listOf(ContentsSection(null, listOf(0, 1, 2))), outline.sections)
        assertEquals(-1, outline.current)
    }

    @Test
    fun looseChaptersBetweenVolumesStayInOrder() {
        val toc = listOf(item("pro"), item("v1"), item("a", 1), item("v2"), item("b", 1), item("epi"))
        val sections = contentsOutline(toc, null).sections
        assertEquals(listOf(null, 1, 3, null), sections.map { it.header })
        assertEquals(listOf(5), sections.last().entries)
    }

    @Test
    fun collapsedVolumesHideTheirChaptersAndCountReads() {
        val outline = contentsOutline(volumes, "c")
        val reads = ChapterReadState.from(readRows = listOf("a", "b", "c"), progressRows = emptyMap())
        val rows = contentsRows(outline, expanded = setOf(1), reads = reads)
        assertEquals(listOf("v0", "v1", "c3", "c4", "c5"), rows.map { it.key })
        val first = rows[0] as ContentsRow.Volume
        assertEquals(2, first.chapters)
        assertEquals(2, first.read)
        assertFalse(first.expanded)
        val current = rows[3] as ContentsRow.Chapter
        assertTrue(current.current)
        assertTrue(current.read)
        assertTrue((rows[2] as ContentsRow.Chapter).volumeStart)
    }

    @Test
    fun theFilterIsFlatAndNamesTheVolume() {
        val outline = contentsOutline(volumes, null)
        val rows = contentsRows(outline, expanded = emptySet(), reads = ChapterReadState.Empty, query = " t D ")
        assertEquals(listOf("c5"), rows.map { it.key })
        assertEquals("T v2", (rows.single() as ContentsRow.Chapter).volume)
    }

    @Test
    fun currentFallsBackToTheChaptersResource() {
        val toc = listOf(item("a.xhtml"), item("b.xhtml#part"))
        assertEquals(1, currentTocIndex(toc.map { it.copy(resourceHref = it.id.substringBefore('#')) }, "b.xhtml"))
        assertEquals(-1, currentTocIndex(toc, "missing.xhtml"))
    }

    @Test
    fun highlightsGroupByChapterInBookOrder() {
        val toc = listOf(item("one.xhtml"), item("two.xhtml"))
        fun h(id: String, href: String?, progression: Double) = ReaderHighlight(
            id = id,
            locatorJson = """{"href":${href?.let { "\"$it\"" } ?: "null"},"locations":{"progression":$progression}}""",
            text = id,
            colorArgb = 0,
            createdAt = 0,
        )
        val groups = groupHighlights(listOf(h("late", "two.xhtml", 0.9), h("early", "two.xhtml", 0.1), h("first", "one.xhtml", 0.5), h("lost", null, 0.0)), toc)
        assertEquals(listOf("T one.xhtml", "T two.xhtml", null), groups.map { it.title })
        assertEquals(listOf("early", "late"), groups[1].items.map { it.id })
    }

    @Test
    fun bookmarksGroupUnderTheirChapter() {
        val toc = listOf(item("one.xhtml"), item("two.xhtml"))
        fun b(id: String, href: String, progression: Double) = ReaderBookmark(id, "{}", href, null, progression, 0)
        val groups = groupBookmarks(listOf(b("y", "two.xhtml", 0.8), b("x", "one.xhtml#p3", 0.2)), toc)
        assertEquals(listOf(listOf("x"), listOf("y")), groups.map { group -> group.items.map { it.id } })
    }

    @Test
    fun locatorFactsReadTheSharedShape() {
        val facts = LocatorFacts.parse("""{"href":"c.xhtml","title":"Ch","locations":{"progression":0.25},"text":{"highlight":"Hello","after":" world"}}""")!!
        assertEquals("c.xhtml", facts.href)
        assertEquals(0.25, facts.progression!!, 0.0)
        assertEquals("Hello world", facts.text)
        assertNull(LocatorFacts.parse("not json"))
        assertNull(LocatorFacts.parse("{}")!!.text)
    }

    @Test
    fun spacingValuesReadPlainly() {
        assertEquals("1.5", formatSpacing(1.5f))
        assertEquals("1.25", formatSpacing(1.25f))
        assertEquals("0", formatSpacing(0f))
    }
}
