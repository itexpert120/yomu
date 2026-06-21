package com.itexpert120.yomu.data.stats

import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReadingDayEntity
import com.itexpert120.yomu.core.database.ReadingSessionEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.data.stats.StatsRepository.Companion.CHART_DAYS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading statistics and history. Each reading session is logged ([recordSession]) for the history
 * list, and the same write incrementally rolls up per-day totals so streaks/totals stay cheap. To
 * write is serialized by a mutex so concurrent flushes can't lose increments.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val dao: BookDao,
) {
    private val writeMutex = Mutex()

    /** Logs a finished reading session and folds its time into the day it started. */
    suspend fun recordSession(bookId: BookId, startedAtMillis: Long, seconds: Long) {
        if (seconds <= 0L) return
        writeMutex.withLock {
            dao.insertReadingSession(
                ReadingSessionEntity(
                    bookId = bookId.value,
                    startedAt = startedAtMillis,
                    seconds = seconds
                ),
            )
            // Bucket by the session's start day (local) so a session that crosses midnight isn't
            // misattributed to the flush time.
            val date = Instant.ofEpochMilli(startedAtMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val current = dao.getReadingDaySeconds(date) ?: 0L
            dao.upsertReadingDay(ReadingDayEntity(date, current + seconds))
        }
    }

    // Day-derived figures recompute only when reading-day data actually changes (not on every book
    // or chapter edit), avoiding repeated date parsing + streak scans during active reading.
    private val dayStats: Flow<DayStats> = dao.observeReadingDays()
        .map { days ->
            val total = days.sumOf { it.seconds }
            val active = days
                .filter { it.seconds > 0L }
                .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                .toSortedSet()
            DayStats(total, currentStreak(active), longestStreak(active))
        }
        .distinctUntilChanged()

    val stats: Flow<ReadingStats> = combine(
        dao.observeBooks(),
        dayStats,
        dao.observeChapterReadCount(),
    ) { books, day, chaptersRead ->
        ReadingStats(
            totalReadingSeconds = day.totalSeconds,
            currentStreakDays = day.currentStreak,
            longestStreakDays = day.longestStreak,
            booksInLibrary = books.size,
            booksStarted = books.count { it.lastOpenedAt > 0L },
            booksFinished = books.count { it.progress >= 0.999f },
            chaptersRead = chaptersRead,
            estimatedWordsRead = (day.totalSeconds / 60.0 * WORDS_PER_MINUTE).toLong(),
        )
    }

    /** Reading time per day for the last [CHART_DAYS] days (zero-filled), oldest first. */
    val dailyBuckets: Flow<List<DailyReading>> = dao.observeReadingDays()
        .map { days ->
            val byDate = days.associate { it.date to it.seconds }
            val today = LocalDate.now()
            (CHART_DAYS - 1 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong()).toString()
                DailyReading(date = date, seconds = byDate[date] ?: 0L)
            }
        }
        .distinctUntilChanged()

    val recentSessions: Flow<List<ReadingSessionItem>> = combine(
        dao.observeRecentSessions(RECENT_LIMIT),
        dao.observeBooks(),
    ) { sessions, books ->
        val byId = books.associateBy { it.id }
        sessions.map {
            val book = byId[it.bookId]
            ReadingSessionItem(
                bookTitle = book?.title ?: "Unknown book",
                coverImagePath = book?.coverImagePath,
                startedAt = it.startedAt,
                seconds = it.seconds,
            )
        }
    }

    /** Consecutive days with reading ending today (a day's grace if today hasn't been read yet). */
    private fun currentStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        val today = LocalDate.now()
        var day = if (today in dates) today else today.minusDays(1)
        if (day !in dates) return 0
        var streak = 0
        while (day in dates) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun longestStreak(dates: Set<LocalDate>): Int {
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in dates) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            if (run > longest) longest = run
            previous = date
        }
        return longest
    }

    private data class DayStats(
        val totalSeconds: Long,
        val currentStreak: Int,
        val longestStreak: Int
    )

    private companion object {
        const val WORDS_PER_MINUTE = 200
        const val RECENT_LIMIT = 40
        const val CHART_DAYS = 14
    }
}
