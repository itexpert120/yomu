package com.itexpert120.yomu.core.designsystem

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

/**
 * Yomu's unified motion language. One calm vocabulary — fade + scale + slide — so every
 * appearing/disappearing surface across the app (reader chrome, menus, library and details
 * transitions) blends between states the same way instead of each animating ad hoc.
 *
 * Grammar: springs for spatial motion (scale/slide) so it feels physical and tweens for fades.
 * Spatial springs are near-critical (no visible bounce) to match the reader's calm tone.
 */
object YomuMotion {
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val FadeInMillis = 220
    const val FadeOutMillis = 160
    const val FadeThroughDurationMillis = 300
    const val FadeThroughScaleFrom = 0.92f
    const val ScreenTransitionDurationMillis = 300
    const val ScreenTransitionFadeDurationMillis = 195
    const val ScreenTransitionTravelDivisor = 20

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
 * A restrained shared-axis X handoff. Both surfaces stay overlapped: the incoming surface travels
 * only five percent of the viewport while the outgoing surface moves the same distance in the
 * opposite direction. Forward navigation enters from the right; backward navigation mirrors both
 * movements.
 */
fun yomuScreenEnter(forward: Boolean = true): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    fadeIn(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionFadeDurationMillis,
            easing = LinearOutSlowInEasing,
        ),
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { width ->
            val travel = (width / YomuMotion.ScreenTransitionTravelDivisor).coerceAtLeast(1)
            if (forward) travel else -travel
        },
    )
}

fun yomuScreenExit(forward: Boolean = true): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    fadeOut(
        animationSpec = tween(
            durationMillis = YomuMotion.ScreenTransitionFadeDurationMillis,
            easing = FastOutLinearInEasing,
        ),
    ) +
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = YomuMotion.ScreenTransitionDurationMillis,
                easing = FastOutSlowInEasing,
            ),
            targetOffsetX = { width ->
                val travel = (width / YomuMotion.ScreenTransitionTravelDivisor).coerceAtLeast(1)
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
