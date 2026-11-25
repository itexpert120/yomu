package com.itexpert120.yomu.app.navigation

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventTransitionState
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import kotlinx.coroutines.flow.first

/**
 * What a NavHost's running transition is, as its pages need to know while drawing. Navigation
 * decides the transition in the same pass its pages compose (through the spec lambdas below), so
 * these are plain fields read in the draw phase, never in composition.
 */
internal class BackMotion {
    // The running transition is a back (pop): the leaving page slides off (BackPreviewFrame).
    var popping = false

    // NavigationEvent.EDGE_LEFT / EDGE_RIGHT: the leaving page slides away from the edge it was pulled from.
    var swipeEdge = NavigationEvent.EDGE_LEFT

    // NavHost resets its edge to EDGE_NONE as a gesture ends, and some events carry no edge; only a
    // real left/right edge replaces the one this gesture started with.
    fun recordEdge(edge: Int) {
        if (edge == NavigationEvent.EDGE_LEFT || edge == NavigationEvent.EDGE_RIGHT) swipeEdge = edge
    }

    // False while a finger is on a back gesture: the pop follows the finger until then.
    var released = true

    // A back tap / back key / close button (no gesture): the same slide, committing from rest.
    var tapPop = false

    // From a gesture's first event until its pop finishes or its cancel settles back to rest.
    var gestureInFlight = false

    // The book page is opening over the library (not returning from the reader or the editor).
    var sheetEnter = false

    // A forward move is still animating; a back gesture can't be previewed until it settles.
    var opening by mutableStateOf(false)

    // Pops the top page; called once a back taken during [opening] has been let go.
    var popSettled: () -> Unit = {}

    // --- The spec lambdas: every NavHost using this motion calls these from its transitions. ---

    /** A forward move: nothing from an earlier back carries over. */
    fun forward(changesEntry: Boolean) {
        popping = false
        tapPop = false
        gestureInFlight = false
        if (changesEntry) opening = true
    }

    /**
     * A pop by tap/key/close button. [changesEntry] false: NavHost re-running a finished
     * transition. NavHost also re-runs this when a released gesture commits, which is not a tap
     * ([gestureInFlight]). Returns whether the leaving page is kept to slide away.
     */
    fun recordPop(changesEntry: Boolean, motionEnabled: Boolean, rtl: Boolean): Boolean {
        sheetEnter = false
        if (!changesEntry || !motionEnabled) {
            popping = false
            return false
        }
        popping = true
        if (!gestureInFlight) {
            tapPop = true
            swipeEdge = if (rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
        }
        return true
    }

    /** A back gesture's pop: both pages hold still and the frames draw it. */
    fun recordPredictivePop(swipeEdge: Int, motionEnabled: Boolean) {
        sheetEnter = false
        popping = motionEnabled
        tapPop = false
        gestureInFlight = true
        recordEdge(swipeEdge)
    }
}

/** A NavHost's pop exit under [motion]: the leaving page stays while its frame slides it away. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.backPopExit(motion: BackMotion, motionEnabled: Boolean, layoutDirection: LayoutDirection): ExitTransition = if (motion.recordPop(initialState.id != targetState.id, motionEnabled, layoutDirection == LayoutDirection.Rtl)) {
    ExitTransition.KeepUntilTransitionsFinished
} else {
    ExitTransition.None
}

/** A NavHost's predictive pop exit under [motion]: both pages hold still while the frames draw it. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.backPredictivePopExit(motion: BackMotion, swipeEdge: Int, motionEnabled: Boolean): ExitTransition {
    motion.recordPredictivePop(swipeEdge, motionEnabled)
    return ExitTransition.KeepUntilTransitionsFinished
}

/**
 * Keeps [motion] told whether a finger is on a back gesture (public navigation-event state, read
 * from the activity's dispatcher), and when [navController]'s forward moves have settled.
 */
@Composable
internal fun TrackBackMotion(motion: BackMotion, navController: NavHostController) {
    val activity = LocalActivity.current
    LaunchedEffect(activity, motion) {
        val dispatcher = (activity as? NavigationEventDispatcherOwner)?.navigationEventDispatcher ?: return@LaunchedEffect
        dispatcher.transitionState.collect { state ->
            if (state is NavigationEventTransitionState.InProgress) {
                motion.released = false
                motion.gestureInFlight = true
                motion.recordEdge(state.latestEvent.swipeEdge)
            } else {
                motion.released = true
            }
        }
    }
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    LaunchedEffect(visibleEntries.size) {
        if (visibleEntries.size <= 1) motion.opening = false
    }
    motion.popSettled = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
}

/**
 * Stack's one back motion on a page: when the page leaves by a back (a gesture, a back tap or key,
 * a close button), it slides sideways off the edge away from the swipe, over the page underneath,
 * which holds perfectly still — no scaling, rounding or darkening. During a gesture it follows the
 * finger 1:1; on release it completes on the default spatial spring from where it is, at the
 * finger's speed, or retraces on cancel ([BackSlide]). A tap plays the same commit from rest.
 * Forward moves are untouched (NavHost's push). [slides] false leaves the page to its own frame
 * (the book sheet slides down in [BookSheetFrame]). Every value is read while drawing.
 */
@Composable
internal fun AnimatedContentScope.BackPreviewFrame(
    motion: BackMotion,
    slides: Boolean = true,
    content: @Composable () -> Unit,
) {
    val leaving = transition.targetState == EnterExitState.PostExit
    val motionEnabled = yomuAnimationsEnabled()
    // 0 on the resting page, 1 at the far end of a back (PostExit for the leaving page). NavHost
    // seeks this with the gesture, then plays the rest on commit, or back to 0 on cancel.
    val clock = transition.animateFloat(
        transitionSpec = { tween(YomuMotion.BackSlideMillis, easing = LinearEasing) },
        label = "backSlide",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    val slide = remember { BackSlide() }
    // The leaving page is removed once its pop has played out.
    DisposableEffect(Unit) {
        onDispose { if (motion.released) motion.gestureInFlight = false }
    }
    // A back gesture begun while a page is still opening can't be previewed: NavHost would seek a
    // transition that is already running toward that page, which leaves the page frozen under the
    // finger and then cuts. The page takes those backs itself (its handler is registered after
    // NavHost's, so it wins) and pops once it has settled, so the back plays as it does for a tap.
    // It stays enabled until its gesture ends: disabling a handler mid-gesture completes the gesture.
    var earlyBack by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled = motion.opening || earlyBack) { progress ->
        earlyBack = true
        try {
            progress.collect { }
            snapshotFlow { motion.opening }.first { !it }
            motion.popSettled()
        } finally {
            earlyBack = false
        }
    }
    val desk = YomuTheme.stack.desk
    fun sliding(): Boolean = slides && leaving && motion.popping && clock.value > 0f
    Box(
        modifier = Modifier
            .graphicsLayer {
                val t = clock.value
                if (t <= 0f && motion.released) motion.gestureInFlight = false
                if (!sliding()) {
                    translationX = 0f
                    return@graphicsLayer
                }
                val x = slide.position(t, dragging = !motion.released, tap = motion.tapPop)
                val towardLeft = motion.swipeEdge == NavigationEvent.EDGE_RIGHT
                translationX = size.width * x * if (towardLeft) -1f else 1f
            }
            // The page slides as one opaque sheet of desk, whatever it draws itself.
            .drawBehind { if (sliding()) drawRect(desk) }
            // An opaque page takes every touch in its area: the library is live beneath the host.
            .then(if (slides) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier),
    ) {
        content()
    }
}
