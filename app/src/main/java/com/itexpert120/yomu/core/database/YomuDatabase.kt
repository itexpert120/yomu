package com.itexpert120.yomu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterReadEntity::class,
        ReaderSettingsEntity::class,
        BookTocEntity::class,
        ReadingDayEntity::class,
        ReadingSessionEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class YomuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao

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

        /** v3 adds per-book reader settings overrides. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_settings` (" +
                            "`bookId` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                            "PRIMARY KEY(`bookId`))",
                )
            }
        }

        /** v4 adds the cached table of contents. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_toc` (" +
                            "`bookId` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                            "PRIMARY KEY(`bookId`))",
                )
            }
        }

        /** v5 adds per-day reading-time tracking for statistics. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_days` (" +
                            "`date` TEXT NOT NULL, `seconds` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`date`))",
                )
            }
        }

        /** v6 adds the reading-session history log. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_sessions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`bookId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                            "`seconds` INTEGER NOT NULL)",
                )
            }
        }

        /** v7 adds user text highlights. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `highlights` (" +
                            "`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                            "`locatorJson` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                            "`colorArgb` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_highlights_bookId` " +
                            "ON `highlights` (`bookId`)",
                )
            }
        }

        /**
         * v8 adds reading-timeline columns (started/finished) to books, backfilled from existing
         * data: started from the earliest logged session (or last-opened as a fallback), finished
         * from last-opened for books already at 100%.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `startedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `finishedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE books SET startedAt = COALESCE(" +
                            "(SELECT MIN(startedAt) FROM reading_sessions " +
                            "WHERE reading_sessions.bookId = books.id), 0)",
                )
                db.execSQL(
                    "UPDATE books SET startedAt = lastOpenedAt " +
                            "WHERE startedAt = 0 AND lastOpenedAt > 0",
                )
                db.execSQL(
                    "UPDATE books SET finishedAt = lastOpenedAt " +
                            "WHERE progress >= 0.999 AND lastOpenedAt > 0",
                )
            }
        }

        /** v9 adds user reading-position bookmarks. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                            "`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                            "`locatorJson` TEXT NOT NULL, `href` TEXT, `chapterTitle` TEXT, " +
                            "`progression` REAL NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` " +
                            "ON `bookmarks` (`bookId`)",
                )
            }
        }
    }
}
