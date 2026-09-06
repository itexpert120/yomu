package com.itexpert120.yomu.data.stats

import com.itexpert120.yomu.app.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Process-owned, ordered reading writes. Failed heads stay owned until acknowledged. */
@Singleton
class ReadingWriteQueue internal constructor(
    private val scope: CoroutineScope,
    private val retryDelayMillis: Long,
) {
    @Inject constructor(@ApplicationScope scope: CoroutineScope) : this(scope, 500L)

    private val lock = Any()
    private val pending = ArrayDeque<suspend () -> Unit>()
    private var running = false
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun enqueue(write: suspend () -> Unit) = synchronized(lock) {
        pending.addLast(write)
        if (_error.value == null) startLocked()
    }

    fun retry() = synchronized(lock) {
        _error.value = null
        startLocked()
    }

    private fun startLocked() {
        if (running || pending.isEmpty()) return
        running = true
        scope.launch {
            while (true) {
                val write = synchronized(lock) {
                    pending.firstOrNull().also { if (it == null) running = false }
                } ?: return@launch
                var acknowledged = false
                try {
                    for (attempt in 0 until 3) {
                        try {
                            write()
                            acknowledged = true
                            break
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (attempt < 2) delay(retryDelayMillis)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    synchronized(lock) { running = false }
                    throw cancelled
                }
                synchronized(lock) {
                    if (acknowledged) {
                        pending.removeFirst()
                    } else {
                        _error.value = "Reading progress or time wasn't saved. Keep the app open and retry."
                        running = false
                    }
                }
                if (!acknowledged) return@launch
            }
        }
    }
}
