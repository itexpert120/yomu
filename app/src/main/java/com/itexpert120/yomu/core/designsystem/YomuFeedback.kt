package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small indeterminate indicator (a thin ring in the current content colour) for the rare wait
 * where no layout is known and nothing else fits. Where the layout is known, show [YomuSkeleton]
 * blocks instead; inside an ink notice, use its 2dp line ([YomuNotice] `loading`). [contained] sets
 * it in a raised circle, for use over other content.
 */
@Composable
fun YomuLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) {
    val ring: @Composable (Modifier) -> Unit = { m ->
        CircularProgressIndicator(
            modifier = m.size(24.dp),
            color = LocalContentColor.current.takeOrElse(YomuTheme.stack.ink),
            strokeWidth = 2.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round,
        )
    }
    if (contained) {
        Box(
            modifier = modifier.size(48.dp).clip(CircleShape).background(YomuTheme.stack.raised),
            contentAlignment = Alignment.Center,
        ) { ring(Modifier) }
    } else {
        ring(modifier)
    }
}

private fun Color.takeOrElse(fallback: Color) = if (this == Color.Unspecified) fallback else this

/**
 * A whole-pane wait whose layout isn't known: the small indicator with an optional muted caption.
 * Prefer [YomuSkeleton] blocks shaped like the coming content.
 */
@Composable
fun YomuLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YomuLoadingIndicator()
        if (message != null) {
            Text(text = message, style = StackType.Value, color = YomuTheme.stack.muted)
        }
    }
}

/**
 * A skeleton block in the raised colour, standing in for content that is loading. Size it like the
 * content it replaces (text lines, rows); [YomuSkeletonCover] stands in for a cover.
 */
@Composable
fun YomuSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(modifier.clip(shape).background(YomuTheme.stack.raised))
}

/** A cover-sized (2:3, radius 4) skeleton block. */
@Composable
fun YomuSkeletonCover(modifier: Modifier = Modifier) {
    YomuSkeleton(modifier.aspectRatio(YomuBookCoverAspectRatio), shape = RoundedCornerShape(4.dp))
}

/** A skeleton text line of the given [height] (12dp ≈ body text). */
@Composable
fun YomuSkeletonLine(modifier: Modifier = Modifier, height: Dp = 12.dp) {
    YomuSkeleton(modifier.height(height))
}

/** The 2dp indeterminate line Stack uses for running work inside notices and buttons. */
@Composable
fun YomuLoadingLine(
    modifier: Modifier = Modifier,
    color: Color = YomuTheme.stack.ink,
) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
}

/**
 * Determinate progress: a 4dp track in the track colour with an ink fill (no wave — [wavy] is kept
 * for source compatibility and ignored).
 */
@Composable
fun YomuProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
) = YomuProgressBar(progress = { progress }, modifier = modifier, wavy = wavy)

/**
 * Lambda form for animated progress: the value is read while drawing, so an animating bar never
 * recomposes its parent.
 */
@Composable
fun YomuProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") wavy: Boolean = false,
) {
    val stack = YomuTheme.stack
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f) },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(stack.track, cornerRadius = radius)
        val fraction = progress().coerceIn(0f, 1f)
        if (fraction > 0f) {
            drawRoundRect(stack.ink, size = Size(size.width * fraction, size.height), cornerRadius = radius)
        }
    }
}
