package com.itexpert120.yomu.feature.bookdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReadingState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    book: BookDetailsUi?,
    toc: TocUiState,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemove: () -> Unit,
    onSaveCover: () -> Unit,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterChapterSelection: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
    onExitChapterSelection: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onMarkSelectedChapters: (Boolean) -> Unit,
    onMarkPreviousRead: () -> Unit,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    var showCover by remember { mutableStateOf(false) }
    // Reveal the title in the bar only once the in-content header (item 0) has scrolled away, so
    // the otherwise-empty bar gains context as you scroll.
    val showHeaderTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    if (toc.selectionMode) {
        BackHandler(onBack = onExitChapterSelection)
    }

    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Quiet bar: title-less until scrolled, then the book title fades in.
                YomuScreenHeader(
                    title = book?.title.orEmpty(),
                    onBack = onBack,
                    elevated = listState.canScrollBackward,
                    titleVisible = showHeaderTitle && book != null,
                )

                Box(Modifier
                    .weight(1f)
                    .fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = navBottom + 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (book == null) {
                            item {
                                Text(
                                    text = "This book is no longer in your library.",
                                    color = YomuTheme.colors.textMuted,
                                    style = YomuTheme.type.body,
                                )
                            }
                            return@LazyColumn
                        }

                        item {
                            BookHeader(
                                book = book,
                                onCoverClick = {
                                    if (book.coverImagePath != null) showCover = true
                                },
                                onEdit = onEdit,
                                onMarkRead = onMarkRead,
                                onMarkUnread = onMarkUnread,
                                onRemove = onRemove,
                            )
                        }

                        tocSection(
                            toc = toc,
                            onTocSortChange = onTocSortChange,
                            onOpenChapter = onOpenChapter,
                            onSetChapterRead = onSetChapterRead,
                            onEnterSelection = onEnterChapterSelection,
                            onToggleSelection = onToggleChapterSelection,
                        )

                        // Trailing room for the floating Read button / selection bar without
                        // shifting the rows above when selection toggles.
                        item { Spacer(Modifier.height(72.dp)) }
                    }

                    BottomScrim(Modifier.align(Alignment.BottomCenter))
                }
            }

            // Primary action floats over the content (hidden while multi-selecting chapters).
            if (book != null) {
                AnimatedVisibility(
                    visible = !toc.selectionMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FloatingReadButton(
                        reading = book.readingState == ReadingState.Reading,
                        onClick = onRead,
                        modifier = Modifier.padding(end = 16.dp, bottom = navBottom + 16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = toc.selectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChapterSelectionBar(
                    selectedCount = toc.selectedCount,
                    onMarkRead = { onMarkSelectedChapters(true) },
                    onMarkUnread = { onMarkSelectedChapters(false) },
                    onMarkPrevious = onMarkPreviousRead,
                    onSelectAll = onSelectAllChapters,
                    onClose = onExitChapterSelection,
                    modifier = Modifier.padding(bottom = navBottom + 16.dp),
                )
            }
        }
    }

    if (showCover && book?.coverImagePath != null) {
        CoverViewerDialog(
            coverPath = book.coverImagePath,
            title = book.title,
            onSave = onSaveCover,
            onClose = { showCover = false },
        )
    }
}

/** The fixed, non-list portion of the screen: cover, metadata, progress, actions, description. */
@Composable
private fun BookHeader(
    book: BookDetailsUi,
    onCoverClick: () -> Unit,
    onEdit: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailCover(book, onClick = onCoverClick, modifier = Modifier.width(116.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = book.title,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
                book.series?.let { SeriesTag(it) }
                Text(
                    text = book.readingState.statusLabel(),
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
        }

        if (book.progress > 0f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailProgress(book.progress)
                Text(
                    // remaining already reads as "5% read" / "Finished" — don't restate the percent.
                    text = book.remaining,
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
            }
        }

        DetailActionBar(
            book = book,
            onEdit = onEdit,
            onMarkRead = onMarkRead,
            onMarkUnread = onMarkUnread,
            onRemove = onRemove,
        )

        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            YomuSettingGroup(title = "About") {
                ExpandableBookDescription(description = description)
            }
        }
    }
}

@Composable
private fun ExpandableBookDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var expandable by remember(description) { mutableStateOf(false) }

    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = description,
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.body,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) expandable = result.hasVisualOverflow
            },
        )
        if (expandable) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = !expanded },
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    color = YomuTheme.colors.accent,
                    style = YomuTheme.type.control,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = YomuTheme.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A single clean row of secondary actions, in place of a header overflow menu. */
@Composable
private fun DetailActionBar(
    book: BookDetailsUi,
    onEdit: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionItem(Icons.Rounded.Edit, "Edit", onEdit)
        if (book.readingState != ReadingState.Finished) {
            ActionItem(Icons.Rounded.Check, "Read", onMarkRead)
        }
        if (book.readingState != ReadingState.Unread) {
            ActionItem(Icons.Rounded.Replay, "Unread", onMarkUnread)
        }
        ActionItem(Icons.Rounded.DeleteOutline, "Remove", onRemove, destructive = true)
    }
}

@Composable
private fun RowScope.ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) YomuTheme.colors.danger else YomuTheme.colors.textPrimary
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(text = label, color = tint, style = YomuTheme.type.caption, maxLines = 1)
    }
}

@Composable
private fun FloatingReadButton(
    reading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onAccent = YomuTheme.colors.appBackground
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = onAccent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (reading) "Resume" else "Read",
            color = onAccent,
            style = YomuTheme.type.control,
        )
    }
}

/** Contents header (with sort control) followed by the flattened, virtualized TOC entries. */
private fun LazyListScope.tocSection(
    toc: TocUiState,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
) {
    item {
        ContentsHeader(toc = toc, onTocSortChange = onTocSortChange)
    }

    when {
        toc.loading -> item { TocLoading() }
        toc.items.isEmpty() -> item { TocNotice("No table of contents.") }
        // No item key: a book's TOC can legitimately repeat an href, and duplicate keys crash
        // LazyColumn. Positional binding is fine here (sort just rebinds in place).
        else -> items(toc.items) { entry ->
            TocRow(
                entry = entry,
                selectionMode = toc.selectionMode,
                onTap = {
                    when {
                        toc.selectionMode -> if (entry.jumpable) onToggleSelection(entry.uid)
                        entry.locatorJson != null -> onOpenChapter(entry.locatorJson)
                    }
                },
                onLongPress = {
                    if (entry.jumpable && !toc.selectionMode) onEnterSelection(entry.uid)
                },
                onToggleRead = { onSetChapterRead(entry.uid, !entry.read) },
            )
        }
    }
}

@Composable
private fun ContentsHeader(toc: TocUiState, onTocSortChange: (TocSortMode) -> Unit) {
    Column(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Contents",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
                modifier = Modifier.weight(1f),
            )
            if (!toc.loading && toc.items.isNotEmpty()) {
                Text(
                    text = "${toc.items.size}",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
            }
        }
        if (!toc.loading && toc.items.isNotEmpty()) {
            val modes = TocSortMode.entries
            YomuSegmentedControl(
                options = modes.map { it.label },
                selectedIndex = modes.indexOf(toc.sort),
                onSelected = { onTocSortChange(modes[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocRow(
    entry: TocEntryUi,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val background = if (entry.selected) YomuTheme.colors.accentSoft else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(background)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            // Indent nested entries so the hierarchy reads at a glance in reading order.
            .padding(
                start = 12.dp + (entry.depth * 16).dp,
                end = 6.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = entry.title,
            color = when {
                !entry.jumpable -> YomuTheme.colors.textMuted
                entry.read -> YomuTheme.colors.textMuted
                else -> YomuTheme.colors.textSecondary
            },
            style = YomuTheme.type.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Per-chapter reading progress for the chapter currently in progress.
        if (entry.percent != null && !selectionMode) {
            Text(
                text = "${(entry.percent * 100).toInt()}%",
                color = YomuTheme.colors.accent,
                style = YomuTheme.type.mono,
            )
        }
        if (entry.jumpable) {
            // The selection circle is shown only in selection mode (animated); otherwise a quick
            // per-chapter read toggle sits here.
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = { fadeIn() + scaleIn(initialScale = 0.6f) togetherWith fadeOut() },
                label = "tocRowTrailing",
            ) { selecting ->
                if (selecting) {
                    SelectionDot(selected = entry.selected)
                } else {
                    ReadToggleButton(read = entry.read, onClick = onToggleRead)
                }
            }
        }
    }
}

/** Per-chapter read toggle: filled accent check when read, muted outline when not. */
@Composable
private fun ReadToggleButton(read: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (read) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = if (read) "Mark unread" else "Mark read",
            tint = if (read) YomuTheme.colors.accent else YomuTheme.colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Trailing selection control, shown only in selection mode. */
@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) YomuTheme.colors.accent else Color.Transparent)
                .then(
                    if (selected) Modifier
                    else Modifier.border(1.5.dp, YomuTheme.colors.textMuted, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = YomuTheme.colors.appBackground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ChapterSelectionBar(
    selectedCount: Int,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPrevious: () -> Unit,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SelectionAction(Icons.Rounded.Close, "Close ($selectedCount)", onClose)
        SelectionAction(Icons.Rounded.Check, "Read", onMarkRead, enabled = selectedCount > 0)
        SelectionAction(
            Icons.Rounded.RemoveDone,
            "Unread",
            onMarkUnread,
            enabled = selectedCount > 0
        )
        // Mark every chapter up to (and including) the selected one as read.
        SelectionAction(
            Icons.AutoMirrored.Rounded.PlaylistAddCheck,
            "To here",
            onMarkPrevious,
            enabled = selectedCount > 0
        )
        SelectionAction(Icons.Rounded.DoneAll, "All", onSelectAll)
    }
}

@Composable
private fun RowScope.SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val tint = if (enabled) YomuTheme.colors.textPrimary else YomuTheme.colors.textMuted
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(text = label, color = tint, style = YomuTheme.type.caption, maxLines = 1)
    }
}

@Composable
private fun BottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, YomuTheme.colors.appBackground),
                ),
            ),
    )
}

@Composable
private fun TocNotice(text: String) {
    Text(
        text = text,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.body,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Loading state for the contents list, with a hint that big books take a moment the first time. */
@Composable
private fun TocLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            color = YomuTheme.colors.accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "Building contents…",
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
        )
        Text(
            text = "Large books can take a few moments the first time.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
    }
}

private fun ReadingState.statusLabel(): String = when (this) {
    ReadingState.Unread -> "Not started"
    ReadingState.Reading -> "In progress"
    ReadingState.Finished -> "Finished"
}

@Composable
private fun SeriesTag(series: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = series, color = YomuTheme.colors.textSecondary, style = YomuTheme.type.caption)
    }
}

@Composable
private fun DetailProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(YomuTheme.colors.accent),
        )
    }
}

@Composable
private fun DetailCover(book: BookDetailsUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickable = book.coverImagePath != null
    val coverModifier = modifier
        .aspectRatio(1f / 1.6f)
        .clip(RoundedCornerShape(YomuTheme.radius.md))
        .then(
            if (clickable) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
        .background(Brush.verticalGradient(book.coverColors))

    if (book.coverImagePath != null) {
        AsyncImage(
            model = File(book.coverImagePath),
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
        )
        return
    }

    Box(modifier = coverModifier.padding(12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.42f)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.72f)),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = book.title,
                color = Color.White,
                style = YomuTheme.type.caption,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
