package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import kotlin.math.abs
import kotlin.math.floor

/**
 * The title row for every [YomuBottomSheet], one line: the title (17/600) at the content's leading
 * edge, then any [trailing] actions, then a quiet close × at the end (its glyph lines up with the
 * content edge). [subtitle] is accepted for source compatibility and not shown: a Stack sheet
 * header carries no second line.
 *
 * Inside a sheet that scrolls its own content the header stays pinned at the sheet's top while the
 * content scrolls under it, with Stack's scroll-edge rule beneath it. Elsewhere (a sheet whose list
 * scrolls itself) pass [scrolledUnder] — e.g. `{ listState.canScrollBackward }` — for the rule.
 */
@Composable
fun YomuSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    scrolledUnder: (() -> Boolean)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .sheetHeaderPinning(scrolledUnder)
            .fillMaxWidth()
            .bleedHorizontally(HeaderBleed)
            .heightIn(min = SheetHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides stack.ink) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = HeaderBleed, end = if (trailing == null && onClose == null) HeaderBleed else 4.dp)
                    .semantics { heading() },
                style = if (LocalYomuSheetPresentation.current == YomuSheetPresentation.Side) SideSheetTitle else PhoneSheetTitle,
                color = stack.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke(this)
            if (onClose != null) SheetCloseButton(onClose)
        }
    }
}

/**
 * A sheet whose tabs are its header: the tab words on one line where the title would be, the ink
 * line under the selected tab, Stack's rule under the row, and the quiet close × at the end. Pass
 * [position] (the pager's live `currentPage + currentPageOffsetFraction`) and the line and the
 * labels' ink follow a swipe 1:1; without it they follow [selectedIndex]. Everything that moves is
 * read while drawing. Pinned like [YomuSheetHeader] inside a sheet that scrolls its content.
 */
@Composable
fun YomuSheetTabsHeader(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    position: (() -> Float)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    if (tabs.isEmpty()) return
    val stack = YomuTheme.stack
    val spans = remember(tabs.size) { mutableStateListOf<TabSpan?>().apply { repeat(tabs.size) { add(null) } } }
    val glide = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "sheetTabLine",
    )
    val at: () -> Float = position ?: { glide.value }
    val line = stack.ink
    val ink = stack.ink
    val faint = stack.faint
    val rule = stack.rule
    Row(
        modifier = modifier
            .sheetHeaderPinning(null, rule = false)
            .fillMaxWidth()
            .bleedHorizontally(HeaderBleed)
            .heightIn(min = SheetHeaderHeight)
            .drawBehind {
                // The tab strip always stands on a rule, as the library's does.
                val h = YomuScrollRuleWidth.toPx()
                val bleed = SheetBackgroundBleed.toPx()
                drawRect(rule, topLeft = Offset(-bleed, size.height - h), size = Size(size.width + bleed * 2, h))
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = HeaderBleed, end = 4.dp)
                .drawBehind {
                    val span = spans.interpolate(at()) ?: return@drawBehind
                    val h = TabLine.toPx()
                    drawRoundRect(line, topLeft = Offset(span.left, size.height - h), size = Size(span.width, h), cornerRadius = CornerRadius(h / 2))
                }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(SheetHeaderHeight)
                        .onPlaced { coordinates ->
                            val span = TabSpan(coordinates.positionInParent().x, coordinates.size.width.toFloat())
                            if (index < spans.size && spans[index] != span) spans[index] = span
                        }
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = { lerp(faint, ink, (1f - abs(at() - index)).coerceIn(0f, 1f)) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(Modifier.height(SheetHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides stack.ink) {
                trailing?.invoke(this)
                if (onClose != null) SheetCloseButton(onClose)
            }
        }
    }
}

/** The quiet close: a muted × in a 48dp target. */
@Composable
private fun SheetCloseButton(onClose: () -> Unit) {
    YomuIconButton(icon = StackIcons.Close, contentDescription = "Close", onClick = onClose, tint = YomuTheme.stack.muted)
}

/**
 * Inside a [YomuBottomSheet] that scrolls its content, the header stays at the sheet's top: it is
 * placed back down by however far the content has scrolled, drawn over the content in the sheet's
 * colour (reaching past its own bounds to cover the gutters; the sheet clips it), with the
 * scroll-edge rule under it while content is beneath. Placement-only work per scrolled frame.
 */
@Composable
private fun Modifier.sheetHeaderPinning(scrolledUnder: (() -> Boolean)?, rule: Boolean = true): Modifier {
    val scroll: ScrollState? = LocalYomuSheetScroll.current
    val ruled = LocalYomuSheetRule.current
    val sheet = MaterialTheme.colorScheme.surface
    val pinned = if (scroll == null) {
        this
    } else {
        this
            .zIndex(1f)
            .offset { IntOffset(0, scroll.value) }
            .drawBehind {
                val bleed = SheetBackgroundBleed.toPx()
                drawRect(sheet, topLeft = Offset(-bleed, -bleed), size = Size(size.width + bleed * 2, size.height + bleed))
            }
    }
    val shown: (() -> Boolean)? = scrolledUnder ?: scroll?.let { { it.value > 0 } }
    return if (shown != null && ruled && rule) pinned.edgeRule(shown, atTop = false, bleed = SheetBackgroundBleed) else pinned
}

private val SheetHeaderHeight = 48.dp

// Stack's boards: a phone sheet's title is 20/26, a side sheet's the 26/28 Title.
private val PhoneSheetTitle = StackType.SheetTitle.copy(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp)
private val SideSheetTitle = StackType.Title
private val HeaderBleed = 12.dp

// Far enough past the header to reach the sheet's edges from inside its gutter.
private val SheetBackgroundBleed: Dp = 48.dp
private val TabGap = 20.dp
private val TabLine = 2.dp

@Immutable
private data class TabSpan(val left: Float, val width: Float)

/** The line's span at a fractional tab [position], between the two tabs it sits between. */
private fun List<TabSpan?>.interpolate(position: Float): TabSpan? {
    if (isEmpty()) return null
    val index = floor(position).toInt().coerceIn(0, lastIndex)
    val from = get(index) ?: return null
    val to = getOrNull((index + 1).coerceAtMost(lastIndex)) ?: from
    val fraction = (position - index).coerceIn(0f, 1f)
    return TabSpan(from.left + (to.left - from.left) * fraction, from.width + (to.width - from.width) * fraction)
}
