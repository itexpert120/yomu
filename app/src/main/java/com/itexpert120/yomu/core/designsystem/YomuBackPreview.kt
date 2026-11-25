package com.itexpert120.yomu.core.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** How far a predictive back swipe has travelled on a surface (0 = at rest, 1 = full swipe). */
@Stable
class YomuBackPreviewState internal constructor() {
    internal val progress = Animatable(0f)
}

@Composable
fun rememberYomuBackPreviewState(): YomuBackPreviewState = remember { YomuBackPreviewState() }

/**
 * Makes a dialog-like surface follow the system back gesture: [state] tracks the swipe, [onBack]
 * runs when the gesture commits, and a cancelled swipe springs back to rest on the default spatial
 * spring. Call it inside the window that owns the surface (a Dialog has its own back dispatcher),
 * and pair it with [yomuBackPreview]. (Sheets use [YomuSheetBackHandler] on their own motion.)
 */
@Composable
fun YomuBackPreviewHandler(state: YomuBackPreviewState, enabled: Boolean = true, onBack: () -> Unit) {
    val latestOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val animate = yomuAnimationsEnabled()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { if (animate) state.progress.snapTo(it.progress) }
            latestOnBack()
        } catch (e: CancellationException) {
            // This coroutine is already cancelled, so the settle runs on the composition's scope.
            scope.launch { state.progress.animateTo(0f, YomuMotion.scheme.defaultSpatialSpec()) }
            throw e
        }
    }
}

/**
 * Stack's one back motion on a dialog-like surface: it slides with the finger, never scaling.
 * [origin] names the edge it leaves by: an end-anchored surface (pivot x 1, or 0 under RTL) slides
 * toward that edge by its width; anything else slides down by half its height.
 */
fun Modifier.yomuBackPreview(
    state: YomuBackPreviewState,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    val p = state.progress.value.coerceIn(0f, 1f)
    when (origin.pivotFractionX) {
        1f -> translationX = size.width * p
        0f -> translationX = -size.width * p
        else -> translationY = size.height * 0.5f * p
    }
}
