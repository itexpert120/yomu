package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.data.books.ChapterReadState
import com.itexpert120.yomu.data.books.ChapterReading
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReaderUiState(
    val experience: ReadingExperienceState = ReadingExperienceState(),
    val chrome: ReaderChromeState = ReaderChromeState(),
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val installedFonts: List<CustomFontRef> = emptyList(),
    /** Which chapters are read, for the Browse sheet's contents. */
    val chapterReads: ChapterReadState = ChapterReadState.Empty,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    experienceFactory: ReadingExperienceFactory,
    private val settingsRepository: ReaderSettingsRepository,
    fonts: FontRepository,
    chapterReading: ChapterReading,
) : ViewModel() {
    private val bookId = BookId(requireNotNull(savedStateHandle["bookId"]))
    private val readingExperience = experienceFactory.create(
        bookId = bookId,
        initialLocatorJson = savedStateHandle["locator"],
        ownerScope = viewModelScope,
    )

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            fonts.installed.collect { list -> _state.update { it.copy(installedFonts = list) } }
        }
        viewModelScope.launch {
            chapterReading.observe(bookId).collect { reads -> _state.update { it.copy(chapterReads = reads) } }
        }
        viewModelScope.launch {
            settingsRepository.customThemes.collect { themes ->
                _state.update { it.copy(customThemes = themes) }
            }
        }
        viewModelScope.launch {
            readingExperience.state.collect { experience ->
                _state.update { it.copy(experience = experience) }
            }
        }
        viewModelScope.launch {
            readingExperience.events.collect { event ->
                when (event) {
                    ReadingExperienceEvent.ToggleChrome -> onAction(ReaderAction.ToggleChrome)
                }
            }
        }
        readingExperience.start()
    }

    fun onAction(action: ReaderAction) {
        _state.update { it.copy(chrome = reduceChrome(it.chrome, action)) }
        when (action) {
            is ReaderAction.ApplyCustomTheme -> applyCustomTheme(action.theme)
            is ReaderAction.SaveCustomTheme -> saveCustomTheme(action.name)
            is ReaderAction.DeleteCustomTheme -> viewModelScope.launch { settingsRepository.deleteCustomTheme(action.id) }
            else -> action.toExperienceAction()?.let(readingExperience::dispatch)
        }
    }

    /** The reader is in the foreground: reading time may accrue (once the page is ready). */
    fun onReadingResumed() = readingExperience.dispatch(ReadingExperienceAction.Resume)

    /** The reader left the foreground: bank the reading session and save the position. */
    fun onReadingPaused() = readingExperience.dispatch(ReadingExperienceAction.Pause)

    private fun applyCustomTheme(theme: CustomReaderTheme) {
        onAction(
            ReaderAction.UpdateSettings(
                state.value.experience.settings.copy(
                    theme = ReaderThemeMode.Custom,
                    customBackground = theme.background,
                    customText = theme.text,
                ),
            ),
        )
    }

    private fun saveCustomTheme(name: String) {
        val settings = state.value.experience.settings
        val theme = CustomReaderTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Custom" },
            background = settings.backgroundArgb,
            text = settings.textArgb,
        )
        viewModelScope.launch { settingsRepository.saveCustomTheme(theme) }
    }

    override fun onCleared() {
        readingExperience.close()
    }
}
