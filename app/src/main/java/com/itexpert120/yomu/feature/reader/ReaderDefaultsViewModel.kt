package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReaderDefaultsUiState(
    val settingsError: String? = null,
    val settingsPending: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val customSheetVisible: Boolean = false,
    val installedFonts: List<CustomFontRef> = emptyList(),
)

/** Edits the app-wide default reader settings (the global preferences applied to every book). */
@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
    fonts: FontRepository,
) : ViewModel() {

    private val customSheetVisible = MutableStateFlow(false)
    private val editor = ReaderSettingsEditor(viewModelScope, settingsRepository::setGlobal) { settingsRepository.global.first() }

    init {
        viewModelScope.launch { settingsRepository.global.collect(editor::observe) }
    }

    val state: StateFlow<ReaderDefaultsUiState> = combine(
        editor.state,
        settingsRepository.customThemes,
        customSheetVisible,
        fonts.installed,
    ) { settings, themes, sheet, installed ->
        ReaderDefaultsUiState(
            settings = settings.settings,
            settingsError = settings.error,
            settingsPending = settings.pending,
            customThemes = themes,
            customSheetVisible = sheet,
            installedFonts = installed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderDefaultsUiState())

    fun onUpdate(settings: ReaderSettings) {
        editor.edit(settings)
    }

    fun onRetrySettings() = editor.retry()

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
