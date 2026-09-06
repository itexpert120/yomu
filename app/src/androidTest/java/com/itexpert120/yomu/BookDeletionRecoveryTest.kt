package com.itexpert120.yomu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itexpert120.yomu.core.database.BookDeletionRecovery
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.storage.BookDeletionFiles
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
                    "addedAt,lastOpenedAt,startedAt,finishedAt) VALUES ('book','Book','Author',?,'hash',4,0,0,0,0,0)",
                arrayOf(epub.absolutePath),
            )
            BookDeletionFiles.stage(listOf(epub))
            BookDeletionRecovery(temporary.root).onOpen(db)
            assertEquals("book", epub.readText())
            val staged = BookDeletionFiles.stage(listOf(epub))
            db.execSQL("DELETE FROM books WHERE id = 'book'")
            BookDeletionRecovery(temporary.root).onOpen(db)
            assertFalse(epub.exists())
            assertFalse(staged.single().second.exists())
        } finally {
            database.close()
        }
    }
}
