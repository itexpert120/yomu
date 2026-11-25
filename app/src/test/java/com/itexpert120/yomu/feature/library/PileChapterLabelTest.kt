package com.itexpert120.yomu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PileChapterLabelTest {
    @Test
    fun numberedTitlesKeepTheirNumber() {
        assertEquals("Chapter 124", pileChapterLabel("Chapter 124: Wrapping Up Work"))
        assertEquals("Chapter IV", pileChapterLabel("Chapter IV. The Rabbit Sends in a Little Bill"))
        assertEquals("Part 2", pileChapterLabel("Part 2 — The Return"))
        assertEquals("CHAPTER I", pileChapterLabel("CHAPTER I - Down the Rabbit-Hole"))
    }

    @Test
    fun otherTitlesStayWhole() {
        assertEquals("Crimson", pileChapterLabel("Crimson"))
        assertEquals("Chapter 17", pileChapterLabel("Chapter 17"))
        assertEquals("Foreword", pileChapterLabel("  Foreword "))
        assertEquals("The Book of Sand", pileChapterLabel("The Book of Sand"))
    }
}
