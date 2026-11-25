package com.itexpert120.yomu.feature.stats

import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.DailyReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class StatsTrendTest {
    // A Wednesday.
    private val today = LocalDate.of(2026, 11, 25)

    private fun days(count: Int, seconds: (LocalDate) -> Long = { 60L }): List<DailyReading> = (count - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        DailyReading(date.toString(), seconds(date))
    }

    @Test
    fun theLastWeekPageIsTheCurrentCalendarWeek() {
        val trend = buildStatsTrend(days(40) { it.dayOfMonth * 60L }, Locale.UK)
        val week = trend.weeks.last()
        assertEquals(LocalDate.of(2026, 11, 23), week.start) // Monday
        assertEquals(LocalDate.of(2026, 11, 29), week.end)
        assertEquals("This week", week.title)
        assertEquals(7, week.bars.size)
        assertEquals((23 + 24 + 25) * 60L, week.seconds)
        assertTrue(week.bars[2].isCurrent)
        assertEquals("Today", week.bars[2].label)
        assertEquals("M", week.bars[0].shortLabel)
        // Thursday to Sunday haven't happened yet.
        assertEquals(listOf(false, false, false, true, true, true, true), week.bars.map { it.isFuture })
    }

    @Test
    fun weekPagesGoBackToTheWeekOfTheFirstReading() {
        // Reading only from Monday 2 Nov; the zero days before it add no pages.
        val trend = buildStatsTrend(days(40) { if (it.isBefore(LocalDate.of(2026, 11, 2))) 0L else 60L }, Locale.UK)
        assertEquals(LocalDate.of(2026, 11, 2), trend.weeks.first().start)
        assertEquals(4, trend.weeks.size)
        assertEquals(listOf("2 – 8 Nov", "9 – 15 Nov", "Last week", "This week"), trend.weeks.map { it.title })
    }

    @Test
    fun weekRangesNameMonthsAndYearsOnlyWhenNeeded() {
        val trend = buildStatsTrend(days(400), Locale.UK)
        val titles = trend.weeks.map { it.title }
        assertTrue("26 Oct – 1 Nov" in titles)
        assertTrue("29 Dec 2025 – 4 Jan 2026" in titles)
        assertTrue("1 – 7 Dec 2025" in titles)
    }

    @Test
    fun theWeekStartsOnTheLocalesFirstDay() {
        val trend = buildStatsTrend(days(10), Locale.US)
        assertEquals(DayOfWeek.SUNDAY, trend.weeks.last().start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 22), trend.weeks.last().start)
    }

    @Test
    fun theCurrentPeriodAveragesOverTheDaysSoFar() {
        // 30 min on each of Monday to Wednesday: 90 min over 3 days, not 7.
        val trend = buildStatsTrend(days(3) { 30 * 60L }, Locale.UK)
        val week = trend.weeks.last()
        assertEquals("1 h 30 min", week.summary.figure)
        assertEquals("30 min a day", week.summary.caption)
        assertEquals("This week · 1 h 30 min", week.summary.compact)
    }

    @Test
    fun aPastPeriodAveragesOverAllItsDays() {
        val trend = buildStatsTrend(days(10) { if (it.dayOfMonth == 17) 70 * 60L else 0L }, Locale.UK)
        val lastWeek = trend.weeks.first { it.title == "Last week" }
        assertEquals("10 min a day", lastWeek.summary.caption)
        assertEquals("Nothing read", trend.weeks.last().summary.caption)
        assertEquals("Nothing read this week", trend.weeks.last().summary.spoken)
    }

    @Test
    fun monthPagesAreCalendarMonths() {
        val trend = buildStatsTrend(days(400), Locale.UK)
        val month = trend.months.last()
        assertEquals("This month", month.title)
        assertEquals(30, month.bars.size)
        assertEquals(LocalDate.of(2026, 11, 1), month.start)
        assertEquals(25 * 60L, month.seconds)
        assertEquals(listOf(0, 7, 14, 21, 28), month.bars.withIndex().filter { it.value.showLabel }.map { it.index })
        assertTrue(month.bars[24].isCurrent)
        assertFalse(month.bars[24].isFuture)
        assertTrue(month.bars[25].isFuture)
        assertEquals("October", trend.months[trend.months.size - 2].title)
        assertEquals(31, trend.months[trend.months.size - 2].bars.size)
        assertEquals("December 2025", trend.months.first { it.start == LocalDate.of(2025, 12, 1) }.title)
        // 400 days back from 25 Nov 2026 is 21 Oct 2025.
        assertEquals(LocalDate.of(2025, 10, 1), trend.months.first().start)
    }

    @Test
    fun withoutReadingThereIsOnlyTheCurrentPage() {
        val trend = buildStatsTrend(days(30) { 0L }, Locale.UK)
        assertEquals(1, trend.weeks.size)
        assertEquals(1, trend.months.size)
        assertEquals("Reading time, this week: no reading", trend.weeks.last().chartHeading + ": no reading")
    }

    @Test
    fun noDaysGivesNoPages() {
        assertEquals(StatsTrend(), buildStatsTrend(emptyList()))
    }

    @Test
    fun removedBooksFoldIntoOneRow() {
        val rows = foldRemovedBooks(
            listOf(
                BookReadingTime("1", "Kept", "A", null, 600L, 4),
                BookReadingTime(null, "Removed book", null, null, 500L, 2),
                BookReadingTime(null, "Removed book", null, null, 300L, 1),
                BookReadingTime("2", "Short", null, null, 100L, 1),
            ),
        )
        assertEquals(listOf("Removed books", "Kept", "Short"), rows.map { it.title })
        assertEquals(800L, rows[0].seconds)
        assertEquals(3, rows[0].sessionCount)
        assertEquals(2, rows[0].removedBooks)
        assertEquals("2 books", rows[0].author)
    }

    @Test
    fun oneRemovedBookKeepsItsSingularName() {
        val rows = foldRemovedBooks(listOf(BookReadingTime(null, "Removed book", null, null, 50L, 1)))
        assertEquals("Removed book", rows.single().title)
        assertEquals(null, rows.single().author)
    }

    @Test
    fun readingTimeFormatsLikeTheBoard() {
        assertEquals("4 h 21 min", formatReadingTime(4 * 3600L + 21 * 60))
        assertEquals("11 h 05 min", formatReadingTime(11 * 3600L + 5 * 60))
        assertEquals("2 h", formatReadingTime(2 * 3600L))
        assertEquals("21 min", formatReadingTime(21 * 60L))
        assertEquals("<1 min", formatReadingTime(20L))
        assertEquals("—", formatReadingTime(0L))
        assertEquals("0 min", formatReadingTime(0L, zero = "0 min"))
    }
}
