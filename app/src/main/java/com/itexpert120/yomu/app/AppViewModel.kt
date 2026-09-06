package com.itexpert120.yomu.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.stats.ReadingWriteQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppAppearance(
    val themePreference: ThemePreference = ThemePreference.System,
    val oledDark: Boolean = false,
    val dynamicColors: Boolean = false,
    val accentSelection: AccentSelection = AccentSelection.Default,
    val isLoaded: Boolean = false,
)

/**
 * Holds app-shell theme state, persisted through [AppSettingsRepository]. The concrete
 * light/dark/OLED resolution happens in [YomuApp] where the system dark setting is observable.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val readingWrites: ReadingWriteQueue,
) : ViewModel() {
    val readingWriteError = readingWrites.error
    fun onRetryReadingWrites() = readingWrites.retry()
    val appearance: StateFlow<AppAppearance> = combine(
        settings.themePreference,
        settings.oledDark,
        settings.dynamicColors,
        settings.accentSelection,
    ) { themePreference, oledDark, dynamicColors, accentSelection ->
        AppAppearance(
            themePreference = themePreference,
            oledDark = oledDark,
            dynamicColors = dynamicColors,
            accentSelection = accentSelection,
            isLoaded = true,
        )
    }.catch {
        // Never strand the Android splash if preferences cannot be read; use safe defaults.
        emit(AppAppearance(isLoaded = true))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppAppearance())

    /** Quick toggle (library header): System → Light → Dark → System. */
    fun onCycleTheme() = viewModelScope.launch {
        val values = ThemePreference.entries
        val next = values[(settings.themePreference.first().ordinal + 1) % values.size]
        settings.setThemePreference(next)
    }

    fun onSelectTheme(preference: ThemePreference) = viewModelScope.launch {
        settings.setThemePreference(preference)
    }

    fun onSetOledDark(enabled: Boolean) = viewModelScope.launch {
        settings.setOledDark(enabled)
    }

    fun onSetDynamicColors(enabled: Boolean) = viewModelScope.launch {
        settings.setDynamicColors(enabled)
    }

    fun onSelectAccent(accent: AccentColor) = viewModelScope.launch {
        settings.setAccentSelection(AccentSelection.Preset(accent))
    }

    fun onSelectCustomAccent(argb: Long) = viewModelScope.launch {
        settings.setAccentSelection(AccentSelection.Custom(argb))
    }
}
