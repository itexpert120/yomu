package com.itexpert120.yomu.feature.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** "4 h 21 min", "21 min", "<1 min"; [zero] for no time at all. */
internal fun formatReadingTime(seconds: Long, zero: String = "—"): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes == 0L -> "$hours h"
        hours > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} min"
        minutes > 0 -> "$minutes min"
        seconds > 0 -> "<1 min"
        else -> zero
    }
}

/** The same figure for TalkBack: "4 hours 21 minutes". */
internal fun speakReadingTime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${plural(hours, "hour")} ${plural(minutes, "minute")}"
        hours > 0 -> plural(hours, "hour")
        minutes > 0 -> plural(minutes, "minute")
        seconds > 0 -> "less than a minute"
        else -> "no time"
    }
}

internal fun plural(count: Number, noun: String): String = "$count ${if (count.toLong() == 1L) noun else "${noun}s"}"

internal fun formatDays(days: Int): String = plural(days, "day")

/** "412,000": the full number, grouped for the reader's locale. */
internal fun formatGrouped(value: Long): String = java.text.NumberFormat.getIntegerInstance().format(value)

private val ClockFormat = LocaleDateFormatter("h:mm a")
private val DayFormat = LocaleDateFormatter("EEE d MMM")
private val DayWithYearFormat = LocaleDateFormatter("EEE d MMM yyyy")

/** A [DateTimeFormatter] built once per default locale instead of once per history row. */
private class LocaleDateFormatter(private val pattern: String) {
    @Volatile
    private var cached: Pair<Locale, DateTimeFormatter>? = null

    fun format(value: TemporalAccessor): String {
        val locale = Locale.getDefault()
        val formatter = cached?.takeIf { it.first == locale }?.second
            ?: DateTimeFormatter.ofPattern(pattern, locale).also { cached = locale to it }
        return formatter.format(value)
    }
}

internal fun formatClock(millis: Long): String = ClockFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "8:12 – 8:44 PM" style range for a session that started at [startMillis] and lasted [seconds]. */
internal fun formatClockRange(startMillis: Long, seconds: Long): String = "${formatClock(startMillis)} – ${formatClock(startMillis + seconds * 1000)}"

internal fun formatDayHeader(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> if (day.year == today.year) DayFormat.format(day) else DayWithYearFormat.format(day)
}
