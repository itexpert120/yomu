package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A container laid out narrower than it can be (the library beside a book pane): [fullExtra] is
 * how many px wider its full width is than its current layout, read while measuring. Provided by
 * whoever narrows it; 0 by default. It also remembers the grid cell size a [yomuSteadyAdaptiveCells]
 * grid had at full width, so a narrower layout keeps exactly that size.
 */
@Stable
class YomuWidthMove(val fullExtra: () -> Int) {
    internal var fullCellPx = 0

    /** Bumped each time the container is laid out at a new width. */
    var generation = 0
}

val LocalYomuWidthMove = staticCompositionLocalOf { YomuWidthMove { 0 } }

/**
 * Adaptive grid columns of at least [minSize] whose cells never change size when their container
 * is narrowed ([LocalYomuWidthMove]): at full width a cell is [GridCells.Adaptive]'s size (whole
 * pixels, the row's few leftover pixels at its end), and a narrower layout keeps exactly the cell
 * size the grid had at full width and just fits fewer of them. So opening a book pane beside the
 * library only changes how many covers fit in a row — keyed covers glide to their new places by
 * translation — never their size, and closing it gives back the very same layout.
 */
@Composable
fun yomuSteadyAdaptiveCells(minSize: Dp): GridCells {
    val move = LocalYomuWidthMove.current
    return remember(minSize, move) { SteadyAdaptiveCells(minSize, move) }
}

/**
 * The cell sizes for [available] px, [spacing] apart: [fullCellPx] (the full-width cell size, 0 if
 * unknown) when narrowed by [fullExtra] px, else the adaptive size for [minSizePx].
 */
internal fun steadyCellSizes(available: Int, fullExtra: Int, minSizePx: Int, spacing: Int, fullCellPx: Int = 0): List<Int> {
    val full = available + fullExtra.coerceAtLeast(0)
    val cell = if (fullExtra > 0 && fullCellPx > 0) {
        fullCellPx
    } else {
        val fullCount = ((full + spacing) / (minSizePx + spacing)).coerceAtLeast(1)
        ((full - spacing * (fullCount - 1)) / fullCount).coerceAtLeast(1)
    }
    val count = ((available + spacing) / (cell + spacing)).coerceAtLeast(1)
    return List(count) { cell.coerceAtMost(available.coerceAtLeast(1)) }
}

private class SteadyAdaptiveCells(private val minSize: Dp, private val move: YomuWidthMove) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val extra = move.fullExtra()
        val sizes = steadyCellSizes(availableSize, extra, minSize.roundToPx(), spacing, move.fullCellPx)
        if (extra <= 0) move.fullCellPx = sizes.first()
        return sizes
    }
}

/**
 * Glides an element sideways to its new place when its container is laid out at a new width (the
 * library settling beside a book pane, [LocalYomuWidthMove]): it is held where it was and slides to
 * its new x on the default spatial spring, in the placement phase only. Nothing else (scrolling,
 * vertical moves) is delayed, and size changes are not animated.
 */
fun Modifier.yomuGlidePlacement(): Modifier = composed {
    val move = LocalYomuWidthMove.current
    val scope = rememberCoroutineScope()
    val spec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val animate = yomuAnimationsEnabled()
    val shift = remember { Animatable(0f) }
    // [x, generation] at the last placement.
    val last = remember { floatArrayOf(Float.NaN, -1f) }
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val x = coordinates?.positionInRoot()?.x
            if (x != null) {
                val generation = move.generation.toFloat()
                val before = last[0]
                if (animate && !before.isNaN() && last[1] != generation && before != x) {
                    val from = before - x + shift.value
                    scope.launch {
                        shift.snapTo(from)
                        shift.animateTo(0f, spec)
                    }
                }
                last[0] = x - shift.value
                last[1] = generation
            }
            placeable.place(IntOffset(shift.value.roundToInt(), 0))
        }
    }
}
