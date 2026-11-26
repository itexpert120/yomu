package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * A container whose width is moving (the library narrowing beside a book pane): [moving] while it
 * moves, and [lag] — how much wider (+) or narrower (−), in px, it was when the move began than it
 * is now. Read while measuring. Provided by whoever moves the width; 0 / not moving by default.
 */
@Immutable
class YomuWidthMove(val moving: Boolean, val lag: () -> Int)

val LocalYomuWidthMove = staticCompositionLocalOf { YomuWidthMove(moving = false) { 0 } }

/**
 * Adaptive grid columns ([GridCells.Adaptive] of [minSize]) that hold still while their container's
 * width is moving ([LocalYomuWidthMove]): the column count stays the one for the width the move
 * began at, so the cells just grow or shrink with the width — no item jumps to another column
 * mid-move — and it changes once, when the move ends, where keyed items' placement animation
 * glides them to their new places. At rest it is exactly [GridCells.Adaptive].
 */
@Composable
fun yomuSteadyAdaptiveCells(minSize: Dp): GridCells {
    val move = LocalYomuWidthMove.current
    // A new instance whenever the move starts or ends, so the grid re-counts its columns then.
    return remember(minSize, move) { SteadyAdaptiveCells(minSize, if (move.moving) move.lag else NoLag) }
}

private val NoLag: () -> Int = { 0 }

private class SteadyAdaptiveCells(private val minSize: Dp, private val lag: () -> Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val basis = (availableSize + lag()).coerceAtLeast(0)
        val count = ((basis + spacing) / (minSize.roundToPx() + spacing)).coerceAtLeast(1)
        val withoutSpacing = (availableSize - spacing * (count - 1)).coerceAtLeast(0)
        val slot = withoutSpacing / count
        val remainder = withoutSpacing % count
        return List(count) { slot + if (it < remainder) 1 else 0 }
    }
}
