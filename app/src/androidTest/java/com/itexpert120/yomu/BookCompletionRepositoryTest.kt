package com.itexpert120.yomu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.core.storage.BookFiles
import com.itexpert120.yomu.data.books.ChapterReading
import com.itexpert120.yomu.data.books.PublicationIndex
import com.itexpert120.yomu.data.books.RoomBookRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCompletionRepositoryTest {
    @Test fun indexedChaptersUnindexedBooksAndFailedBulkCompletion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuDatabase::class.java,
        ).build()
        try {
            val sql = database.openHelper.writableDatabase
            for (id in listOf("a", "b", "c")) {
                sql.execSQL(
                    "INSERT INTO books (id,title,author,storagePath,sha256,fileSizeBytes,progress," +
                        "addedAt,lastOpenedAt,startedAt,finishedAt) VALUES (?, 'Book','Author',?,?,4,0,0,0,0,0)",
                    arrayOf(id, id, id),
                )
            }
            var parses = 0
            val engine = object : ReaderEngine {
                override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? = null
                override suspend fun tableOfContents(filePath: String): List<ReaderTocItem>? {
                    parses++
                    return null
                }
            }
            val dao = database.bookDao()
            val chapters = ChapterReading(dao, database)
            val publications = PublicationIndex(dao, database, engine, this)
            val repository = RoomBookRepository(dao, database, database.highlightDao(), database.bookmarkDao(), database.collectionDao(), chapters, BookFiles(ApplicationProvider.getApplicationContext(), this), publications)
            publications.remember(
                BookId("a"),
                ReaderPublicationCache(toc = listOf(ReaderTocItem("chapter#one", "One", "{}", 0), ReaderTocItem("chapter#two", "Two", "{}", 0))),
            )
            repository.markRead(BookId("a"))
            val read = chapters.observe(BookId("a")).first()
            assertTrue(read.isRead("chapter#one") && read.isRead("chapter#two"))
            assertEquals(2, read.readCount)
            // A book the index knows nothing about is finished without chapter rows, never parsed.
            repository.markRead(BookId("c"))
            assertEquals(1f, dao.getBook("c")!!.progress)
            assertEquals(0, chapters.observe(BookId("c")).first().readCount)
            assertEquals(0, parses)
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
