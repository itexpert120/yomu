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
    fun weightedSeekMapsIntoTheCorrectResource() {
        val position = ReadingPosition(emptyList<ReadingPosition.Section<String>>(), listOf("a", "b"), listOf(1, 3), ReaderLayout.Paged)

        val target = position.progressionTarget(0.5)!!

        assertEquals(1, target.resourceIndex)
        assertEquals(1.0 / 3.0, target.progression, 0.0001)
    }

    @Test
    fun weightedSeekAtEndTargetsTheEndOfTheLastResource() {
        val position = ReadingPosition(emptyList<ReadingPosition.Section<String>>(), listOf("a", "b"), listOf(2, 5), ReaderLayout.Paged)

        val target = position.progressionTarget(1.0)!!

        assertEquals(1, target.resourceIndex)
        assertEquals(1.0, target.progression, 0.0001)
    }

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
        assertTrue(position.isForward(0.9))
    }
}
