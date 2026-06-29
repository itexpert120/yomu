package com.itexpert120.yomu.core.designsystem

import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Yomu's unified motion language. One calm vocabulary — fade + scale + slide + blur — so every
 * appearing/disappearing surface across the app (reader chrome, menus, library and details
 * transitions) blends between states the same way instead of each animating ad hoc.
 *
 * Grammar: springs for spatial motion (scale/slide) so it feels physical; tweens for fade/blur so
 * they don't overshoot. Spatial springs are near-critical (no visible bounce) to match the reader's
 * calm tone. Blur is only ever applied to a small chrome element's own pixels during its short
 * enter/exit — never the live reading WebView (perf + SurfaceView correctness) — and is a no-op
 * below API 31, where motion degrades cleanly to fade + scale.
 */
object YomuMotion {
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val FadeInMillis = 220
    const val FadeOutMillis = 160

    const val ChromeScaleFrom = 0.92f
    const val PopupScaleFrom = 0.90f

    val ChromeBlur = 12.dp
    val PopupBlur = 16.dp
}

/** Enter for bottom-anchored chrome (bars, pills): fade + settle-up scale (+ a small slide). */
fun yomuChromeEnter(fromBottom: Boolean = true): EnterTransition {
    val enter = fadeIn(spring(stiffness = 380f)) +
        scaleIn(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            initialScale = YomuMotion.ChromeScaleFrom,
            transformOrigin = TransformOrigin(0.5f, 1f),
        )
    return if (fromBottom) {
        enter + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 }
    } else {
        enter
    }
}

fun yomuChromeExit(toBottom: Boolean = true): ExitTransition {
    val exit = fadeOut(tween(YomuMotion.FadeOutMillis, easing = YomuMotion.EmphasizedAccel)) +
        scaleOut(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            targetScale = YomuMotion.ChromeScaleFrom,
            transformOrigin = TransformOrigin(0.5f, 1f),
        )
    return if (toBottom) {
        exit + slideOutVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 }
    } else {
        exit
    }
}

/** Enter for popups/menus that should "materialize" in place — scale + fade, no slide. */
fun yomuPopupEnter(origin: TransformOrigin = TransformOrigin.Center): EnterTransition = fadeIn(tween(YomuMotion.FadeInMillis, easing = YomuMotion.EmphasizedDecel)) +
    scaleIn(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        initialScale = YomuMotion.PopupScaleFrom,
        transformOrigin = origin,
    )

fun yomuPopupExit(origin: TransformOrigin = TransformOrigin.Center): ExitTransition = fadeOut(tween(YomuMotion.FadeOutMillis, easing = YomuMotion.EmphasizedAccel)) +
    scaleOut(
        animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        targetScale = YomuMotion.PopupScaleFrom,
        transformOrigin = origin,
    )

/**
 * A directional content swap for tab/segment changes: the incoming content slides in horizontally
 * (toward the left when moving forward, right when moving back) with a cross-fade, so the tabs feel
 * like they move with the content. Pair with [yomuChromeBlur] on the content for the smooth
 * blur-through. [forward] is true when switching to a later tab than the current one.
 */
fun <S> AnimatedContentTransitionScope<S>.yomuContentSwap(forward: Boolean = true): ContentTransform {
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

/**
 * Animates a blur in lockstep with the SAME [AnimatedVisibilityScope] enter/exit transition, so the
 * element blurs as it fades/scales in and out. API 31+ only (below, returns the modifier unchanged);
 * the blur Modifier is dropped entirely at rest (radius 0) so steady state has zero blur cost.
 */
@Composable
fun Modifier.yomuChromeBlur(
    scope: AnimatedVisibilityScope,
    maxRadius: Dp = YomuMotion.ChromeBlur,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val radius by scope.transition.animateDp(label = "yomuChromeBlur") { state ->
        if (state == EnterExitState.Visible) 0.dp else maxRadius
    }
    return if (radius > 0.5.dp) this.blur(radius, BlurredEdgeTreatment.Unbounded) else this
}
