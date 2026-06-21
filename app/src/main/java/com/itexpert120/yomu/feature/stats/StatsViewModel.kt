package com.itexpert120.yomu.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val stats: ReadingStats = ReadingStats(),
    val daily: List<DailyReading> = emptyList(),
    val history: List<ReadingSessionItem> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> =
        combine(
            statsRepository.stats,
            statsRepository.dailyBuckets,
            statsRepository.recentSessions,
        ) { stats, daily, history ->
            StatsUiState(stats = stats, daily = daily, history = history)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
