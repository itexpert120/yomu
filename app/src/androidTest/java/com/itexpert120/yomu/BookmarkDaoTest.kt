package com.itexpert120.yomu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {
    private lateinit var database: YomuDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun mixedKnownAndUnknownPositionMatchesIndicator() = runBlocking {
        val dao = database.bookmarkDao()
        val bookmark = BookmarkEntity("one", "book", "same", "c#a", "A", -1.0, 1L)
        dao.setBookmarked(bookmark, bookmarked = true)
        dao.setBookmarked(bookmark.copy(id = "two", progression = 0.4), bookmarked = true)
        assertEquals(listOf("one"), dao.observeForBook("book").first().map { it.id })
        dao.setBookmarked(bookmark.copy(id = "two", progression = 0.4), bookmarked = false)
        assertTrue(dao.observeForBook("book").first().isEmpty())
        dao.setBookmarked(bookmark.copy(progression = 0.4), bookmarked = true)
        dao.setBookmarked(bookmark.copy(id = "two", href = "c#b", progression = 0.401), bookmarked = true)
        assertEquals(2, dao.observeForBook("book").first().size)
    }

    @Test
    fun setBookmarkedIsIdempotentAndUnknownProgressUsesExactLocator() = runBlocking {
        val dao = database.bookmarkDao()
        val bookmark = BookmarkEntity(
            id = "bookmark-1",
            bookId = "book-1",
            locatorJson = "{\"href\":\"chapter.xhtml\",\"locations\":{\"progression\":0.4}}",
            href = "chapter.xhtml",
            chapterTitle = "Chapter",
            progression = -1.0,
            createdAt = 1L,
        )

        dao.setBookmarked(bookmark, bookmarked = true)
        dao.setBookmarked(bookmark.copy(id = "bookmark-2"), bookmarked = true)
        assertEquals(1, dao.observeForBook("book-1").first().size)
        dao.setBookmarked(bookmark.copy(id = "bookmark-2"), bookmarked = false)
        dao.setBookmarked(bookmark.copy(id = "bookmark-2"), bookmarked = false)
        assertTrue(dao.observeForBook("book-1").first().isEmpty())
    }
}
