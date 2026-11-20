package com.itexpert120.yomu.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
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
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Yomu's unified motion language, built on the Material 3 Expressive motion scheme so custom
 * transitions share the springs Material components use. Grammar: geometry (slide, scale, size,
 * shape) takes *spatial* springs, which may overshoot; opacity and colour take *effects* springs,
 * which never do. Small elements use the fast tokens, surfaces the default ones. Screen-level
 * shared-axis transitions keep fixed timings so the handoff never shows a seam.
 */
object YomuMotion {
    val scheme = MotionScheme.expressive()
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

    const val PopupScaleFrom = 0.90f

    // Predictive back on dialog-like surfaces (sheets, dialogs, cover viewer): how far they shrink.
    const val BackPreviewScale = 0.90f

    // Back out of a child screen is a page slide (One UI style): the leaving page travels its full
    // width at full size, the page underneath follows from [BackParallaxFraction] of the width to
    // its left (parallax) and starts dimmed so two same-coloured pages stay distinct. Both slides
    // are linear in transition progress, so while the gesture scrubs them the page stays locked to
    // the back progress instead of running ahead of the finger.
    const val ScreenPopDurationMillis = 280
    const val BackParallaxFraction = 0.30f
    const val BackPreviewDim = 0.24f
    val BackEdgeShadowWidth = 32.dp

    // Kept faint: it only marks the seam between two pages, it is not a drop shadow.
    const val BackEdgeShadowAlpha = 0.10f
}

/** Honors the system's reduced-motion setting without calling the API 26 method on older devices. */
fun yomuAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/**
 * Enter for edge-anchored chrome (bars, pills): fade + slide from its own edge — bottom-anchored
 * bars slide up from below, top-anchored bars slide down from above — so it reads as sliding off
 * its edge rather than materializing in place.
 */
fun yomuChromeEnter(fromBottom: Boolean = true): EnterTransition {
    if (!yomuAnimationsEnabled()) return EnterTransition.None
    val offset: (Int) -> Int = if (fromBottom) { h -> h / 3 } else { h -> -h / 3 }
    return fadeIn(YomuMotion.scheme.defaultEffectsSpec()) +
        slideInVertically(YomuMotion.scheme.defaultSpatialSpec(), initialOffsetY = offset)
}

fun yomuChromeExit(toBottom: Boolean = true): ExitTransition {
    if (!yomuAnimationsEnabled()) return ExitTransition.None
    val offset: (Int) -> Int = if (toBottom) { h -> h / 3 } else { h -> -h / 3 }
    return fadeOut(YomuMotion.scheme.fastEffectsSpec()) +
        slideOutVertically(YomuMotion.scheme.defaultSpatialSpec(), targetOffsetY = offset)
}

/** Enter for popups/menus that should "materialize" in place — scale + fade, no slide. */
fun yomuPopupEnter(origin: TransformOrigin = TransformOrigin.Center): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    fadeIn(YomuMotion.scheme.fastEffectsSpec()) +
        scaleIn(
            animationSpec = YomuMotion.scheme.fastSpatialSpec(),
            initialScale = YomuMotion.PopupScaleFrom,
            transformOrigin = origin,
        )
}

fun yomuPopupExit(origin: TransformOrigin = TransformOrigin.Center): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    fadeOut(YomuMotion.scheme.fastEffectsSpec()) +
        scaleOut(
            animationSpec = YomuMotion.scheme.fastEffectsSpec(),
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
 * The page underneath on back: it slides in from [YomuMotion.BackParallaxFraction] of its width
 * toward the start edge, so it moves slower than the page leaving over it (parallax). Linear, so a
 * scrubbed back gesture moves both pages in a fixed ratio.
 */
fun yomuScreenPopEnter(fromStart: Boolean = true): EnterTransition = if (!yomuAnimationsEnabled()) {
    EnterTransition.None
} else {
    slideInHorizontally(
        animationSpec = tween(YomuMotion.ScreenPopDurationMillis, easing = LinearEasing),
        initialOffsetX = { width ->
            val travel = (width * YomuMotion.BackParallaxFraction).toInt()
            if (fromStart) -travel else travel
        },
    )
}

/**
 * The page leaving on back: it slides its full width toward the end edge, opaque and at full size,
 * like a page being pulled off the stack (the edge shadow is drawn by the nav host's frame).
 */
fun yomuScreenPopExit(towardEnd: Boolean = true): ExitTransition = if (!yomuAnimationsEnabled()) {
    ExitTransition.None
} else {
    slideOutHorizontally(
        animationSpec = tween(YomuMotion.ScreenPopDurationMillis, easing = LinearEasing),
        targetOffsetX = { width -> if (towardEnd) width else -width },
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
    val slide = YomuMotion.scheme.defaultSpatialSpec<IntOffset>()
    return (
        fadeIn(YomuMotion.scheme.defaultEffectsSpec()) +
            slideIntoContainer(direction, animationSpec = slide)
        ) togetherWith (
        fadeOut(YomuMotion.scheme.fastEffectsSpec()) +
            slideOutOfContainer(direction, animationSpec = slide)
        )
}
