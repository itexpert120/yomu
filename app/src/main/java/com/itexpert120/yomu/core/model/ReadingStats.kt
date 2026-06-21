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
)
