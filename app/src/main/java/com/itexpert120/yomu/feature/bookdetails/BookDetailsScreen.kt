@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.bookdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.feature.library.ConfirmRemoveDialog
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import java.io.File

@Composable
fun BookDetailsScreen(
    book: BookDetailsUi?,
    toc: TocUiState,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onSaveCover: () -> Unit,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterChapterSelection: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
    onToggleChapterBookmark: (Int) -> Unit,
    onExitChapterSelection: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onDeselectAllChapters: () -> Unit,
    onMarkSelectedChapters: (Boolean) -> Unit,
    onMarkPreviousRead: () -> Unit,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val detailsScrollState = rememberScrollState()
    var showCover by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    val selectableChapterCount = toc.items.count { it.jumpable }
    val allChaptersSelected = selectableChapterCount > 0 &&
        toc.selectedCount == selectableChapterCount
    var readButtonCollapsed by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topBarHeight = if (topBarHeightPx > 0) {
        with(density) { topBarHeightPx.toDp() }
    } else {
        64.dp
    }

    LaunchedEffect(Unit) {
        readButtonCollapsed = false
        val positions = snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        var previous: Pair<Int, Int>? = null
        positions.collect { current ->
            previous?.let { before ->
                if (current != before) {
                    readButtonCollapsed = current.first > before.first ||
                        current.first == before.first &&
                        current.second > before.second
                }
            }
            previous = current
        }
    }
    // The timeline button only appears once there's at least one date to show (mirrors
    // ReadingTimeline, which renders nothing when empty).
    val hasTimeline = book != null &&
        (
            book.addedDate != null ||
                book.startedDate != null ||
                book.lastReadDate != null ||
                book.finishedDate != null
            )
    // The Remove action opens a confirmation first; only the dialog's confirm performs the removal.
    val requestRemove = { showRemoveConfirm = true }
    // Chapter selection is a local mode, so it consumes ordinary back before the details route
    // is popped. No gesture progress is exposed here.
    BackHandler(enabled = toc.selectionMode) {
        onExitChapterSelection()
    }

    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // On a tablet-wide screen, split the cover/metadata/actions into a left column
                // and the contents/description into a right column so the two read side by side
                // instead of the cover sitting alone above a long scroll. Phones stay single-pane.
                val wideEnough = YomuWidthClass.fromWidth(maxWidth).isWide

                if (wideEnough && book != null) {
                    TwoPaneDetails(
                        book = book,
                        toc = toc,
                        listState = listState,
                        detailsScrollState = detailsScrollState,
                        navBottom = navBottom,
                        topInset = topBarHeight,
                        onCoverClick = { if (book.coverImagePath != null) showCover = true },
                        onTocSortChange = onTocSortChange,
                        onOpenChapter = onOpenChapter,
                        onSetChapterRead = onSetChapterRead,
                        onEnterChapterSelection = onEnterChapterSelection,
                        onToggleChapterSelection = onToggleChapterSelection,
                        onToggleChapterBookmark = onToggleChapterBookmark,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = navBottom + 28.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        if (book == null) {
                            item { Spacer(Modifier.height(topBarHeight + 4.dp)) }
                            item {
                                Text(
                                    text = "This book is no longer in your library.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            return@LazyColumn
                        }

                        item {
                            BookHeader(
                                book = book,
                                topInset = topBarHeight,
                                onCoverClick = {
                                    if (book.coverImagePath != null) showCover = true
                                },
                            )
                        }

                        tocSection(
                            toc = toc,
                            onTocSortChange = onTocSortChange,
                            onOpenChapter = onOpenChapter,
                            onSetChapterRead = onSetChapterRead,
                            onEnterSelection = onEnterChapterSelection,
                            onToggleSelection = onToggleChapterSelection,
                            onToggleBookmark = onToggleChapterBookmark,
                        )

                        // Trailing room for the floating Read button / selection toolbar without
                        // shifting the rows above when selection toggles.
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }

                BottomScrim(Modifier.align(Alignment.BottomCenter))
            }

            BookDetailsTopBar(
                title = book?.title.orEmpty(),
                bookAvailable = book != null,
                hasTimeline = hasTimeline,
                selectionMode = toc.selectionMode,
                allChaptersSelected = allChaptersSelected,
                scrolled = listState.canScrollBackward || detailsScrollState.value > 0,
                titleVisible = listState.firstVisibleItemIndex > 0 ||
                    detailsScrollState.value > topBarHeightPx.coerceAtLeast(1),
                onBack = onBack,
                onEdit = onEdit,
                onRemove = requestRemove,
                onTimeline = { showTimeline = true },
                onExitChapterSelection = onExitChapterSelection,
                onSelectAllChapters = onSelectAllChapters,
                onDeselectAllChapters = onDeselectAllChapters,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height },
            )

            // Primary action floats over the content (hidden while multi-selecting chapters).
            if (book != null) {
                AnimatedVisibility(
                    visible = !toc.selectionMode,
                    enter = yomuPopupEnter(),
                    exit = yomuPopupExit(),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FloatingReadButton(
                        reading = book.readingState == ReadingState.Reading,
                        collapsed = readButtonCollapsed,
                        onClick = onRead,
                        modifier = Modifier
                            .padding(end = 16.dp, bottom = navBottom + 16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = toc.selectionMode,
                enter = yomuChromeEnter(),
                exit = yomuChromeExit(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChapterSelectionToolbar(
                    selectedCount = toc.selectedCount,
                    onMarkRead = { onMarkSelectedChapters(true) },
                    onMarkUnread = { onMarkSelectedChapters(false) },
                    onMarkPrevious = onMarkPreviousRead,
                    modifier = Modifier,
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

    ConfirmRemoveDialog(
        visible = showRemoveConfirm,
        count = 1,
        onCancel = { showRemoveConfirm = false },
        onConfirm = {
            showRemoveConfirm = false
            onRemove()
        },
    )

    if (book != null) {
        YomuBottomSheet(
            visible = showTimeline,
            onDismiss = { showTimeline = false },
        ) {
            ReadingTimeline(book)
        }
    }
}

@Composable
private fun BookDetailsTopBar(
    title: String,
    bookAvailable: Boolean,
    hasTimeline: Boolean,
    selectionMode: Boolean,
    allChaptersSelected: Boolean,
    scrolled: Boolean,
    titleVisible: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTimeline: () -> Unit,
    onExitChapterSelection: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onDeselectAllChapters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opaqueContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val containerColor by animateColorAsState(
        targetValue = opaqueContainerColor.copy(alpha = if (scrolled) 1f else 0f),
        animationSpec = tween(durationMillis = 220),
        label = "bookDetailsTopBarContainer",
    )
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !selectionMode,
            enter = yomuChromeEnter(fromBottom = false),
            exit = yomuChromeExit(toBottom = false),
        ) {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = titleVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                            slideInVertically(
                                animationSpec = tween(durationMillis = 260),
                                initialOffsetY = { fullHeight -> fullHeight / 2 },
                            ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                            slideOutVertically(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetY = { fullHeight -> fullHeight / 2 },
                            ),
                        label = "bookDetailsTopBarTitle",
                    ) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (bookAvailable) {
                        HeaderIconButton(
                            icon = Icons.Rounded.Edit,
                            contentDescription = "Edit book",
                            onClick = onEdit,
                        )
                        HeaderIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove book",
                            onClick = onRemove,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (hasTimeline) {
                        HeaderIconButton(
                            icon = Icons.Rounded.History,
                            contentDescription = "Reading timeline",
                            onClick = onTimeline,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    scrolledContainerColor = containerColor,
                ),
            )
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = yomuChromeEnter(fromBottom = false),
            exit = yomuChromeExit(toBottom = false),
        ) {
            ChapterSelectionTopBar(
                allSelected = allChaptersSelected,
                onClose = onExitChapterSelection,
                onSelectAll = onSelectAllChapters,
                onDeselectAll = onDeselectAllChapters,
            )
        }
    }
}

/** Header action using the platform touch target. */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Tablet/expanded layout: cover + metadata + actions + description in a scrollable left column,
 * with the contents list in a scrollable right column, so the two read side by side. The contents
 * stays a [LazyColumn] (a book's TOC can be thousands of entries).
 */
@Composable
private fun TwoPaneDetails(
    book: BookDetailsUi,
    toc: TocUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    detailsScrollState: androidx.compose.foundation.ScrollState,
    navBottom: androidx.compose.ui.unit.Dp,
    topInset: Dp,
    onCoverClick: () -> Unit,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterChapterSelection: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
    onToggleChapterBookmark: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Left pane: book identity + actions + description, independently scrollable.
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .verticalScroll(detailsScrollState)
                    .padding(top = 4.dp, bottom = navBottom + 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BookHeader(
                    book = book,
                    topInset = topInset,
                    onCoverClick = onCoverClick,
                )
            }

            // Right pane: the contents list (virtualized).
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    top = topInset + 4.dp,
                    bottom = navBottom + 28.dp,
                ),
                verticalArrangement = Arrangement.Top,
            ) {
                tocSection(
                    toc = toc,
                    onTocSortChange = onTocSortChange,
                    onOpenChapter = onOpenChapter,
                    onSetChapterRead = onSetChapterRead,
                    onEnterSelection = onEnterChapterSelection,
                    onToggleSelection = onToggleChapterSelection,
                    onToggleBookmark = onToggleChapterBookmark,
                )
                // Trailing room for the floating Read button / selection toolbar.
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

/** The fixed, non-list portion of the screen: cover, metadata, progress, and description. */
@Composable
private fun BookHeader(
    book: BookDetailsUi,
    onCoverClick: () -> Unit,
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            if (book.coverImagePath != null) {
                AsyncImage(
                    model = File(book.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(18.dp)
                        .alpha(0.18f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailCover(
                        book,
                        onClick = onCoverClick,
                        modifier = Modifier.width(108.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = book.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = book.author,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        book.series?.let { SeriesTag(it) }
                        ReadingStatus(
                            state = book.readingState,
                            readingTime = book.readingTime,
                        )
                    }
                }

                if (book.progress > 0f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${(book.progress.coerceIn(0f, 1f) * 100).toInt()}% read",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = book.remaining,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                            )
                        }
                        DetailProgress(book.progress)
                    }
                }
            }
        }

        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "About",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                ExpandableBookDescription(description = description)
            }
        }
    }
}

@Composable
private fun ReadingStatus(
    state: ReadingState,
    readingTime: String?,
) {
    val (containerColor, contentColor) = when (state) {
        ReadingState.Unread ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
        ReadingState.Reading ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
        ReadingState.Finished ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = when (state) {
                    ReadingState.Unread -> Icons.Rounded.Book
                    ReadingState.Reading -> Icons.Rounded.PlayArrow
                    ReadingState.Finished -> Icons.Rounded.CheckCircle
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = state.statusLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
            if (readingTime != null) {
                Text(
                    text = "· $readingTime",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Reading timeline shown in the details sheet; rows with no date are omitted. */
@Composable
private fun ReadingTimeline(book: BookDetailsUi) {
    val rows = buildList {
        book.addedDate?.let { add("Added" to it) }
        book.startedDate?.let { add("Started" to it) }
        book.lastReadDate?.let { add("Last read" to it) }
        book.finishedDate?.let { add("Finished" to it) }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Reading timeline",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                rows.forEachIndexed { index, (label, value) ->
                    TimelineRow(label, value)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, value: String) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ExpandableBookDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var expandable by remember(description) { mutableStateOf(false) }

    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) expandable = result.hasVisualOverflow
            },
        )
        if (expandable) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingReadButton(
    reading: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        expanded = !collapsed,
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
        },
        text = {
            Text(text = if (reading) "Resume" else "Read")
        },
    )
}

/** Contents header (with sort control) followed by the flattened, virtualized TOC entries. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.tocSection(
    toc: TocUiState,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
) {
    // Pin the "Contents" header + sort switcher so it stays reachable while the (potentially long)
    // chapter list scrolls underneath, instead of scrolling away with the first rows.
    stickyHeader {
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
                onToggleBookmark = { onToggleBookmark(entry.uid) },
            )
        }
    }
}

@Composable
private fun ContentsHeader(toc: TocUiState, onTocSortChange: (TocSortMode) -> Unit) {
    Row(
        // Opaque background: as a pinned sticky header, scrolling TOC rows must not show through it.
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (!toc.loading && toc.items.isNotEmpty()) {
                    val chapterCount = toc.readCount + toc.unreadCount
                    "$chapterCount ${if (chapterCount == 1) "chapter" else "chapters"}"
                } else {
                    "Contents"
                },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!toc.loading && toc.items.isNotEmpty()) {
                Text(
                    text = "${toc.readCount} read · ${toc.unreadCount} unread",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (!toc.loading && toc.items.isNotEmpty()) {
            val nextSort = if (toc.sort == TocSortMode.Ascending) {
                TocSortMode.Descending
            } else {
                TocSortMode.Ascending
            }
            IconButton(onClick = { onTocSortChange(nextSort) }) {
                Icon(
                    imageVector = if (toc.sort == TocSortMode.Ascending) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.Rounded.KeyboardArrowUp
                    },
                    contentDescription = "Sort ${nextSort.label.lowercase()}",
                )
            }
        }
    }
}

@Composable
private fun TocRow(
    entry: TocEntryUi,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (entry.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(durationMillis = 220),
        label = "tocRowSelectionContainer",
    )
    val headlineColor by animateColorAsState(
        targetValue = when {
            entry.selected -> MaterialTheme.colorScheme.onSecondaryContainer
            !entry.jumpable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            entry.read -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 220),
        label = "tocRowHeadlineColor",
    )
    val supportingColor by animateColorAsState(
        targetValue = if (entry.selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "tocRowSupportingColor",
    )

    val rowContent: @Composable () -> Unit = {
        TocListItem(
            entry = entry,
            selectionMode = selectionMode,
            onTap = onTap,
            onLongPress = onLongPress,
            containerColor = containerColor,
            headlineColor = headlineColor,
            supportingColor = supportingColor,
        )
    }

    if (selectionMode || !entry.jumpable) {
        rowContent()
    } else {
        val actionBackground = MaterialTheme.colorScheme.primaryContainer
        val readAction = tocSwipeAction(
            icon = if (entry.read) Icons.Rounded.RemoveDone else Icons.Rounded.CheckCircle,
            background = actionBackground,
            isUndo = entry.read,
            onSwipe = onToggleRead,
        )
        val bookmarkAction = tocSwipeAction(
            icon = if (entry.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            background = actionBackground,
            isUndo = entry.bookmarked,
            onSwipe = onToggleBookmark,
        )
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOf(readAction),
            endActions = listOf(bookmarkAction),
            swipeThreshold = TocSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            rowContent()
        }
    }
}

@Composable
private fun TocListItem(
    entry: TocEntryUi,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    containerColor: Color,
    headlineColor: Color,
    supportingColor: Color,
) {
    val progress = entry.percent?.coerceIn(0f, 1f) ?: 0f
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.selectable(
                        selected = entry.selected,
                        role = Role.Checkbox,
                        onClick = onTap,
                    )
                } else {
                    Modifier.yomuPressable(
                        onClick = onTap,
                        onLongClick = onLongPress,
                        role = Role.Button,
                    )
                },
            ),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
        ),
        headlineContent = {
            Text(
                text = entry.title,
                color = headlineColor,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = (entry.depth * 16).dp),
            )
        },
        trailingContent = if (entry.jumpable) {
            {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = entry.selected,
                            onCheckedChange = null,
                        )
                    } else {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = supportingColor,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

private fun tocSwipeAction(
    icon: ImageVector,
    background: Color,
    isUndo: Boolean,
    onSwipe: () -> Unit,
): SwipeAction = SwipeAction(
    icon = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColorFor(background),
            modifier = Modifier.padding(16.dp),
        )
    },
    background = background,
    onSwipe = onSwipe,
    isUndo = isUndo,
)

private val TocSwipeActionThreshold = 56.dp

@Composable
private fun ChapterSelectionTopBar(
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit chapter selection",
                )
            }
        },
        title = {
            Text(text = "Select chapters")
        },
        actions = {
            IconButton(
                onClick = if (allSelected) onDeselectAll else onSelectAll,
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = if (allSelected) "Deselect all chapters" else "Select all chapters",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun ChapterSelectionToolbar(
    selectedCount: Int,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    BottomAppBar(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionToolbarAction(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Read",
                    onClick = onMarkRead,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                )
                SelectionToolbarAction(
                    icon = Icons.Rounded.RemoveDone,
                    label = "Unread",
                    onClick = onMarkUnread,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                )
                SelectionToolbarAction(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                    label = "To here",
                    onClick = onMarkPrevious,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun SelectionToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val contentColor = if (enabled) {
        tint
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                ),
            ),
    )
}

@Composable
private fun TocNotice(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "Building contents…",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Large books can take a few moments the first time.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
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
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = series,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DetailProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun DetailCover(book: BookDetailsUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickable = book.coverImagePath != null
    Card(
        modifier = modifier
            .then(
                if (clickable) Modifier.yomuPressable(onClick = onClick) else Modifier,
            )
            .aspectRatio(1f / 1.6f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(book.coverColors))
                    .padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.42f)
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.72f)),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = book.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
