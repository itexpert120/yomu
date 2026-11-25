package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderPageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTimeTest {
    private val average = ReadingTime.defaultKbPerMinute

    @Test
    fun startsAtAnAverageReadersSpeed() {
        assertEquals(average, ReadingTime.kbPerMinute(null, null), 0.0)
        // Too little reading to go on.
        assertEquals(average, ReadingTime.kbPerMinute(5 * 60L, 400.0), 0.0)
        assertEquals(average, ReadingTime.kbPerMinute(60 * 60L, 10.0), 0.0)
    }

    @Test
    fun usesTheReadersOwnSpeedOnceThereIsEnoughReading() {
        // 180 KB in an hour: 3 KB a minute.
        assertEquals(3.0, ReadingTime.kbPerMinute(60 * 60L, 180.0), 0.0001)
    }

    @Test
    fun ignoresAnImplausibleSpeed() {
        // 2,000 KB "read" in 20 minutes is a jump ahead, not reading.
        assertEquals(average, ReadingTime.kbPerMinute(20 * 60L, 2_000.0), 0.0)
        // 60 KB over ten hours is a book left open.
        assertEquals(average, ReadingTime.kbPerMinute(10 * 60 * 60L, 60.0), 0.0)
    }

    @Test
    fun roundsMinutesUp() {
        assertEquals(5, ReadingTime.minutesLeft(15.0, 3.0))
        assertEquals(6, ReadingTime.minutesLeft(16.0, 3.0))
        assertEquals(1, ReadingTime.minutesLeft(0.5, 3.0))
        assertEquals(0, ReadingTime.minutesLeft(0.0, 3.0))
        assertNull(ReadingTime.minutesLeft(null, 3.0))
    }

    @Test
    fun writesDurations() {
        assertEquals("6 min", ReadingTime.duration(6))
        assertEquals("1 h", ReadingTime.duration(61))
        assertEquals("3 h 10 min", ReadingTime.duration(189))
        assertEquals("6 min left in chapter", ReadingTime.chapterLabel(6))
        assertEquals("Less than a minute left in chapter", ReadingTime.chapterLabel(1))
        assertEquals("6 min left", ReadingTime.footerChapterLabel(6))
        assertEquals("3 h 10 min in book", ReadingTime.footerBookLabel(189))
        assertNull(ReadingTime.chapterLabel(null))
    }

    @Test
    fun writesPrintedPages() {
        assertEquals("p. 112", printedPageLabel("112"))
        assertEquals("pp. 112–113", printedPageLabel("112–113"))
        assertNull(printedPageLabel(null))
        assertNull(printedPageLabel(" "))
    }

    @Test
    fun captionFollowsTheBoards() {
        assertEquals("6 min left in chapter · pages 112–113", controlsCaption("6 min left in chapter", "112–113", "3 pages left in chapter"))
        assertEquals("6 min left in chapter · page 112", controlsCaption("6 min left in chapter", "112", null))
        assertEquals("6 min left in chapter", controlsCaption("6 min left in chapter", null, "3 pages left in chapter"))
        assertEquals("3 pages left in chapter · page 12", controlsCaption(null, "12", "3 pages left in chapter"))
        assertNull(controlsCaption(null, null, null))
    }

    @Test
    fun findsAPageByItsLabelOrTheNearestBefore() {
        val pages = listOf("iii", "iv", "1", "2", "5", "6").map { ReaderPageTarget(it, "at-$it") }
        assertEquals("at-iv", findPrintedPage(pages, "IV")?.locatorJson)
        assertEquals("at-5", findPrintedPage(pages, "5")?.locatorJson)
        // A page the list skips lands on the one before it.
        assertEquals("at-2", findPrintedPage(pages, "4")?.locatorJson)
        assertNull(findPrintedPage(pages, "0"))
        assertNull(findPrintedPage(pages, "x"))
        assertNull(findPrintedPage(pages, ""))
    }
}
