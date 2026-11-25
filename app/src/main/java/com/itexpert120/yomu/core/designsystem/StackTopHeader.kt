@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The top of Stack's three top-level screens (Library, Statistics, Settings): one title row, so the
 * titles sit at exactly the same place on all three, and one compact header that pins under the
 * status bar once that title has scrolled (or been covered) away: the name small, with the
 * screen's actions, above whatever else pins on that screen (the library tabs, the stats period).
 * Both are driven by a progress lambda read only in the draw/layer phase, so scrolling never
 * recomposes them.
 */

/** Space between the status bar and the large title row on a phone… */
val StackTitleRowTop: Dp = 20.dp

/** …and beside a navigation rail (from 600dp wide), as on the tablet boards. */
val StackTitleRowTopWide: Dp = 16.dp

/** Minimum height of the large title row (its actions are 48dp targets). */
val StackTitleRowHeight: Dp = 48.dp

/** Height of the compact header's row under the status bar. */
val StackCompactHeaderHeight: Dp = 52.dp

/** How far before the compact header is fully in place it starts to appear. */
val StackHeaderFadeDistance: Dp = 56.dp

/**
 * The large title of a top-level screen: Display 32/600 at [gutter] from the start, [StackTitleRowTop]
 * under the status bar (the caller pads for the status bar), with [actions] at the end. If the
 * actions don't fit beside the title (large font sizes) they wrap under it rather than squeeze it.
 * [collapse] (0 → 1) fades the row out as the compact header takes over.
 */
@Composable
fun StackTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    collapse: (() -> Float)? = null,
    // Icon-button actions carry their own 12dp inset, so the row ends 12dp (8dp beside a rail) in
    // by default; a bare control (e.g. a segmented choice) asks for its own.
    endPadding: Dp? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // The boards end an icon-button row 12dp in on a phone and 8dp in beside a rail.
    val end = endPadding ?: if (actions == null) {
        gutter
    } else if (wide) {
        YomuTheme.space.xs
    } else {
        12.dp
    }
    val top = if (wide) StackTitleRowTopWide else StackTitleRowTop
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .then(if (collapse != null) Modifier.graphicsLayer { alpha = 1f - collapse().coerceIn(0f, 1f) } else Modifier)
            .padding(start = gutter, end = end, top = top),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = StackTitleRowHeight)
                .padding(end = YomuTheme.space.sm),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = StackType.Display,
                color = stack.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier.heightIn(min = StackTitleRowHeight),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * The compact header that pins under the status bar on a scrolled top-level screen: the screen
 * name (17/600) and the screen's [actions], on the desk colour. The status bar band behind it is
 * always desk, so nothing scrolls under the status bar icons; the row itself fades and settles in
 * with [progress] (0 hidden, 1 in place). While hidden it isn't composed at all, so touches reach
 * the large title row underneath; once shown it takes the touches in its area.
 */
@Composable
fun StackCompactHeader(
    title: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    background: Color = YomuTheme.stack.desk,
    endPadding: Dp? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val end = endPadding ?: if (actions != null) YomuTheme.space.xs else gutter
    val shown by remember(progress) { derivedStateOf { progress() > 0f } }
    Column(modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                .background(background),
        )
        if (shown) {
            val settle = with(LocalDensity.current) { 6.dp.toPx() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StackCompactHeaderHeight)
                    .drawBehind { drawRect(background.copy(alpha = background.alpha * progress().coerceIn(0f, 1f))) }
                    // Swallows taps so they don't fall through to the faded title row under it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = gutter, end = end),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val p = progress().coerceIn(0f, 1f)
                            alpha = p
                            translationY = settle * (1f - p)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = StackType.SheetTitle,
                        color = stack.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

/**
 * How far a compact header has come in, from where the thing it pins over currently is: 0 while
 * [position] is [StackHeaderFadeDistance] or more below [pinnedAt], 1 once it reaches it.
 */
fun stackHeaderProgress(position: Float, pinnedAt: Float, fadeDistance: Float): Float = if (fadeDistance <= 0f) {
    if (position <= pinnedAt) 1f else 0f
} else {
    (1f - (position - pinnedAt) / fadeDistance).coerceIn(0f, 1f)
}

/**
 * Stack's scroll-edge rule for a scroll container whose content passes under a pinned header that
 * sits *inside* the container's bounds (a single scrolling column under an overlaid header): the
 * 1dp `rule` hairline at [edge] (px from the container's top, the header's bottom edge), at
 * [strength] (0–1) as content actually arrives under the header — the same one rule as
 * `yomuPinnedHeaderRule` (see YomuScrollEdge.kt). [height] is kept for source compatibility.
 * Everything is read while drawing.
 */
fun Modifier.stackPinnedEdgeFade(
    edge: () -> Float,
    strength: () -> Float,
    @Suppress("UNUSED_PARAMETER") height: Dp = 28.dp,
): Modifier = composed {
    val color = YomuTheme.stack.rule
    drawWithContent {
        drawContent()
        val s = strength().coerceIn(0f, 1f)
        if (s <= 0f) return@drawWithContent
        drawRect(
            color = color.copy(alpha = color.alpha * s),
            topLeft = Offset(0f, edge()),
            size = Size(size.width, YomuScrollRuleWidth.toPx()),
        )
    }
}
