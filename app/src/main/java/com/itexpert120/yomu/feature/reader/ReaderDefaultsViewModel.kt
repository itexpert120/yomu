package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReaderDefaultsUiState(
    val settings: ReaderSettings = ReaderSettings(),
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val customSheetVisible: Boolean = false,
)

/** Edits the app-wide default reader settings (the global preferences applied to every book). */
@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val customSheetVisible = MutableStateFlow(false)

    val state: StateFlow<ReaderDefaultsUiState> = combine(
        settingsRepository.global,
        settingsRepository.customThemes,
        customSheetVisible,
    ) { settings, themes, sheet ->
        ReaderDefaultsUiState(
            settings = settings,
            customThemes = themes,
            customSheetVisible = sheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderDefaultsUiState())

    fun onUpdate(settings: ReaderSettings) {
        viewModelScope.launch { settingsRepository.setGlobal(settings) }
    }

    fun onOpenCustomTheme() {
        customSheetVisible.value = true
    }

    fun onCloseCustomTheme() {
        customSheetVisible.value = false
    }

    fun onApplyCustomTheme(theme: CustomReaderTheme) {
        onUpdate(
            state.value.settings.copy(
                theme = ReaderThemeMode.Custom,
                customBackground = theme.background,
                customText = theme.text,
            ),
        )
    }

    fun onSaveCustomTheme(name: String) {
        val s = state.value.settings
        val theme = CustomReaderTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Custom" },
            background = s.backgroundArgb,
            text = s.textArgb,
        )
        viewModelScope.launch { settingsRepository.saveCustomTheme(theme) }
    }

    fun onDeleteCustomTheme(id: String) {
        viewModelScope.launch { settingsRepository.deleteCustomTheme(id) }
    }
}
