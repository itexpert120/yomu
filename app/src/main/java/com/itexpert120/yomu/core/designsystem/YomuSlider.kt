package com.itexpert120.yomu.core.designsystem

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A value-driven slider on top of Material 3's [SliderState] API (the plain `value` overload is
 * deprecated since material3 1.5.0). The caller still owns [value]: the state follows it, so
 * buttons, resets, and coercion in [onValueChange] are reflected in the thumb.
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
    Slider(
        state = state,
        onValueChange = {
            state.value = it
            onValueChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
