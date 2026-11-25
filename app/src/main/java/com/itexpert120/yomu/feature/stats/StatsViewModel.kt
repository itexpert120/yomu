package com.itexpert120.yomu.feature.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stats: ReadingStats = ReadingStats(),
    val history: List<ReadingSessionItem> = emptyList(),
    val bookTimes: List<BookReadingTime> = emptyList(),
    val trend: StatsTrend = StatsTrend(),
)

/** The window the dashboard's one figure and its chart cover. */
enum class StatsPeriod { Week, Month, All }

/**
 * One bar of a dashboard chart. [shortLabel] fits under a phone bar, [label] under a tablet bar;
 * [showLabel] is false for bars the chart leaves unlabelled (the 30-day chart labels one a week).
 */
@Immutable
data class TrendBar(
    val shortLabel: String,
    val label: String,
    val spokenLabel: String,
    val seconds: Long,
    val isCurrent: Boolean,
    val showLabel: Boolean = true,
)

/** Last 7 days, last 30 days and the last 12 months (one bar per calendar month), oldest first. */
@Immutable
data class StatsTrend(
    val week: List<TrendBar> = emptyList(),
    val month: List<TrendBar> = emptyList(),
    val year: List<TrendBar> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StatsUiState> = attempts.flatMapLatest { attempt ->
        combine(
            statsRepository.stats,
            statsRepository.recentSessions,
            statsRepository.bookReadingTimes,
            statsRepository.dailyReading.map(::buildStatsTrend).flowOn(Dispatchers.Default),
        ) { stats, history, bookTimes, trend ->
            StatsUiState(isLoading = false, stats = stats, history = history, bookTimes = bookTimes, trend = trend)
        }
            // A retry shows the skeleton again rather than leaving the error up while it loads.
            .onStart { if (attempt > 0) emit(StatsUiState()) }
            .catch { emit(StatsUiState(isLoading = false, error = "Statistics couldn't be loaded.")) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun retry() {
        attempts.value++
    }
}

/** Buckets the zero-filled daily series (oldest first, ending today) into the three charts. */
internal fun buildStatsTrend(days: List<DailyReading>, locale: Locale = Locale.getDefault()): StatsTrend {
    val dated = days.mapNotNull { day ->
        runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.seconds }
    }
    if (dated.isEmpty()) return StatsTrend()
    val today = dated.last().first
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    val spokenDay = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)

    val week = dated.takeLast(7).map { (date, seconds) ->
        val current = date == today
        TrendBar(
            shortLabel = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
            label = if (current) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            spokenLabel = if (current) "today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            seconds = seconds,
            isCurrent = current,
        )
    }
    val monthDays = dated.takeLast(30)
    val month = monthDays.mapIndexed { index, (date, seconds) ->
        val current = date == today
        val text = if (current) "Today" else dayMonth.format(date)
        TrendBar(
            shortLabel = text,
            label = text,
            spokenLabel = if (current) "today" else spokenDay.format(date),
            seconds = seconds,
            isCurrent = current,
            // One label a week, counted back from today so today is always labelled.
            showLabel = (monthDays.lastIndex - index) % 7 == 0,
        )
    }
    val thisMonth = YearMonth.from(today)
    val byMonth = dated.groupBy({ YearMonth.from(it.first) }, { it.second })
    val year = (11 downTo 0).map { back ->
        val yearMonth = thisMonth.minusMonths(back.toLong())
        TrendBar(
            shortLabel = yearMonth.month.getDisplayName(TextStyle.NARROW, locale),
            label = yearMonth.month.getDisplayName(TextStyle.SHORT, locale),
            spokenLabel = "${yearMonth.month.getDisplayName(TextStyle.FULL, locale)} ${yearMonth.year}",
            seconds = byMonth[yearMonth]?.sum() ?: 0L,
            isCurrent = back == 0,
        )
    }
    return StatsTrend(week = week, month = month, year = year)
}
