package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.CuratedFont
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FontLibraryUiState(
    val installedFonts: List<CustomFontRef> = emptyList(),
    // Families currently downloading, so the screen can show per-font progress.
    val downloadingFonts: Set<String> = emptySet(),
    val error: String? = null,
    // The full searchable Google Fonts catalog (bundled).
    val catalog: List<CuratedFont> = emptyList(),
)

/** Add/remove reading fonts from the bundled Google Fonts catalog. A dedicated screen (not a
 *  bottom sheet) — Material3's ModalBottomSheet resizes its own Dialog window under the keyboard,
 *  which fought the search field here badly enough to warrant a real destination instead. */
@HiltViewModel
class FontLibraryViewModel @Inject constructor(
    private val fonts: FontRepository,
    private val settingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val downloadingFonts = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<String?>(null)
    private val catalog = MutableStateFlow<List<CuratedFont>>(emptyList())

    init {
        viewModelScope.launch { catalog.value = fonts.catalog() }
    }

    val state: StateFlow<FontLibraryUiState> = combine(
        fonts.installed,
        downloadingFonts,
        error,
        catalog,
    ) { installed, downloading, err, cat ->
        FontLibraryUiState(installed, downloading, err, cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontLibraryUiState())

    /** Downloads a Google Font family and registers it; surfaces an error on failure. */
    fun onInstallFont(family: String) {
        if (family in downloadingFonts.value) return
        viewModelScope.launch {
            error.value = null
            downloadingFonts.update { it + family }
            val result = fonts.install(family)
            downloadingFonts.update { it - family }
            if (result.isFailure) error.value = "Couldn’t download $family. Check your connection."
        }
    }

    /** Removes an installed custom font; if it was the selected default, reverts to the bundled font. */
    fun onRemoveFont(family: String) {
        viewModelScope.launch {
            fonts.remove(family)
            val settings = settingsRepository.global.first()
            if (settings.customFont?.family == family) {
                settingsRepository.setGlobal(settings.copy(customFont = null))
            }
        }
    }
}
