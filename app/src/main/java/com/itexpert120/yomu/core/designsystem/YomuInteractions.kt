@file:OptIn(ExperimentalFoundationApi::class)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Yomu's anti-Material press affordance — the deliberate replacement for the Material ripple this
 * design system removes (`indication = null`). The surface dips slightly while held, spring-eased,
 * so a tap is acknowledged under the finger without any ink. [onLongClick], when set, fires a light
 * haptic first — the native cue for a long-press, which has no hover precursor on a touch screen.
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
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "yomuPressScale",
    )
    val haptics = LocalHapticFeedback.current
    val longClick: (() -> Unit)? = onLongClick?.let { action ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
    scale(scale).combinedClickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onLongClick = longClick,
        onClick = onClick,
    )
}

/**
 * A subtle scroll-edge fade drawn over a scrolling surface, so content dissolves into [color] at the
 * top and/or bottom edge instead of hard-cutting under a header or at the panel/screen edge. Keeps the
 * scroll affordance consistent across the app.
 *
 * Pass the surface colour the scroll area sits on ([YomuColors.appBackground] for screens,
 * [YomuColors.panel] for sheets). Gate [top]/[bottom] on the list's `canScrollBackward` /
 * `canScrollForward` so a fade only appears when there is more content in that direction; the fade
 * cross-fades in/out as that changes.
 *
 * Apply it OUTSIDE (before) a `verticalScroll(...)` modifier, or directly on a `LazyColumn`/
 * `LazyVerticalGrid` modifier, so the gradient stays pinned to the viewport rather than scrolling
 * with the content.
 */
fun Modifier.yomuScrollEdgeShadow(
    color: Color,
    top: Boolean = false,
    bottom: Boolean = false,
    height: Dp = 20.dp,
): Modifier = composed {
    val topAlpha by animateFloatAsState(
        targetValue = if (top) 1f else 0f,
        label = "yomuScrollEdgeTop",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (bottom) 1f else 0f,
        label = "yomuScrollEdgeBottom",
    )
    drawWithContent {
        drawContent()
        val h = height.toPx()
        if (h <= 0f) return@drawWithContent
        if (topAlpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(colors = listOf(color, Color.Transparent), startY = 0f, endY = h),
                size = Size(size.width, h),
                alpha = topAlpha,
            )
        }
        if (bottomAlpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, color),
                    startY = size.height - h,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
                alpha = bottomAlpha,
            )
        }
    }
}
