package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuScreenEnter
import com.itexpert120.yomu.core.designsystem.yomuScreenExit
import com.itexpert120.yomu.core.designsystem.yomuScreenPushEnter
import com.itexpert120.yomu.core.designsystem.yomuScreenPushExit

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * The directional screen slide every NavHost in the app shares: forward moves follow the
 * top-level order (Library → Stats → Settings; anything else counts as forward), pops slide back,
 * and both are mirrored under RTL. A transition onto the same entry, or with system animations
 * off, is instant. The root host keeps its own predictive-back pop on top of [enter]/[exit].
 */
@Stable
internal class YomuScreenTransitions(
    val motionEnabled: Boolean,
    val layoutDirection: LayoutDirection,
    private val travelDistancePx: Int,
) {
    val enter: EnterSpec = { slideIn(isForwardTopLevelMove(initialState.destination, targetState.destination)) }
    val exit: ExitSpec = { slideOut(isForwardTopLevelMove(initialState.destination, targetState.destination)) }
    val popEnter: EnterSpec = { slideIn(forward = false) }

    // The root host opens child screens with a push (the old page stays under the new one) rather
    // than the tab fade-through, so a slow first frame never shows an empty screen.
    val pushEnter: EnterSpec = {
        if (initialState.id == targetState.id || !motionEnabled) {
            EnterTransition.None
        } else {
            yomuScreenPushEnter(travelDistancePx = travelDistancePx, forward = physicalDirection(true, layoutDirection))
        }
    }
    val pushExit: ExitSpec = {
        if (initialState.id == targetState.id || !motionEnabled) {
            ExitTransition.None
        } else {
            yomuScreenPushExit(travelDistancePx = travelDistancePx, forward = physicalDirection(true, layoutDirection))
        }
    }
    val popExit: ExitSpec = { slideOut(forward = false) }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(forward: Boolean): EnterTransition = if (initialState.id == targetState.id || !motionEnabled) {
        EnterTransition.None
    } else {
        yomuScreenEnter(travelDistancePx = travelDistancePx, forward = physicalDirection(forward, layoutDirection))
    }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(forward: Boolean): ExitTransition = if (initialState.id == targetState.id || !motionEnabled) {
        ExitTransition.None
    } else {
        yomuScreenExit(travelDistancePx = travelDistancePx, forward = physicalDirection(forward, layoutDirection))
    }
}

@Composable
internal fun rememberYomuScreenTransitions(): YomuScreenTransitions {
    val motionEnabled = yomuAnimationsEnabled()
    val layoutDirection = LocalLayoutDirection.current
    val travelDistancePx = with(LocalDensity.current) { YomuMotion.ScreenTransitionDistance.roundToPx() }
    return remember(motionEnabled, layoutDirection, travelDistancePx) {
        YomuScreenTransitions(motionEnabled, layoutDirection, travelDistancePx)
    }
}

/** A logical forward move slides toward the reading direction's end: mirrored under RTL. */
internal fun physicalDirection(forward: Boolean, layoutDirection: LayoutDirection): Boolean = when (layoutDirection) {
    LayoutDirection.Ltr -> forward
    LayoutDirection.Rtl -> !forward
}

/** Top-level destinations have a stable left-to-right order; any move involving another screen is forward. */
internal fun isForwardTopLevelMove(fromIndex: Int?, targetIndex: Int?): Boolean = if (fromIndex != null && targetIndex != null) targetIndex > fromIndex else true

private fun isForwardTopLevelMove(from: NavDestination, target: NavDestination): Boolean = isForwardTopLevelMove(from.topLevelIndex(), target.topLevelIndex())

private fun NavDestination.topLevelIndex(): Int? = when {
    hasRoute<Library>() -> 0
    hasRoute<Stats>() -> 1
    hasRoute<Settings>() -> 2
    else -> null
}
