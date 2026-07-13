package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun YomuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: YomuButtonEmphasis = YomuButtonEmphasis.Primary,
) {
    val colors = YomuTheme.colors
    val background = when (emphasis) {
        YomuButtonEmphasis.Primary -> colors.textPrimary
        YomuButtonEmphasis.Secondary -> colors.surfaceRaised
        YomuButtonEmphasis.Ghost -> Color.Transparent
    }
    val foreground = when (emphasis) {
        YomuButtonEmphasis.Primary -> colors.appBackground
        YomuButtonEmphasis.Secondary -> colors.textPrimary
        YomuButtonEmphasis.Ghost -> colors.textSecondary
    }
    val border = when (emphasis) {
        YomuButtonEmphasis.Primary -> colors.textPrimary
        YomuButtonEmphasis.Secondary -> colors.border
        YomuButtonEmphasis.Ghost -> colors.border.copy(alpha = 0.0f)
    }
    Box(
        modifier = modifier
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = foreground, style = YomuTheme.type.control, maxLines = 1)
    }
}

enum class YomuButtonEmphasis { Primary, Secondary, Ghost }

@Composable
fun YomuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    Box(
        modifier = modifier
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(if (selected) colors.textPrimary else colors.surfaceRaised)
            .border(
                1.dp,
                if (selected) colors.textPrimary else colors.border,
                RoundedCornerShape(YomuTheme.radius.pill),
            )
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.appBackground else colors.textSecondary,
            style = YomuTheme.type.caption,
            maxLines = 1,
        )
    }
}

@Composable
fun YomuSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    val count = options.size.coerceAtLeast(1)
    val selected = selectedIndex.coerceIn(0, count - 1)
    val controlHeight = 42.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(3.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val segmentWidth = maxWidth / count
            // The selection pill slides between segments instead of snapping.
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selected,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "segmentIndicator",
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .height(controlHeight)
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .background(colors.textPrimary),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selected
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) colors.appBackground else colors.textSecondary,
                        animationSpec = tween(220),
                        label = "segmentText",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(controlHeight)
                            .clip(RoundedCornerShape(YomuTheme.radius.pill))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelected(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            color = textColor,
                            style = YomuTheme.type.caption,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YomuRangeRow(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = YomuTheme.type.body,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueLabel, color = colors.textMuted, style = YomuTheme.type.mono)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            YomuStepButton(
                text = "-",
                onClick = { onValueChange((value - 0.05f).coerceIn(0f, 1f)) },
            )
            Spacer(Modifier.width(12.dp))
            var sliderSize by remember { mutableStateOf(IntSize.Zero) }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, 20)
                        setProgress { requested ->
                            onValueChange(requested.coerceIn(0f, 1f))
                            true
                        }
                    }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionDown -> {
                                onValueChange((value - 0.05f).coerceIn(0f, 1f))
                                true
                            }
                            Key.DirectionRight, Key.DirectionUp -> {
                                onValueChange((value + 0.05f).coerceIn(0f, 1f))
                                true
                            }
                            else -> false
                        }
                    }
                    .onSizeChanged { sliderSize = it }
                    .pointerInput(sliderSize) {
                        fun updateValue(x: Float) {
                            val handleRadius = 7.dp.toPx()
                            val trackWidth =
                                (sliderSize.width - handleRadius * 2f).coerceAtLeast(1f)
                            onValueChange(((x - handleRadius) / trackWidth).coerceIn(0f, 1f))
                        }

                        detectDragGestures(
                            onDragStart = { updateValue(it.x) },
                            onDrag = { change, _ ->
                                updateValue(change.position.x)
                            },
                        )
                    },
            ) {
                val trackHeight = 3.dp.toPx()
                val handleRadius = 7.dp.toPx()
                val trackStart = handleRadius
                val trackWidth = (size.width - handleRadius * 2f).coerceAtLeast(1f)
                val y = center.y - trackHeight / 2f
                val handleCenterX = trackStart + trackWidth * value.coerceIn(0f, 1f)
                drawRoundRect(
                    color = colors.border,
                    topLeft = Offset(trackStart, y),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight, trackHeight),
                )
                drawRoundRect(
                    color = colors.textPrimary,
                    topLeft = Offset(trackStart, y),
                    size = Size(handleCenterX - trackStart, trackHeight),
                    cornerRadius = CornerRadius(trackHeight, trackHeight),
                )
                drawCircle(
                    color = colors.textPrimary,
                    radius = handleRadius,
                    center = Offset(handleCenterX, center.y),
                )
                drawCircle(
                    color = colors.appBackground,
                    radius = 2.5.dp.toPx(),
                    center = Offset(handleCenterX, center.y),
                )
            }
            Spacer(Modifier.width(12.dp))
            YomuStepButton(
                text = "+",
                onClick = { onValueChange((value + 0.05f).coerceIn(0f, 1f)) },
            )
        }
    }
}

@Composable
private fun YomuStepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .yomuPressable(onClick = onClick)
            .size(48.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.control)
    }
}

@Composable
fun YomuColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(if (selected) colors.surfaceSunken else colors.surfaceRaised)
            .border(
                1.dp,
                if (selected) colors.textPrimary else colors.border,
                RoundedCornerShape(YomuTheme.radius.md),
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = colors.textSecondary,
            style = YomuTheme.type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
