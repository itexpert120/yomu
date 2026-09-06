package com.itexpert120.yomu.feature.bookdetails

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns incomplete destinations until deletion succeeds, including failed cleanup retries. */
internal class CoverExport {
    private val mutex = Mutex()
    private val pending = mutableSetOf<String>()

    suspend fun export(
        create: () -> String?,
        copy: suspend (String) -> Unit,
        publish: (String) -> Boolean,
        delete: (String) -> Boolean,
    ): Boolean = mutex.withLock {
        fun cleanup() {
            pending.toList().forEach { destination ->
                if (runCatching { delete(destination) }.getOrDefault(false)) pending.remove(destination)
            }
        }
        cleanup()
        try {
            val destination = create() ?: return@withLock false
            pending.add(destination)
            copy(destination)
            currentCoroutineContext().ensureActive()
            check(publish(destination)) { "Couldn't publish cover" }
            pending.remove(destination)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            withContext(NonCancellable) { cleanup() }
        }
    }
}
