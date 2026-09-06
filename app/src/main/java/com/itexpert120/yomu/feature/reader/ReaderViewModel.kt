package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
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
    val chapterControlsVisible: Boolean = false,
    val sheetVisible: Boolean = false,
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val installedFonts: List<CustomFontRef> = emptyList(),
    val customSheetVisible: Boolean = false,
    val browseTab: BrowseTab? = null,
    val searchSheetVisible: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    experienceFactory: ReadingExperienceFactory,
    private val settingsRepository: ReaderSettingsRepository,
    fonts: FontRepository,
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
                    ReadingExperienceEvent.ToggleChrome ->
                        _state.update {
                            it.copy(chapterControlsVisible = !it.chapterControlsVisible)
                        }
                }
            }
        }
        readingExperience.start()
    }

    fun onRetryOpen() = dispatch(ReadingExperienceAction.RetryOpen)

    fun onOpenSheet() = _state.update { it.copy(sheetVisible = true, chapterControlsVisible = false) }

    fun onCloseSheet() = _state.update { it.copy(sheetVisible = false) }

    fun onSelectChapter(locatorJson: String) = navigateTo(locatorJson)

    fun onNextChapter() {
        dispatch(ReadingExperienceAction.Navigate(ReaderNavigation.NextChapter))
        _state.update { it.copy(chapterControlsVisible = false) }
    }

    fun onPreviousChapter() {
        dispatch(ReadingExperienceAction.Navigate(ReaderNavigation.PreviousChapter))
        _state.update { it.copy(chapterControlsVisible = false) }
    }

    fun onOpenBrowse() = openBrowse(BrowseTab.Contents)

    fun onSelectBrowseTab(tab: BrowseTab) = _state.update { it.copy(browseTab = tab) }

    fun onCloseBrowse() = _state.update { it.copy(browseTab = null) }

    fun onJumpToLocator(locatorJson: String) {
        navigateTo(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    fun onUpdateSettings(settings: ReaderSettings) {
        if (settings != state.value.experience.settings) {
            dispatch(ReadingExperienceAction.ApplySettings(settings))
        }
    }

    fun onResetBookSettings() = dispatch(ReadingExperienceAction.ResetSettings)
    fun onRetrySettings() = dispatch(ReadingExperienceAction.RetrySettings)
    fun onRetryAnnotations() = dispatch(ReadingExperienceAction.RetryAnnotations)

    fun onOpenCustomTheme() = _state.update { it.copy(customSheetVisible = true, sheetVisible = false) }

    fun onCloseCustomTheme() = _state.update { it.copy(customSheetVisible = false) }

    fun onApplyCustomTheme(theme: CustomReaderTheme) {
        onUpdateSettings(
            state.value.experience.settings.copy(
                theme = ReaderThemeMode.Custom,
                customBackground = theme.background,
                customText = theme.text,
            ),
        )
    }

    fun onSaveCustomTheme(name: String) {
        val settings = state.value.experience.settings
        val theme = CustomReaderTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Custom" },
            background = settings.backgroundArgb,
            text = settings.textArgb,
        )
        viewModelScope.launch { settingsRepository.saveCustomTheme(theme) }
    }

    fun onDeleteCustomTheme(id: String) {
        viewModelScope.launch { settingsRepository.deleteCustomTheme(id) }
    }

    fun onLookUpWord(raw: String) = dispatch(ReadingExperienceAction.LookUpWord(raw))

    fun onRetryLookup() = dispatch(ReadingExperienceAction.RetryLookup)

    fun onLookupBack() = dispatch(ReadingExperienceAction.LookupBack)

    fun onPronounce(word: String) = dispatch(ReadingExperienceAction.Pronounce(word))

    fun onCloseLookup() = dispatch(ReadingExperienceAction.CloseLookup)

    fun onCloseFootnote() = dispatch(ReadingExperienceAction.CloseFootnote)

    fun onCloseEditHighlight() = dispatch(ReadingExperienceAction.CloseEditingHighlight)

    fun onDeleteHighlight() = dispatch(ReadingExperienceAction.DeleteEditingHighlight)

    fun onDeleteHighlightById(id: String) = dispatch(ReadingExperienceAction.DeleteHighlight(id))

    fun onJumpToHighlight(locatorJson: String) {
        navigateTo(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    fun onSetHighlightColor(colorArgb: Int) = dispatch(ReadingExperienceAction.SetEditingHighlightColor(colorArgb))

    fun onToggleBookmark() = dispatch(ReadingExperienceAction.ToggleBookmark)

    fun onJumpToBookmark(locatorJson: String) {
        navigateTo(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    fun onDeleteBookmarkById(id: String) = dispatch(ReadingExperienceAction.DeleteBookmark(id))

    fun onOpenSearch() = _state.update {
        it.copy(
            searchSheetVisible = true,
            browseTab = null,
            sheetVisible = false,
            chapterControlsVisible = false,
        )
    }

    fun onCloseSearch() {
        dispatch(ReadingExperienceAction.ClearSearch)
        _state.update { it.copy(searchSheetVisible = false) }
    }

    fun onSearchQueryChange(query: String) = dispatch(ReadingExperienceAction.ChangeSearchQuery(query))

    fun onSubmitSearch() = dispatch(ReadingExperienceAction.SubmitSearch)

    fun onJumpToSearchResult(locatorJson: String) {
        navigateTo(locatorJson)
        _state.update { it.copy(searchSheetVisible = false) }
    }

    fun onReadingResumed() = dispatch(ReadingExperienceAction.Resume)

    fun onReadingPaused() = dispatch(ReadingExperienceAction.Pause)

    private fun openBrowse(tab: BrowseTab) = _state.update {
        it.copy(
            browseTab = tab,
            searchSheetVisible = false,
            sheetVisible = false,
            chapterControlsVisible = false,
        )
    }

    private fun navigateTo(locatorJson: String) {
        dispatch(ReadingExperienceAction.Navigate(ReaderNavigation.Locator(locatorJson)))
    }

    private fun dispatch(action: ReadingExperienceAction) {
        readingExperience.dispatch(action)
    }

    override fun onCleared() {
        readingExperience.close()
    }
}
