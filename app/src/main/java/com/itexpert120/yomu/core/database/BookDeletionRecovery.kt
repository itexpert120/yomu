package com.itexpert120.yomu.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.itexpert120.yomu.core.storage.BookDeletionFiles
import java.io.File

internal class BookDeletionRecovery(private val filesDir: File) : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        val livePaths = buildSet {
            db.query("SELECT storagePath, coverImagePath FROM books").use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                    if (!cursor.isNull(1)) add(cursor.getString(1))
                }
            }
        }
        BookDeletionFiles.recover(
            listOf(File(filesDir, "epubs"), File(filesDir, "covers")),
            livePaths,
        )
    }
}
