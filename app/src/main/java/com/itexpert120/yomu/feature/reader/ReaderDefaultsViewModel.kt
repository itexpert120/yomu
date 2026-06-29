package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.CuratedFont
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReaderDefaultsUiState(
    val settings: ReaderSettings = ReaderSettings(),
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val customSheetVisible: Boolean = false,
    val installedFonts: List<CustomFontRef> = emptyList(),
    val fontSheetVisible: Boolean = false,
    // Families currently downloading, so the library sheet can show per-font progress.
    val downloadingFonts: Set<String> = emptySet(),
    val fontError: String? = null,
    // The full searchable Google Fonts catalog (bundled).
    val fontCatalog: List<CuratedFont> = emptyList(),
)

/** Edits the app-wide default reader settings (the global preferences applied to every book). */
@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
    private val fonts: FontRepository,
) : ViewModel() {

    private val customSheetVisible = MutableStateFlow(false)
    private val fontSheetVisible = MutableStateFlow(false)
    private val downloadingFonts = MutableStateFlow<Set<String>>(emptySet())
    private val fontError = MutableStateFlow<String?>(null)
    private val fontCatalog = MutableStateFlow<List<CuratedFont>>(emptyList())

    private data class FontUi(
        val sheetVisible: Boolean,
        val downloading: Set<String>,
        val error: String?,
        val catalog: List<CuratedFont>,
    )

    private val fontUi = combine(
        fontSheetVisible,
        downloadingFonts,
        fontError,
        fontCatalog,
    ) { sheet, downloading, error, catalog ->
        FontUi(sheet, downloading, error, catalog)
    }

    init {
        // Load the bundled searchable catalog once (lazily on first construction).
        viewModelScope.launch { fontCatalog.value = fonts.catalog() }
    }

    val state: StateFlow<ReaderDefaultsUiState> = combine(
        settingsRepository.global,
        settingsRepository.customThemes,
        customSheetVisible,
        fonts.installed,
        fontUi,
    ) { settings, themes, sheet, installed, font ->
        ReaderDefaultsUiState(
            settings = settings,
            customThemes = themes,
            customSheetVisible = sheet,
            installedFonts = installed,
            fontSheetVisible = font.sheetVisible,
            downloadingFonts = font.downloading,
            fontError = font.error,
            fontCatalog = font.catalog,
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

    fun onOpenFontLibrary() {
        fontError.value = null
        fontSheetVisible.value = true
    }

    fun onCloseFontLibrary() {
        fontSheetVisible.value = false
        fontError.value = null
    }

    /** Downloads a Google Font family and registers it; surfaces an error on failure. */
    fun onInstallFont(family: String) {
        if (family in downloadingFonts.value) return
        viewModelScope.launch {
            fontError.value = null
            downloadingFonts.update { it + family }
            val result = fonts.install(family)
            downloadingFonts.update { it - family }
            if (result.isFailure) fontError.value = "Couldn’t download $family. Check your connection."
        }
    }

    /** Removes an installed custom font; if it was the selected default, reverts to the bundled font. */
    fun onRemoveFont(family: String) {
        viewModelScope.launch {
            fonts.remove(family)
            if (state.value.settings.customFont?.family == family) {
                onUpdate(state.value.settings.copy(customFont = null))
            }
        }
    }
}
