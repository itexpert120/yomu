package com.itexpert120.yomu.app.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.rememberSettlingSpatialSpring
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One tab's place in [TopLevelTabs]: how visible it is and how far it is shifted, both read only
 * while drawing, and whether it is laid out at all.
 */
@Stable
private class TabSlot(shown: Boolean) {
    val alpha = Animatable(if (shown) 1f else 0f)
    val shift = Animatable(0f)

    // Placed (laid out, drawn, touchable) — the selected tab, and one still fading out or uncovered
    // by a back gesture. Every other visited tab stays composed but is not placed.
    var placed by mutableStateOf(shown)

    // Its laid-out width, for the back slide (written while drawing).
    var width = 0f
}

/**
 * The three top-level destinations (Library, Statistics, Settings) as one host that keeps every
 * tab it has shown composed, so switching back never rebuilds a page: the library keeps its pile,
 * scroll and covers (nothing reloads or blinks), and Statistics and Settings come back as left.
 * Statistics and Settings are also composed ahead, hidden, once the library has had a moment to
 * settle, so even the first switch has no page to build. Only the selected tab (and one moving
 * out) is laid out and drawn; a hidden tab's lifecycle sits at STARTED (it stays current, and its
 * back handlers are off), the selected one's follows the library's.
 *
 * Switching is Stack's top-level move (motion spec): the content slides 24dp and fades through —
 * the outgoing tab fades out on the fast effects spring while sliding 24dp away, and the incoming
 * one fades in on the default effects spring while sliding the last 24dp into place, overlapping,
 * on the default spatial spring, toward the reading direction of the move. The bar stays put. All
 * of it is graphicsLayer alpha and translation: no layout, no recomposition per frame. A new tap
 * mid-switch retargets from wherever things are.
 *
 * Back from Statistics or Settings is Stack's one back slide: the tab follows the finger sideways
 * over the library underneath, then completes on the spatial spring (or springs back); a back key
 * plays the same commit from rest.
 */
@Composable
internal fun TopLevelTabs(
    selected: YomuTopLevelDestination,
    onSelectedChange: (YomuTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (YomuTopLevelDestination) -> Unit,
) {
    val slots = remember { YomuTopLevelDestination.entries.associateWith { TabSlot(shown = it == selected) } }
    // Tabs composed so far, in first-shown order; the library always first, so a tab moving away
    // over it in a back gesture is drawn above it.
    val composed = remember {
        mutableStateListOf(YomuTopLevelDestination.Library).apply { if (selected != YomuTopLevelDestination.Library) add(selected) }
    }
    if (selected !in composed) composed += selected

    // Compose the other tabs ahead, one at a time, once the library has settled.
    LaunchedEffect(Unit) {
        delay(PrewarmStartMillis)
        for (tab in PrewarmOrder) {
            delay(PrewarmDelayMillis)
            if (tab !in composed) composed += tab
        }
    }

    val scope = rememberCoroutineScope()
    val animate = yomuAnimationsEnabled()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val travelPx = with(LocalDensity.current) { YomuMotion.ScreenTransitionDistance.toPx() }
    val scheme = MaterialTheme.motionScheme
    // The tab on screen; a new [selected] (a tab tapped in the bar, "open library" on Statistics)
    // switches to it. A back gesture's commit updates both itself.
    var shownTab by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val from = shownTab
        if (from == selected) return@LaunchedEffect
        shownTab = selected
        val forward = physicalDirection(selected.ordinal > from.ordinal, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
        switchTabs(scope, slots, from, selected, animate, forward, travelPx, scheme)
    }

    // Back from Statistics or Settings: the tab slides away over the library with the finger.
    val spatial = rememberSettlingSpatialSpring()
    PredictiveBackHandler(enabled = selected != YomuTopLevelDestination.Library) { events ->
        val from = selected
        val library = slots.getValue(YomuTopLevelDestination.Library)
        val leaving = slots.getValue(from)
        library.placed = true
        library.alpha.snapTo(1f)
        library.shift.snapTo(0f)
        var width = 0f
        // Always toward the end edge, whichever edge the finger pulled from.
        val direction = if (rtl) -1f else 1f
        try {
            events.collect { event ->
                width = leaving.width
                if (animate) leaving.shift.snapTo(width * event.progress * direction)
            }
            width = leaving.width
            if (animate) leaving.shift.animateTo(width * direction, spatial)
            shownTab = YomuTopLevelDestination.Library
            onSelectedChange(YomuTopLevelDestination.Library)
            leaving.placed = false
            leaving.alpha.snapTo(0f)
            leaving.shift.snapTo(0f)
        } catch (e: CancellationException) {
            scope.launch {
                leaving.shift.animateTo(0f, spatial)
                if (shownTab == from) {
                    library.placed = false
                    library.alpha.snapTo(0f)
                }
            }
            throw e
        }
    }

    val host = LocalLifecycleOwner.current
    val hostState by host.lifecycle.currentStateFlow.collectAsState()
    val desk = YomuTheme.stack.desk
    Layout(
        modifier = modifier,
        content = {
            composed.forEach { tab ->
                key(tab) {
                    val slot = slots.getValue(tab)
                    val active = tab == selected || slot.placed
                    val lifecycle = remember { TabLifecycleOwner() }
                    val target = if (active) hostState else minOf(hostState, Lifecycle.State.STARTED)
                    SideEffect { lifecycle.moveTo(target.coerceAtLeast(Lifecycle.State.CREATED)) }
                    DisposableEffect(lifecycle) { onDispose { lifecycle.moveTo(Lifecycle.State.DESTROYED) } }
                    val back = rememberNavigationEventDispatcherOwner(enabled = tab == selected)
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides lifecycle,
                        LocalNavigationEventDispatcherOwner provides back,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    slot.width = size.width
                                    alpha = slot.alpha.value
                                    translationX = slot.shift.value
                                }
                                // A tab sliding over the library is one opaque sheet of desk.
                                .drawBehind { if (slot.shift.value != 0f) drawRect(desk) }
                                .then(if (tab == selected) Modifier else Modifier.clearAndSetSemantics { }),
                        ) { content(tab) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val tab = composed.getOrNull(index) ?: return@forEachIndexed
                if (slots.getValue(tab).placed) placeable.place(0, 0)
            }
        }
    }
}

private fun switchTabs(
    scope: CoroutineScope,
    slots: Map<YomuTopLevelDestination, TabSlot>,
    from: YomuTopLevelDestination,
    to: YomuTopLevelDestination,
    animate: Boolean,
    forward: Boolean,
    travelPx: Float,
    scheme: androidx.compose.material3.MotionScheme,
) {
    val incoming = slots.getValue(to)
    val outgoing = slots.getValue(from)
    incoming.placed = true
    if (!animate) {
        scope.launch {
            incoming.alpha.snapTo(1f)
            incoming.shift.snapTo(0f)
            outgoing.alpha.snapTo(0f)
            outgoing.shift.snapTo(0f)
            outgoing.placed = false
        }
        return
    }
    val sign = if (forward) 1f else -1f
    // Each value moves on its own, so a new switch retargets any of them without cancelling the rest.
    scope.launch {
        if (incoming.alpha.value == 0f) incoming.shift.snapTo(travelPx * sign)
        incoming.shift.animateTo(0f, scheme.defaultSpatialSpec())
    }
    scope.launch { incoming.alpha.animateTo(1f, scheme.defaultEffectsSpec()) }
    scope.launch { outgoing.shift.animateTo(-travelPx * sign, scheme.defaultSpatialSpec()) }
    scope.launch {
        try {
            outgoing.alpha.animateTo(0f, scheme.fastEffectsSpec())
            // Faded out and not brought back meanwhile: stop laying it out.
            if (outgoing.alpha.value == 0f && outgoing !== incoming) {
                outgoing.placed = false
                outgoing.shift.snapTo(0f)
            }
        } catch (_: CancellationException) {
            // Retargeted by a newer switch, which now owns this tab.
        }
    }
}

/** A tab's own lifecycle (see [TopLevelTabs]). */
private class TabLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun moveTo(state: Lifecycle.State) {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = state
    }
}

/** The tabs composed ahead, hidden, after the library has settled, and how long apart. */
private val PrewarmOrder = listOf(YomuTopLevelDestination.Statistics, YomuTopLevelDestination.Settings)
private const val PrewarmStartMillis = 1000L
private const val PrewarmDelayMillis = 1200L
