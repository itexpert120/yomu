package com.itexpert120.yomu.core.model

/** Reading time for one calendar day, for the dashboard chart. [date] is ISO `yyyy-MM-dd`. */
data class DailyReading(
    val date: String,
    val seconds: Long,
)

/** One past reading session, projected for the history list. */
data class ReadingSessionItem(
    val bookTitle: String,
    val coverImagePath: String?,
    val startedAt: Long,
    val seconds: Long,
)

/**
 * Reading time bucketed by day-of-week (Mon..Sun). [dayIndex] is 0 = Monday … 6 = Sunday so the
 * UI can order/label without re-deriving the calendar.
 */
data class WeekdayReading(
    val dayIndex: Int,
    val seconds: Long,
)

/**
 * Reading time bucketed by hour-of-day (0..23, local time the session *started*). Drives the
 * "when do you read" distribution chart.
 */
data class HourlyReading(
    val hour: Int,
    val seconds: Long,
)

/**
 * One cell of the activity heatmap: a calendar day and its reading intensity. [level] is a
 * pre-bucketed 0..4 intensity (0 = no reading) so the UI doesn't need the global max. [date] is
 * ISO `yyyy-MM-dd`; [inMonth] flags leading/trailing padding days from adjacent months.
 */
data class HeatmapDay(
    val date: String,
    val seconds: Long,
    val level: Int,
    val inMonth: Boolean,
)

/** Aggregate reading statistics surfaced on the Stats screen. */
data class ReadingStats(
    val totalReadingSeconds: Long = 0L,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val booksInLibrary: Int = 0,
    val booksStarted: Int = 0,
    val booksFinished: Int = 0,
    val chaptersRead: Int = 0,
    val estimatedWordsRead: Long = 0L,
    val sessionCount: Int = 0,
    val averageSessionSeconds: Long = 0L,
    val longestSessionSeconds: Long = 0L,
    val daysRead: Int = 0,
    val averageSecondsPerActiveDay: Long = 0L,
    val secondsLast7Days: Long = 0L,
    val secondsLast30Days: Long = 0L,
)
