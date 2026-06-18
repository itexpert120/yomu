package com.itexpert120.yomu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, ChapterReadEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class YomuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        /** v2 adds chapter read-state tracking; books are preserved. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_reads` (" +
                        "`bookId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`bookId`, `chapterId`))",
                )
            }
        }
    }
}
