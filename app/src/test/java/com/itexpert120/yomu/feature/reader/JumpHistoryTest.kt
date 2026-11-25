package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpHistoryTest {
    private fun at(name: String, pageLabel: String? = null, chapter: String? = null) = ReaderLocator(
        locatorJson = name,
        totalProgression = null,
        chapterTitle = chapter,
        href = null,
        pageLabel = pageLabel,
    )

    @Test
    fun backReturnsToEachOriginInTurn() {
        val history = JumpHistory()
        history.recordJump(at("p10"))
        history.onLocator(at("toc-target"), 0)
        history.recordJump(at("toc-target"))
        history.onLocator(at("search-hit"), 100)

        assertEquals("toc-target", history.pop()?.locatorJson)
        history.onLocator(at("toc-target"), 200)
        assertEquals("p10", history.back?.locatorJson)
        assertEquals("p10", history.pop()?.locatorJson)
        assertNull(history.back)
    }

    @Test
    fun readingOnFromTheLandingEndsTheOffer() {
        val history = JumpHistory(readOnMoves = 3, moveSpacingMillis = 700)
        history.recordJump(at("p10"))
        // The landing itself isn't reading on.
        assertFalse(history.onLocator(at("p50"), 0))
        assertFalse(history.onLocator(at("p51"), 1_000))
        assertFalse(history.onLocator(at("p52"), 2_000))
        assertTrue(history.onLocator(at("p53"), 3_000))
        assertNull(history.back)
    }

    @Test
    fun aBurstOfPositionsCountsAsOneMove() {
        val history = JumpHistory(readOnMoves = 2, moveSpacingMillis = 700)
        history.recordJump(at("p10"))
        history.onLocator(at("a"), 0)
        // A scroll reports many positions in quick succession.
        assertFalse(history.onLocator(at("b"), 800))
        assertFalse(history.onLocator(at("c"), 900))
        assertFalse(history.onLocator(at("d"), 1_000))
        assertTrue(history.onLocator(at("e"), 1_600))
    }

    @Test
    fun scrubbingTwiceGoesBackToWhereTheFirstScrubStarted() {
        val history = JumpHistory()
        history.recordJump(at("start"), coalesce = true)
        history.onLocator(at("scrub1"), 0)
        history.recordJump(at("scrub1"), coalesce = true)
        history.onLocator(at("scrub2"), 100)

        assertEquals("start", history.pop()?.locatorJson)
        assertNull(history.back)
    }

    @Test
    fun aJumpFromWhereBackLandsIsNotRecordedTwice() {
        val history = JumpHistory()
        history.recordJump(at("p10"))
        history.recordJump(at("p10"))
        history.pop()
        assertNull(history.back)
    }

    @Test
    fun keepsOnlyTheNewestJumps() {
        val history = JumpHistory(maxEntries = 2)
        history.recordJump(at("a"))
        history.recordJump(at("b"))
        history.recordJump(at("c"))
        assertEquals("c", history.pop()?.locatorJson)
        assertEquals("b", history.pop()?.locatorJson)
        assertNull(history.pop())
    }

    @Test
    fun labelPrefersThePrintedPageThenTheChapter() {
        assertEquals("Back to p. 112", jumpBackLabel(at("x", pageLabel = "112–113", chapter = "Chapter 3"), null))
        assertEquals("Back to Chapter 3", jumpBackLabel(at("x", chapter = "Chapter 3"), null))
        assertEquals("Back to Prologue", jumpBackLabel(at("x"), "Prologue"))
        assertEquals("Back to where you were", jumpBackLabel(at("x"), null))
    }
}
