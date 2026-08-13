package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToInt

private data class ScrollIndicatorMetrics(
    val contentSize: Int,
    val scrollOffset: Int,
    val viewportSize: Int,
) {
    val scrollable: Boolean
        get() = contentSize != Int.MAX_VALUE &&
            scrollOffset != Int.MAX_VALUE &&
            viewportSize != Int.MAX_VALUE &&
            viewportSize > 0 &&
            contentSize > viewportSize
}

private data class ScrollThumbGeometry(
    val height: Float,
    val travel: Float,
    val top: Float,
)

private val ScrollIndicatorWidth = 4.dp
private val ScrollIndicatorDragWidth = 24.dp
private val ScrollIndicatorMinThumbHeight = 48.dp

/**
 * A compact Material-aligned vertical scrollbar for any Compose scroll container that exposes a
 * [ScrollIndicatorState]. It remains subtly visible at rest and strengthens while content moves,
 * making long reading and chapter surfaces easy to orient without competing with their content.
 * The wider transparent drag lane keeps the visible rail delicate while supporting thumb dragging.
 * Track taps are ignored so an incidental touch beside book-details content never jumps the list.
 */
@Composable
fun YomuVerticalScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = YomuTheme.colors.textSecondary,
    trackColor: Color = YomuTheme.colors.border,
) {
    VerticalScrollIndicator(
        state = state.scrollIndicatorState,
        isScrollInProgress = state.isScrollInProgress,
        onScrollToProgress = { progress ->
            val itemCount = state.layoutInfo.totalItemsCount
            if (itemCount > 0) {
                state.scrollToItem(((itemCount - 1) * progress).roundToInt())
            }
        },
        modifier = modifier,
        thumbColor = thumbColor,
        trackColor = trackColor,
    )
}

@Composable
fun YomuVerticalScrollIndicator(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thumbColor: Color = YomuTheme.colors.textSecondary,
    trackColor: Color = YomuTheme.colors.border,
) {
    VerticalScrollIndicator(
        state = state.scrollIndicatorState,
        isScrollInProgress = state.isScrollInProgress,
        onScrollToProgress = { progress ->
            val maxValue = state.maxValue
            if (maxValue != Int.MAX_VALUE) {
                state.scrollTo((maxValue * progress).roundToInt())
            }
        },
        modifier = modifier,
        thumbColor = thumbColor,
        trackColor = trackColor,
    )
}

@Composable
private fun VerticalScrollIndicator(
    state: ScrollIndicatorState?,
    isScrollInProgress: Boolean,
    onScrollToProgress: suspend (Float) -> Unit,
    modifier: Modifier,
    thumbColor: Color,
    trackColor: Color,
) {
    val metrics by remember(state) {
        derivedStateOf {
            ScrollIndicatorMetrics(
                contentSize = state?.contentSize ?: 0,
                scrollOffset = state?.scrollOffset ?: 0,
                viewportSize = state?.viewportSize ?: 0,
            )
        }
    }
    val alpha by animateFloatAsState(
        targetValue = when {
            !metrics.scrollable -> 0f
            isScrollInProgress -> 0.88f
            else -> 0.52f
        },
        animationSpec = if (yomuAnimationsEnabled()) {
            tween(durationMillis = if (isScrollInProgress) 90 else 220)
        } else {
            snap()
        },
        label = "verticalScrollIndicatorAlpha",
    )
    val currentMetrics = rememberUpdatedState(metrics)
    val currentOnScrollToProgress = rememberUpdatedState(onScrollToProgress)
    val dragProgress = remember { Channel<Float>(capacity = Channel.CONFLATED) }
    DisposableEffect(dragProgress) {
        onDispose { dragProgress.close() }
    }
    LaunchedEffect(dragProgress) {
        for (progress in dragProgress) {
            currentOnScrollToProgress.value(progress)
        }
    }

    Box(
        modifier = modifier
            .width(ScrollIndicatorDragWidth)
            .semantics {
                if (metrics.scrollable) {
                    val range = (metrics.contentSize - metrics.viewportSize).coerceAtLeast(1)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (metrics.scrollOffset.toFloat() / range).coerceIn(0f, 1f),
                        range = 0f..1f,
                        steps = 0,
                    )
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initialMetrics = currentMetrics.value
                    if (!initialMetrics.scrollable || size.height <= 0) {
                        return@awaitEachGesture
                    }

                    val minThumbHeight = ScrollIndicatorMinThumbHeight.toPx()
                    val initialThumb = initialMetrics.thumbGeometry(
                        trackHeight = size.height.toFloat(),
                        minThumbHeight = minThumbHeight,
                    )
                    if (down.position.y !in initialThumb.top..(initialThumb.top + initialThumb.height)) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    val grabOffset = down.position.y - initialThumb.top

                    fun scrollTo(pointerY: Float) {
                        val latestMetrics = currentMetrics.value
                        if (!latestMetrics.scrollable) return
                        val thumb = latestMetrics.thumbGeometry(
                            trackHeight = size.height.toFloat(),
                            minThumbHeight = minThumbHeight,
                        )
                        val targetTop = (pointerY - grabOffset).coerceIn(0f, thumb.travel)
                        val progress = if (thumb.travel > 0f) targetTop / thumb.travel else 0f
                        dragProgress.trySend(progress)
                    }

                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        change.consume()
                        scrollTo(change.position.y)
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ScrollIndicatorWidth),
        ) {
            if (!metrics.scrollable || alpha <= 0f || size.height <= 0f) return@Canvas

            val thumb = metrics.thumbGeometry(
                trackHeight = size.height,
                minThumbHeight = ScrollIndicatorMinThumbHeight.toPx(),
            )
            val radius = size.width / 2f

            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(radius, radius),
                alpha = alpha * 0.22f,
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(0f, thumb.top),
                size = Size(size.width, thumb.height),
                cornerRadius = CornerRadius(radius, radius),
                alpha = alpha,
            )
        }
    }
}

private fun ScrollIndicatorMetrics.thumbGeometry(
    trackHeight: Float,
    minThumbHeight: Float,
): ScrollThumbGeometry {
    val scrollRange = (contentSize - viewportSize).coerceAtLeast(1)
    val thumbHeight =
        (trackHeight * viewportSize.toFloat() / contentSize)
            .coerceIn(minThumbHeight.coerceAtMost(trackHeight), trackHeight)
    val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0f)
    val progress = (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)
    return ScrollThumbGeometry(
        height = thumbHeight,
        travel = thumbTravel,
        top = thumbTravel * progress,
    )
}
