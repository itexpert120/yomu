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
import org.junit.Assert.assertFalse
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
    fun toggleIsAtomicAndUnknownProgressUsesExactLocator() = runBlocking {
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

        assertTrue(dao.toggle(bookmark))
        assertEquals(1, dao.observeForBook("book-1").first().size)
        assertFalse(dao.toggle(bookmark.copy(id = "bookmark-2")))
        assertTrue(dao.observeForBook("book-1").first().isEmpty())
    }
}
