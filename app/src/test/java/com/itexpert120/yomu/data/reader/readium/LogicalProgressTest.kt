package com.itexpert120.yomu.data.reader.readium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalProgressTest {
    private val yenPressSections = listOf(
        SectionBoundary(0, 0.0), // Cover
        SectionBoundary(1, 0.0), // Insert (continues through resources 2-4)
        SectionBoundary(5, 0.0), // Title page
        SectionBoundary(6, 0.0), // Copyright
        SectionBoundary(7, 0.0), // Contents (also owns the epigraph)
        SectionBoundary(9, 0.0), // Chapter 1: resources 9-10
        SectionBoundary(11, 0.0), // Chapter 2: resources 11-12
        SectionBoundary(13, 0.0), // Chapter 3: resources 13-14
        SectionBoundary(15, 0.0), // Chapter 4
    )

    @Test
    fun weightedSeekMapsIntoTheCorrectResource() {
        val target = weightedProgressTarget(0.5, listOf(1, 3))!!

        assertEquals(1, target.resourceIndex)
        assertEquals(1.0 / 3.0, target.resourceProgress, 0.0001)
    }

    @Test
    fun weightedSeekAtEndTargetsTheEndOfTheLastResource() {
        val target = weightedProgressTarget(1.0, listOf(2, 5))!!

        assertEquals(1, target.resourceIndex)
        assertEquals(1.0, target.resourceProgress, 0.0001)
    }

    @Test
    fun scrollModeIgnoresSingleVisualPageCompletion() {
        val result = resolveResourceProgress(
            locatorProgress = 0.43478260869565216,
            visualPageProgress = 1.0,
            measuredScrollProgress = 0.5,
            useVisualPageProgress = false,
        )

        assertEquals(0.5, result, 0.0001)
    }

    @Test
    fun scrollModeCanReachActualBottom() {
        val result = resolveResourceProgress(
            locatorProgress = 0.97,
            visualPageProgress = 1.0,
            measuredScrollProgress = 1.0,
            useVisualPageProgress = false,
        )

        assertEquals(1.0, result, 0.0001)
    }

    @Test
    fun pagedModeUsesVisualPageProgress() {
        val result = resolveResourceProgress(
            locatorProgress = 0.25,
            visualPageProgress = 0.5,
            useVisualPageProgress = true,
        )

        assertEquals(0.5, result, 0.0001)
    }

    @Test
    fun sequentialResourceCrossingCompletesTheSectionBeingLeft() {
        val result = completedSectionOnSequentialCrossing(
            pendingChapterId = "chapter005.xhtml",
            currentChapterId = "chapter007.xhtml",
            previousHref = "chapter006.xhtml",
            currentHref = "chapter007.xhtml",
        )

        assertEquals("chapter005.xhtml", result)
    }

    @Test
    fun continuationResourceDoesNotCompleteItsLogicalSection() {
        val result = completedSectionOnSequentialCrossing(
            pendingChapterId = null,
            currentChapterId = "chapter005.xhtml",
            previousHref = "chapter005.xhtml",
            currentHref = "chapter006.xhtml",
        )

        assertEquals(null, result)
    }

    @Test
    fun continuationResourceStaysInLogicalChapter() {
        val result = calculateLogicalProgress(
            resourceIndex = 14,
            resourceProgress = 0.5,
            locatorProgress = 0.5,
            resourceWeights = List(40) { 1 },
            sections = yenPressSections,
        )

        assertEquals(7, result.sectionIndex)
        assertEquals(0.75, result.sectionProgress!!, 0.0001)
        assertFalse(result.completed)
    }

    @Test
    fun skewedPositionWeightsCannotCompleteSectionBeforeContinuationResource() {
        val result = calculateLogicalProgress(
            resourceIndex = 13,
            resourceProgress = 1.0,
            locatorProgress = 0.99,
            resourceWeights = List(40) { index -> if (index == 13) 10_000 else 1 },
            sections = yenPressSections,
        )

        assertEquals(7, result.sectionIndex)
        assertTrue(result.sectionProgress!! < 0.999)
        assertFalse(result.completed)
    }

    @Test
    fun coverEndIsSmallBookProgressNotCompletion() {
        val result = calculateLogicalProgress(
            resourceIndex = 0,
            resourceProgress = 1.0,
            locatorProgress = 0.0,
            resourceWeights = List(40) { 1 },
            sections = yenPressSections,
        )

        assertEquals(0.025, result.bookProgress, 0.0001)
        assertFalse(result.completed)
        assertEquals(0, result.sectionIndex)
    }

    @Test
    fun inclusiveLastPageCompletesCurrentSectionWithoutSelectingNext() {
        val result = calculateLogicalProgress(
            resourceIndex = 14,
            resourceProgress = 1.0,
            locatorProgress = 0.95,
            resourceWeights = List(40) { 1 },
            sections = yenPressSections,
        )

        assertEquals(7, result.sectionIndex)
        assertEquals(1.0, result.sectionProgress!!, 0.0001)
        assertFalse(result.completed)
    }

    @Test
    fun finalPublicationPageIsExplicitCompletion() {
        val result = calculateLogicalProgress(
            resourceIndex = 39,
            resourceProgress = 1.0,
            locatorProgress = 0.9,
            resourceWeights = List(40) { 1 },
            sections = yenPressSections,
        )

        assertTrue(result.completed)
        assertEquals(1.0, result.bookProgress, 0.0001)
    }
}
