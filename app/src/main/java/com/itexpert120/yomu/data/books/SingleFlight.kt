package com.itexpert120.yomu.data.books

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/** Shares one application-scoped load among all concurrent callers for the same key. */
internal class SingleFlight<K : Any, V>(
    private val scope: CoroutineScope,
) {
    private val inFlight = ConcurrentHashMap<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val candidate = scope.async(start = CoroutineStart.LAZY) { block() }
        val existing = inFlight.putIfAbsent(key, candidate)
        val deferred = if (existing != null) {
            candidate.cancel()
            existing
        } else {
            candidate.invokeOnCompletion { inFlight.remove(key, candidate) }
            candidate.start()
            candidate
        }
        return deferred.await()
    }
}
