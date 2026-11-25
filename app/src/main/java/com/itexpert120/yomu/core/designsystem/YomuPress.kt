package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far Stack's press highlight reaches past an element: a little breathing room. */
val YomuPressOutset: Dp = 4.dp

/** The highlight's shape where the element's own is unknown (a plain `clickable`): a soft row corner. */
val YomuPressDefaultShape: Shape = RoundedCornerShape(14.dp)

/** Stack's press tint: the content colour at 12% (spec), never a ripple wave. */
private const val PressAlpha = 0.12f

/**
 * Stack's press highlight, app-wide (it is the theme's `LocalIndication`, and [yomuPressable] uses
 * it with the element's shape): while an element is held, a soft tint of the content colour fades
 * in over [shape], drawn [outset] past the element's bounds so it never hugs the element tightly.
 * It fades in on the fast effects spring and out on the default one; a quick tap still shows it
 * fully before it fades. A caller that clips the element (pills, round icon buttons) clips the
 * outset away, so filled shapes are tinted exactly within themselves.
 */
class YomuPressIndication(
    private val shape: Shape = YomuPressDefaultShape,
    private val outset: Dp = YomuPressOutset,
    // The tint's colour; unspecified = the content colour where the element sits.
    private val color: Color = Color.Unspecified,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = YomuPressNode(interactionSource, shape, outset, color)

    override fun equals(other: Any?): Boolean = other is YomuPressIndication && other.shape == shape && other.outset == outset && other.color == color

    override fun hashCode(): Int = (31 * shape.hashCode() + outset.hashCode()) * 31 + color.hashCode()
}

private class YomuPressNode(
    private val interactions: InteractionSource,
    private val shape: Shape,
    private val outset: Dp,
    private val tint: Color,
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val held = mutableListOf<PressInteraction.Press>()
            var fadeIn: Job? = null
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        held += interaction
                        if (!yomuAnimationsEnabled()) {
                            strength.snapTo(1f)
                        } else {
                            fadeIn = launch { strength.animateTo(1f, YomuMotion.scheme.fastEffectsSpec()) }
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val press = (interaction as? PressInteraction.Release)?.press ?: (interaction as PressInteraction.Cancel).press
                        held -= press
                        if (held.isEmpty()) {
                            val pending = fadeIn
                            launch {
                                // Let a quick tap's highlight arrive before it leaves.
                                pending?.join()
                                if (held.isEmpty()) {
                                    if (yomuAnimationsEnabled()) strength.animateTo(0f, YomuMotion.scheme.defaultEffectsSpec()) else strength.snapTo(0f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = strength.value
        if (a <= 0f) return
        val o = outset.toPx()
        val color = tint.takeOrElse { currentValueOf(LocalContentColor) }
        translate(-o, -o) {
            drawOutline(
                outline = shape.createOutline(Size(size.width + o * 2, size.height + o * 2), layoutDirection, this),
                color = color.copy(alpha = color.alpha * PressAlpha * a),
            )
        }
    }
}
