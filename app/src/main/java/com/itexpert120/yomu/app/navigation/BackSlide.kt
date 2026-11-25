package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.SpringSpec
import com.itexpert120.yomu.core.designsystem.YomuMotion

/**
 * Stack's one back motion, as a position: 0 = the leaving page (or sheet) at rest, 1 = fully off
 * its edge. Navigation runs every back as a seekable transition whose 0..1 [clock] runs linearly
 * over [YomuMotion.BackSlideMillis]: a gesture seeks it with the finger, then the platform plays
 * the rest on release (or seeks it back to 0 on cancel); a back tap plays it from 0.
 *
 * - While a finger drags ([dragging]), the position is the gesture's progress: 1:1 with the finger.
 * - On commit it continues from where the finger let go on the default spatial spring, starting at
 *   the finger's own speed, and arrives exactly when the transition ends — so the page never stops
 *   short of the edge or lingers after it, at any swipe speed.
 * - A cancel retraces the clock back to 0.
 * - A tap ([tap]) is a commit from rest.
 *
 * One per page (or sheet) frame; [position] is called while drawing, every frame of a back.
 */
internal class BackSlide(private val nowNanos: () -> Long = System::nanoTime) {
    private var releaseAt = 0f
    private var releaseVelocity = 0f
    private var lastClock = 0f
    private var lastNanos = 0L
    private var velocity = 0f

    fun position(clock: Float, dragging: Boolean, tap: Boolean): Float {
        val t = clock.coerceIn(0f, 1f)
        if (t <= 0f) {
            // At rest: nothing from an earlier back carries over.
            releaseAt = 0f
            releaseVelocity = 0f
            velocity = 0f
            lastNanos = 0L
            return 0f
        }
        if (tap) return commit(t, from = 0f, speed = 0f)
        if (dragging) {
            track(t)
            releaseAt = t
            releaseVelocity = velocity
            return t
        }
        lastNanos = 0L
        // Cancelling (or not yet past the release point): the clock retraces the drag.
        if (t <= releaseAt) return t
        return commit(t, from = releaseAt, speed = releaseVelocity)
    }

    /** The finger's speed in positions per second, smoothed over the last frames of the drag. */
    private fun track(t: Float) {
        val now = nowNanos()
        if (lastNanos != 0L && now > lastNanos) {
            val instant = (t - lastClock) / ((now - lastNanos) / 1e9f)
            velocity = velocity * 0.5f + instant * 0.5f
        } else {
            velocity = 0f
        }
        lastClock = t
        lastNanos = now
    }

    private fun commit(t: Float, from: Float, speed: Float): Float {
        if (from >= 1f) return 1f
        val c = (t - from) / (1f - from)
        return from + (1f - from) * BackSlideCurve.at(c, speed.coerceIn(0f, MaxSpeed))
    }

    private companion object {
        const val MaxSpeed = 8f
    }
}

/**
 * The default spatial spring's response from 0 to 1, stretched or squeezed to fill a 0..1 window
 * ([at]'s `c`) so it always lands as the window closes. [at]'s `speed` is the speed (in positions
 * per second) the move starts at: a commit's window lasts its remaining share of
 * [YomuMotion.BackSlideMillis], so the spring time runs at `springLength / BackSlideMillis` of real
 * time whatever the remaining distance, and that ratio converts the speed.
 */
internal object BackSlideCurve {
    private val spring: FloatSpringSpec = (YomuMotion.scheme.defaultSpatialSpec<Float>() as? SpringSpec<Float>)
        ?.let { FloatSpringSpec(it.dampingRatio, it.stiffness, 0.0005f) }
        ?: FloatSpringSpec(visibilityThreshold = 0.0005f)

    // The spring's own settle time from rest.
    private val springNanos = spring.getDurationNanos(0f, 1f, 0f)

    fun at(c: Float, speed: Float = 0f): Float {
        if (c <= 0f) return 0f
        if (c >= 1f) return 1f
        val startVelocity = speed * (YomuMotion.BackSlideMillis / 1000f) / (springNanos / 1e9f)
        return spring.getValueFromNanos((c * springNanos).toLong(), 0f, 1f, startVelocity)
    }
}
