package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Opens and closes a volume like an accordion: its chapters' rows grow from (or shrink to) nothing
 * together while the rows below follow, and the contents list changes only once they are closed.
 *
 * The list has no item keys, so the motion is in the rows' height alone (read in the layout phase,
 * never recomposing them). While a volume moves, only the first [cap] of its rows stay in the list,
 * enough to fill the screen below its title: the rest are off screen anyway, and a thousand rows
 * shrinking toward zero height would otherwise all have to be composed to fill the space.
 */
@Stable
internal class TocVolumeMotion(
    private val scope: CoroutineScope,
    private val toc: State<TocUiState>,
    private val viewportHeight: () -> Int,
    private val rowHeight: Float,
) {
    /** The volume (uid) that is opening or closing, or -1. */
    var volume by mutableIntStateOf(-1)
        private set
    private var span by mutableIntStateOf(0)
    private var opening by mutableStateOf(false)

    /** How much of the moving volume's rows shows: 0 closed, 1 open. */
    private val reveal = Animatable(1f)
    private var job: Job? = null
    private var commitOnCancel: (() -> Unit)? = null

    var spec: AnimationSpec<Float>? = null

    /** Rows of the moving volume kept in the list while it moves. */
    var cap = 0
        private set

    /** Whether [uid] is one of the moving volume's rows. */
    fun moves(uid: Int): Boolean = volume >= 0 && uid > volume && uid <= volume + span

    /** Height fraction for [uid]'s row; read it while laying out. */
    fun heightFraction(uid: Int): Float = if (moves(uid)) reveal.value else 1f

    /** Where [entry]'s chevron points: at the motion's target while it moves. */
    fun showsExpanded(entry: TocEntryUi): Boolean = if (entry.uid == volume) opening else entry.expanded

    fun toggle(entry: TocEntryUi, onToggle: (Int) -> Unit) {
        val uid = entry.uid
        val animation = spec
        val wasMoving = volume == uid
        val shownOpen = showsExpanded(entry)
        finishOther(uid)
        if (animation == null) {
            reset()
            if (shownOpen == entry.expanded) onToggle(uid)
            return
        }
        cap = (viewportHeight() / rowHeight).toInt() + 2
        volume = uid
        span = entry.children
        if (shownOpen) {
            // Close: shrink the rows, then drop them from the list.
            opening = false
            commitOnCancel = { onToggle(uid) }
            job = scope.launch {
                if (!wasMoving) reveal.snapTo(1f)
                reveal.animateTo(0f, animation)
                commitOnCancel = null
                onToggle(uid)
                awaitExpanded(uid, false)
                reset()
            }
        } else if (wasMoving && entry.expanded) {
            // Reopen mid-close: the rows are still in the list, so just grow them back.
            opening = true
            commitOnCancel = null
            job = scope.launch {
                reveal.animateTo(1f, animation)
                reset()
            }
        } else {
            // Open: put the rows in the list at no height, then grow them.
            opening = true
            commitOnCancel = null
            job = scope.launch {
                reveal.snapTo(0f)
                onToggle(uid)
                awaitExpanded(uid, true)
                reveal.animateTo(1f, animation)
                reset()
            }
        }
    }

    /** Settles another volume's motion at once, so only one volume moves at a time. */
    private fun finishOther(uid: Int) {
        job?.cancel()
        job = null
        if (volume != uid) {
            commitOnCancel?.invoke()
            commitOnCancel = null
            reset()
        }
    }

    private fun reset() {
        volume = -1
        span = 0
    }

    // Waits for the contents to show the volume open or closed; never longer than a moment, so a
    // change that doesn't arrive can't leave its rows stuck at no height.
    private suspend fun awaitExpanded(uid: Int, expanded: Boolean) {
        withTimeoutOrNull(DataWaitMillis) {
            snapshotFlow { toc.value.items.firstOrNull { it.uid == uid }?.expanded }.first { it == expanded }
        }
    }

    /**
     * The rows to list: all of [items], except that a moving volume keeps only its first [cap]
     * rows. Reads the motion's state, so the list follows when a volume starts or stops moving.
     */
    fun rows(items: List<TocEntryUi>): List<TocEntryUi> {
        val moving = volume
        if (moving < 0) return items
        val header = items.indexOfFirst { it.uid == moving }
        if (header < 0) return items
        var end = header + 1
        while (end < items.size && moves(items[end].uid)) end++
        val keepUntil = minOf(end, header + 1 + cap)
        if (keepUntil == end) return items
        return items.subList(0, keepUntil) + items.subList(end, items.size)
    }
}

private const val DataWaitMillis = 1_000L

@Composable
internal fun rememberTocVolumeMotion(toc: TocUiState, listState: LazyListState): TocVolumeMotion {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(toc)
    val rowHeight = with(LocalDensity.current) { TocRowMinHeight.toPx() }
    val motion = remember(scope, listState) {
        TocVolumeMotion(scope, latest, { listState.layoutInfo.viewportSize.height }, rowHeight)
    }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    motion.spec = if (yomuAnimationsEnabled()) spec else null
    return motion
}

/** A volume's row: its height follows the motion, measured at full height and clipped. */
internal fun Modifier.tocVolumeReveal(motion: TocVolumeMotion, uid: Int): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * motion.heightFraction(uid)).roundToInt()
    layout(placeable.width, height) { placeable.place(0, 0) }
}
