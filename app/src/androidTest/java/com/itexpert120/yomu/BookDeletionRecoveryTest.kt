package com.itexpert120.yomu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itexpert120.yomu.core.database.BookDeletionRecovery
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.storage.BookFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookDeletionRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recoveryUsesCommittedRoomState() {
        val epub = File(temporary.newFolder("epubs"), "book.epub").apply { writeText("book") }
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuDatabase::class.java,
        ).build()
        try {
            val db = database.openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO books (id,title,author,storagePath,sha256,fileSizeBytes,progress," +
                    "addedAt,lastOpenedAt,startedAt,finishedAt,metadataEditedAt,shelfEditedAt) VALUES ('book','Book','Author',?,'hash',4,0,0,0,0,0,0,0)",
                arrayOf(epub.absolutePath),
            )
            val files = BookFiles(temporary.root, { null }, CoroutineScope(Dispatchers.IO))
            val trash = File(epub.parentFile, ".book.epub.123e4567-e89b-12d3-a456-426614174000.deleting")
            check(epub.renameTo(trash))
            BookDeletionRecovery(files).onOpen(db)
            assertEquals("book", epub.readText())
            check(epub.renameTo(trash))
            db.execSQL("DELETE FROM books WHERE id = 'book'")
            BookDeletionRecovery(files).onOpen(db)
            assertFalse(epub.exists())
            assertFalse(trash.exists())
        } finally {
            database.close()
        }
    }
}
