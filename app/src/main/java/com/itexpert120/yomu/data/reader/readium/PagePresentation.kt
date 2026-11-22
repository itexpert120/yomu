package com.itexpert120.yomu.data.reader.readium

import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderRenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** The page view a [PagePresentation] reveals and turns. */
internal interface PageSurface {
    /** Non-critical view setup redone before every styling pass (e.g. scrollbars). */
    fun prepareStyling()

    /** Applies all layout-affecting styling to the shown resource; false if it never landed. */
    suspend fun applyCriticalStyle(): Boolean

    /** Whether the shown resource has rendered content (false for a blank page). */
    suspend fun hasContent(): Boolean

    /** Waits for the next pre-draw, so the cover lifts only after layout; false if detached. */
    suspend fun awaitPreDraw(): Boolean

    /** Runs once a resource is revealed: work deliberately kept after the first stable frame. */
    fun onRevealed()

    fun turn(forward: Boolean, animated: Boolean)

    suspend fun awaitFrame()
}

/**
 * When the page is shown and how it turns. The page stays covered from a chapter change (or a
 * layout-affecting settings change) until the new resource is styled, has content and has been
 * laid out; a watchdog shows a stalled transition as it is. Tap turns run one at a time.
 *
 * Every transition gets a new render generation; a reveal for an older generation, or for a
 * resource/surface that is no longer current, never shows the page.
 */
internal class PagePresentation(
    private val scope: CoroutineScope,
    /** Href of the resource the reader is currently positioned in. */
    private val currentHref: () -> String?,
    private val animationsEnabled: () -> Boolean,
    private val onRevealStalled: (generation: Long) -> Unit = {},
    private val revealTimeoutMillis: Long = REVEAL_TIMEOUT_MS,
    private val turnSettleTimeoutMillis: Long = TURN_SETTLE_TIMEOUT_MS,
) {
    private val mutableRenderState = MutableStateFlow<ReaderRenderState>(ReaderRenderState.Opening)

    /** Atomic first-frame state; content is shown only from [ReaderRenderState.Ready]. */
    val renderState: StateFlow<ReaderRenderState> = mutableRenderState.asStateFlow()

    private var surface: PageSurface? = null
    private var generation = 0L
    private var styleJob: Job? = null
    private var revealWatchdog: Job? = null

    private var turnSettle: Job? = null

    // A queued turn waiting for the next frame; page events in that window must not replay it again.
    private var turnReplay: Job? = null
    private var queuedTurns = 0

    /** The surface now rendering the book; reveals for an earlier surface are dropped. */
    fun attach(surface: PageSurface) {
        this.surface = surface
    }

    /** Covers the page during a chapter change so it is revealed only once the new resource is re-styled. */
    fun cover(forward: Boolean = true) {
        generation++
        mutableRenderState.value = ReaderRenderState.Transitioning(forward)
        styleJob?.cancel()
        revealWatchdog?.cancel()
        val expected = generation
        revealWatchdog = scope.launch {
            delay(revealTimeoutMillis)
            onRevealStalled(expected)
            // Shows a transition that never finished its checks, as it is. The generation is kept,
            // so a late reveal for the same transition still completes normally.
            if (expected == generation && mutableRenderState.value is ReaderRenderState.Transitioning) {
                mutableRenderState.value = ReaderRenderState.Ready(currentHref())
            }
        }
    }

    /**
     * Styles the shown resource and reveals it when ready. All layout-affecting mutations go through
     * this one idempotent pass, which keeps locator, settings, and page-loaded triggers from racing
     * one another on first paint; a newer call supersedes an older one.
     */
    fun revealWhenReady() {
        styleJob?.cancel()
        val expectedGeneration = generation
        val expectedHref = currentHref()
        val expectedSurface = surface ?: return
        styleJob = scope.launch {
            ReaderOpenTrace.mark("reader.critical-bootstrap-start")
            expectedSurface.prepareStyling()
            val isCurrent = { surface === expectedSurface && expectedHref == currentHref() }
            val checks = listOf(
                expectedSurface::applyCriticalStyle,
                expectedSurface::hasContent,
                expectedSurface::awaitPreDraw,
            )
            for (check in checks) {
                if (expectedGeneration != generation || !isCurrent() || !check()) return@launch
                currentCoroutineContext().ensureActive()
            }
            if (expectedGeneration != generation || !isCurrent()) return@launch
            mutableRenderState.value = ReaderRenderState.Ready(expectedHref)
            revealWatchdog?.cancel()
            ReaderOpenTrace.mark("reader.pre-draw")
            ReaderOpenTrace.mark("reader.final-reveal")
            // Gesture work is deliberately after the first stable frame.
            expectedSurface.onRevealed()
        }
    }

    /** Lifts a cover left by a failed jump: re-reveals whatever the surface shows now. */
    fun recover() {
        cover()
        revealWhenReady()
    }

    // Readium computes each turn from the WebView's scroll position at that instant. A tap that lands
    // mid-animation measures a half-scrolled page, and one that lands while the next resource is still
    // settling reads as "can't scroll" and jumps a whole chapter; pages passed that way also never
    // report a settled page, so the chapter left behind keeps an early progress. Run turns one at a
    // time instead: taps made during a turn are queued and replayed once it settles. Rapid tapping is
    // how readers catch up to a position (e.g. from another device), so every tap counts and queued
    // turns skip the slide animation to catch up quickly.
    fun requestTurn(forward: Boolean) {
        val step = if (forward) 1 else -1
        if (turnSettle?.isActive == true) {
            queuedTurns = if (queuedTurns == 0 || queuedTurns.sign == step) {
                (queuedTurns + step).coerceIn(-MAX_QUEUED_TURNS, MAX_QUEUED_TURNS)
            } else {
                // Reversing direction cancels the turns still waiting in the old direction.
                0
            }
            return
        }
        startTurn(forward, animated = animationsEnabled())
    }

    /** The surface reported a settled page: replays the next queued turn, if any. */
    fun onTurnSettled() {
        if (turnReplay?.isActive == true) return
        val settle = turnSettle ?: return
        turnSettle = null
        settle.cancel()
        val queued = queuedTurns
        if (queued == 0) return
        queuedTurns -= queued.sign
        // One follow-up tap (typically back, then forward again) keeps the slide: replaying it as an
        // instant jump from a page that was still settling read as a jerk. Only a backlog of several
        // taps skips the animation to catch up. Start on the next frame so the pager has settled.
        val animated = abs(queued) == 1 && animationsEnabled()
        val frameSource = surface
        turnReplay = scope.launch {
            frameSource?.awaitFrame()
            turnReplay = null
            startTurn(forward = queued > 0, animated = animated)
        }
        // Taps during the one-frame wait keep queueing behind this turn.
        turnSettle = turnReplay
    }

    /** An explicit jump supersedes any page turns still queued from taps. */
    fun cancelTurns() {
        queuedTurns = 0
        turnReplay?.cancel()
        turnReplay = null
        turnSettle?.cancel()
        turnSettle = null
    }

    fun close() {
        styleJob?.cancel()
        styleJob = null
        revealWatchdog?.cancel()
        cancelTurns()
    }

    private fun startTurn(forward: Boolean, animated: Boolean) {
        val target = surface ?: return
        // Fallback for turns that produce no page event (e.g. at the very start or end of the book).
        turnSettle = scope.launch {
            delay(turnSettleTimeoutMillis)
            onTurnSettled()
        }
        target.turn(forward, animated)
    }

    private companion object {
        const val MAX_QUEUED_TURNS = 50
        const val TURN_SETTLE_TIMEOUT_MS = 900L

        // Safety cap: reveal a transitioning chapter even if its onPageLoaded never arrives, so a
        // failed/stalled load can't leave the reader stuck behind the transition cover.
        const val REVEAL_TIMEOUT_MS = 3000L
    }
}
