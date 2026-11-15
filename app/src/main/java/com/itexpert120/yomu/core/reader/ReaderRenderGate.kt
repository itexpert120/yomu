package com.itexpert120.yomu.core.reader

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The only publisher of successful readiness; all rendering paths use the same ordered checks. */
internal class ReaderRenderGate {
    private val mutableState = MutableStateFlow<ReaderRenderState>(ReaderRenderState.Opening)
    val state = mutableState.asStateFlow()
    var generation = 0L
        private set

    fun transition(forward: Boolean): Long {
        generation++
        mutableState.value = ReaderRenderState.Transitioning(forward)
        return generation
    }

    /**
     * Shows a transition that never finished its checks, as it is. The generation is kept, so a
     * late [reveal] for the same transition still completes normally.
     */
    fun revealStalled(expected: Long, href: String?) {
        if (expected == generation && mutableState.value is ReaderRenderState.Transitioning) {
            mutableState.value = ReaderRenderState.Ready(href)
        }
    }

    suspend fun reveal(
        expected: Long,
        href: String?,
        isCurrent: () -> Boolean,
        style: suspend () -> Boolean,
        content: suspend () -> Boolean,
        preDraw: suspend () -> Boolean,
    ): Boolean {
        for (check in listOf(style, content, preDraw)) {
            if (expected != generation || !isCurrent() || !check()) return false
            currentCoroutineContext().ensureActive()
        }
        if (expected != generation || !isCurrent()) return false
        mutableState.value = ReaderRenderState.Ready(href)
        return true
    }
}
