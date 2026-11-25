package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.InstrumentSansCondensed
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

private val TocRowMinHeight = 52.dp
private val TocNumberWidth = 34.dp

/** Deeper TOC levels share the last indent so long hierarchies keep a readable line length. */
private const val TocMaxIndentLevels = 4

private const val TocRowContentType = "tocRow"

/** Contents header (pinned, with the order toggle) followed by the virtualized chapter rows. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.tocSection(
    toc: TocUiState,
    gutter: Dp,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
) {
    // Pinned so the order toggle stays reachable while a long contents list scrolls beneath it.
    stickyHeader { ContentsHeader(toc = toc, gutter = gutter, onTocSortChange = onTocSortChange) }

    when {
        toc.loading -> item { TocBuilding(gutter) }
        toc.items.isEmpty() -> item { TocEmpty(gutter) }
        // No item key: a book's TOC can legitimately repeat an href, and duplicate keys crash
        // LazyColumn. Positional binding is fine here (sort just rebinds in place).
        else -> items(toc.items, contentType = { TocRowContentType }) { entry ->
            TocRow(
                entry = entry,
                gutter = gutter,
                selectionMode = toc.selectionMode,
                onTap = {
                    when {
                        toc.selectionMode -> if (entry.jumpable) onToggleSelection(entry.uid)
                        entry.locatorJson != null -> onOpenChapter(entry.locatorJson)
                    }
                },
                onLongPress = { if (entry.jumpable && !toc.selectionMode) onEnterSelection(entry.uid) },
                onToggleRead = { onSetChapterRead(entry.uid, !entry.read) },
                onToggleBookmark = { onToggleBookmark(entry.uid) },
            )
        }
    }
}

@Composable
private fun ContentsHeader(
    toc: TocUiState,
    gutter: Dp,
    onTocSortChange: (TocSortMode) -> Unit,
) {
    val stack = YomuTheme.stack
    val ready = !toc.loading && toc.items.isNotEmpty()
    Row(
        // Opaque: as a pinned header, rows scrolling beneath must not show through it.
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = gutter, end = gutter - 6.dp, top = 12.dp, bottom = 2.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Contents", style = StackType.Heading, color = stack.ink)
            if (ready) {
                val total = toc.readCount + toc.unreadCount
                Text(
                    text = "$total ${if (total == 1) "chapter" else "chapters"} · ${toc.readCount} read",
                    style = StackType.Caption,
                    color = stack.muted,
                )
            }
        }
        if (ready) {
            val next = if (toc.sort == TocSortMode.Ascending) TocSortMode.Descending else TocSortMode.Ascending
            SheetIconButton(
                icon = StackIcons.SwapVert,
                contentDescription = "Sort ${next.label.lowercase()}",
                onClick = { onTocSortChange(next) },
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun TocRow(
    entry: TocEntryUi,
    gutter: Dp,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val stack = YomuTheme.stack
    val sheet = MaterialTheme.colorScheme.surface
    val colorSpec: AnimationSpec<Color> = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultEffectsSpec() else snap()
    // Kept as State and read while drawing, so the selection fade doesn't recompose the row.
    val highlight = animateColorAsState(
        targetValue = when {
            entry.selected -> stack.raised
            entry.current && !selectionMode -> stack.raised
            else -> stack.raised.copy(alpha = 0f)
        },
        animationSpec = colorSpec,
        label = "tocRowHighlight",
    )
    val titleColor by animateColorAsState(
        targetValue = when {
            entry.selected || entry.current -> stack.ink
            // Read chapters recede (like visited links) so unread ones stand out at a glance.
            !entry.jumpable || entry.read -> stack.muted
            else -> stack.ink
        },
        animationSpec = colorSpec,
        label = "tocRowTitle",
    )

    val row: @Composable () -> Unit = {
        TocListItem(
            entry = entry,
            gutter = gutter,
            selectionMode = selectionMode,
            onTap = onTap,
            onLongPress = onLongPress,
            background = sheet,
            highlight = highlight,
            titleColor = titleColor,
        )
    }

    if (selectionMode || !entry.jumpable) {
        row()
    } else {
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOf(
                tocSwipeAction(if (entry.read) StackIcons.RemoveDone else StackIcons.DoneAll, stack.raised, stack.ink, entry.read, onToggleRead),
            ),
            endActions = listOf(
                tocSwipeAction(if (entry.bookmarked) StackIcons.BookmarkFilled else StackIcons.Bookmark, stack.raised, stack.ink, entry.bookmarked, onToggleBookmark),
            ),
            swipeThreshold = 56.dp,
            backgroundUntilSwipeThreshold = stack.raised,
        ) { row() }
    }
}

/**
 * One chapter row: number column, title, and a quiet trailing mark. The current chapter sits on a
 * raised inset; the others are divided by hairline rules. A plain [Row] so it stays cheap in a
 * list of thousands.
 */
@Composable
private fun TocListItem(
    entry: TocEntryUi,
    gutter: Dp,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    background: Color,
    highlight: State<Color>,
    titleColor: Color,
) {
    val stack = YomuTheme.stack
    val progress = entry.percent?.coerceIn(0f, 1f) ?: 0f
    val insetPx = 10.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background)
                val inset = (gutter - insetPx).toPx()
                val tint = highlight.value
                if (tint.alpha > 0f) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(inset, 2.dp.toPx()),
                        size = size.copy(width = size.width - inset * 2, height = size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                } else {
                    val stroke = 1.dp.toPx()
                    drawLine(
                        stack.rule,
                        start = Offset(gutter.toPx(), size.height - stroke / 2),
                        end = Offset(size.width - gutter.toPx(), size.height - stroke / 2),
                        strokeWidth = stroke,
                    )
                }
            }
            .then(
                if (selectionMode) {
                    Modifier.selectable(selected = entry.selected, enabled = entry.jumpable, role = Role.Checkbox, onClick = onTap)
                } else {
                    Modifier.yomuPressable(onClick = onTap, onLongClick = onLongPress, role = Role.Button, pressedScale = 1f)
                },
            )
            .heightIn(min = TocRowMinHeight)
            .padding(start = gutter + 4.dp, end = gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val indent = 16.dp * entry.depth.coerceIn(0, TocMaxIndentLevels)
        if (entry.number != null) {
            Text(
                text = entry.number.toString(),
                style = NumberStyle,
                color = if (entry.current || entry.selected) stack.ink else stack.faint,
                maxLines = 1,
                modifier = Modifier.width(TocNumberWidth),
            )
        }
        Text(
            text = entry.title,
            color = titleColor,
            style = if (entry.current && !selectionMode) StackType.Body.copy(fontWeight = FontWeight.SemiBold) else StackType.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = indent, top = 8.dp, bottom = 8.dp, end = 8.dp),
        )
        if (entry.jumpable) {
            when {
                selectionMode -> SelectionBox(selected = entry.selected)

                entry.read -> Icon(
                    imageVector = StackIcons.Check,
                    contentDescription = "Read",
                    tint = stack.faint,
                    modifier = Modifier.size(18.dp),
                )

                progress > 0f -> Text(
                    text = "${(progress * 100).toInt()}%",
                    style = NumberStyle,
                    color = stack.body,
                )
            }
        }
    }
}

private val NumberStyle = StackType.Label.copy(fontFamily = InstrumentSansCondensed)

/** Selection-mode checkbox: a rounded square that fills with ink and a check. */
@Composable
private fun SelectionBox(selected: Boolean) {
    val stack = YomuTheme.stack
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(stack.ink) else Modifier.border(1.5.dp, stack.faint, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(StackIcons.Check, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(18.dp))
    }
}

private fun tocSwipeAction(
    icon: ImageVector,
    background: Color,
    tint: Color,
    isUndo: Boolean,
    onSwipe: () -> Unit,
): SwipeAction = SwipeAction(
    icon = { Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.padding(16.dp)) },
    background = background,
    onSwipe = onSwipe,
    isUndo = isUndo,
)

/** First open of a long book: a thin line while the contents are built, and a word on why. */
@Composable
private fun TocBuilding(gutter: Dp) {
    val stack = YomuTheme.stack
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 12.dp)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = stack.ink,
            trackColor = stack.raised,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
        Text(
            text = "Building contents…",
            style = StackType.Body,
            color = stack.ink,
            modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = "Building contents" },
        )
        Text(
            text = "Long books take a moment the first time. You can start reading now.",
            style = StackType.Label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
            color = stack.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TocEmpty(gutter: Dp) {
    val stack = YomuTheme.stack
    Column(
        modifier = Modifier
            .padding(horizontal = gutter, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(stack.raised)
            .padding(16.dp),
    ) {
        Text(text = "No table of contents", style = StackType.Body.copy(fontWeight = FontWeight.SemiBold), color = stack.ink)
        Text(
            text = "This book doesn't list its chapters. Read from the start, or search inside it from the reader.",
            style = StackType.BodySmall.copy(fontSize = 14.sp),
            color = stack.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The floating ink toolbar that replaces Read while chapters are selected. */
@Composable
internal fun ChapterSelectionToolbar(
    selectedCount: Int,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    YomuFloatingToolbar(
        vibrant = true,
        actions = listOf(
            YomuToolbarAction(StackIcons.DoneAll, "Mark read", onMarkRead, enabled = hasSelection),
            YomuToolbarAction(StackIcons.RemoveDone, "Unread", onMarkUnread, enabled = hasSelection),
            YomuToolbarAction(StackIcons.VerticalAlignBottom, "Read to here", onMarkPrevious, enabled = hasSelection),
        ),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp),
    )
}

/** Stack icon button: 48dp target, 24dp ink icon, a subtle press. */
@Composable
internal fun SheetIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    tint: Color = YomuTheme.stack.ink,
    background: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .yomuPressable(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(iconSize))
    }
}
