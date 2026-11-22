package com.itexpert120.yomu.data.collections

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun entry(id: String, progress: Float = 0f, status: ManualStatus? = null, vararg collections: String) = CatalogEntry(Book(id = BookId(id), title = id, author = "A", progress = progress, manualStatus = status), collections.toSet())

    private val fantasy = BookCollection("c1", "Fantasy", 0)
    private val club = BookCollection("c2", "Club", 1)

    private val unread = entry("unread")
    private val reading = entry("reading", 0.4f, null, "c1")
    private val finished = entry("finished", 1f)
    private val dropped = entry("dropped", 1f, ManualStatus.Dropped, "c1", "c2")

    private fun catalog(vararg entries: CatalogEntry, collections: List<BookCollection> = listOf(fantasy, club)) = Catalog(entries.toList(), collections)

    private fun LibraryTabs.keys() = visible.map { it.key }

    @Test
    fun tabCountsFollowShelfStatusAndCollections() {
        val tabs = catalog(unread, reading, finished, dropped).tabs(emptyList(), emptySet(), LibraryTabKeys.ALL)
        val counts = tabs.all.associate { it.key to it.count }
        assertEquals(4, counts[LibraryTabKeys.ALL])
        assertEquals(1, counts[LibraryTabKeys.READING])
        assertEquals(1, counts[LibraryTabKeys.TO_READ])
        // The dropped book is at 100% but its manual status wins.
        assertEquals(1, counts[LibraryTabKeys.FINISHED])
        assertEquals(1, counts[LibraryTabKeys.DROPPED])
        assertEquals(0, counts[LibraryTabKeys.ON_HOLD])
        assertEquals(2, counts[LibraryTabKeys.collection("c1")])
        assertEquals(1, counts[LibraryTabKeys.collection("c2")])
        assertTrue(dropped.belongsTo(LibraryTabKeys.collection("c2")))
        assertFalse(dropped.belongsTo(LibraryTabKeys.FINISHED))
    }

    @Test
    fun optionalStatusTabsOnlyShowWhenTheyHoldBooksOrAreOpen() {
        val tabs = catalog(unread, reading, collections = listOf(fantasy)).tabs(emptyList(), emptySet(), LibraryTabKeys.ALL)
        assertEquals(listOf("all", "reading", "toRead", "finished", LibraryTabKeys.collection("c1")), tabs.keys())

        // Emptying the open On hold tab keeps it visible instead of yanking it away.
        val open = catalog(unread).tabs(emptyList(), emptySet(), LibraryTabKeys.ON_HOLD)
        assertTrue(open.visible.any { it.key == LibraryTabKeys.ON_HOLD && it.count == 0 })
        assertEquals(LibraryTabKeys.ON_HOLD, open.selected)
    }

    @Test
    fun aDeletedCollectionsTabFallsBackToAll() {
        val tabs = catalog(reading, collections = listOf(club)).tabs(emptyList(), emptySet(), LibraryTabKeys.collection("c1"))
        assertEquals(LibraryTabKeys.ALL, tabs.selected)
        assertFalse(tabs.all.any { it.key == LibraryTabKeys.collection("c1") })
    }

    @Test
    fun userOrderAppliesToEveryTabAndNewOnesFollowIt() {
        val order = listOf(LibraryTabKeys.collection("c1"), LibraryTabKeys.FINISHED, LibraryTabKeys.ALL)
        val tabs = catalog(finished).tabs(order, emptySet(), LibraryTabKeys.ALL)
        assertEquals(
            listOf(LibraryTabKeys.collection("c1"), "finished", "all", "reading", "toRead", LibraryTabKeys.collection("c2")),
            tabs.keys(),
        )
    }

    @Test
    fun hiddenStatusTabsLeaveTheStripButAllAndCollectionsCannotBeHidden() {
        val hidden = setOf(LibraryTabKeys.FINISHED, LibraryTabKeys.ALL, LibraryTabKeys.collection("c1"))
        val tabs = catalog(finished, collections = listOf(fantasy)).tabs(emptyList(), hidden, LibraryTabKeys.FINISHED)
        assertEquals(listOf("all", "reading", "toRead", LibraryTabKeys.collection("c1")), tabs.keys())
        assertEquals(LibraryTabKeys.ALL, tabs.selected)
        assertFalse(tabs.all.first { it.key == LibraryTabKeys.ALL }.hideable)
        assertTrue(tabs.all.first { it.key == LibraryTabKeys.FINISHED }.hideable)
    }

    @Test
    fun organizeIsTriStateAcrossTheSelection() {
        val organize = catalog(reading, dropped).organize(listOf(BookId("reading"), BookId("dropped")))
        assertEquals(StatusChoice.Mixed, organize.status)
        assertEquals(
            listOf(OrganizeCollection("c1", "Fantasy", Membership.All), OrganizeCollection("c2", "Club", Membership.Some)),
            organize.collections,
        )
    }

    @Test
    fun organizeOneBookIsOnOrOff() {
        val organize = catalog(reading, dropped).organize(listOf(BookId("dropped")))
        assertEquals(StatusChoice.Dropped, organize.status)
        assertEquals(listOf(Membership.All, Membership.All), organize.collections.map { it.membership })
        val none = catalog(unread).organize(listOf(BookId("unread")))
        assertEquals(StatusChoice.Auto, none.status)
        assertEquals(listOf(Membership.None, Membership.None), none.collections.map { it.membership })
    }

    @Test
    fun entryLooksUpABookAndItsCollections() {
        val catalog = catalog(reading)
        assertEquals(setOf("c1"), catalog.entry(BookId("reading"))?.collectionIds)
        assertNull(catalog.entry(BookId("gone")))
        assertEquals(StatusChoice.Auto, catalog.organize(listOf(BookId("gone"))).status)
    }

    @Test
    fun statusChoiceIsMixedOnlyWhenBooksDisagree() {
        assertEquals(StatusChoice.Auto, StatusChoice.of(listOf(null, null)))
        assertEquals(StatusChoice.OnHold, StatusChoice.of(listOf(ManualStatus.OnHold, ManualStatus.OnHold)))
        assertEquals(StatusChoice.Mixed, StatusChoice.of(listOf(ManualStatus.OnHold, null)))
        assertEquals(StatusChoice.Auto, StatusChoice.of(emptyList()))
    }
}
