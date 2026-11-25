package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction

/** Everything the person can do in the reader, raised by the reader screen through one `onAction`. */
sealed interface ReaderAction {
    data object Back : ReaderAction
    data object RetryOpen : ReaderAction

    /** A centre tap on the page: shows or hides the reader menu. */
    data object ToggleChrome : ReaderAction

    // Display sheet (controls + display settings) and the custom-theme sheet it leads to.
    data object OpenDisplay : ReaderAction
    data object CloseDisplay : ReaderAction
    data class SelectChapter(val locatorJson: String) : ReaderAction

    /** The controls panel's slider: go to [progression] (0..1) of the chapter being read. */
    data class SeekInChapter(val progression: Double) : ReaderAction
    data object NextChapter : ReaderAction
    data object PreviousChapter : ReaderAction
    data class UpdateSettings(val settings: ReaderSettings) : ReaderAction
    data object ResetSettings : ReaderAction
    data object OpenCustomTheme : ReaderAction
    data object CloseCustomTheme : ReaderAction
    data class ApplyCustomTheme(val theme: CustomReaderTheme) : ReaderAction
    data class SaveCustomTheme(val name: String) : ReaderAction
    data class DeleteCustomTheme(val id: String) : ReaderAction

    /** "Get fonts" in the font row: the font library, over the reader (handled by the route). */
    data object OpenFontLibrary : ReaderAction

    // Browse sheet (Contents / Bookmarks / Highlights).
    data object OpenBrowse : ReaderAction
    data class SelectBrowseTab(val tab: BrowseTab) : ReaderAction
    data object CloseBrowse : ReaderAction
    data class JumpToTocEntry(val locatorJson: String) : ReaderAction
    data class JumpToBookmark(val locatorJson: String) : ReaderAction
    data class JumpToHighlight(val locatorJson: String) : ReaderAction
    data class DeleteBookmark(val id: String) : ReaderAction
    data class DeleteHighlight(val id: String) : ReaderAction

    // In-book search.
    data object OpenSearch : ReaderAction
    data object CloseSearch : ReaderAction
    data class ChangeSearchQuery(val query: String) : ReaderAction
    data object SubmitSearch : ReaderAction
    data class JumpToSearchResult(val locatorJson: String) : ReaderAction

    // Word lookup and footnote popups.
    data class LookUpWord(val word: String) : ReaderAction
    data object RetryLookup : ReaderAction
    data object LookupBack : ReaderAction
    data class Pronounce(val word: String) : ReaderAction
    data object CloseLookup : ReaderAction
    data class SelectLookupTab(val tab: LookupTab) : ReaderAction

    /** The book's language has no entry for the word: look it up in the English dictionary. */
    data object LookUpInEnglish : ReaderAction
    data object RetryWikipedia : ReaderAction

    /** Hands [text] to the device's translation app. */
    data class Translate(val text: String) : ReaderAction

    /** Opens in-book search for [text] (from the look-up sheet or the selection menu). */
    data class SearchInBook(val text: String) : ReaderAction
    data object CloseFootnote : ReaderAction

    // Annotations on the current page.
    data object ToggleBookmark : ReaderAction
    data class SetHighlightColor(val colorArgb: Int) : ReaderAction
    data class SetHighlightStyle(val style: HighlightStyle) : ReaderAction

    /** The note on highlight [id]; blank clears it. Carries the id so a save that lands after the sheet closed still finds it. */
    data class SetHighlightNote(val id: String, val note: String) : ReaderAction
    data object CopyEditingHighlight : ReaderAction
    data object ShareEditingHighlight : ReaderAction
    data object DeleteEditingHighlight : ReaderAction
    data object CloseHighlightEditor : ReaderAction

    // Notices.
    data object RetrySettings : ReaderAction
    data object RetryAnnotations : ReaderAction
    data object AcceptSyncOffer : ReaderAction
    data object DismissSyncOffer : ReaderAction

    /** Puts back the highlight or bookmark the undo notice [key] is about. */
    data class UndoDelete(val key: Long) : ReaderAction
    data class DismissUndo(val key: Long) : ReaderAction

    /** Export / import of the book's highlights and notes (Browse → Highlights). */
    data class Annotations(val action: AnnotationTransferAction) : ReaderAction
}

/** Which reader surfaces are up: the menu (top bar, toolbar, system bars) and at most one sheet. */
data class ReaderChromeState(
    val controlsVisible: Boolean = false,
    val displaySheetVisible: Boolean = false,
    val customThemeSheetVisible: Boolean = false,
    val browseTab: BrowseTab? = null,
    val searchSheetVisible: Boolean = false,
)

/**
 * The reader's sheet/menu rules. Opening a sheet from the menu hides the menu and any sheet it could
 * stack on; jumping from a sheet closes that sheet so the destination page is visible.
 */
internal fun reduceChrome(state: ReaderChromeState, action: ReaderAction): ReaderChromeState = when (action) {
    ReaderAction.ToggleChrome -> state.copy(controlsVisible = !state.controlsVisible)

    ReaderAction.OpenDisplay -> state.copy(displaySheetVisible = true, controlsVisible = false)
    ReaderAction.CloseDisplay -> state.copy(displaySheetVisible = false)
    ReaderAction.NextChapter, ReaderAction.PreviousChapter -> state.copy(controlsVisible = false)
    ReaderAction.OpenCustomTheme -> state.copy(customThemeSheetVisible = true, displaySheetVisible = false)
    ReaderAction.CloseCustomTheme -> state.copy(customThemeSheetVisible = false)

    ReaderAction.OpenBrowse -> state.copy(
        browseTab = BrowseTab.Contents,
        searchSheetVisible = false,
        displaySheetVisible = false,
        controlsVisible = false,
    )
    is ReaderAction.SelectBrowseTab -> state.copy(browseTab = action.tab)
    ReaderAction.CloseBrowse,
    is ReaderAction.JumpToTocEntry,
    is ReaderAction.JumpToBookmark,
    is ReaderAction.JumpToHighlight,
    -> state.copy(browseTab = null)

    ReaderAction.OpenSearch -> state.copy(
        searchSheetVisible = true,
        browseTab = null,
        displaySheetVisible = false,
        controlsVisible = false,
    )
    is ReaderAction.SearchInBook -> state.copy(
        searchSheetVisible = true,
        browseTab = null,
        displaySheetVisible = false,
        controlsVisible = false,
    )
    ReaderAction.CloseSearch, is ReaderAction.JumpToSearchResult -> state.copy(searchSheetVisible = false)

    else -> state
}

/** The book-scoped work an action asks of the reading experience, if any. */
internal fun ReaderAction.toExperienceAction(): ReadingExperienceAction? = when (this) {
    ReaderAction.RetryOpen -> ReadingExperienceAction.RetryOpen
    is ReaderAction.SelectChapter -> navigateTo(locatorJson)
    is ReaderAction.SeekInChapter -> ReadingExperienceAction.Navigate(ReaderNavigation.ChapterProgression(progression))
    ReaderAction.NextChapter -> ReadingExperienceAction.Navigate(ReaderNavigation.NextChapter)
    ReaderAction.PreviousChapter -> ReadingExperienceAction.Navigate(ReaderNavigation.PreviousChapter)
    is ReaderAction.UpdateSettings -> ReadingExperienceAction.ApplySettings(settings)
    ReaderAction.ResetSettings -> ReadingExperienceAction.ResetSettings
    is ReaderAction.JumpToTocEntry -> navigateTo(locatorJson)
    is ReaderAction.JumpToBookmark -> navigateTo(locatorJson)
    is ReaderAction.JumpToHighlight -> navigateTo(locatorJson)
    is ReaderAction.DeleteBookmark -> ReadingExperienceAction.DeleteBookmark(id)
    is ReaderAction.DeleteHighlight -> ReadingExperienceAction.DeleteHighlight(id)
    ReaderAction.CloseSearch -> ReadingExperienceAction.ClearSearch
    is ReaderAction.ChangeSearchQuery -> ReadingExperienceAction.ChangeSearchQuery(query)
    ReaderAction.SubmitSearch -> ReadingExperienceAction.SubmitSearch
    is ReaderAction.JumpToSearchResult -> navigateTo(locatorJson)
    is ReaderAction.LookUpWord -> ReadingExperienceAction.LookUpWord(word)
    ReaderAction.RetryLookup -> ReadingExperienceAction.RetryLookup
    ReaderAction.LookupBack -> ReadingExperienceAction.LookupBack
    is ReaderAction.Pronounce -> ReadingExperienceAction.Pronounce(word)
    ReaderAction.CloseLookup -> ReadingExperienceAction.CloseLookup
    is ReaderAction.SelectLookupTab -> ReadingExperienceAction.SelectLookupTab(tab)
    ReaderAction.LookUpInEnglish -> ReadingExperienceAction.LookUpInEnglish
    ReaderAction.RetryWikipedia -> ReadingExperienceAction.RetryWikipedia
    is ReaderAction.Translate -> ReadingExperienceAction.Translate(text)
    is ReaderAction.SearchInBook -> ReadingExperienceAction.SearchFor(text)
    ReaderAction.CloseFootnote -> ReadingExperienceAction.CloseFootnote
    ReaderAction.ToggleBookmark -> ReadingExperienceAction.ToggleBookmark
    is ReaderAction.SetHighlightColor -> ReadingExperienceAction.SetEditingHighlightColor(colorArgb)
    is ReaderAction.SetHighlightStyle -> ReadingExperienceAction.SetEditingHighlightStyle(style)
    is ReaderAction.SetHighlightNote -> ReadingExperienceAction.SetHighlightNote(id, note)
    ReaderAction.CopyEditingHighlight -> ReadingExperienceAction.CopyEditingHighlight
    ReaderAction.ShareEditingHighlight -> ReadingExperienceAction.ShareEditingHighlight
    ReaderAction.DeleteEditingHighlight -> ReadingExperienceAction.DeleteEditingHighlight
    ReaderAction.CloseHighlightEditor -> ReadingExperienceAction.CloseEditingHighlight
    ReaderAction.RetrySettings -> ReadingExperienceAction.RetrySettings
    ReaderAction.RetryAnnotations -> ReadingExperienceAction.RetryAnnotations
    ReaderAction.AcceptSyncOffer -> ReadingExperienceAction.AcceptSyncOffer
    ReaderAction.DismissSyncOffer -> ReadingExperienceAction.DismissSyncOffer
    is ReaderAction.UndoDelete -> ReadingExperienceAction.UndoDelete(key)
    is ReaderAction.DismissUndo -> ReadingExperienceAction.DismissUndo(key)

    // Chrome-only, navigation-only, or handled by the view model itself.
    ReaderAction.Back,
    ReaderAction.OpenFontLibrary,
    ReaderAction.ToggleChrome,
    ReaderAction.OpenDisplay,
    ReaderAction.CloseDisplay,
    ReaderAction.OpenCustomTheme,
    ReaderAction.CloseCustomTheme,
    is ReaderAction.ApplyCustomTheme,
    is ReaderAction.SaveCustomTheme,
    is ReaderAction.DeleteCustomTheme,
    ReaderAction.OpenBrowse,
    is ReaderAction.SelectBrowseTab,
    ReaderAction.CloseBrowse,
    ReaderAction.OpenSearch,
    is ReaderAction.Annotations,
    -> null
}

private fun navigateTo(locatorJson: String) = ReadingExperienceAction.Navigate(ReaderNavigation.Locator(locatorJson))
