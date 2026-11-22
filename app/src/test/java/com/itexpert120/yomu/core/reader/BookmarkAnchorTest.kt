package com.itexpert120.yomu.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkAnchorTest {
    @Test fun unknownPositionUsesExactLocatorOnEitherSide() {
        assertTrue(BookmarkAnchor("c", "position", null).matches(BookmarkAnchor("c", "position", 0.4)))
        assertTrue(BookmarkAnchor("c", "position", 0.4).matches(BookmarkAnchor("c", "position", null)))
        assertFalse(BookmarkAnchor("c", "one", null).matches(BookmarkAnchor("c", "two", 0.4)))
    }

    @Test fun nearbyPositionsInOneChapterAreTheSamePage() {
        assertTrue(BookmarkAnchor("c#a", "one", 0.4).matches(BookmarkAnchor("c#a", "two", 0.409)))
        assertFalse(BookmarkAnchor("c#a", "one", 0.4).matches(BookmarkAnchor("c#a", "two", 0.42)))
    }

    @Test fun distinctLogicalChaptersStayDistinct() {
        assertFalse(BookmarkAnchor("c#a", "one", 0.4).matches(BookmarkAnchor("c#b", "two", 0.401)))
        assertFalse(BookmarkAnchor("c", "one", 0.4).matches(BookmarkAnchor("d", "two", 0.4)))
    }

    @Test fun legacyResourceOnlyAnchorsMatchChaptersInThatResource() {
        assertTrue(BookmarkAnchor("c", "one", 0.4).matches(BookmarkAnchor("c#a", "two", 0.401)))
        assertTrue(BookmarkAnchor("c#a", "two", 0.401).matches(BookmarkAnchor("c", "one", 0.4)))
    }

    @Test fun locatorAnchorPrefersTheLogicalChapter() {
        val inChapter = ReaderLocator(locatorJson = "{}", totalProgression = 0.5, chapterTitle = null, href = "c", chapterId = "c#a")
        assertEquals(BookmarkAnchor("c#a", "{}", 0.5), inChapter.bookmarkAnchor())
        assertEquals(BookmarkAnchor("c", "{}", null), inChapter.copy(chapterId = null, totalProgression = null).bookmarkAnchor())
    }

    @Test fun bookmarksAtAPageIncludeLegacyOnes() {
        val legacy = ReaderBookmark("legacy", "old", "c", null, 0.4, 1L)
        val other = ReaderBookmark("other", "far", "c#a", null, 0.8, 2L)
        assertEquals(listOf(legacy), listOf(legacy, other).at(BookmarkAnchor("c#a", "now", 0.405)))
    }
}
