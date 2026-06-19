package com.itexpert120.yomu.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds app-shell theme state, persisted through [AppSettingsRepository]. The concrete
 * light/dark/OLED resolution happens in [YomuApp] where the system dark setting is observable.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
) : ViewModel() {

    val themePreference: StateFlow<ThemePreference> = settings.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.System)

    val oledDark: StateFlow<Boolean> = settings.oledDark
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val accentSelection: StateFlow<AccentSelection> = settings.accentSelection
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentSelection.Default)

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

    fun onSelectAccent(accent: AccentColor) = viewModelScope.launch {
        settings.setAccentSelection(AccentSelection.Preset(accent))
    }

    fun onSelectCustomAccent(argb: Long) = viewModelScope.launch {
        settings.setAccentSelection(AccentSelection.Custom(argb))
    }
}
