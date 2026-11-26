package com.itexpert120.yomu.reader.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PageCurlGeometryTest {
    private val w = 1000f
    private val h = 1400f

    @Test
    fun flatAndLandedPagesHaveNoCurl() {
        val flat = CurlShape(0f, w, h, w, h, w, h)
        assertEquals(0f, flat.radius, 0.001f)
        assertEquals(0f, flat.progress, 0.001f)
        val landed = CurlShape(0f, w, h, w, h, -w, h)
        assertEquals(0f, landed.radius, 0.001f)
        assertEquals(1f, landed.progress, 0.001f)
        val midway = CurlShape(0f, w, h, w, h, 0f, h)
        assertTrue(midway.radius > 0f)
    }

    @Test
    fun theCornerLandsUnderThePoint() {
        // A page curled from its bottom corner: the corner, after its half turn round the cylinder
        // and the flat stretch back, is where the point is.
        val s = CurlShape(0f, w, h, w, h, 300f, 1200f)
        val cornerD = (s.cornerX - s.axisX) * s.normalX + (s.cornerY - s.axisY) * s.normalY
        val pointD = (s.pointX - s.axisX) * s.normalX + (s.pointY - s.axisY) * s.normalY
        assertEquals(Math.PI.toFloat() * s.radius - cornerD, pointD, 0.01f)
    }

    @Test
    fun aDragFromAnywhereTurnsTheWholePageByTheFarEdge() {
        val grip = CurlGrip(0f, w, h, startX = 600f, startY = 1300f, fromTurned = false)
        assertEquals(h, grip.cornerY)
        val (x0, _) = grip.point(600f, 1300f)
        assertEquals(w, x0, 0.5f)
        val (x1, _) = grip.point(0f, 1300f)
        assertEquals(-w, x1, w * 0.06f)
    }

    @Test
    fun thePaperNeverTears() {
        val grip = CurlGrip(0f, w, h, startX = 900f, startY = 50f, fromTurned = false)
        val (x, y) = grip.point(-400f, 1400f)
        assertTrue(hypot(x - 0f, y - grip.cornerY) <= w + 0.5f)
        assertTrue(hypot(x - 0f, y - h) <= hypot(w, h) + 0.5f)
    }

    @Test
    fun releaseCommitsByDistanceOrFling() {
        val grip = CurlGrip(0f, w, h, startX = 900f, startY = 700f, fromTurned = false)
        assertFalse(grip.commits(pointX = 900f, velocityX = 0f, minFling = 100f))
        assertTrue(grip.commits(pointX = 200f, velocityX = 0f, minFling = 100f))
        assertTrue(grip.commits(pointX = 950f, velocityX = -2000f, minFling = 100f))
        assertFalse(grip.commits(pointX = 200f, velocityX = 2000f, minFling = 100f))
        val back = CurlGrip(0f, w, h, startX = 50f, startY = 700f, fromTurned = true)
        assertEquals(-w, back.rest(commit = false).first)
        assertEquals(w, back.rest(commit = true).first)
        assertTrue(back.commits(pointX = 400f, velocityX = 0f, minFling = 100f))
    }
}
