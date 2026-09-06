package com.itexpert120.yomu.data.stats

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReadingDayEntity
import com.itexpert120.yomu.core.database.ReadingSessionEntity
import com.itexpert120.yomu.core.database.ReadingTotalEntity
import com.itexpert120.yomu.core.database.ReadingWriteReceipt
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.core.model.isBookCompleted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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
class StatsRepository internal constructor(
    private val dao: BookDao,
    private val database: YomuDatabase,
    dates: Flow<LocalDate>,
) {
    @Inject constructor(dao: BookDao, database: YomuDatabase, calendar: ReadingCalendar) : this(dao, database, calendar.dates)

    /** Total time spent reading a single book (seconds), as a live flow. */
    fun bookReadingSeconds(bookId: BookId): Flow<Long> = dao.observeBookReadingSeconds(bookId.value).distinctUntilChanged()

    /** Logs a finished reading session and folds its time into the day it started. */
    suspend fun recordSession(bookId: BookId, startedAtMillis: Long, seconds: Long, operationId: String = UUID.randomUUID().toString()) {
        if (seconds <= 0L) return
        database.withTransaction {
            if (dao.insertReadingWriteReceipt(ReadingWriteReceipt(operationId)) == -1L) return@withTransaction
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
            val lifetime = dao.getReadingTotal(bookId.value)
            dao.upsertReadingTotal(
                ReadingTotalEntity(
                    bookId.value,
                    (lifetime?.seconds ?: 0L) + seconds,
                    (lifetime?.sessionCount ?: 0) + 1,
                    maxOf(lifetime?.longestSeconds ?: 0L, seconds),
                ),
            )
            dao.pruneReadingSessions(MAX_RETAINED_SESSIONS)
        }
    }

    // Day-derived figures recompute only when reading-day data actually changes (not on every book
    // or chapter edit), avoiding repeated date parsing + streak scans during active reading.
    private val dayStats: Flow<DayStats> = combine(dao.observeReadingDays(), dates) { days, today ->
        val active = days
            .filter { it.seconds > 0L }
            .mapNotNull { day ->
                runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.seconds }
            }
        val activeDates = active.map { it.first }.toSortedSet()
        val total = days.sumOf { it.seconds }
        DayStats(
            totalSeconds = total,
            currentStreak = currentStreak(activeDates, today),
            longestStreak = longestStreak(activeDates),
            daysRead = activeDates.size,
            secondsLast7Days = total(active, 7, today),
            secondsLast30Days = total(active, 30, today),
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
                seconds = aggregate.seconds,
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
            booksStarted = books.count { it.startedAt > 0L || it.lastOpenedAt > 0L },
            booksFinished = books.count { isBookCompleted(it.progress) },
            chaptersRead = chaptersRead,
            estimatedWordsRead = (day.totalSeconds / 60.0 * WORDS_PER_MINUTE).toLong(),
            estimatedReadingSpeedWpm = if (day.totalSeconds > 0L) WORDS_PER_MINUTE else 0,
            sessionCount = session.count,
            averageSessionSeconds = session.averageSeconds,
            longestSessionSeconds = session.longestSeconds,
            historicalSessionDetailIncomplete = session.seconds < day.totalSeconds,
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

    private fun total(active: List<Pair<LocalDate, Long>>, windowDays: Int, today: LocalDate): Long {
        val cutoff = today.minusDays((windowDays - 1).toLong())
        return active.filter { !it.first.isBefore(cutoff) && !it.first.isAfter(today) }.sumOf { it.second }
    }

    /** Consecutive days with reading ending today (a day's grace if today hasn't been read yet). */
    private fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int {
        if (dates.isEmpty()) return 0
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
        val seconds: Long,
    )

    private companion object {
        const val WORDS_PER_MINUTE = 200
        const val RECENT_LIMIT = 300
        const val MAX_RETAINED_SESSIONS = 10_000
    }
}
