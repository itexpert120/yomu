package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.data.collections.LibraryTab
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val TabHeight = 48.dp
private val TabGap = 22.dp
private val LineHeight = 2.dp

/** A tab's horizontal span inside the strip, in px; written once it has been placed. */
private data class TabSpan(val left: Float, val width: Float) {
    val center get() = left + width / 2
}

/**
 * The library tabs: words on a scrolling strip (Stack — no counts, no pills). One 2dp ink line is
 * drawn from the pager's live position, so it glides 1:1 with a swipe, and each label blends toward
 * ink by how close the pager is to it. Both are read in the draw phase, so a swipe never recomposes
 * the strip. The strip scrolls to keep the line centred, sits on a rule, and ends with a quiet "+"
 * that opens the tab editor.
 */
@Composable
internal fun LibraryTabRow(
    tabs: List<LibraryTab>,
    pagerState: PagerState,
    onSelect: (index: Int) -> Unit,
    onEditTabs: () -> Unit,
    horizontalInset: Dp,
    modifier: Modifier = Modifier,
    // Actions at the row's end, outside the scrolling strip (e.g. search once the shelf has pinned).
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    if (tabs.isEmpty()) return
    val scroll = rememberScrollState()
    val spans = remember(tabs.size) { mutableStateListOf<TabSpan?>().apply { repeat(tabs.size) { add(null) } } }
    val stack = YomuTheme.stack
    val lineColor = stack.ink
    val ruleColor = stack.rule
    val selectedColor = stack.ink
    val unselectedColor = stack.muted
    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // Follow the line: keep it centred while swiping or animating between tabs.
    LaunchedEffect(pagerState, spans) {
        snapshotFlow {
            val span = spans.interpolate(position()) ?: return@snapshotFlow null
            (span.center - scroll.viewportSize / 2f).roundToInt()
        }.collect { target ->
            if (target != null) scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
        }
    }

    val fadeWidth = YomuTheme.space.lg
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(ruleColor, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // Soft scroll edges: the strip fades out toward an edge only while there is more
                // to scroll that way, ramping in over the first few dp of travel.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = fadeWidth.toPx()
                    val start = (scroll.value / fade).coerceIn(0f, 1f)
                    val end = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
                    if (start > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = start),
                                1f to Color.Transparent,
                                endX = fade,
                            ),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    if (end > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = end),
                                startX = size.width - fade,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fade, 0f),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                }
                .horizontalScroll(scroll)
                .padding(start = horizontalInset, end = if (trailing != null) YomuTheme.space.xs else horizontalInset)
                .drawBehind {
                    val span = spans.interpolate(position()) ?: return@drawBehind
                    val line = LineHeight.toPx()
                    drawRoundRect(
                        color = lineColor,
                        topLeft = Offset(span.left, size.height - line),
                        size = Size(span.width, line),
                        cornerRadius = CornerRadius(line / 2),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(TabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                // 1 on the selected tab, falling to 0 a whole page away.
                val closeness = { (1f - abs(position() - index)).coerceIn(0f, 1f) }
                LibraryTabPill(
                    tab = tab,
                    selected = pagerState.currentPage == index,
                    labelColor = { lerp(unselectedColor, selectedColor, closeness()) },
                    onClick = { onSelect(index) },
                    modifier = Modifier.onPlaced { coordinates ->
                        val span = TabSpan(coordinates.positionInParent().x, coordinates.size.width.toFloat())
                        if (spans.getOrNull(index) != span && index < spans.size) spans[index] = span
                    },
                )
            }
            AddTabButton(onClick = onEditTabs)
        }
        if (trailing != null) {
            Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** The pill's span at a fractional pager [position], between the two tabs it sits between. */
private fun List<TabSpan?>.interpolate(position: Float): TabSpan? {
    if (isEmpty()) return null
    val index = floor(position).toInt().coerceIn(0, lastIndex)
    val from = get(index) ?: return null
    val to = getOrNull((index + 1).coerceAtMost(lastIndex)) ?: from
    val fraction = (position - index).coerceIn(0f, 1f)
    return TabSpan(
        left = from.left + (to.left - from.left) * fraction,
        width = from.width + (to.width - from.width) * fraction,
    )
}

@Composable
private fun LibraryTabPill(
    tab: LibraryTab,
    selected: Boolean,
    labelColor: ColorProducer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    Box(
        modifier = modifier
            .height(TabHeight)
            .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null, onClick = onClick)
            .semantics {
                contentDescription = "${tab.label}, ${tab.count} ${if (tab.count == 1) "book" else "books"}"
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = tab.label,
            style = style,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The strip's quiet last item: opens the tab editor (add a collection, rearrange tabs). */
@Composable
private fun AddTabButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TabHeight)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add or arrange tabs" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            StackIcons.Add,
            contentDescription = null,
            tint = YomuTheme.stack.muted,
            modifier = Modifier.size(22.dp),
        )
    }
}
