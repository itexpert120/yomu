package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.itexpert120.yomu.feature.contents.CurrentChapterPill
import com.itexpert120.yomu.feature.contents.revealItem
import com.itexpert120.yomu.feature.contents.showsItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

internal val TocRowMinHeight = 52.dp
private val TocNumberWidth = 30.dp

/** Deeper TOC levels share the last indent so long hierarchies keep a readable line length. */
private const val TocMaxIndentLevels = 4

// How long a contents load may take before its "Building contents…" note shows.
private const val TocBuildingGraceMillis = 400L

private const val TocRowContentType = "tocRow"

/** What the contents list raises. */
internal class TocActions(
    val onTocSortChange: (TocSortMode) -> Unit,
    val onOpenChapter: (String) -> Unit,
    val onSetChapterRead: (Int, Boolean) -> Unit,
    val onEnterSelection: (Int) -> Unit,
    val onToggleSelection: (Int) -> Unit,
    val onToggleBookmark: (Int) -> Unit,
    val onToggleVolume: (Int) -> Unit,
    val onCollapseAllVolumes: () -> Unit,
    val onExpandAllVolumes: () -> Unit,
)

/**
 * Contents header (pinned, with the order toggle) followed by the virtualized chapter rows.
 * Volumes open and close through [motion]; [edge] draws the fade under the pinned header.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.tocSection(
    toc: TocUiState,
    gutter: Dp,
    actions: TocActions,
    motion: TocVolumeMotion,
    edge: ContentsEdge,
) {
    // Pinned so the order toggle stays reachable while a long contents list scrolls beneath it.
    stickyHeader { ContentsHeader(toc = toc, gutter = gutter, actions = actions, edge = edge) }

    when {
        toc.loading -> item { TocBuilding(gutter) }
        toc.items.isEmpty() -> item { TocEmpty(gutter) }
        // No item key: a book's TOC can legitimately repeat an href, and duplicate keys crash
        // LazyColumn. Positional binding is fine here (sort just rebinds in place).
        else -> items(motion.rows(toc.items), contentType = { TocRowContentType }) { entry ->
            // A slot rebound to another entry (after a sort or a volume closing) starts afresh
            // rather than animating the previous entry's colours into this one.
            key(entry.uid) {
                TocRow(
                    entry = entry,
                    gutter = gutter,
                    selectionMode = toc.selectionMode,
                    expanded = motion.showsExpanded(entry),
                    onTap = {
                        when {
                            toc.selectionMode -> if (entry.jumpable) actions.onToggleSelection(entry.uid)
                            entry.locatorJson != null -> actions.onOpenChapter(entry.locatorJson)
                            entry.children > 0 -> motion.toggle(entry, actions.onToggleVolume)
                        }
                    },
                    onLongPress = { if (entry.jumpable && !toc.selectionMode) actions.onEnterSelection(entry.uid) },
                    onToggleRead = { actions.onSetChapterRead(entry.uid, !entry.read) },
                    onToggleBookmark = { actions.onToggleBookmark(entry.uid) },
                    onToggleVolume = { motion.toggle(entry, actions.onToggleVolume) },
                    modifier = Modifier.tocVolumeReveal(motion, entry.uid),
                )
            }
        }
    }
}

/**
 * Where the contents' pinned header is, for the soft fade where rows slide under an edge: under
 * the sheet's bar while the book's details scroll away, then under the pinned header once rows
 * pass beneath it. Both fades are read in the draw phase from the list's scroll position.
 */
@Stable
internal class ContentsEdge(private val listState: LazyListState, private val headerIndex: Int) {
    // The header's top and bottom within the list, from its last placement.
    var headerTop by mutableFloatStateOf(Float.MAX_VALUE)
    var headerBottom by mutableFloatStateOf(0f)

    /** How far the list has scrolled past its top, in px (large once the first item is gone). */
    fun scrolled(): Float = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat()

    /** How far rows have slid under the pinned header, in px. */
    fun underHeader(): Float = when {
        listState.firstVisibleItemIndex > headerIndex -> Float.MAX_VALUE
        listState.firstVisibleItemIndex == headerIndex -> listState.firstVisibleItemScrollOffset.toFloat()
        else -> 0f
    }
}

private val EdgeFadeHeight = 28.dp

/**
 * The fade at the list's top edge, while the book's details scroll under the sheet's bar. It
 * thins out as the contents header arrives there, so it never lies over the header.
 */
internal fun Modifier.contentsTopFade(edge: ContentsEdge, color: Color): Modifier = drawWithContent {
    drawContent()
    val fade = EdgeFadeHeight.toPx()
    val strength = (edge.scrolled() / fade).coerceIn(0f, 1f) * (edge.headerTop / fade).coerceIn(0f, 1f)
    if (strength > 0f) drawEdgeFade(color, top = 0f, height = fade, strength = strength)
}

/**
 * Stack's one scroll-edge treatment: a hairline in [color] at [top], shown at [strength]. [height]
 * is only how far content must slide under before the rule is fully drawn.
 */
@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawEdgeFade(color: Color, top: Float, height: Float, strength: Float) {
    val line = 1.dp.toPx()
    drawRect(
        color = color.copy(alpha = color.alpha * strength),
        topLeft = Offset(0f, if (top >= size.height) top - line else top),
        size = Size(size.width, line),
    )
}

@Composable
private fun ContentsHeader(
    toc: TocUiState,
    gutter: Dp,
    actions: TocActions,
    edge: ContentsEdge,
) {
    val stack = YomuTheme.stack
    val sheet = MaterialTheme.colorScheme.surface
    val rule = stack.rule
    val ready = !toc.loading && toc.items.isNotEmpty()
    Row(
        // Opaque: as a pinned header, rows scrolling beneath must not show through it.
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced {
                val top = it.positionInParent().y
                edge.headerTop = top
                edge.headerBottom = top + it.size.height
            }
            .drawWithContent {
                drawContent()
                // A hairline under the pinned header once rows pass beneath it.
                val fade = EdgeFadeHeight.toPx()
                val strength = (edge.underHeader() / fade).coerceIn(0f, 1f)
                if (strength > 0f) drawEdgeFade(rule, top = size.height, height = fade, strength = strength)
            }
            .background(sheet)
            .padding(start = gutter, end = gutter - 6.dp, top = 4.dp, bottom = 2.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Contents", style = StackType.Heading, color = stack.ink, modifier = Modifier.weight(1f))
        if (ready && toc.collapsibleCount > 0) {
            // Anything collapsed → open everything; nothing collapsed → close every volume.
            val anyCollapsed = toc.collapsedCount > 0
            HeaderTextAction(
                text = if (anyCollapsed) "Expand all" else "Collapse all",
                onClick = if (anyCollapsed) actions.onExpandAllVolumes else actions.onCollapseAllVolumes,
            )
        }
        if (ready) {
            val next = if (toc.sort == TocSortMode.Ascending) TocSortMode.Descending else TocSortMode.Ascending
            SheetIconButton(
                icon = StackIcons.SwapVert,
                contentDescription = "Sort ${next.label.lowercase()}",
                onClick = { actions.onTocSortChange(next) },
                iconSize = 20.dp,
            )
        }
    }
}

/** A quiet text button in the contents header (48dp tall target). */
@Composable
private fun HeaderTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = StackType.Label, color = YomuTheme.stack.body, maxLines = 1)
    }
}

@Composable
private fun TocRow(
    entry: TocEntryUi,
    gutter: Dp,
    selectionMode: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVolume: () -> Unit,
    modifier: Modifier = Modifier,
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
            entry.readRun -> stack.muted
            // A volume's title heads its chapters, so it stays ink unless it is a read chapter itself.
            entry.children > 0 && !entry.jumpable -> stack.ink
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
            expanded = expanded,
            onTap = onTap,
            onLongPress = onLongPress,
            onToggleVolume = onToggleVolume,
            background = sheet,
            highlight = highlight,
            titleColor = titleColor,
        )
    }

    Box(modifier) {
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
}

/**
 * One contents row: number column, title, and a quiet trailing mark. The current chapter sits on a
 * raised inset; the others are divided by hairline rules. A volume (an entry with chapters under
 * it) adds its tally under the title and a chevron that opens or closes it. A plain [Row] so it
 * stays cheap in a list of thousands.
 */
@Composable
private fun TocListItem(
    entry: TocEntryUi,
    gutter: Dp,
    selectionMode: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleVolume: () -> Unit,
    background: Color,
    highlight: State<Color>,
    titleColor: Color,
) {
    val stack = YomuTheme.stack
    val progress = entry.percent?.coerceIn(0f, 1f) ?: 0f
    val volume = entry.children > 0
    // The board's "you are here" card: a raised inset reaching 14dp past the gutter, so the
    // number and title stay in the same column as every other row's.
    val card = entry.current && !selectionMode
    val highlightInset = if (card) 14.dp else 10.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background)
                val inset = (gutter - highlightInset).toPx()
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
            .padding(
                start = gutter,
                end = if (volume) gutter - 12.dp else gutter,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val indent = 16.dp * entry.depth.coerceIn(0, TocMaxIndentLevels)
        val number = entry.numberLabel ?: entry.number?.toString()
        if (number != null) {
            Text(
                text = number,
                style = NumberStyle,
                color = if (entry.current || entry.selected) stack.ink else stack.faint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(min = TocNumberWidth),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = indent + if (number != null) 14.dp else 0.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        ) {
            Text(
                text = entry.title,
                color = titleColor,
                style = if (card || (volume && !entry.readRun)) StackType.Body.copy(fontWeight = FontWeight.SemiBold) else StackType.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (volume && !entry.readRun && entry.chapterCount > 0) {
                Text(
                    text = volumeTally(entry, expanded),
                    style = StackType.Caption,
                    color = stack.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (entry.jumpable) {
            when {
                selectionMode -> SelectionBox(selected = entry.selected)

                volume -> Unit

                // The chapter being read shows how far into it you are, never a "read" tick.
                entry.current -> if (progress > 0f) {
                    Text(text = "${(progress * 100).toInt()}%", style = NumberStyle, color = stack.ink)
                }

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
        if (volume) VolumeChevron(expanded = expanded, title = entry.title, onClick = onToggleVolume)
    }
}

/** "40 chapters · 12 read", and where the reading position is while the volume is closed. */
private fun volumeTally(entry: TocEntryUi, expanded: Boolean): String {
    val chapters = "${entry.chapterCount} ${if (entry.chapterCount == 1) "chapter" else "chapters"}"
    val read = when (entry.chapterReadCount) {
        0 -> null
        entry.chapterCount -> "all read"
        else -> "${entry.chapterReadCount} read"
    }
    val here = if (entry.holdsCurrent && !expanded) "you're here" else null
    return listOfNotNull(chapters, read, here).joinToString(" · ")
}

/** Opens or closes a volume; the chevron turns on the spatial spring, read in the draw phase. */
@Composable
private fun VolumeChevron(expanded: Boolean, title: String, onClick: () -> Unit) {
    val turn = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "volumeChevron",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .semantics {
                contentDescription = "${if (expanded) "Collapse" else "Expand"} $title"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = StackIcons.KeyboardArrowDown,
            contentDescription = null,
            tint = YomuTheme.stack.body,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = turn.value },
        )
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

/**
 * First open of a long book: a thin line while the contents are built, and a word on why. It keeps
 * its space but stays blank for [TocBuildingGraceMillis], then fades in, so contents that arrive
 * within a moment (most books, read from storage) never flash a loading state.
 */
@Composable
private fun TocBuilding(gutter: Dp) {
    val stack = YomuTheme.stack
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(TocBuildingGraceMillis)
        waited = true
    }
    val shown = animateFloatAsState(if (waited) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tocBuilding")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = shown.value }
            .padding(horizontal = gutter, vertical = 12.dp),
    ) {
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

/**
 * The reader's "Current chapter" pill on Book Details' contents: it shows while the chapter being
 * read is off screen or inside a folded volume, and a tap opens what hides it and glides it into
 * view with a row of context above (the contents header is pinned over the row before that).
 * [firstRowIndex] is the list index of the first contents row.
 */
@Composable
internal fun ContentsCurrentChapterPill(
    listState: LazyListState,
    toc: TocUiState,
    motion: TocVolumeMotion,
    onToggleVolume: (Int) -> Unit,
    firstRowIndex: Int,
    modifier: Modifier = Modifier,
) {
    val latest = rememberUpdatedState(toc)
    val rows = motion.rows(toc.items)
    val currentRow = rows.indexOfFirst { it.current }
    val target = if (currentRow >= 0) currentRow else rows.indexOfFirst { it.holdsCurrent && !motion.showsExpanded(it) }
    val targetIndex = if (target >= 0) firstRowIndex + target else -1
    val away by remember(listState, targetIndex) {
        derivedStateOf { targetIndex >= 0 && !listState.showsItem(targetIndex) }
    }
    val below by remember(listState, targetIndex) { derivedStateOf { targetIndex > listState.firstVisibleItemIndex } }
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    CurrentChapterPill(
        visible = away && !toc.selectionMode && !toc.loading,
        below = below,
        onClick = {
            scope.launch {
                // Open every folded volume around the chapter (outermost first), then glide to it.
                repeat(TocMaxIndentLevels + 1) {
                    val folded = latest.value.items.firstOrNull { it.holdsCurrent && !it.expanded } ?: return@repeat
                    onToggleVolume(folded.uid)
                    withTimeoutOrNull(1_000L) {
                        snapshotFlow { latest.value.items.firstOrNull { it.uid == folded.uid }?.expanded }.first { it == true }
                    }
                }
                val row = latest.value.items.indexOfFirst { it.current }
                if (row >= 0) listState.revealItem(firstRowIndex + row, lead = 2, spec = spec)
            }
        },
        modifier = modifier,
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
