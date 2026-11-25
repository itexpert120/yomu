package com.itexpert120.yomu.feature.stats

import androidx.compose.runtime.Immutable
import com.itexpert120.yomu.core.model.DailyReading
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The calendar period the dashboard's one figure and its chart cover. */
enum class StatsPeriod { Week, Month }

/**
 * One bar of a dashboard chart. [shortLabel] fits under a phone bar, [label] under a tablet bar;
 * [showLabel] is false for bars the chart leaves unlabelled (the month chart labels one a week).
 * [isFuture] bars are the rest of the current week or month: drawn as nothing yet.
 */
@Immutable
data class TrendBar(
    val shortLabel: String,
    val label: String,
    val spokenLabel: String,
    val seconds: Long,
    val isCurrent: Boolean,
    val showLabel: Boolean = true,
    val isFuture: Boolean = false,
)

/** What the one figure says for a page. */
@Immutable
data class PeriodSummary(val figure: String, val caption: String, val compact: String, val spoken: String)

/** One week or month the dashboard can show: its days (inclusive), bars and figure. */
@Immutable
data class StatsPage(
    val start: LocalDate,
    val end: LocalDate,
    // "This week", "Last week", "10 – 16 Nov", "This month", "October", "October 2025".
    val title: String,
    val seconds: Long,
    val bars: List<TrendBar>,
    val summary: PeriodSummary,
    // The chart's heading for TalkBack, e.g. "Reading time, this week".
    val chartHeading: String,
)

/**
 * Every calendar week and month from the one holding the first day with reading up to the current
 * one, oldest first, so the last page is always "this week" / "this month".
 */
@Immutable
data class StatsTrend(
    val weeks: List<StatsPage> = emptyList(),
    val months: List<StatsPage> = emptyList(),
)

internal fun StatsTrend.pages(period: StatsPeriod): List<StatsPage> = when (period) {
    StatsPeriod.Week -> weeks
    StatsPeriod.Month -> months
}

/**
 * Buckets the daily series (ending today) into calendar weeks (starting on [locale]'s first day of
 * the week) and calendar months. Days missing from [days] count as no reading.
 */
internal fun buildStatsTrend(days: List<DailyReading>, locale: Locale = Locale.getDefault()): StatsTrend {
    val dated = days.mapNotNull { day ->
        runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.seconds }
    }
    if (dated.isEmpty()) return StatsTrend()
    val today = dated.maxOf { it.first }
    val byDate = HashMap<LocalDate, Long>(dated.size * 2).apply { dated.forEach { (date, seconds) -> merge(date, seconds, Long::plus) } }
    val first = dated.filter { it.second > 0L }.minOfOrNull { it.first } ?: today
    val calendar = StatsCalendar(today, locale)

    val weeks = ArrayList<StatsPage>()
    var weekStart = calendar.weekStart(first)
    while (!weekStart.isAfter(today)) {
        weeks += calendar.weekPage(weekStart, byDate)
        weekStart = weekStart.plusWeeks(1)
    }
    val months = ArrayList<StatsPage>()
    var month = YearMonth.from(first)
    while (!month.isAfter(YearMonth.from(today))) {
        months += calendar.monthPage(month, byDate)
        month = month.plusMonths(1)
    }
    return StatsTrend(weeks = weeks, months = months)
}

/** Labels and figures for pages, relative to [today]. */
private class StatsCalendar(private val today: LocalDate, private val locale: Locale) {
    private val firstDay = WeekFields.of(locale).firstDayOfWeek
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val spokenDay = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val monthName = DateTimeFormatter.ofPattern("LLLL", locale)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun weekPage(start: LocalDate, byDate: Map<LocalDate, Long>): StatsPage {
        val end = start.plusDays(6)
        val bars = (0 until 7).map { offset ->
            val date = start.plusDays(offset.toLong())
            val current = date == today
            TrendBar(
                shortLabel = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                label = if (current) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                spokenLabel = if (current) "today" else spokenDay.format(date),
                seconds = byDate[date] ?: 0L,
                isCurrent = current,
                isFuture = date.isAfter(today),
            )
        }
        val thisWeek = weekStart(today)
        val title = when (start) {
            thisWeek -> "This week"
            thisWeek.minusWeeks(1) -> "Last week"
            else -> weekRange(start, end)
        }
        val window = when (start) {
            thisWeek -> "this week"
            thisWeek.minusWeeks(1) -> "last week"
            else -> title
        }
        return page(start, end, bars, title, window, heading = window)
    }

    fun monthPage(month: YearMonth, byDate: Map<LocalDate, Long>): StatsPage {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val bars = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val current = date == today
            TrendBar(
                shortLabel = day.toString(),
                label = day.toString(),
                spokenLabel = if (current) "today" else spokenDay.format(date),
                seconds = byDate[date] ?: 0L,
                isCurrent = current,
                // One label a week: the 1st, 8th, 15th, 22nd and 29th.
                showLabel = (day - 1) % 7 == 0,
                isFuture = date.isAfter(today),
            )
        }
        val thisMonth = YearMonth.from(today)
        val name = if (month.year == today.year) monthName.format(month) else monthYear.format(month)
        val title = if (month == thisMonth) "This month" else name
        val window = if (month == thisMonth) "this month" else "in $name"
        return page(start, end, bars, title, window, heading = if (month == thisMonth) "this month" else name)
    }

    private fun weekRange(start: LocalDate, end: LocalDate): String = when {
        start.year != end.year -> "${dayMonthYear.format(start)} – ${dayMonthYear.format(end)}"
        start.month == end.month && end.year == today.year -> "${start.dayOfMonth} – ${dayMonth.format(end)}"
        start.month == end.month -> "${start.dayOfMonth} – ${dayMonthYear.format(end)}"
        end.year == today.year -> "${dayMonth.format(start)} – ${dayMonth.format(end)}"
        else -> "${dayMonth.format(start)} – ${dayMonthYear.format(end)}"
    }

    private fun page(start: LocalDate, end: LocalDate, bars: List<TrendBar>, title: String, window: String, heading: String): StatsPage {
        val seconds = bars.sumOf { it.seconds }
        // The current period averages over the days so far, a past one over all of its days.
        val days = ChronoUnit.DAYS.between(start, minOf(end, today)) + 1
        val perDay = seconds / days.coerceAtLeast(1L)
        val figure = formatReadingTime(seconds, zero = "0 min")
        // The page's title sits above the figure, so the caption doesn't name the period again.
        val caption = when {
            seconds == 0L -> "Nothing read"
            perDay < 60L -> "less than a minute a day"
            else -> "${formatReadingTime(perDay)} a day"
        }
        return StatsPage(
            start = start,
            end = end,
            title = title,
            seconds = seconds,
            bars = bars,
            summary = PeriodSummary(
                figure = figure,
                caption = caption,
                compact = "$title · $figure",
                spoken = if (seconds == 0L) "Nothing read $window" else "${speakReadingTime(seconds)} read $window, ${speakReadingTime(perDay)} a day",
            ),
            chartHeading = "Reading time, $heading",
        )
    }
}
