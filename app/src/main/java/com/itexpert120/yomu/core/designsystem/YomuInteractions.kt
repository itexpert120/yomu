@file:OptIn(ExperimentalFoundationApi::class)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared press affordance for the migration bridge. Native Material indication remains enabled so
 * touch, keyboard, and accessibility interactions feel like Android; the small spring scale adds
 * the expressive emphasis without replacing the platform cue. [onLongClick], when set, fires a
 * light haptic first — the native cue for a long-press.
 *
 * Apply this EARLY in the modifier chain (before `clip`/`background`/`border`) so the whole surface —
 * fill, border, and content — scales together rather than just the inner content.
 */
fun Modifier.yomuPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val motionEnabled = yomuAnimationsEnabled()
    val scale = animateFloatAsState(
        targetValue = if (motionEnabled && pressed && enabled) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "yomuPressScale",
    )
    val haptics = LocalHapticFeedback.current
    val focusColor = MaterialTheme.colorScheme.primary
    val longClick: (() -> Unit)? = onLongClick?.let { action ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
    // Read the animated scale inside the layer block: the press animates on the render path at the
    // display's native refresh rate without recomposing or relaying out the pressed element.
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = 2.dp.toPx()
                val radius = 12.dp.toPx()
                drawRoundRect(
                    color = focusColor,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(stroke),
                )
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onLongClick = longClick,
            onClick = onClick,
        )
}

/**
 * A soft scroll-edge fade: content dissolves toward the top and/or bottom edge instead of
 * hard-cutting under a header or at the panel/screen edge. The content itself is faded with an
 * alpha mask (it never paints a colour over it), so it works on any background — sheets, tonal
 * cards, OLED black — and follows an eased curve so it reads as depth, not a band.
 *
 * Gate [top]/[bottom] on the list's `canScrollBackward` / `canScrollForward` so a fade only
 * appears when there is more content in that direction; it cross-fades in/out as that changes.
 *
 * Apply it OUTSIDE (before) a `verticalScroll(...)` modifier, or directly on a `LazyColumn`/
 * `LazyVerticalGrid` modifier, so the fade stays pinned to the viewport.
 */
fun Modifier.yomuScrollEdgeShadow(
    top: Boolean = false,
    bottom: Boolean = false,
    height: Dp = 28.dp,
): Modifier = composed {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val topState = animateFloatAsState(
        targetValue = if (top) 1f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) effects else snap(),
        label = "yomuScrollEdgeTop",
    )
    val bottomState = animateFloatAsState(
        targetValue = if (bottom) 1f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) effects else snap(),
        label = "yomuScrollEdgeBottom",
    )
    // Offscreen so the DstOut mask erases only this content, not what lies behind it.
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        // Alphas are read in the draw phase only, so the fade never recomposes the scroll container.
        val topAlpha = topState.value
        val bottomAlpha = bottomState.value
        val h = height.toPx()
        if (h <= 0f) return@drawWithContent
        if (topAlpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(*edgeFadeStops(topAlpha, atStart = true), startY = 0f, endY = h),
                size = Size(size.width, h),
                blendMode = BlendMode.DstOut,
            )
        }
        if (bottomAlpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(*edgeFadeStops(bottomAlpha, atStart = false), startY = size.height - h, endY = size.height),
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}

/**
 * Mask stops for an edge fade: fully erasing ([strength]) at the edge and easing to nothing inward
 * on a smoothstep curve, so there is no visible line where the fade ends.
 */
private fun edgeFadeStops(strength: Float, atStart: Boolean): Array<Pair<Float, Color>> = Array(7) { i ->
    val t = i / 6f
    // t = distance from the edge (0) inward (1); erase = 1 at the edge, 0 inside.
    val inward = 1f - t
    val erase = inward * inward * (3f - 2f * inward)
    val position = if (atStart) t else 1f - t
    position to Color.Black.copy(alpha = erase * strength)
}.sortedBy { it.first }.toTypedArray()
