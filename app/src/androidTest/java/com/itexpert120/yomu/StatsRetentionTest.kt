package com.itexpert120.yomu

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.data.stats.StatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatsRetentionTest {
    @Test fun lifetimeStatisticsSurviveRecentHistoryRetention() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), YomuDatabase::class.java).build()
        try {
            val repository = StatsRepository(database.bookDao(), database)
            database.withTransaction {
                repository.recordSession(BookId("old"), 1, 300)
                repeat(10_000) { repository.recordSession(BookId("new"), it + 2L, 1) }
            }
            assertEquals(300L, repository.bookReadingSeconds(BookId("old")).first())
            val stats = repository.stats.first()
            assertEquals(10_001, stats.sessionCount)
            assertEquals(10_300L, stats.totalReadingSeconds)
            assertEquals(300L, stats.longestSessionSeconds)
            assertEquals(1L, stats.averageSessionSeconds)
            assertFalse(stats.historicalSessionDetailIncomplete)
            assertEquals(10_000, database.bookDao().observeRecentSessions(20_000).first().size)
        } finally {
            database.close()
        }
    }
}
