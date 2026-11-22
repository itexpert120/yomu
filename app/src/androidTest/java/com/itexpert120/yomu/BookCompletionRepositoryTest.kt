package com.itexpert120.yomu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.ChapterReading
import com.itexpert120.yomu.data.books.RoomBookRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCompletionRepositoryTest {
    @Test fun coldChaptersAndFailedBulkCompletionAreAtomic() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuDatabase::class.java,
        ).build()
        try {
            val sql = database.openHelper.writableDatabase
            for (id in listOf("a", "b")) {
                sql.execSQL(
                    "INSERT INTO books (id,title,author,storagePath,sha256,fileSizeBytes,progress," +
                        "addedAt,lastOpenedAt,startedAt,finishedAt) VALUES (?, 'Book','Author',?,?,4,0,0,0,0,0)",
                    arrayOf(id, id, id),
                )
            }
            val engine = object : ReaderEngine {
                override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? = null
                override suspend fun tableOfContents(filePath: String) = listOf(
                    ReaderTocItem("chapter#one", "One", "{}", 0),
                    ReaderTocItem("chapter#two", "Two", "{}", 0),
                )
            }
            val dao = database.bookDao()
            val chapters = ChapterReading(dao, database)
            val repository = RoomBookRepository(dao, database, database.highlightDao(), database.bookmarkDao(), database.collectionDao(), chapters, engine, this)
            repository.markRead(BookId("a"))
            val read = chapters.observe(BookId("a")).first()
            assertTrue(read.isRead("chapter#one") && read.isRead("chapter#two"))
            assertEquals(2, read.readCount)
            repository.markUnread(BookId("a"))
            assertEquals(0, chapters.observe(BookId("a")).first().readCount)
            assertTrue(dao.observeChapterProgress("a").first().isEmpty())
            sql.execSQL("CREATE TRIGGER fail_completion BEFORE UPDATE ON books WHEN NEW.id = 'b' BEGIN SELECT RAISE(ABORT, 'injected'); END")
            assertTrue(runCatching { repository.markRead(listOf(BookId("a"), BookId("b"))) }.isFailure)
            assertEquals(0f, dao.getBook("a")!!.progress)
            assertEquals(0, chapters.observe(BookId("a")).first().readCount)
        } finally {
            database.close()
        }
    }
}
