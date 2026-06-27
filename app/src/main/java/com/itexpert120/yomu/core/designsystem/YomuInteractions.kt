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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

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
