package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.data.collections.LibraryTab
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val TabHeight = 40.dp

/** A tab's horizontal span inside the strip, in px; written once it has been placed. */
private data class TabSpan(val left: Float, val width: Float) {
    val center get() = left + width / 2
}

/**
 * The library tabs as a scrolling strip of pill tabs. The selection pill is drawn from the
 * pager's live position, so it slides 1:1 with a swipe, and each label blends toward the selected
 * colour by how close the pager is to it. Both are read in the draw phase, so a swipe never
 * recomposes the strip. The strip scrolls to keep the pill centred, and ends with the
 * collection-management chip.
 */
@Composable
internal fun LibraryTabRow(
    tabs: List<LibraryTab>,
    pagerState: PagerState,
    onSelect: (index: Int) -> Unit,
    onEditTabs: () -> Unit,
    horizontalInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val scroll = rememberScrollState()
    val spans = remember(tabs.size) { mutableStateListOf<TabSpan?>().apply { repeat(tabs.size) { add(null) } } }
    val pillColor = MaterialTheme.colorScheme.secondaryContainer
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // Follow the pill: keep it centred while swiping or animating between tabs.
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
            .padding(horizontal = horizontalInset, vertical = YomuTheme.space.xs)
            .drawBehind {
                val span = spans.interpolate(position()) ?: return@drawBehind
                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(span.left, 0f),
                    size = Size(span.width, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
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
    val labelStyle = MaterialTheme.typography.titleSmallEmphasized
    val countStyle = MaterialTheme.typography.labelMedium
    Row(
        modifier = modifier
            .height(TabHeight)
            .clip(YomuShapeTokens.pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = "${tab.label}, ${tab.count} ${if (tab.count == 1) "book" else "books"}"
            }
            .padding(horizontal = YomuTheme.space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = tab.label,
            style = labelStyle,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = tab.count.toString(),
            style = countStyle,
            color = { labelColor().copy(alpha = 0.72f) },
        )
    }
}

/** The strip's quiet last item: opens the tab editor (add a collection, rearrange tabs). */
@Composable
private fun AddTabButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(TabHeight)
            .clip(YomuShapeTokens.pill)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add or arrange tabs" }
            .padding(start = YomuTheme.space.sm, end = YomuTheme.space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
