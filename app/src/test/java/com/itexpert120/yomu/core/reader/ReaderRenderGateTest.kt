package com.itexpert120.yomu.core.reader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRenderGateTest {
    @Test fun failedStylingCannotBeBypassedByContentOrTimeout() = runBlocking {
        val gate = ReaderRenderGate()
        val generation = gate.transition(true)
        assertFalse(gate.reveal(generation, "c", { true }, { false }, { error("unexpected") }, { error("unexpected") }))
        gate.fail(generation)
        assertEquals(ReaderRenderState.Failed, gate.state.value)
        assertFalse(gate.reveal(generation, "c", { true }, { true }, { true }, { true }))
    }

    @Test fun revealRequiresCurrentGenerationAndAllChecksInOrder() = runBlocking {
        val gate = ReaderRenderGate()
        val calls = mutableListOf<String>()
        assertTrue(
            gate.reveal(0, "c", { true }, {
                calls += "style"
                true
            }, {
                calls += "content"
                true
            }, {
                calls += "draw"
                true
            }),
        )
        assertEquals(listOf("style", "content", "draw"), calls)
        val generation = gate.transition(true)
        assertFalse(
            gate.reveal(generation, "c", { true }, {
                gate.transition(false)
                true
            }, { true }, { true }),
        )
        assertTrue(gate.state.value is ReaderRenderState.Transitioning)
    }
}
