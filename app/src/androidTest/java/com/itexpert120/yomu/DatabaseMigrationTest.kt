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
            9,
            true,
            YomuDatabase.MIGRATION_1_2,
            YomuDatabase.MIGRATION_2_3,
            YomuDatabase.MIGRATION_3_4,
            YomuDatabase.MIGRATION_4_5,
            YomuDatabase.MIGRATION_5_6,
            YomuDatabase.MIGRATION_6_7,
            YomuDatabase.MIGRATION_7_8,
            YomuDatabase.MIGRATION_8_9,
        ).close()
    }

    private companion object {
        const val DatabaseName = "migration-test"
    }
}
