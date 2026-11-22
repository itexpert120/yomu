package com.itexpert120.yomu.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.itexpert120.yomu.core.storage.BookFiles

/** Settles book files against the committed rows each time the database opens. */
internal class BookDeletionRecovery(private val files: BookFiles) : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        val livePaths = buildSet {
            db.query("SELECT storagePath, coverImagePath FROM books").use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                    if (!cursor.isNull(1)) add(cursor.getString(1))
                }
            }
        }
        files.reconcile(livePaths)
    }
}
