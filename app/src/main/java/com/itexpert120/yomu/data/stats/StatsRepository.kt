package com.itexpert120.yomu.data.stats

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReadingDayEntity
import com.itexpert120.yomu.core.database.ReadingSessionEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading statistics and history. Each reading session is logged ([recordSession]) for the history
 * list, and the same write incrementally rolls up per-day totals so streaks/totals stay cheap. The
 * session and rollup are committed in one Room transaction so partial writes cannot diverge.
 *
 * Distributions (weekday, hour-of-day) and session aggregates derive from the session log; daily
 * trend + streaks + heatmap derive from the per-day rollup.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val dao: BookDao,
    private val database: YomuDatabase,
) {
    /** Total time spent reading a single book (seconds), as a live flow. */
    fun bookReadingSeconds(bookId: BookId): Flow<Long> = dao.observeBookReadingSeconds(bookId.value).distinctUntilChanged()

    /** Logs a finished reading session and folds its time into the day it started. */
    suspend fun recordSession(bookId: BookId, startedAtMillis: Long, seconds: Long) {
        if (seconds <= 0L) return
        database.withTransaction {
            dao.insertReadingSession(
                ReadingSessionEntity(
                    bookId = bookId.value,
                    startedAt = startedAtMillis,
                    seconds = seconds,
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
            dao.pruneReadingSessions(MAX_RETAINED_SESSIONS)
        }
    }

    // Day-derived figures recompute only when reading-day data actually changes (not on every book
    // or chapter edit), avoiding repeated date parsing + streak scans during active reading.
    private val dayStats: Flow<DayStats> = dao.observeReadingDays()
        .map { days ->
            val active = days
                .filter { it.seconds > 0L }
                .mapNotNull { day ->
                    runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.seconds }
                }
            val activeDates = active.map { it.first }.toSortedSet()
            val total = days.sumOf { it.seconds }
            DayStats(
                totalSeconds = total,
                currentStreak = currentStreak(activeDates),
                longestStreak = longestStreak(activeDates),
                daysRead = activeDates.size,
                secondsLast7Days = total(active, 7),
                secondsLast30Days = total(active, 30),
            )
        }
        .distinctUntilChanged()

    // Session-derived aggregates: count / average / longest. Independent of the day rollup so they
    // recompute only when the session log changes.
    private val sessionStats: Flow<SessionStats> = dao.observeSessionAggregate()
        .map { aggregate ->
            SessionStats(
                count = aggregate.count,
                averageSeconds = aggregate.averageSeconds,
                longestSeconds = aggregate.longestSeconds,
            )
        }
        .distinctUntilChanged()

    val stats: Flow<ReadingStats> = combine(
        dao.observeBooks(),
        dayStats,
        dao.observeChapterReadCount(),
        sessionStats,
    ) { books, day, chaptersRead, session ->
        ReadingStats(
            totalReadingSeconds = day.totalSeconds,
            currentStreakDays = day.currentStreak,
            longestStreakDays = day.longestStreak,
            booksInLibrary = books.size,
            booksStarted = books.count { it.lastOpenedAt > 0L },
            booksFinished = books.count { it.progress >= 0.999f },
            chaptersRead = chaptersRead,
            estimatedWordsRead = (day.totalSeconds / 60.0 * WORDS_PER_MINUTE).toLong(),
            estimatedReadingSpeedWpm = if (day.totalSeconds > 0L) WORDS_PER_MINUTE else 0,
            sessionCount = session.count,
            averageSessionSeconds = session.averageSeconds,
            longestSessionSeconds = session.longestSeconds,
            daysRead = day.daysRead,
            averageSecondsPerActiveDay =
            if (day.daysRead > 0) day.totalSeconds / day.daysRead else 0L,
            secondsLast7Days = day.secondsLast7Days,
            secondsLast30Days = day.secondsLast30Days,
        )
    }

    val recentSessions: Flow<List<ReadingSessionItem>> = combine(
        dao.observeRecentSessions(RECENT_LIMIT),
        dao.observeBooks(),
    ) { sessions, books ->
        val byId = books.associateBy { it.id }
        sessions.map {
            val book = byId[it.bookId]
            ReadingSessionItem(
                bookId = book?.id,
                bookTitle = book?.title ?: "Unknown book",
                coverImagePath = book?.coverImagePath,
                startedAt = it.startedAt,
                seconds = it.seconds,
            )
        }
    }

    private fun total(active: List<Pair<LocalDate, Long>>, windowDays: Int): Long {
        val cutoff = LocalDate.now().minusDays((windowDays - 1).toLong())
        return active.filter { !it.first.isBefore(cutoff) }.sumOf { it.second }
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
        val longestStreak: Int,
        val daysRead: Int,
        val secondsLast7Days: Long,
        val secondsLast30Days: Long,
    )

    private data class SessionStats(
        val count: Int,
        val averageSeconds: Long,
        val longestSeconds: Long,
    )

    private companion object {
        const val WORDS_PER_MINUTE = 200
        const val RECENT_LIMIT = 300
        const val MAX_RETAINED_SESSIONS = 10_000
    }
}
