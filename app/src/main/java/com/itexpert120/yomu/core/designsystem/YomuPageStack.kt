package com.itexpert120.yomu.core.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A screen with pages of its own (About's Privacy, Terms, …) that move like every other page in
 * the app. Opening a page pushes it (it fades in quickly while travelling a short way in, over the
 * page it covers); leaving it is Stack's one back slide — it slides off the end edge over [root],
 * which is drawn and still underneath: following the finger 1:1 during a back gesture, then
 * completing on the spatial spring (or springing back), and the same commit from rest for a back
 * key or the page's own ← button ([onBack] sets [page] back to [root]).
 *
 * [root] stays composed throughout (its scroll is kept); it is drawn only while it can be seen.
 */
@Composable
fun <T : Any> YomuPageStack(
    page: T,
    root: T,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val animate = yomuAnimationsEnabled()
    val scheme = MaterialTheme.motionScheme
    val spring = rememberSettlingSpatialSpring()
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)
    val travelPx = with(LocalDensity.current) { YomuMotion.ScreenTransitionDistance.toPx() }
    val towardStart = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The page shown over the root (kept while it slides away), how far it has slid off (0..1 of
    // its width), and how far it has pushed in (0 → 1).
    var top by remember { mutableStateOf(page.takeIf { it != root }) }
    val away = remember { Animatable(0f) }
    val pushed = remember { Animatable(1f) }
    var byGesture by remember { mutableStateOf(false) }

    LaunchedEffect(page) {
        if (page != root) {
            top = page
            away.snapTo(0f)
            if (animate) {
                pushed.snapTo(0f)
                pushed.animateTo(1f, scheme.defaultSpatialSpec())
            } else {
                pushed.snapTo(1f)
            }
        } else if (top != null) {
            // Back by a tap or key: the same commit as a released gesture, from rest.
            if (!byGesture && animate) away.animateTo(1f, spring)
            byGesture = false
            top = null
            away.snapTo(0f)
        }
    }

    PredictiveBackHandler(enabled = page != root) { events ->
        try {
            events.collect { if (animate) away.snapTo(it.progress.coerceIn(0f, 1f)) }
            if (animate) away.animateTo(1f, spring)
            byGesture = true
            latestOnBack()
        } catch (e: CancellationException) {
            scope.launch { away.animateTo(0f, spring) }
            throw e
        }
    }

    val desk = YomuTheme.stack.desk
    Box(modifier.fillMaxSize()) {
        // The root, drawn only while it can show (under a page sliding away or pushing in).
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { if (top == null || away.value > 0f || pushed.value < 1f) drawContent() },
        ) { content(root) }
        val shown = top
        if (shown != null) {
            key(shown) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val direction = if (towardStart) -1f else 1f
                            val push = pushed.value.coerceIn(0f, 1f)
                            translationX = direction * (size.width * away.value + travelPx * (1f - push))
                            alpha = (push / PushFadeShare).coerceAtMost(1f)
                        }
                        // One opaque page over the root, taking every touch in its area.
                        .drawBehind { drawRect(desk) }
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                ) { content(shown) }
            }
        }
    }
}

/** The pushed page is opaque within the first part of its push (as the app's own push). */
private const val PushFadeShare = 0.4f
