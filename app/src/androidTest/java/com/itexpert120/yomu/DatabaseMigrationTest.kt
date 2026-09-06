package com.itexpert120.yomu

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itexpert120.yomu.core.database.YomuDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(YomuDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateFromVersionOneToCurrent() {
        helper.createDatabase(DatabaseName, 1).close()
        helper.runMigrationsAndValidate(
            DatabaseName,
            YomuDatabase.VERSION,
            true,
            YomuDatabase.MIGRATION_1_2,
            YomuDatabase.MIGRATION_2_3,
            YomuDatabase.MIGRATION_3_4,
            YomuDatabase.MIGRATION_4_5,
            YomuDatabase.MIGRATION_5_6,
            YomuDatabase.MIGRATION_6_7,
            YomuDatabase.MIGRATION_7_8,
            YomuDatabase.MIGRATION_8_9,
            YomuDatabase.MIGRATION_9_10,
            YomuDatabase.MIGRATION_10_11,
            YomuDatabase.MIGRATION_11_12,
            YomuDatabase.MIGRATION_12_13,
            YomuDatabase.MIGRATION_13_14,
            YomuDatabase.MIGRATION_14_15,
        ).close()
    }

    @Test
    fun migrateLegacyVersionThirteenWithoutDroppingAnyData() {
        helper.createDatabase(LegacyVersionThirteenDatabaseName, 13).apply {
            execSQL(
                """
                INSERT INTO `books` (
                    `id`, `title`, `subtitle`, `author`, `description`, `language`, `publisher`,
                    `series`, `coverImagePath`, `storagePath`, `originalUri`,
                    `originalDisplayName`, `sha256`, `fileSizeBytes`, `progress`,
                    `totalProgression`, `locatorJson`, `currentChapterId`, `addedAt`,
                    `lastOpenedAt`, `startedAt`, `finishedAt`
                ) VALUES (
                    'book-id', 'Preserved book', NULL, 'Author', NULL, NULL, NULL,
                    NULL, NULL, 'book.epub', NULL, NULL, 'sha256', 1, 0.5,
                    NULL, NULL, NULL, 1, 2, 3, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_metadata` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO `sync_metadata` (`key`, `value`) VALUES ('device', 'preserved')",
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_records` (
                    `recordKey` TEXT NOT NULL,
                    `bookSha256` TEXT,
                    `kind` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `payloadJson` TEXT,
                    `deleted` INTEGER NOT NULL,
                    `physicalTime` INTEGER NOT NULL,
                    `logicalCounter` INTEGER NOT NULL,
                    `originDeviceId` TEXT NOT NULL,
                    `dirty` INTEGER NOT NULL,
                    PRIMARY KEY(`recordKey`)
                )
                """.trimIndent(),
            )
            execSQL(
                "CREATE INDEX `index_sync_records_bookSha256` " +
                    "ON `sync_records` (`bookSha256`)",
            )
            execSQL(
                "CREATE INDEX `index_sync_records_dirty` ON `sync_records` (`dirty`)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            LegacyVersionThirteenDatabaseName,
            YomuDatabase.VERSION,
            true,
            YomuDatabase.MIGRATION_13_14,
            YomuDatabase.MIGRATION_14_15,
        ).use { database ->
            database.query("SELECT `title` FROM `books` WHERE `id` = 'book-id'").use {
                check(it.moveToFirst())
                check(it.getString(0) == "Preserved book")
            }
            database.query("SELECT `value` FROM `sync_metadata` WHERE `key` = 'device'").use {
                check(it.moveToFirst())
                check(it.getString(0) == "preserved")
            }
        }
    }

    @Test fun lifetimeTotalsBackfillOnlyRecoverableHistory() {
        helper.createDatabase("migration-totals", 14).apply {
            execSQL("INSERT INTO reading_sessions (bookId, startedAt, seconds) VALUES ('old', 1, 60), ('old', 2, 120)")
            execSQL("INSERT INTO reading_days (date, seconds) VALUES ('2026-01-01', 900)")
            close()
        }
        helper.runMigrationsAndValidate("migration-totals", 15, true, YomuDatabase.MIGRATION_14_15).use { db ->
            db.query("SELECT seconds, sessionCount, longestSeconds FROM reading_totals WHERE bookId = 'old'").use {
                check(it.moveToFirst())
                check(it.getLong(0) == 180L)
                check(it.getInt(1) == 2)
                check(it.getLong(2) == 120L)
            }
            db.query("SELECT seconds FROM reading_days").use {
                check(it.moveToFirst())
                check(it.getLong(0) == 900L)
            }
        }
    }

    private companion object {
        const val DatabaseName = "migration-test"
        const val LegacyVersionThirteenDatabaseName = "migration-test-legacy-v13"
    }
}
