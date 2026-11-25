package com.itexpert120.yomu.core.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Whether a sheet let go after a drag of [offsetPx] toward its edge (of [extentPx] in all) at
 * [velocityPx] px/s (positive = toward the edge) goes away: past [dismissFraction] of its extent, or
 * flung toward its edge faster than [flingPx]. A fling back toward rest always keeps it.
 */
internal fun sheetReleaseDismisses(
    offsetPx: Float,
    extentPx: Float,
    velocityPx: Float,
    flingPx: Float,
    dismissFraction: Float = YomuSheetDismissFraction,
): Boolean {
    if (offsetPx <= 0f) return false
    if (velocityPx < -flingPx) return false
    return velocityPx > flingPx || (extentPx > 0f && offsetPx > extentPx * dismissFraction)
}

/** A drag past this share of a sheet's extent dismisses it on release (Stack motion spec: 35%). */
internal const val YomuSheetDismissFraction = 0.35f

/** A fling toward the edge faster than this dismisses a sheet (Stack motion spec: 1,000 dp/s). */
internal val YomuSheetFlingVelocity: Dp = 1000.dp

/**
 * Where a Stack sheet stands, as one value: [travel] is 0 at rest and 1 fully off its edge (below
 * the window for a bottom sheet, past the end edge for a side sheet). Everything that moves a sheet
 * moves this one value — rising in, a drag (from its title row, or a pull past the top of its
 * content), the system back gesture (the sheet follows the finger toward its edge, then completes
 * or springs back), and every dismissal — so two motions can never fight over it. Geometry runs on
 * the default spatial spring; the screen behind dims with `1 - travel`. Read [travel] only while
 * drawing.
 */
@Stable
class YomuSheetMotion internal constructor(
    private val scope: CoroutineScope,
    private val animate: Boolean,
    atRest: Boolean,
) {
    // Written synchronously by drags and by the one running animation (never two at once).
    private var value by mutableFloatStateOf(if (atRest) 0f else 1f)
    private var velocity = 0f
    private var running: Job? = null

    /** 0 at rest, 1 fully away. */
    val travel: Float get() = value

    /** The sheet's size along its axis, in px (its height, or a side sheet's width). */
    var extentPx: Float = 0f

    internal var spec: FiniteAnimationSpec<Float> = spring()
    internal var flingPx: Float = 0f
    internal var onGone: () -> Unit = {}

    /** True once a dismissal has begun; the sheet only moves away from then on. */
    var leaving by mutableStateOf(false)
        private set

    /** Moves the sheet to [target] on the spatial spring, carrying the current velocity (or [velocityPx]). */
    private fun animateTo(target: Float, velocityPx: Float? = null, then: () -> Unit = {}) {
        running?.cancel()
        if (velocityPx != null) velocity = if (extentPx > 0f) velocityPx / extentPx else 0f
        if (!animate) {
            value = target
            velocity = 0f
            then()
            return
        }
        running = scope.launch {
            animate(value, target, velocity, spec) { v, speed ->
                value = v
                velocity = speed
            }
            velocity = 0f
            then()
        }
    }

    private fun stop() {
        running?.cancel()
        running = null
        velocity = 0f
    }

    internal fun show() {
        if (!leaving) animateTo(0f)
    }

    /** Slides the sheet off its edge, then calls the owner's dismissal. Safe to call repeatedly. */
    fun dismiss(velocityPx: Float? = null) {
        if (leaving) return
        leaving = true
        animateTo(1f, velocityPx) { onGone() }
    }

    internal fun dragBy(deltaPx: Float) {
        if (leaving || extentPx <= 0f) return
        stop()
        value = (value + deltaPx / extentPx).coerceIn(0f, 1f)
    }

    internal fun release(velocityPx: Float) {
        if (leaving) return
        if (sheetReleaseDismisses(value * extentPx, extentPx, velocityPx, flingPx)) dismiss(velocityPx) else animateTo(0f, velocityPx)
    }

    // Back gesture: the sheet follows the finger toward its edge from wherever it stood.
    private var backFrom = 0f

    internal fun followBack(progress: Float, first: Boolean) {
        if (leaving || !animate) return
        if (first) {
            stop()
            backFrom = value
        }
        value = backFrom + (1f - backFrom) * progress.coerceIn(0f, 1f)
    }

    internal fun cancelBack() {
        if (!leaving) animateTo(0f, 0f)
    }

    /** Nested scrolling: content scrolls first; a pull past its top moves the sheet, and scrolling back takes the sheet to rest first. */
    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || value <= 0f || available.y >= 0f || leaving) return Offset.Zero
            val used = available.y.coerceAtLeast(-value * extentPx)
            dragBy(used)
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || leaving) return Offset.Zero
            dragBy(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (value <= 0f || leaving) return Velocity.Zero
            release(available.y)
            return available
        }
    }
}

/**
 * A sheet's motion. It rises in on its first frame (or starts at rest in previews and with
 * animations off) and calls [onGone] once a dismissal has carried it off its edge.
 */
@Composable
fun rememberYomuSheetMotion(onGone: () -> Unit): YomuSheetMotion {
    val scope = rememberCoroutineScope()
    val inspection = LocalInspectionMode.current
    val animate = yomuAnimationsEnabled() && !inspection
    val motion = remember { YomuSheetMotion(scope, animate, atRest = !animate) }
    val latestOnGone by rememberUpdatedState(onGone)
    motion.onGone = { latestOnGone() }
    motion.spec = rememberSettlingSpatialSpring()
    motion.flingPx = with(LocalDensity.current) { YomuSheetFlingVelocity.toPx() }
    LaunchedEffect(motion) { motion.show() }
    return motion
}

/**
 * The system back gesture on a sheet: it follows the finger toward its edge, then completes the
 * dismissal (continuing from where it is) or springs back to rest. Call it inside the window that
 * owns the sheet (a Dialog has its own back dispatcher).
 */
@Composable
fun YomuSheetBackHandler(motion: YomuSheetMotion, enabled: Boolean = true) {
    PredictiveBackHandler(enabled = enabled && !motion.leaving) { events ->
        var first = true
        try {
            events.collect {
                motion.followBack(it.progress, first)
                first = false
            }
            motion.dismiss()
        } catch (e: CancellationException) {
            motion.cancelBack()
            throw e
        }
    }
}

/**
 * The default spatial spring, landing within a hair of its 0..1 target so a long move (a sheet's
 * full height, a page's width) settles smoothly instead of stopping a few pixels short.
 */
@Composable
fun rememberSettlingSpatialSpring(): FiniteAnimationSpec<Float> {
    val scheme = MaterialTheme.motionScheme
    return remember(scheme) {
        (scheme.defaultSpatialSpec<Float>() as? SpringSpec<Float>)?.let { spring(it.dampingRatio, it.stiffness, visibilityThreshold = 0.0005f) }
            ?: scheme.defaultSpatialSpec()
    }
}
