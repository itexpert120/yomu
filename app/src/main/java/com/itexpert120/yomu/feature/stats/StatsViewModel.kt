package com.itexpert120.yomu.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stats: ReadingStats = ReadingStats(),
    val history: List<ReadingSessionItem> = emptyList(),
    val bookTimes: List<BookReadingTime> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {
    val state: StateFlow<StatsUiState> =
        combine(
            statsRepository.stats,
            statsRepository.recentSessions,
            statsRepository.bookReadingTimes,
        ) { stats, history, bookTimes ->
            StatsUiState(isLoading = false, stats = stats, history = history, bookTimes = bookTimes)
        }.catch {
            emit(StatsUiState(isLoading = false, error = "Statistics couldn't be loaded."))
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
