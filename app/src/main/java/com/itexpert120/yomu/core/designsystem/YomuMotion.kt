package com.itexpert120.yomu.core.designsystem

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Yomu's unified motion language. One calm vocabulary — fade + scale + slide — so every
 * appearing/disappearing surface across the app (reader chrome, menus, library and details
 * transitions) blends between states the same way instead of each animating ad hoc.
 *
 * Grammar: springs for spatial motion (scale/slide) so it feels physical and tweens for fades.
 * Spatial springs are near-critical (no visible bounce) to match the reader's calm tone.
 */
object YomuMotion {
    val Emphasized = PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val FadeInMillis = 220
    const val FadeOutMillis = 160
    const val FadeThroughDurationMillis = 300
    const val FadeThroughScaleFrom = 0.92f
    const val ScreenTransitionDurationMillis = 300
    const val ScreenTransitionFadeOutMillis = 105
    const val ScreenTransitionFadeInMillis =
        ScreenTransitionDurationMillis - ScreenTransitionFadeOutMillis
    val ScreenTransitionDistance = 30.dp

    const val FabExpandMillis = 260
    const val FabCollapseMillis = 200
    const val FabLabelFadeInMillis = 180
    const val FabLabelFadeInDelayMillis = 40
    const val FabLabelFadeOutMillis = 120

    const val PopupScaleFrom = 0.90f
}

/** Honors the system's reduced-motion setting without calling the API 26 method on older devices. */
fun yomuAnimationsEnabled(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

/**
 * Enter for edge-anchored chrome (bars, pills): fade + slide from its own edge — bottom-anchored
 * bars slide up from below, top-anchored bars slide down from above — so it reads as sliding off
 * its edge rather than materializing in place.
 */
fun yomuChromeEnter(fromBottom: Boolean = true): EnterTransition {
    if (!yomuAnimationsEnabled()) return EnterTransition.None
    val offset: (Int) -> Int = if (fromBottom) { h -> h / 3 } else { h -> -h / 3 }
    return fadeIn(spring(stiffness = 380f)) +
        slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f), initialOffsetY = offset)
}

fun yomuChromeExit(toBottom: Boolean = true): ExitTransition {
    if (!yomuAnimationsEnabled()) return ExitTransition.None
    val offset: (Int) -> Int = if (toBottom) { h -> h / 3 } else { h -> -h / 3 }
    return fadeOut(tween(YomuMotion.FadeOutMillis, easing = YomuMotion.EmphasizedAccel)) +
        slideOutVertically(spring(dampingRatio = 0.85f, stiffness = 380f), targetOffsetY = offset)
}

/** Enter for popups/menus that should "materialize" in place — scale + fade, no slide. */
fun yomuPopupEnter(origin: TransformOrigin = TransformOrigin.Center): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    fadeIn(tween(YomuMotion.FadeInMillis, easing = YomuMotion.EmphasizedDecel)) +
        scaleIn(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
            initialScale = YomuMotion.PopupScaleFrom,
            transformOrigin = origin,
        )
}

fun yomuPopupExit(origin: TransformOrigin = TransformOrigin.Center): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    fadeOut(tween(YomuMotion.FadeOutMillis, easing = YomuMotion.EmphasizedAccel)) +
        scaleOut(
            animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
            targetScale = YomuMotion.PopupScaleFrom,
            transformOrigin = origin,
        )
}

/** Fade-through for non-directional content handoffs, such as loading-to-library state changes. */
fun yomuFadeThroughEnter(): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    val fadeOutMillis = (YomuMotion.FadeThroughDurationMillis * 0.35f).toInt()
    fadeIn(
        tween(
            durationMillis = YomuMotion.FadeThroughDurationMillis - fadeOutMillis,
            delayMillis = fadeOutMillis,
            easing = YomuMotion.EmphasizedDecel,
        ),
    ) + scaleIn(
        animationSpec = tween(
            durationMillis = YomuMotion.FadeThroughDurationMillis,
            easing = YomuMotion.EmphasizedDecel,
        ),
        initialScale = YomuMotion.FadeThroughScaleFrom,
    )
}

fun yomuFadeThroughExit(): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    val fadeOutMillis = (YomuMotion.FadeThroughDurationMillis * 0.35f).toInt()
    fadeOut(
        tween(
            durationMillis = fadeOutMillis,
            easing = YomuMotion.EmphasizedAccel,
        ),
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = YomuMotion.FadeThroughDurationMillis,
            easing = YomuMotion.EmphasizedAccel,
        ),
        targetScale = YomuMotion.FadeThroughScaleFrom,
    )
}

/**
 * A Material shared-axis X handoff tuned for Yomu. Both surfaces travel the same short, fixed
 * distance, while a 35% fade-through removes the outgoing surface before revealing the incoming
 * one. By the time the incoming surface is visible, the emphasized spatial curve has brought its
 * boundary to the viewport edge, avoiding a hard vertical seam or translucent content overlap.
 */
fun yomuScreenEnter(
    travelDistancePx: Int,
    forward: Boolean = true,
): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    fadeIn(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionFadeInMillis,
            delayMillis = YomuMotion.ScreenTransitionFadeOutMillis,
            easing = YomuMotion.EmphasizedDecel,
        ),
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionDurationMillis,
            easing = YomuMotion.Emphasized,
        ),
        initialOffsetX = { width ->
            val travel = travelDistancePx.coerceAtMost(width).coerceAtLeast(1)
            if (forward) travel else -travel
        },
    )
}

fun yomuScreenExit(
    travelDistancePx: Int,
    forward: Boolean = true,
): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    fadeOut(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionFadeOutMillis,
            easing = YomuMotion.EmphasizedAccel,
        ),
    ) +
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = YomuMotion.ScreenTransitionDurationMillis,
                easing = YomuMotion.Emphasized,
            ),
            targetOffsetX = { width ->
                val travel = travelDistancePx.coerceAtMost(width).coerceAtLeast(1)
                if (forward) -travel else travel
            },
        )
}

/**
 * A directional content swap for tab/segment changes: the incoming content slides in horizontally
 * (toward the left when moving forward, right when moving back) with a cross-fade, so the tabs feel
 * like they move with the content. [forward] is true when switching to a later tab than the
 * current one.
 */
fun <S> AnimatedContentTransitionScope<S>.yomuContentSwap(forward: Boolean = true): ContentTransform {
    if (!yomuAnimationsEnabled()) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 320f)
    return (
        fadeIn(tween(YomuMotion.FadeInMillis, easing = YomuMotion.EmphasizedDecel)) +
            slideIntoContainer(direction, animationSpec = slide)
        ) togetherWith (
        fadeOut(tween(YomuMotion.FadeOutMillis, easing = YomuMotion.EmphasizedAccel)) +
            slideOutOfContainer(direction, animationSpec = slide)
        )
}
