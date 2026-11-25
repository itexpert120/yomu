package com.itexpert120.yomu.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookReadingTime
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
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stats: ReadingStats = ReadingStats(),
    val history: List<ReadingSessionItem> = emptyList(),
    val bookTimes: List<BookReadingTime> = emptyList(),
    val trend: StatsTrend = StatsTrend(),
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
            statsRepository.dailyReading.map { buildStatsTrend(it) }.flowOn(Dispatchers.Default),
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
