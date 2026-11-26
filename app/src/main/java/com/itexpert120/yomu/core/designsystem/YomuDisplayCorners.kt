package com.itexpert120.yomu.core.designsystem

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** The display's corner radii, by physical corner (0dp where that corner is square). */
@Immutable
data class YomuDisplayCorners(val topLeft: Dp, val topRight: Dp, val bottomLeft: Dp, val bottomRight: Dp) {
    val top: Dp get() = max(topLeft, topRight)
    val bottom: Dp get() = max(bottomLeft, bottomRight)
}

/**
 * The device display's rounded corners as this window sees them (API 31+), or null on a
 * square-cornered display, before API 31, and in previews. Kept current: re-read whenever the
 * window lays out again — rotation, folding or unfolding, a resized or moved window.
 */
@Composable
fun rememberDisplayCornerRadii(): YomuDisplayCorners? {
    if (LocalInspectionMode.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val view = LocalView.current
    val density = LocalDensity.current
    var corners by remember(view, density) { mutableStateOf(readCorners(view, density)) }
    DisposableEffect(view, density) {
        val root = view.rootView
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> corners = readCorners(view, density) }
        root.addOnLayoutChangeListener(listener)
        corners = readCorners(view, density)
        onDispose { root.removeOnLayoutChangeListener(listener) }
    }
    return corners
}

@RequiresApi(Build.VERSION_CODES.S)
private fun readCorners(view: View, density: Density): YomuDisplayCorners? {
    val insets = view.rootWindowInsets ?: return null
    fun radius(position: Int): Dp? = insets.getRoundedCorner(position)?.radius?.let { with(density) { it.toDp() } }
    val tl = radius(RoundedCorner.POSITION_TOP_LEFT)
    val tr = radius(RoundedCorner.POSITION_TOP_RIGHT)
    val bl = radius(RoundedCorner.POSITION_BOTTOM_LEFT)
    val br = radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
    if (tl == null && tr == null && bl == null && br == null) return null
    return YomuDisplayCorners(tl ?: 0.dp, tr ?: 0.dp, bl ?: 0.dp, br ?: 0.dp)
}

/** Stack's sheet corner when the display gives none (and in previews). */
val YomuSheetCornerFallback: Dp = 32.dp

/** The smallest a sheet's top corners ever get. */
private val YomuSheetCornerMinimum = 24.dp

/**
 * The top corners of a sheet that rises from the bottom edge (bottom sheets, the book sheet, the
 * reader rising from it): the display's bottom-corner radius, so the sheet echoes the device's
 * own shape; 32dp where the display reports none, never below 24dp.
 */
@Composable
fun yomuSheetCorner(): Dp {
    val bottom = rememberDisplayCornerRadii()?.bottom?.takeIf { it > 0.dp } ?: return YomuSheetCornerFallback
    return max(bottom, YomuSheetCornerMinimum)
}

/**
 * The corner of a surface floating [gap] in from a screen edge (a floating notice, a card near an
 * edge): concentric with the display's corner — its radius less the gap — but never below
 * [minimum] (the surface's own corner) and the minimum wherever the display reports none.
 */
@Composable
fun yomuConcentricCorner(gap: Dp, minimum: Dp, bottom: Boolean = true): Dp {
    val corners = rememberDisplayCornerRadii() ?: return minimum
    val display = if (bottom) corners.bottom else corners.top
    return max(display - gap, minimum)
}
