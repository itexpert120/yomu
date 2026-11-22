package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.BookmarkAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBookmarkRepositoryTest {
    private val dao = FakeBookmarkDao()
    private val repository = RoomBookmarkRepository(dao)
    private val book = BookId("book")
    private val page = BookmarkAnchor("c#a", "{page}", 0.4)

    @Test fun bookmarkingTwiceKeepsOneBookmark() = runTest {
        repository.setBookmarked(book, page, "A", bookmarked = true)
        repository.setBookmarked(book, page.copy(locatorJson = "{retry}", progression = 0.401), "A", bookmarked = true)
        val saved = repository.observeForBook(book).first().single()
        assertEquals("{page}", saved.locatorJson)
        assertEquals("c#a", saved.href)
    }

    @Test fun unbookmarkingIsIdempotentAndRemovesEveryMatch() = runTest {
        repository.setBookmarked(book, page, "A", bookmarked = true)
        dao.rows.value += dao.rows.value.single().copy(id = "duplicate")
        repository.setBookmarked(book, page, "A", bookmarked = false)
        repository.setBookmarked(book, page, "A", bookmarked = false)
        assertTrue(repository.observeForBook(book).first().isEmpty())
    }

    @Test fun legacyResourceOnlyBookmarkIsTheSamePage() = runTest {
        dao.rows.value = listOf(BookmarkEntity("legacy", "book", "{old}", "c", null, 0.402, 1L))
        repository.setBookmarked(book, page, "A", bookmarked = true)
        assertEquals(listOf("legacy"), dao.rows.value.map { it.id })
        repository.setBookmarked(book, page, "A", bookmarked = false)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun unknownProgressionRoundTripsAndMatchesByLocator() = runTest {
        val unknown = BookmarkAnchor("c", "{exact}", null)
        repository.setBookmarked(book, unknown, null, bookmarked = true)
        assertEquals(-1.0, dao.rows.value.single().progression, 0.0)
        assertNull(repository.observeForBook(book).first().single().progression)
        repository.setBookmarked(book, unknown.copy(progression = 0.0), null, bookmarked = true)
        assertEquals(1, dao.rows.value.size)
        repository.setBookmarked(book, BookmarkAnchor("c", "{other}", 0.0), null, bookmarked = true)
        assertEquals(2, dao.rows.value.size)
    }

    private class FakeBookmarkDao : BookmarkDao {
        val rows = MutableStateFlow<List<BookmarkEntity>>(emptyList())

        override suspend fun insert(bookmark: BookmarkEntity): Long {
            if (rows.value.any { it.id == bookmark.id }) return -1L
            rows.value += bookmark
            return rows.value.size.toLong()
        }

        override fun observeForBook(bookId: String): Flow<List<BookmarkEntity>> = rows.map { list -> list.filter { it.bookId == bookId } }

        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun positionsForBook(bookId: String): List<BookmarkEntity> = rows.value.filter { it.bookId == bookId }

        override suspend fun deleteForBooks(bookIds: List<String>) {
            rows.value = rows.value.filterNot { it.bookId in bookIds }
        }
    }
}
