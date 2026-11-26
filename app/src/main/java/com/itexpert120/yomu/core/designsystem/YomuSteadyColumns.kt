package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * A container laid out narrower than it can be (the library beside a book pane): [fullExtra] is
 * how many px wider its full width is than its current layout, read while measuring. Provided by
 * whoever narrows it; 0 by default.
 */
@Immutable
class YomuWidthMove(val fullExtra: () -> Int)

val LocalYomuWidthMove = staticCompositionLocalOf { YomuWidthMove { 0 } }

/**
 * Adaptive grid columns of at least [minSize] whose cells never change size when their container
 * is narrowed ([LocalYomuWidthMove]): the cell width is the one [GridCells.Adaptive] gives at the
 * container's full width, and a narrower layout just fits fewer of those cells. So opening a book
 * pane beside the library only ever changes how many covers fit in a row — keyed covers glide to
 * their new places by translation — never their size. At full width it is [GridCells.Adaptive].
 */
@Composable
fun yomuSteadyAdaptiveCells(minSize: Dp): GridCells {
    val move = LocalYomuWidthMove.current
    return remember(minSize, move) { SteadyAdaptiveCells(minSize, move.fullExtra) }
}

/** The cell sizes for [available] px (of which [fullExtra] more at full width), [spacing] apart. */
internal fun steadyCellSizes(available: Int, fullExtra: Int, minSizePx: Int, spacing: Int): List<Int> {
    val full = available + fullExtra.coerceAtLeast(0)
    val fullCount = ((full + spacing) / (minSizePx + spacing)).coerceAtLeast(1)
    val cell = ((full - spacing * (fullCount - 1)) / fullCount).coerceAtLeast(1)
    if (fullExtra <= 0) {
        // Full width: exactly Adaptive, the leftover pixels spread over the first cells.
        val withoutSpacing = (available - spacing * (fullCount - 1)).coerceAtLeast(0)
        val slot = withoutSpacing / fullCount
        val remainder = withoutSpacing % fullCount
        return List(fullCount) { slot + if (it < remainder) 1 else 0 }
    }
    val count = ((available + spacing) / (cell + spacing)).coerceAtLeast(1)
    return List(count) { cell.coerceAtMost(available) }
}

private class SteadyAdaptiveCells(private val minSize: Dp, private val fullExtra: () -> Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> = steadyCellSizes(availableSize, fullExtra(), minSize.roundToPx(), spacing)
}
