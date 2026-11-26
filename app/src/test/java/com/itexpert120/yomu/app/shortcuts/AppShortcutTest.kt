package com.itexpert120.yomu.app.shortcuts

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShortcutTest {
    @Test
    fun intentsParseToTheirShortcut() {
        assertEquals(AppShortcut.Continue("b1"), AppShortcut.parse(AppShortcut.ACTION_CONTINUE, "b1"))
        assertEquals(AppShortcut.SearchLibrary, AppShortcut.parse(AppShortcut.ACTION_SEARCH, null))
        assertEquals(AppShortcut.ImportBooks, AppShortcut.parse(AppShortcut.ACTION_IMPORT, null))
        assertEquals(AppShortcut.Statistics, AppShortcut.parse(AppShortcut.ACTION_STATISTICS, null))
    }

    @Test
    fun otherIntentsAreNotShortcuts() {
        assertNull(AppShortcut.parse("android.intent.action.VIEW", null))
        assertNull(AppShortcut.parse("android.intent.action.MAIN", null))
        assertNull(AppShortcut.parse(null, null))
        // Continue without a book has nothing to open.
        assertNull(AppShortcut.parse(AppShortcut.ACTION_CONTINUE, " "))
    }

    @Test
    fun savedStateRoundTrips() {
        listOf(AppShortcut.Continue("b|1"), AppShortcut.SearchLibrary, AppShortcut.ImportBooks, AppShortcut.Statistics).forEach {
            assertEquals(it, AppShortcut.decode(it.encode()))
        }
    }

    @Test
    fun continueIsTheMostRecentBookInProgress() {
        val books = listOf(
            book("older", progress = 0.4f, lastOpenedAt = 10),
            book("newer", progress = 0.2f, lastOpenedAt = 20, cover = "/covers/newer.webp"),
            book("finished", progress = 1f, lastOpenedAt = 30),
            book("unopened", progress = 0f, lastOpenedAt = 0),
        )
        assertEquals(ContinueTarget("newer", "Title newer", "/covers/newer.webp"), continueTarget(books))
    }

    @Test
    fun noContinueWithoutABookInProgress() {
        assertNull(continueTarget(emptyList()))
        assertNull(continueTarget(listOf(book("done", progress = 1f, lastOpenedAt = 5))))
        assertNull(continueTarget(listOf(book("held", progress = 0.5f, lastOpenedAt = 5, manual = ManualStatus.OnHold))))
    }

    @Test
    fun pageTurnsKeepTheSameTarget() {
        val before = continueTarget(listOf(book("a", progress = 0.30f, lastOpenedAt = 5)))
        val after = continueTarget(listOf(book("a", progress = 0.31f, lastOpenedAt = 6)))
        assertEquals(before, after)
    }

    @Test
    fun longTitlesAreShortenedAtAWord() {
        assertEquals("Emma", fitTitle("Emma", 16))
        assertEquals("Pride and…", fitTitle("Pride and Prejudice", 16))
        assertEquals("Supercalifragil…", fitTitle("Supercalifragilisticexpialidocious", 16))
        assertEquals("Dune: Part…", fitTitle("Dune: Part Two, The Sequel", 12))
    }

    private fun book(id: String, progress: Float, lastOpenedAt: Long, cover: String? = null, manual: ManualStatus? = null) = Book(
        id = BookId(id),
        title = "Title $id",
        author = "Author",
        coverImagePath = cover,
        progress = progress,
        lastOpenedAt = lastOpenedAt,
        manualStatus = manual,
    )
}
