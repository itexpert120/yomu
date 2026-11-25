package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * Stack's slider — a 4dp track, ink fill and a 6×24 ink handle — on top of Material 3's
 * [SliderState] API (which keeps the drag, keyboard and accessibility behaviour). The caller still
 * owns [value]: the state follows it, so buttons, resets, and coercion in [onValueChange] are
 * reflected in the handle.
 */
@Composable
fun YomuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    // Follow outside changes; a no-op while dragging, since the drag already wrote the same value.
    SideEffect { if (state.value != value) state.value = value }
    val stack = YomuTheme.stack
    Slider(
        state = state,
        onValueChange = {
            state.value = it
            onValueChange(it)
        },
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(Modifier.size(width = 6.dp, height = 24.dp).background(stack.ink, SliderHandleShape))
        },
        track = { sliderState ->
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val range = valueRange
                val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
                // Read while drawing, so a drag only redraws the track.
                val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(stack.track, cornerRadius = radius)
                drawRoundRect(stack.ink, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            }
        },
    )
}

private val SliderHandleShape = RoundedCornerShape(3.dp)
