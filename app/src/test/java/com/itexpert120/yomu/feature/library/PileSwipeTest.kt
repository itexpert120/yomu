package com.itexpert120.yomu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PileSwipeTest {
    @Test
    fun shortSlowDragSpringsBack() {
        assertEquals(2, pileSettleTarget(start = 2, position = 2.1f, velocity = 0.5f))
        assertEquals(2, pileSettleTarget(start = 2, position = 1.85f, velocity = -0.5f))
    }

    @Test
    fun dragPastTheDistanceThresholdCommitsThatWay() {
        assertEquals(3, pileSettleTarget(start = 2, position = 2f + PileCommitDistance + 0.01f, velocity = 0f))
        assertEquals(1, pileSettleTarget(start = 2, position = 2f - PileCommitDistance - 0.01f, velocity = 0f))
    }

    @Test
    fun aShortFlickCommitsOnVelocity() {
        assertEquals(3, pileSettleTarget(start = 2, position = 2.05f, velocity = PileCommitVelocity))
        assertEquals(1, pileSettleTarget(start = 2, position = 1.97f, velocity = -PileCommitVelocity - 1f))
    }

    @Test
    fun aFlingAgainstTheDragWins() {
        // Dragged most of the way forward, then flung back: the flick decides, and it goes back.
        assertEquals(1, pileSettleTarget(start = 2, position = 2.6f, velocity = -4f))
    }

    @Test
    fun slowingDownAfterAFarDragStillCommits() {
        assertEquals(3, pileSettleTarget(start = 2, position = 2.8f, velocity = 0.2f))
    }

    @Test
    fun neverMovesMoreThanOneBook() {
        assertEquals(3, pileSettleTarget(start = 2, position = 4.4f, velocity = 30f))
        assertEquals(1, pileSettleTarget(start = 2, position = -0.5f, velocity = 0f))
    }

    @Test
    fun theLoopTurnsTheSameWayPastTheEnds() {
        // Positions run past the book count or below zero: the same rule applies.
        assertEquals(-1, pileSettleTarget(start = 0, position = -0.4f, velocity = 0f))
        assertEquals(6, pileSettleTarget(start = 5, position = 5.2f, velocity = 3f))
    }
}
