package com.itexpert120.yomu.data.books

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {
    @Test
    fun concurrentCallersShareOneLoad() = runBlocking {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val singleFlight = SingleFlight<String, String>(this)

        val first = async {
            singleFlight.run("book") {
                calls.incrementAndGet()
                gate.await()
                "contents"
            }
        }
        while (calls.get() == 0) yield()
        val second = async {
            singleFlight.run("book") {
                calls.incrementAndGet()
                "duplicate"
            }
        }
        yield()
        gate.complete(Unit)

        assertEquals("contents", first.await())
        assertEquals("contents", second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun failedLoadIsRemovedAndCanBeRetried() = runBlocking {
        supervisorScope {
            val calls = AtomicInteger()
            val singleFlight = SingleFlight<String, String>(this)

            val failure = runCatching {
                singleFlight.run("book") {
                    calls.incrementAndGet()
                    error("broken")
                }
            }
            val result = singleFlight.run("book") {
                calls.incrementAndGet()
                "retried"
            }

            assertTrue(failure.isFailure)
            assertEquals("retried", result)
            assertEquals(2, calls.get())
        }
    }
}
