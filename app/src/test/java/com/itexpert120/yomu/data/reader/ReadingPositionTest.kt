package com.itexpert120.yomu.data.reader

import com.itexpert120.yomu.core.model.ReaderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionTest {
    // A Yen Press volume: 40 resources; several logical sections span more than one resource.
    private val yenPressHrefs = List(40) { "r$it.xhtml" }
    private val yenPressSections = listOf(
        section("cover", 0), // Cover
        section("insert", 1), // Insert (continues through resources 2-4)
        section("title", 5), // Title page
        section("copyright", 6), // Copyright
        section("contents", 7), // Contents (also owns the epigraph)
        section("chapter1", 9), // Chapter 1: resources 9-10
        section("chapter2", 11), // Chapter 2: resources 11-12
        section("chapter3", 13), // Chapter 3: resources 13-14
        section("chapter4", 15), // Chapter 4
    )

    private fun section(id: String, resourceIndex: Int, start: Double = 0.0) = ReadingPosition.Section(id, id, resourceIndex, start, target = id)

    private fun yenPress(
        layout: ReaderLayout = ReaderLayout.Paged,
        weights: List<Int> = List(40) { 1 },
    ) = ReadingPosition(yenPressSections, yenPressHrefs, weights, layout)

    @Test
    fun scrollModeIgnoresSingleVisualPageCompletion() {
        val position = ReadingPosition(listOf(section("only", 0)), listOf("a"), listOf(1), ReaderLayout.Scroll)
        position.onPage("a", pageIndex = 0, totalPages = 1)
        position.onScrollMeasured("a", 0.5)

        val snapshot = position.onLocator("a", 0.43478260869565216, null, null)

        assertEquals(0.5, snapshot.bookProgress, 0.0001)
        assertFalse(snapshot.completed)
    }

    @Test
    fun scrollModeCanReachActualBottom() {
        val position = ReadingPosition(listOf(section("only", 0)), listOf("a"), listOf(1), ReaderLayout.Scroll)
        position.onScrollMeasured("a", 1.0)

        val snapshot = position.onLocator("a", 0.97, null, null)

        assertEquals(1.0, snapshot.bookProgress, 0.0001)
        assertTrue(snapshot.completed)
    }

    @Test
    fun pagedModeUsesVisualPageProgress() {
        val position = ReadingPosition(listOf(section("only", 0)), listOf("a"), listOf(1), ReaderLayout.Paged)
        position.onPage("a", pageIndex = 1, totalPages = 4)

        val snapshot = position.onLocator("a", 0.25, null, null)

        assertEquals(0.5, snapshot.bookProgress, 0.0001)
    }

    @Test
    fun overscrollCrossingCompletesTheSectionBeingLeft() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onLocator("r10.xhtml", 0.9, null, null)

        position.expectSequentialCrossing("r11.xhtml")
        val snapshot = position.onLocator("r11.xhtml", 0.0, null, null)

        assertEquals("chapter2", snapshot.chapterId)
        assertEquals("chapter1", snapshot.completedChapterId)
        // Completion is reported once.
        assertNull(position.onLocator("r11.xhtml", 0.1, null, null).completedChapterId)
    }

    @Test
    fun continuationResourceDoesNotCompleteItsLogicalSection() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onLocator("r9.xhtml", 0.9, null, null)

        position.expectSequentialCrossing("r10.xhtml")
        val snapshot = position.onLocator("r10.xhtml", 0.0, null, null)

        assertEquals("chapter1", snapshot.chapterId)
        assertNull(snapshot.completedChapterId)
    }

    @Test
    fun cancelledCrossingCompletesNothing() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onLocator("r10.xhtml", 0.9, null, null)
        position.expectSequentialCrossing("r11.xhtml")
        position.cancelCrossing()

        assertNull(position.onLocator("r11.xhtml", 0.0, null, null).completedChapterId)
    }

    @Test
    fun continuationResourceStaysInLogicalChapter() {
        val snapshot = yenPress().onLocator("r14.xhtml", 0.5, null, null)

        assertEquals("chapter3", snapshot.chapterId)
        assertEquals(0.75, snapshot.chapterProgression!!, 0.0001)
        assertFalse(snapshot.completed)
    }

    @Test
    fun skewedPositionWeightsCannotCompleteSectionBeforeContinuationResource() {
        val position = yenPress(weights = List(40) { index -> if (index == 13) 10_000 else 1 })
        position.onPage("r13.xhtml", pageIndex = 9, totalPages = 10)

        val snapshot = position.onLocator("r13.xhtml", 0.99, null, null)

        assertEquals("chapter3", snapshot.chapterId)
        assertTrue(snapshot.chapterProgression!! < 0.999)
        assertFalse(snapshot.completed)
    }

    @Test
    fun coverEndIsSmallBookProgressNotCompletion() {
        val position = yenPress()
        position.onPage("r0.xhtml", pageIndex = 0, totalPages = 1)

        val snapshot = position.onLocator("r0.xhtml", 0.0, null, null)

        assertEquals(0.025, snapshot.bookProgress, 0.0001)
        assertFalse(snapshot.completed)
        assertEquals("cover", snapshot.chapterId)
    }

    @Test
    fun inclusiveLastPageCompletesCurrentSectionWithoutSelectingNext() {
        val position = yenPress()
        position.onPage("r14.xhtml", pageIndex = 19, totalPages = 20)

        val snapshot = position.onLocator("r14.xhtml", 0.95, null, null)

        assertEquals("chapter3", snapshot.chapterId)
        assertEquals(1.0, snapshot.chapterProgression!!, 0.0001)
        assertFalse(snapshot.completed)
    }

    @Test
    fun finalPublicationPageIsExplicitCompletion() {
        val position = yenPress()
        position.onPage("r39.xhtml", pageIndex = 9, totalPages = 10)

        val snapshot = position.onLocator("r39.xhtml", 0.9, null, null)

        assertTrue(snapshot.completed)
        assertEquals(1.0, snapshot.bookProgress, 0.0001)
    }

    @Test
    fun pagesLeftOnlyInPagedModeOnTheSameResource() {
        val paged = yenPress()
        paged.onPage("r9.xhtml", pageIndex = 1, totalPages = 5)
        assertEquals(3, paged.onLocator("r9.xhtml", 0.3, null, null).chapterPagesLeft)
        // The page count belongs to the previous resource until the new one reports its own.
        assertNull(paged.onLocator("r10.xhtml", 0.0, null, null).chapterPagesLeft)

        val scroll = yenPress(ReaderLayout.Scroll)
        scroll.onPage("r9.xhtml", pageIndex = 1, totalPages = 5)
        assertNull(scroll.onLocator("r9.xhtml", 0.3, null, null).chapterPagesLeft)
    }

    @Test
    fun sectionsSharingAStartAdvanceTogether() {
        val position = ReadingPosition(
            listOf(
                section("story", 0),
                section("gallery", 1),
                section("characters", 1),
                section("afterword", 2),
            ),
            listOf("a", "b", "c"),
            listOf(1, 1, 1),
            ReaderLayout.Scroll,
        )
        position.onLocator("b", 0.9, null, null)

        val current = position.onLocator("b", 0.95, null, null)
        assertEquals("characters", current.chapterId)
        assertEquals(listOf("gallery"), current.sharedChapterIds)

        position.expectSequentialCrossing("c")
        val crossed = position.onLocator("c", 0.0, null, null)
        assertEquals("characters", crossed.completedChapterId)
        assertEquals(listOf("gallery"), crossed.completedSharedChapterIds)
    }

    @Test
    fun scrollMeasurementIsDiscardedOnResourceChange() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onScrollMeasured("r9.xhtml", 0.9)
        position.onLocator("r9.xhtml", 0.2, null, null)
        position.onLocator("r10.xhtml", 0.0, null, null)

        // Back in the first resource, the old measurement no longer applies.
        val snapshot = position.onLocator("r9.xhtml", 0.2, null, null)

        assertEquals((9 + 0.2) / 40, snapshot.bookProgress, 0.0001)
    }

    @Test
    fun leavingScrollModeDropsTheMeasurement() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onScrollMeasured("r9.xhtml", 0.9)
        position.layout = ReaderLayout.Paged
        position.layout = ReaderLayout.Scroll

        val snapshot = position.onLocator("r9.xhtml", 0.2, null, null)

        assertEquals((9 + 0.2) / 40, snapshot.bookProgress, 0.0001)
    }

    @Test
    fun sectionTargetsAndDirectionFollowTheCurrentSection() {
        val position = yenPress()
        assertNull(position.sectionTarget(1))

        position.onLocator("r12.xhtml", 0.5, null, null)

        assertEquals("chapter3", position.sectionTarget(1))
        assertEquals("chapter1", position.sectionTarget(-1))
        assertNull(position.sectionTarget(100))
        assertTrue(position.isForward("r13.xhtml", 0.0))
        assertFalse(position.isForward("r9.xhtml", 0.0))
    }

    @Test
    fun chapterTargetScrubsWithinAOneResourceChapter() {
        val position = yenPress(ReaderLayout.Scroll)
        position.onLocator("r5.xhtml", 0.2, null, null)

        assertTarget("r5.xhtml", 0.0, position.chapterTarget(0.0))
        assertTarget("r5.xhtml", 0.6, position.chapterTarget(0.6))
        assertTarget("r5.xhtml", 1.0, position.chapterTarget(1.0))
        // Chapter 4 runs to the end of the book.
        position.onLocator("r15.xhtml", 0.2, null, null)
        assertTarget("r39.xhtml", 1.0, position.chapterTarget(1.0))
    }

    @Test
    fun chapterTargetCrossesTheResourcesOfAChapterByWeight() {
        // Chapter 1 is resources 9 (weight 3) and 10 (weight 1).
        val position = yenPress(ReaderLayout.Scroll, weights = List(40) { if (it == 9) 3 else 1 })
        position.onLocator("r10.xhtml", 0.5, null, null)

        assertTarget("r9.xhtml", 0.5, position.chapterTarget(0.375))
        assertTarget("r10.xhtml", 0.5, position.chapterTarget(0.875))
        // The chapter's end is the end of its last resource, not the start of the next chapter.
        assertTarget("r10.xhtml", 1.0, position.chapterTarget(1.0))
    }

    @Test
    fun chapterTargetInvertsChapterProgression() {
        val position = yenPress(ReaderLayout.Scroll, weights = List(40) { if (it == 9) 3 else 1 })
        position.onLocator("r9.xhtml", 0.1, null, null)
        val (href, progression) = position.chapterTarget(0.7)!!
        position.onScrollMeasured(href, progression)

        assertEquals(0.7, position.onLocator(href, progression, null, null).chapterProgression!!, 0.0001)
    }

    @Test
    fun chapterTargetStartsAtASectionsOffsetInItsResource() {
        val position = ReadingPosition(
            listOf(section("a", 0), section("b", 0, start = 0.5)),
            listOf("x"),
            listOf(4),
            ReaderLayout.Scroll,
        )
        position.onLocator("x", 0.6, null, null)

        assertTarget("x", 0.75, position.chapterTarget(0.5))
        assertNull(ReadingPosition(listOf(section("a", 0)), listOf("x"), listOf(1), ReaderLayout.Scroll).chapterTarget(0.5))
    }

    @Test
    fun reportsTextLeftInTheChapterAndTheBook() {
        // Two chapters: resources of 10 and 30 KB, then a 60 KB one.
        val position = ReadingPosition(
            listOf(section("one", 0), section("two", 2)),
            listOf("a", "b", "c"),
            listOf(10, 30, 60),
            ReaderLayout.Scroll,
        )
        position.onScrollMeasured("b", 0.5)

        val snapshot = position.onLocator("b", 0.5, null, null)

        // 10 + 15 KB read; 15 KB left of chapter one; 75 KB left of the book.
        assertEquals(25.0, snapshot.bookSizeRead!!, 0.0001)
        assertEquals(15.0, snapshot.chapterSizeLeft!!, 0.0001)
        assertEquals(75.0, snapshot.bookSizeLeft!!, 0.0001)
    }

    private fun assertTarget(href: String, progression: Double, actual: Pair<String, Double>?) {
        assertEquals(href, actual?.first)
        assertEquals(progression, actual!!.second, 0.0001)
    }
}
