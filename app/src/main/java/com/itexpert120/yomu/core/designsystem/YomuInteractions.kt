@file:OptIn(ExperimentalFoundationApi::class)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
 *
 * Keyboard focus draws Stack's 2dp ink ring and a mouse/stylus hover an 8% ink tint, both in
 * [shape] — pass the element's own shape (`CircleShape` for round buttons, a pill for pills and
 * chips) so they follow its outline. The indication only answers presses; hover and focus are ours.
 */
fun Modifier.yomuPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.98f,
    role: Role? = Role.Button,
    shape: Shape = YomuPressableDefaultShape,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    // The ripple sees presses only, so its rectangular hover/focus state layers don't stack on ours.
    val presses = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction) presses.emit(it) }
    }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val motionEnabled = yomuAnimationsEnabled()
    val scale = animateFloatAsState(
        targetValue = if (motionEnabled && pressed && enabled) pressedScale else 1f,
        // Stack: subtle and critically damped — the press never bounces.
        animationSpec = PressSpring,
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
            if (hovered && enabled) {
                drawOutline(shape.createOutline(size, layoutDirection, this), color = focusColor.copy(alpha = HoverAlpha))
            }
            if (focused) {
                // Inset by half the stroke so the whole ring lands inside the element's bounds.
                val stroke = 2.dp.toPx()
                val inner = Size(size.width - stroke, size.height - stroke)
                translate(stroke / 2f, stroke / 2f) {
                    drawOutline(shape.createOutline(inner, layoutDirection, this), color = focusColor, style = Stroke(stroke))
                }
            }
        }
        .indication(presses, LocalIndication.current)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = longClick,
            onClick = onClick,
        )
}

private val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/** Stack's hover state: an 8% ink tint. */
private const val HoverAlpha = 0.08f

/** The focus ring's shape when the caller names none: today's 12dp rounding. */
val YomuPressableDefaultShape: Shape = RoundedCornerShape(12.dp)

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
