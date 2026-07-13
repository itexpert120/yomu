package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

/**
 * Custom HSV color picker: a saturation/value square plus a hue rail. Emits live as the user
 * drags. Built from Canvas + gestures (no Material color picker exists).
 */
@Composable
fun YomuColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = remember(color) { FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }

    fun emit() = onColorChange(Color.hsv(hue, sat, value))

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.md))
                .semantics {
                    contentDescription = "Saturation and brightness"
                    stateDescription = "${(sat * 100).toInt()}% saturation, ${(value * 100).toInt()}% brightness"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, 20)
                    setProgress { requested ->
                        value = requested.coerceIn(0f, 1f)
                        emit()
                        true
                    }
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> sat = (sat - 0.05f).coerceIn(0f, 1f)
                        Key.DirectionRight -> sat = (sat + 0.05f).coerceIn(0f, 1f)
                        Key.DirectionDown -> value = (value - 0.05f).coerceIn(0f, 1f)
                        Key.DirectionUp -> value = (value + 0.05f).coerceIn(0f, 1f)
                        else -> return@onKeyEvent false
                    }
                    emit()
                    true
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        sat = (offset.x / size.width).coerceIn(0f, 1f)
                        value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { emit() },
                        onDragCancel = { emit() },
                    ) { change, _ ->
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val hueColor = Color.hsv(hue, 1f, 1f)
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(sat * size.width, (1f - value) * size.height),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .semantics {
                    contentDescription = "Hue"
                    stateDescription = "${hue.toInt()} degrees"
                    progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f, 72)
                    setProgress { requested ->
                        hue = requested.coerceIn(0f, 360f)
                        emit()
                        true
                    }
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionDown -> hue = (hue - 5f).mod(360f)
                        Key.DirectionRight, Key.DirectionUp -> hue = (hue + 5f).mod(360f)
                        else -> return@onKeyEvent false
                    }
                    emit()
                    true
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { emit() },
                        onDragCancel = { emit() },
                    ) { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(HueColors))
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset((hue / 360f) * size.width, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

private val HueColors = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)
