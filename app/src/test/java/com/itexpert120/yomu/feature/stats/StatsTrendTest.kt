package com.itexpert120.yomu.feature.stats

import com.itexpert120.yomu.core.model.DailyReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StatsTrendTest {
    private val today = LocalDate.of(2026, 11, 25)

    private fun days(count: Int, seconds: (LocalDate) -> Long = { 60L }): List<DailyReading> = (count - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        DailyReading(date.toString(), seconds(date))
    }

    @Test
    fun weekIsTheLastSevenDaysEndingToday() {
        val trend = buildStatsTrend(days(40) { it.dayOfMonth * 60L }, Locale.UK)
        assertEquals(7, trend.week.size)
        assertEquals(25 * 60L, trend.week.last().seconds)
        assertTrue(trend.week.last().isCurrent)
        assertEquals("Today", trend.week.last().label)
        assertEquals("Thu", trend.week.first().label) // 19 Nov 2026
        assertEquals("T", trend.week.first().shortLabel)
    }

    @Test
    fun monthLabelsOneBarAWeekCountingBackFromToday() {
        val trend = buildStatsTrend(days(40), Locale.UK)
        assertEquals(30, trend.month.size)
        val labelled = trend.month.withIndex().filter { it.value.showLabel }.map { it.index }
        assertEquals(listOf(1, 8, 15, 22, 29), labelled)
    }

    @Test
    fun yearSumsTwelveCalendarMonths() {
        val trend = buildStatsTrend(days(400), Locale.UK)
        assertEquals(12, trend.year.size)
        assertEquals(25 * 60L, trend.year.last().seconds)
        assertEquals(31 * 60L, trend.year[trend.year.size - 2].seconds)
        assertEquals("Nov", trend.year.last().label)
    }

    @Test
    fun noDaysGivesNoCharts() {
        assertEquals(StatsTrend(), buildStatsTrend(emptyList()))
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
