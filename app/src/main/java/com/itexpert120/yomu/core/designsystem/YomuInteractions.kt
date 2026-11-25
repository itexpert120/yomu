@file:OptIn(ExperimentalFoundationApi::class)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stack's press: the element scales to [pressedScale] (critically damped, never bouncing) and a
 * soft highlight in its own [shape] fades in while it is held, reaching [pressOutset] past its
 * bounds ([YomuPressIndication]). [onLongClick], when set, fires a light haptic first — the native
 * cue for a long-press.
 *
 * Apply this EARLY in the modifier chain (before `clip`/`background`/`border`) so the whole surface —
 * fill, border, and content — scales together rather than just the inner content.
 *
 * Keyboard focus (reached by keys, never touch or the window's initial focus) draws Stack's 2dp
 * ink ring and a mouse/stylus hover an 8% ink tint, both in
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
    // How far the press highlight reaches past the element (clipped away when the caller clips).
    pressOutset: Dp = YomuPressOutset,
    // The press tint; unspecified = the content colour (pass the on-colour over a filled container).
    pressColor: Color = Color.Unspecified,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    // The press highlight sees presses only; hover and focus are drawn here in the element's shape.
    val presses = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction) presses.emit(it) }
    }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    // The ring is for keyboard navigation only: it shows once a key has reached the element while
    // it is focused (a Tab or arrow press lands its key-up on the element it moved focus to), never
    // for focus given on touch or the window's initial focus.
    var keyed by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (!focused) keyed = false }
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
            if (focused && keyed) {
                // Inset by half the stroke so the whole ring lands inside the element's bounds.
                val stroke = 2.dp.toPx()
                val inner = Size(size.width - stroke, size.height - stroke)
                translate(stroke / 2f, stroke / 2f) {
                    drawOutline(shape.createOutline(inner, layoutDirection, this), color = focusColor, style = Stroke(stroke))
                }
            }
        }
        .indication(presses, remember(shape, pressOutset, pressColor) { YomuPressIndication(shape, pressOutset, pressColor) })
        .onPreviewKeyEvent {
            if (focused) keyed = true
            false
        }
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
