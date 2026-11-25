package com.itexpert120.yomu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackSlideTest {
    private var now = 0L
    private fun slide() = BackSlide { now }

    /** Advances the fake clock one 120 Hz frame. */
    private fun frame() {
        now += 8_333_333L
    }

    @Test
    fun `a dragged page follows the finger one to one`() {
        val slide = slide()
        for (p in listOf(0.05f, 0.1f, 0.2f, 0.35f)) {
            frame()
            assertEquals(p, slide.position(p, dragging = true, tap = false), 0f)
        }
    }

    @Test
    fun `a commit continues from the release point without a jump and lands at the edge`() {
        val slide = slide()
        var p = 0f
        repeat(20) {
            frame()
            p += 0.015f
            slide.position(p, dragging = true, tap = false)
        }
        val releasedAt = p
        // The first committed frame sits at (or just past) where the finger let go.
        val first = slide.position(releasedAt + 0.001f, dragging = false, tap = false)
        assertTrue("first=$first released=$releasedAt", first >= releasedAt && first - releasedAt < 0.02f)
        // It never moves backwards while committing, and arrives exactly as the clock ends.
        var last = first
        var t = releasedAt
        while (t < 1f) {
            t = minOf(1f, t + 0.01f)
            val x = slide.position(t, dragging = false, tap = false)
            assertTrue("went back at t=$t: $x < $last", x >= last - 0.03f)
            last = x
        }
        assertEquals(1f, slide.position(1f, dragging = false, tap = false), 0f)
    }

    @Test
    fun `a commit starts at the finger's speed`() {
        val slide = slide()
        var p = 0f
        repeat(20) {
            frame()
            p += 0.02f // 2.4 widths per second
            slide.position(p, dragging = true, tap = false)
        }
        // A fast flick keeps moving faster than a commit from rest over the same clock step.
        val step = 0.01f
        val fromFinger = slide.position(p + step, dragging = false, tap = false) - p
        val fromRest = slide().position(step, dragging = false, tap = true)
        assertTrue("fromFinger=$fromFinger fromRest=$fromRest", fromFinger > fromRest)
    }

    @Test
    fun `a cancel retraces the clock back to rest`() {
        val slide = slide()
        frame()
        slide.position(0.3f, dragging = true, tap = false)
        assertEquals(0.2f, slide.position(0.2f, dragging = false, tap = false), 0f)
        assertEquals(0.05f, slide.position(0.05f, dragging = false, tap = false), 0f)
        assertEquals(0f, slide.position(0f, dragging = false, tap = false), 0f)
    }

    @Test
    fun `a tap commits from rest on the spring and lands at the edge`() {
        val slide = slide()
        assertEquals(0f, slide.position(0f, dragging = false, tap = true), 0f)
        val early = slide.position(0.1f, dragging = false, tap = true)
        val middle = slide.position(0.5f, dragging = false, tap = true)
        assertTrue(early in 0f..middle)
        // The spatial spring front-loads the move: well past halfway at half time.
        assertTrue("middle=$middle", middle > 0.6f)
        assertEquals(1f, slide.position(1f, dragging = false, tap = true), 0f)
    }

    @Test
    fun `nothing carries over from an earlier back once the page is at rest`() {
        val slide = slide()
        frame()
        slide.position(0.6f, dragging = true, tap = false)
        slide.position(0f, dragging = false, tap = false)
        // A new gesture released straight away commits from rest, not from the old release point.
        val x = slide.position(0.2f, dragging = false, tap = false)
        assertTrue("x=$x", x > 0.2f)
    }

    @Test
    fun `the curve runs from 0 to 1 within its window`() {
        assertEquals(0f, BackSlideCurve.at(0f), 0f)
        assertEquals(1f, BackSlideCurve.at(1f), 0f)
        assertTrue(BackSlideCurve.at(0.95f) > 0.97f)
    }
}

class BackMotionTest {
    @Test
    fun `a forward move clears an earlier back`() {
        val motion = BackMotion()
        motion.popping = true
        motion.tapPop = true
        motion.gestureInFlight = true
        motion.forward(changesEntry = true)
        assertFalse(motion.popping)
        assertFalse(motion.tapPop)
        assertFalse(motion.gestureInFlight)
        assertTrue(motion.opening)
    }

    @Test
    fun `a pop with a gesture in flight is not a tap`() {
        val motion = BackMotion()
        motion.gestureInFlight = true
        motion.tapPop = false
        motion.popTap(changesEntry = true)
        assertTrue(motion.popping)
        assertFalse(motion.tapPop)
    }

    @Test
    fun `a pop without a gesture is a tap from the leading edge`() {
        val motion = BackMotion()
        motion.popTap(changesEntry = true)
        assertTrue(motion.popping)
        assertTrue(motion.tapPop)
    }

    @Test
    fun `re-running a finished pop does not slide`() {
        val motion = BackMotion()
        motion.popTap(changesEntry = false)
        assertFalse(motion.popping)
    }

    private fun BackMotion.popTap(changesEntry: Boolean): Boolean = recordPop(changesEntry, motionEnabled = true, rtl = false)
}
