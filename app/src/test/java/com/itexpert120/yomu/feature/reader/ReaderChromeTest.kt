package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderChromeTest {
    private val closed = ReaderChromeState()
    private val menuUp = closed.copy(controlsVisible = true)

    @Test
    fun centreTapTogglesTheMenu() {
        assertEquals(menuUp, reduceChrome(closed, ReaderAction.ToggleChrome))
        assertEquals(closed, reduceChrome(menuUp, ReaderAction.ToggleChrome))
    }

    @Test
    fun openingDisplayHidesTheMenu() {
        assertEquals(
            closed.copy(displaySheetVisible = true),
            reduceChrome(menuUp, ReaderAction.OpenDisplay),
        )
    }

    @Test
    fun openingBrowseShowsContentsAndClosesEverythingItCouldStackOn() {
        val busy = ReaderChromeState(controlsVisible = true, displaySheetVisible = true, searchSheetVisible = true)
        assertEquals(
            closed.copy(browseTab = BrowseTab.Contents),
            reduceChrome(busy, ReaderAction.OpenBrowse),
        )
    }

    @Test
    fun openingSearchClosesBrowseDisplayAndTheMenu() {
        val busy = ReaderChromeState(controlsVisible = true, displaySheetVisible = true, browseTab = BrowseTab.Bookmarks)
        assertEquals(
            closed.copy(searchSheetVisible = true),
            reduceChrome(busy, ReaderAction.OpenSearch),
        )
    }

    @Test
    fun everyJumpFromBrowseClosesIt() {
        val browsing = closed.copy(browseTab = BrowseTab.Highlights)
        listOf(
            ReaderAction.JumpToTocEntry("{}"),
            ReaderAction.JumpToBookmark("{}"),
            ReaderAction.JumpToHighlight("{}"),
            ReaderAction.CloseBrowse,
        ).forEach { action ->
            assertNull(action.toString(), reduceChrome(browsing, action).browseTab)
        }
    }

    @Test
    fun switchingBrowseTabsKeepsTheSheetOpen() {
        val browsing = closed.copy(browseTab = BrowseTab.Contents)
        assertEquals(BrowseTab.Bookmarks, reduceChrome(browsing, ReaderAction.SelectBrowseTab(BrowseTab.Bookmarks)).browseTab)
    }

    @Test
    fun jumpingToASearchResultOrClosingSearchClosesIt() {
        val searching = closed.copy(searchSheetVisible = true)
        assertEquals(closed, reduceChrome(searching, ReaderAction.JumpToSearchResult("{}")))
        assertEquals(closed, reduceChrome(searching, ReaderAction.CloseSearch))
    }

    @Test
    fun chapterStepsHideTheMenuButKeepTheDisplaySheet() {
        val state = ReaderChromeState(controlsVisible = true, displaySheetVisible = true)
        val expected = closed.copy(displaySheetVisible = true)
        assertEquals(expected, reduceChrome(state, ReaderAction.NextChapter))
        assertEquals(expected, reduceChrome(state, ReaderAction.PreviousChapter))
    }

    @Test
    fun choosingAChapterFromDisplayKeepsItOpen() {
        val state = closed.copy(displaySheetVisible = true)
        assertSame(state, reduceChrome(state, ReaderAction.SelectChapter("{}")))
    }

    @Test
    fun customThemeReplacesTheDisplaySheet() {
        val display = closed.copy(displaySheetVisible = true)
        val custom = reduceChrome(display, ReaderAction.OpenCustomTheme)
        assertEquals(closed.copy(customThemeSheetVisible = true), custom)
        assertEquals(closed, reduceChrome(custom, ReaderAction.CloseCustomTheme))
        assertEquals(closed, reduceChrome(display, ReaderAction.CloseDisplay))
    }

    @Test
    fun bookActionsLeaveTheChromeAlone() {
        val state = ReaderChromeState(controlsVisible = true, browseTab = BrowseTab.Bookmarks)
        listOf(
            ReaderAction.Back,
            ReaderAction.RetryOpen,
            ReaderAction.ToggleBookmark,
            ReaderAction.UpdateSettings(ReaderSettings()),
            ReaderAction.ApplyCustomTheme(CustomReaderTheme("id", "name", 0L, 0L)),
            ReaderAction.DeleteBookmark("b"),
            ReaderAction.LookUpWord("word"),
            ReaderAction.ChangeSearchQuery("q"),
            ReaderAction.SubmitSearch,
            ReaderAction.CloseLookup,
            ReaderAction.AcceptSyncOffer,
        ).forEach { action ->
            assertSame(action.toString(), state, reduceChrome(state, action))
        }
    }

    @Test
    fun jumpsNavigateTheBook() {
        val expected = ReadingExperienceAction.Navigate(ReaderNavigation.Locator("loc"))
        listOf(
            ReaderAction.SelectChapter("loc"),
            ReaderAction.JumpToTocEntry("loc"),
            ReaderAction.JumpToBookmark("loc"),
            ReaderAction.JumpToHighlight("loc"),
            ReaderAction.JumpToSearchResult("loc"),
        ).forEach { assertEquals(it.toString(), expected, it.toExperienceAction()) }
    }

    @Test
    fun closingSearchClearsIt() {
        assertEquals(ReadingExperienceAction.ClearSearch, ReaderAction.CloseSearch.toExperienceAction())
        assertNull(ReaderAction.OpenSearch.toExperienceAction())
    }
}
