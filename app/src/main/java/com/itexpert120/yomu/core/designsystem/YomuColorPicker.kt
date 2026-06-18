package com.itexpert120.yomu.core.designsystem

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

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
    val initial = remember { FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) } }
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
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        sat = (offset.x / size.width).coerceIn(0f, 1f)
                        value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
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
                .height(24.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
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
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)
