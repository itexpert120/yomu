package com.itexpert120.yomu.data.reader.readium

import com.itexpert120.yomu.core.reader.ReaderRenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagePresentationTest {
    private class FakeSurface : PageSurface {
        val turns = mutableListOf<Pair<Boolean, Boolean>>()
        val checks = mutableListOf<String>()
        var revealed = 0
        var style: suspend () -> Boolean = { true }
        var content: suspend () -> Boolean = { true }

        override fun prepareStyling() = Unit

        override suspend fun applyCriticalStyle(): Boolean {
            checks += "style"
            return style()
        }

        override suspend fun hasContent(): Boolean {
            checks += "content"
            return content()
        }

        override suspend fun awaitPreDraw(): Boolean {
            checks += "draw"
            return true
        }

        override fun onRevealed() {
            revealed++
        }

        override fun turn(forward: Boolean, animated: Boolean) {
            turns += forward to animated
        }

        override suspend fun awaitFrame() = delay(FRAME_MS)
    }

    private var href: String? = "c1"

    private fun TestScope.presentation(scope: CoroutineScope = backgroundScope) = PagePresentation(
        scope = scope,
        currentHref = { href },
        animationsEnabled = { true },
    )

    @Test fun reversedTapClearsTheQueue() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        presentation.requestTurn(forward = true)
        presentation.requestTurn(forward = true)
        presentation.requestTurn(forward = false)
        presentation.onTurnSettled()
        advanceTimeBy(SETTLE_MS * 2)

        assertEquals(listOf(true to true), surface.turns)
    }

    @Test fun oneQueuedTapReplaysAnimatedAfterAFrame() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        presentation.requestTurn(forward = false)
        presentation.requestTurn(forward = false)
        presentation.onTurnSettled()
        runCurrent()
        assertEquals(1, surface.turns.size)
        // Page events during the one-frame wait don't replay the turn twice.
        presentation.onTurnSettled()
        advanceTimeBy(FRAME_MS + 1)

        assertEquals(listOf(false to true, false to true), surface.turns)
    }

    @Test fun backlogOfThreeReplaysUnanimatedUntilOneIsLeft() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        repeat(4) { presentation.requestTurn(forward = true) }
        repeat(3) {
            presentation.onTurnSettled()
            advanceTimeBy(FRAME_MS + 1)
        }

        assertEquals(listOf(true to true, true to false, true to false, true to true), surface.turns)
    }

    @Test fun aTurnWithoutAPageEventSettlesOnTimeout() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        presentation.requestTurn(forward = true)
        presentation.requestTurn(forward = true)
        advanceTimeBy(SETTLE_MS + FRAME_MS + 1)

        assertEquals(listOf(true to true, true to true), surface.turns)
    }

    @Test fun aJumpCancelsQueuedTurns() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        repeat(3) { presentation.requestTurn(forward = true) }
        presentation.cancelTurns()
        presentation.onTurnSettled()
        advanceTimeBy(SETTLE_MS * 2)
        assertEquals(1, surface.turns.size)

        // The next tap turns straight away.
        presentation.requestTurn(forward = true)
        assertEquals(2, surface.turns.size)
    }

    @Test fun revealRunsAllChecksInOrderThenShowsThePage() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }

        presentation.revealWhenReady()
        runCurrent()

        assertEquals(listOf("style", "content", "draw"), surface.checks)
        assertEquals(ReaderRenderState.Ready("c1"), presentation.renderState.value)
        assertEquals(1, surface.revealed)
    }

    @Test fun failedStylingSkipsLaterChecksAndStaysCovered() = runTest {
        val surface = FakeSurface().apply { style = { false } }
        val presentation = presentation().apply { attach(surface) }

        presentation.cover(forward = true)
        presentation.revealWhenReady()
        runCurrent()

        assertEquals(listOf("style"), surface.checks)
        assertEquals(ReaderRenderState.Transitioning(true), presentation.renderState.value)
    }

    @Test fun stalledRevealIsShownByTheWatchdog() = runTest {
        val surface = FakeSurface().apply { content = { awaitCancellation() } }
        val presentation = presentation().apply { attach(surface) }

        presentation.cover(forward = false)
        presentation.revealWhenReady()
        href = "c2"
        advanceTimeBy(REVEAL_TIMEOUT_MS - 1)
        assertTrue(presentation.renderState.value is ReaderRenderState.Transitioning)

        advanceTimeBy(2)
        assertEquals(ReaderRenderState.Ready("c2"), presentation.renderState.value)
    }

    @Test fun staleGenerationIsNeverRevealed() = runTest {
        val surface = FakeSurface()
        val presentation = presentation().apply { attach(surface) }
        presentation.cover(forward = true)
        // A newer transition starts while the first one is being styled.
        surface.style = {
            presentation.cover(forward = false)
            true
        }

        presentation.revealWhenReady()
        advanceTimeBy(REVEAL_TIMEOUT_MS - 1)

        assertEquals(listOf("style"), surface.checks)
        assertEquals(ReaderRenderState.Transitioning(false), presentation.renderState.value)
    }

    @Test fun revealForAReplacedSurfaceOrResourceIsDropped() = runTest {
        val old = FakeSurface()
        val presentation = presentation().apply { attach(old) }
        presentation.cover()
        old.style = {
            presentation.attach(FakeSurface())
            true
        }
        presentation.revealWhenReady()
        runCurrent()
        assertTrue(presentation.renderState.value is ReaderRenderState.Transitioning)

        val current = FakeSurface()
        presentation.attach(current)
        current.style = {
            href = "c2"
            true
        }
        presentation.revealWhenReady()
        runCurrent()
        assertTrue(presentation.renderState.value is ReaderRenderState.Transitioning)
        assertEquals(0, old.revealed + current.revealed)
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 900L
        const val REVEAL_TIMEOUT_MS = 3000L
    }
}
