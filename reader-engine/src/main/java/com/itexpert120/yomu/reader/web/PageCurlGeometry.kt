package com.itexpert120.yomu.reader.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The shape of a page curl, in the curl's own ("canonical") coordinates: the turning leaf is hinged
 * at [hinge] (the spine: the screen's left edge for a single page, its middle for a spread) and lies
 * to its right, [leafWidth] wide and [height] tall. Every turn is drawn in these terms; a turn the
 * other way (back a page in a spread, or forward in a right-to-left book) is the same picture
 * mirrored.
 *
 * The leaf's free-edge point [corner] (the corner the finger took, or the middle of the edge) has
 * been carried to [point]. The paper between the hinge and the fold lies flat; past the fold it wraps
 * round a cylinder of [radius] lying on the page and comes back flat over the top, upside down, so
 * the corner lands at [point] — the same model as a page lifted by its edge and rolled back.
 */
internal data class CurlShape(
    val hinge: Float,
    val leafWidth: Float,
    val height: Float,
    /** Where the free-edge point rests when the page is flat. */
    val cornerX: Float,
    val cornerY: Float,
    /** Where that point is now. */
    val pointX: Float,
    val pointY: Float,
) {
    /** The fold's normal, pointing at the lifted paper (toward the free edge), and its length. */
    val distance: Float = hypot(cornerX - pointX, cornerY - pointY)
    val normalX: Float = if (distance > EPSILON) (cornerX - pointX) / distance else 1f
    val normalY: Float = if (distance > EPSILON) (cornerY - pointY) / distance else 0f

    /** The turned-over rest of the free-edge point: [corner] mirrored about the hinge. */
    val turnedX: Float get() = 2 * hinge - cornerX

    /**
     * The cylinder's radius: none on flat paper (it grows over the first stretch of a lift) or on a
     * page that has landed on the other side, so both ends of a turn are perfectly flat.
     */
    val radius: Float = run {
        val max = leafWidth * MAX_RADIUS_FRACTION
        val ramp = leafWidth * RADIUS_RAMP_FRACTION
        val lifted = (distance / ramp).coerceIn(0f, 1f)
        val landing = (hypot(pointX - turnedX, pointY - cornerY) / ramp).coerceIn(0f, 1f)
        max * smooth(lifted) * smooth(landing)
    }

    /**
     * Distance from the free-edge point back to the fold's axis, along the normal: the corner lands
     * on [point] after half a turn round the cylinder (πR) and the flat stretch back.
     */
    val axisOffset: Float = (distance + PI.toFloat() * radius) / 2f

    /** A point on the fold's axis (where the cylinder touches the page). */
    val axisX: Float get() = cornerX - axisOffset * normalX
    val axisY: Float get() = cornerY - axisOffset * normalY

    /** How far through the turn this is, 0 (flat) .. 1 (landed on the other side), by the point's x. */
    val progress: Float get() = ((cornerX - pointX) / (cornerX - turnedX)).coerceIn(0f, 1f)

    companion object {
        const val EPSILON = 0.5f

        /** The curl's radius at most, as a share of the leaf's width. */
        const val MAX_RADIUS_FRACTION = 0.065f

        /** Over how much travel (from flat, or from landing) the radius grows to its full size. */
        const val RADIUS_RAMP_FRACTION = 0.3f

        private fun smooth(t: Float) = t * t * (3 - 2 * t)
    }
}

/**
 * Where a turn's free-edge point is taken from and how a finger moves it. A drag holds the leaf by
 * its edge at the finger's height: the top or bottom corner when the finger starts near one (so the
 * page tilts as it turns), else the middle of the edge (a straight turn that tilts only a little).
 */
internal class CurlGrip(
    private val hinge: Float,
    private val leafWidth: Float,
    private val height: Float,
    /** Where the drag began, canonical coordinates. */
    private val startX: Float,
    private val startY: Float,
    /** True when the leaf starts turned over (back a page on a single page: it comes back in). */
    private val fromTurned: Boolean,
) {
    val cornerX: Float = hinge + leafWidth
    val cornerY: Float = when {
        startY < height * CORNER_BAND -> 0f
        startY > height * (1 - CORNER_BAND) -> height
        else -> startY
    }
    private val tilt = if (cornerY == 0f || cornerY == height) 1f else MIDDLE_TILT
    private val turnedX = 2 * hinge - cornerX

    /**
     * The point for a finger at ([x], [y]). Turning over, the finger's way from where it began to the
     * far edge of the screen (canonical 0) carries the point from the free edge to its turned-over
     * rest, so the curl keeps up with the finger wherever the drag began; coming back in, the way
     * from where it began to the near edge brings it back.
     */
    fun point(x: Float, y: Float): Pair<Float, Float> {
        val t = if (!fromTurned) {
            val span = max(startX, MIN_SPAN)
            ((startX - x) / span)
        } else {
            val span = max(cornerX - startX, MIN_SPAN)
            1f - (x - startX) / span
        }.coerceIn(-0.05f, 1.05f)
        val px = cornerX + (turnedX - cornerX) * t
        val py = cornerY + (y - startY) * tilt
        return constrain(px, py)
    }

    /**
     * Keeps the paper whole: the point can't get further from the hinge than the paper reaches —
     * the leaf's width from the hinge at its own height, and the diagonal from the far corner.
     */
    fun constrain(px: Float, py: Float): Pair<Float, Float> {
        var x = px
        var y = py
        val near = hypot(x - hinge, y - cornerY)
        if (near > leafWidth) {
            x = hinge + (x - hinge) * leafWidth / near
            y = cornerY + (y - cornerY) * leafWidth / near
        }
        if (cornerY == 0f || cornerY == height) {
            val farY = height - cornerY
            val diagonal = hypot(leafWidth, height)
            val far = hypot(x - hinge, y - farY)
            if (far > diagonal) {
                x = hinge + (x - hinge) * diagonal / far
                y = farY + (y - farY) * diagonal / far
            }
        }
        return x to y.coerceIn(-height * 0.25f, height * 1.25f)
    }

    /** The rest positions: flat ([commit] false) and turned over ([commit] true). */
    fun rest(commit: Boolean): Pair<Float, Float> = if (commit != fromTurned) turnedX to cornerY else cornerX to cornerY

    /**
     * Whether letting go turns the page (or, coming back in, lands it): a fling along the turn, else
     * past a third of the way. [velocityX] in canonical px/s; [minFling] the platform's fling speed.
     */
    fun commits(pointX: Float, velocityX: Float, minFling: Float): Boolean {
        val travel = cornerX - turnedX
        val along = (cornerX - pointX) / travel // 0 flat .. 1 turned
        val toward = if (fromTurned) velocityX else -velocityX
        if (abs(velocityX) > minFling * FLING_FACTOR) return toward > 0
        return if (fromTurned) along < 1 - COMMIT_SHARE else along > COMMIT_SHARE
    }

    companion object {
        /** The top and bottom bands of the edge that take a corner. */
        const val CORNER_BAND = 0.28f

        /** How much a drag held by the middle of the edge tilts the page. */
        const val MIDDLE_TILT = 0.35f

        const val COMMIT_SHARE = 0.33f
        const val FLING_FACTOR = 3f
        private const val MIN_SPAN = 48f
    }
}

/** A tap's turn: the bottom corner sweeps over in a shallow arc, as a thumb turns a page. */
internal fun tapPath(hinge: Float, leafWidth: Float, height: Float, t: Float): Pair<Float, Float> {
    val cornerX = hinge + leafWidth
    val turnedX = hinge - leafWidth
    val x = cornerX + (turnedX - cornerX) * t
    val lift = min(height * TAP_LIFT, leafWidth * 0.5f)
    val y = height - lift * kotlin.math.sin(PI.toFloat() * t)
    return x to y
}

private const val TAP_LIFT = 0.16f

/** Whether this device can draw the page curl (an AGSL shader: Android 13 and up). */
object PageCurlSupport {
    val supported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 33
}
