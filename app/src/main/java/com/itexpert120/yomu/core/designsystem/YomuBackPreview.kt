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
 * Makes a surface follow the system back gesture: [state] tracks the swipe, [onBack] runs when the
 * gesture commits, and a cancelled swipe settles back to rest. Call it inside the window that owns
 * the surface (a Dialog has its own back dispatcher), and pair it with [yomuBackPreview].
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
            scope.launch { state.progress.animateTo(0f, YomuMotion.scheme.defaultEffectsSpec()) }
            throw e
        }
    }
}

/** Shrinks a surface toward [origin] as the back gesture in [state] progresses. */
fun Modifier.yomuBackPreview(
    state: YomuBackPreviewState,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    val eased = YomuMotion.EmphasizedDecel.transform(state.progress.value.coerceIn(0f, 1f))
    val scale = 1f - (1f - YomuMotion.BackPreviewScale) * eased
    scaleX = scale
    scaleY = scale
    transformOrigin = origin
}
