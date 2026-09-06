package com.itexpert120.yomu.data.stats

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReadingWriteQueueTest {
    @Test fun boundedFailureRetainsOrderedSnapshotsUntilExplicitRetry() = runBlocking {
        val queue = ReadingWriteQueue(this, 0L)
        var fail = true
        var attempts = 0
        val committed = mutableListOf<String>()
        queue.enqueue {
            attempts++
            check(!fail)
            committed += "completed chapter"
        }
        queue.enqueue { committed += "new position" }
        yield()
        assertEquals(3, attempts)
        assertNotNull(queue.error.value)
        assertEquals(emptyList<String>(), committed)
        fail = false
        queue.retry()
        yield()
        assertEquals(listOf("completed chapter", "new position"), committed)
    }
}
