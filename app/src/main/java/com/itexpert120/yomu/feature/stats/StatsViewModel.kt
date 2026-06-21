package com.itexpert120.yomu.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.HeatmapDay
import com.itexpert120.yomu.core.model.HourlyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.core.model.WeekdayReading
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which daily-trend window the column chart is showing. */
enum class TrendRange(val label: String, val days: Int) {
    Week("7 days", 7),
    Month("30 days", 30),
}

data class StatsUiState(
    val stats: ReadingStats = ReadingStats(),
    val trendRange: TrendRange = TrendRange.Week,
    val weekly: List<DailyReading> = emptyList(),
    val monthly: List<DailyReading> = emptyList(),
    val weekday: List<WeekdayReading> = emptyList(),
    val hourly: List<HourlyReading> = emptyList(),
    val heatmap: List<HeatmapDay> = emptyList(),
    val history: List<ReadingSessionItem> = emptyList(),
) {
    val trend: List<DailyReading>
        get() = if (trendRange == TrendRange.Week) weekly else monthly
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {

    private val trendRange = MutableStateFlow(TrendRange.Week)

    private data class Data(
        val stats: ReadingStats,
        val weekly: List<DailyReading>,
        val monthly: List<DailyReading>,
        val weekday: List<WeekdayReading>,
        val hourly: List<HourlyReading>,
        val heatmap: List<HeatmapDay>,
        val history: List<ReadingSessionItem>,
    )

    // combine tops out at five typed flows, so the last three are pre-combined into a Triple.
    private val data = combine(
        statsRepository.stats,
        statsRepository.weeklyBuckets,
        statsRepository.monthlyBuckets,
        statsRepository.weekdayBuckets,
        combine(
            statsRepository.hourlyBuckets,
            statsRepository.heatmap,
            statsRepository.recentSessions,
        ) { hourly, heatmap, history -> Triple(hourly, heatmap, history) },
    ) { stats, weekly, monthly, weekday, rest ->
        Data(stats, weekly, monthly, weekday, rest.first, rest.second, rest.third)
    }

    val state: StateFlow<StatsUiState> =
        combine(data, trendRange) { d, range ->
            StatsUiState(
                stats = d.stats,
                trendRange = range,
                weekly = d.weekly,
                monthly = d.monthly,
                weekday = d.weekday,
                hourly = d.hourly,
                heatmap = d.heatmap,
                history = d.history,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onSelectTrendRange(range: TrendRange) {
        trendRange.value = range
    }
}
