package com.itexpert120.yomu.feature.library

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.feature.collections.StatusChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryShelvesTest {
    private fun book(id: String, progress: Float = 0f, status: ManualStatus? = null) = Book(id = BookId(id), title = id, author = "A", progress = progress, manualStatus = status)

    private val unread = book("unread")
    private val reading = book("reading", progress = 0.4f)
    private val finished = book("finished", progress = 1f)
    private val onHold = book("held", progress = 0.2f, status = ManualStatus.OnHold)
    private val dropped = book("dropped", progress = 1f, status = ManualStatus.Dropped)

    @Test
    fun statusTabsFollowProgressUnlessAManualStatusIsSet() {
        assertTrue(reading.belongsTo(LibraryTabKeys.READING, emptySet()))
        assertTrue(unread.belongsTo(LibraryTabKeys.TO_READ, emptySet()))
        assertTrue(finished.belongsTo(LibraryTabKeys.FINISHED, emptySet()))
        // A manual status takes the book out of its progress tab...
        assertFalse(onHold.belongsTo(LibraryTabKeys.READING, emptySet()))
        assertFalse(dropped.belongsTo(LibraryTabKeys.FINISHED, emptySet()))
        // ...and into its own, while All still lists everything.
        assertTrue(onHold.belongsTo(LibraryTabKeys.ON_HOLD, emptySet()))
        assertTrue(dropped.belongsTo(LibraryTabKeys.DROPPED, emptySet()))
        assertTrue(dropped.belongsTo(LibraryTabKeys.ALL, emptySet()))
    }

    @Test
    fun collectionTabsIgnoreStatus() {
        val key = LibraryTabKeys.collection("c1")
        assertTrue(dropped.belongsTo(key, setOf("c1")))
        assertFalse(reading.belongsTo(key, setOf("c2")))
    }

    @Test
    fun optionalStatusTabsOnlyShowWhenTheyHoldBooksOrAreOpen() {
        val collections = listOf(BookCollection("c1", "Fantasy", 0))
        val tabs = libraryTabs(listOf(unread, reading), collections, mapOf("reading" to setOf("c1")), LibraryTabKeys.ALL)
        assertEquals(
            listOf("all", "reading", "toRead", "finished", LibraryTabKeys.collection("c1")),
            tabs.map { it.key },
        )
        assertEquals(1, tabs.last().count)

        // Emptying the open On hold tab keeps it visible instead of yanking it away.
        val open = libraryTabs(listOf(unread), emptyList(), emptyMap(), LibraryTabKeys.ON_HOLD)
        assertTrue(open.any { it.key == LibraryTabKeys.ON_HOLD && it.count == 0 })
    }

    @Test
    fun statusChoiceIsMixedOnlyWhenBooksDisagree() {
        assertEquals(StatusChoice.Auto, StatusChoice.of(listOf(null, null)))
        assertEquals(StatusChoice.OnHold, StatusChoice.of(listOf(ManualStatus.OnHold, ManualStatus.OnHold)))
        assertEquals(StatusChoice.Mixed, StatusChoice.of(listOf(ManualStatus.OnHold, null)))
        assertEquals(StatusChoice.Auto, StatusChoice.of(emptyList()))
    }

    @Test
    fun userOrderAppliesToEveryTabAndNewOnesFollowIt() {
        val collections = listOf(BookCollection("c1", "Fantasy", 0), BookCollection("c2", "Club", 1))
        val order = listOf(LibraryTabKeys.collection("c1"), LibraryTabKeys.FINISHED, LibraryTabKeys.ALL)
        val tabs = libraryTabs(listOf(finished), collections, emptyMap(), LibraryTabKeys.ALL, order)
        assertEquals(
            listOf(LibraryTabKeys.collection("c1"), "finished", "all", "reading", "toRead", LibraryTabKeys.collection("c2")),
            tabs.map { it.key },
        )
    }

    @Test
    fun hiddenStatusTabsLeaveTheStripButAllAndCollectionsCannotBeHidden() {
        val collections = listOf(BookCollection("c1", "Fantasy", 0))
        val hidden = setOf(LibraryTabKeys.FINISHED, LibraryTabKeys.ALL, LibraryTabKeys.collection("c1"))
        val tabs = libraryTabs(listOf(finished), collections, emptyMap(), LibraryTabKeys.ALL, hidden = hidden)
        assertEquals(
            listOf("all", "reading", "toRead", LibraryTabKeys.collection("c1")),
            tabs.map { it.key },
        )
        assertFalse(isHideableTab(LibraryTabKeys.ALL))
        assertTrue(isHideableTab(LibraryTabKeys.DROPPED))
    }
}
