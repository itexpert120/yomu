package com.itexpert120.yomu.data.stats

import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReadingDayEntity
import com.itexpert120.yomu.core.database.ReadingSessionEntity
import com.itexpert120.yomu.core.database.SessionTime
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.HeatmapDay
import com.itexpert120.yomu.core.model.HourlyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.core.model.WeekdayReading
import android.content.Context
import com.itexpert120.yomu.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * list, and the same write incrementally rolls up per-day totals so streaks/totals stay cheap. The
 * write is serialized by a mutex so concurrent flushes can't lose increments.
 *
 * Distributions (weekday, hour-of-day) and session aggregates derive from the session log; daily
 * trend + streaks + heatmap derive from the per-day rollup.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val dao: BookDao,
    @ApplicationContext private val context: Context,
) {
    private val writeMutex = Mutex()

    /** Total time spent reading a single book (seconds), as a live flow. */
    fun bookReadingSeconds(bookId: BookId): Flow<Long> =
        dao.observeBookReadingSeconds(bookId.value).distinctUntilChanged()

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
        // Reflect the new session in the home-screen reading-activity widget.
        WidgetUpdater.refreshActivity(context)
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
    private val sessionStats: Flow<SessionStats> = dao.observeSessionTimes()
        .map { sessions ->
            val count = sessions.size
            val totalSeconds = sessions.sumOf { it.seconds }
            SessionStats(
                count = count,
                averageSeconds = if (count > 0) totalSeconds / count else 0L,
                longestSeconds = sessions.maxOfOrNull { it.seconds } ?: 0L,
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

    /** Reading time per day for the last 7 days (zero-filled), oldest first. */
    val weeklyBuckets: Flow<List<DailyReading>> = dailyBucketsFlow(WEEK_DAYS)

    /** Reading time per day for the last 30 days (zero-filled), oldest first. */
    val monthlyBuckets: Flow<List<DailyReading>> = dailyBucketsFlow(MONTH_DAYS)

    private fun dailyBucketsFlow(days: Int): Flow<List<DailyReading>> =
        dao.observeReadingDays()
            .map { rows ->
                val byDate = rows.associate { it.date to it.seconds }
                val today = LocalDate.now()
                (days - 1 downTo 0).map { offset ->
                    val date = today.minusDays(offset.toLong()).toString()
                    DailyReading(date = date, seconds = byDate[date] ?: 0L)
                }
            }
            .distinctUntilChanged()

    /** Total reading time per day-of-week (Mon..Sun), over the whole session history. */
    val weekdayBuckets: Flow<List<WeekdayReading>> = dao.observeSessionTimes()
        .map { sessions ->
            val totals = LongArray(7)
            sessions.forEach { session ->
                // Monday = 0 … Sunday = 6 (DayOfWeek.value is 1..7 with Monday = 1).
                val index = startZoned(session).dayOfWeek.value - 1
                totals[index] += session.seconds
            }
            totals.mapIndexed { index, seconds -> WeekdayReading(index, seconds) }
        }
        .distinctUntilChanged()

    /** Total reading time per hour-of-day (0..23) the session started, over the whole history. */
    val hourlyBuckets: Flow<List<HourlyReading>> = dao.observeSessionTimes()
        .map { sessions ->
            val totals = LongArray(24)
            sessions.forEach { session ->
                totals[startZoned(session).hour] += session.seconds
            }
            totals.mapIndexed { hour, seconds -> HourlyReading(hour, seconds) }
        }
        .distinctUntilChanged()

    /**
     * Calendar heatmap for the last [HEATMAP_WEEKS] weeks, week-aligned to start on Monday and
     * ending with the week containing today. Each day carries a 0..4 intensity level relative to
     * the busiest day in the window.
     */
    val heatmap: Flow<List<HeatmapDay>> = dao.observeReadingDays()
        .map { rows ->
            val byDate = rows.associate { it.date to it.seconds }
            val today = LocalDate.now()
            val currentMonth = today.month
            // End on the Sunday of this week so columns are full weeks.
            val end = today.plusDays((7 - today.dayOfWeek.value).toLong())
            val start = end.minusWeeks((HEATMAP_WEEKS - 1).toLong())
                .with(java.time.DayOfWeek.MONDAY)
            val max = (HEATMAP_WEEKS * 7).let { capacity ->
                var m = 0L
                var d = start
                repeat(capacity) {
                    m = maxOf(m, byDate[d.toString()] ?: 0L)
                    d = d.plusDays(1)
                }
                m
            }.coerceAtLeast(1L)
            buildList {
                var d = start
                while (!d.isAfter(end)) {
                    val seconds = byDate[d.toString()] ?: 0L
                    add(
                        HeatmapDay(
                            date = d.toString(),
                            seconds = seconds,
                            level = intensityLevel(seconds, max),
                            inMonth = d.month == currentMonth,
                        )
                    )
                    d = d.plusDays(1)
                }
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

    private fun startZoned(session: SessionTime) =
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())

    private fun total(active: List<Pair<LocalDate, Long>>, windowDays: Int): Long {
        val cutoff = LocalDate.now().minusDays((windowDays - 1).toLong())
        return active.filter { !it.first.isBefore(cutoff) }.sumOf { it.second }
    }

    private fun intensityLevel(seconds: Long, max: Long): Int = when {
        seconds <= 0L -> 0
        else -> {
            val frac = seconds.toDouble() / max
            when {
                frac <= 0.25 -> 1
                frac <= 0.5 -> 2
                frac <= 0.75 -> 3
                else -> 4
            }
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
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30
        const val HEATMAP_WEEKS = 18
    }
}
