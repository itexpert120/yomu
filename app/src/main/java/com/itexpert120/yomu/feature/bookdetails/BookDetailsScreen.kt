@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.bookdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuExtendedFloatingActionButton
import com.itexpert120.yomu.core.designsystem.YomuLabeledIconAction
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.YomuVerticalScrollIndicator
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
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

private data class BookDetailsScrollSample(
    val itemIndex: Int,
    val itemOffset: Int,
    val inProgress: Boolean,
)

private val BookDetailsFabTransformOrigin = TransformOrigin(1f, 1f)
private val ReadFabScrollThreshold = 32.dp

@Composable
fun BookDetailsScreen(
    book: BookDetailsUi?,
    bookLoaded: Boolean,
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
    val readFabScrollThresholdPx = with(density) { ReadFabScrollThreshold.roundToPx() }
    val topBarHeight = if (topBarHeightPx > 0) {
        with(density) { topBarHeightPx.toDp() }
    } else {
        64.dp
    }

    LaunchedEffect(readFabScrollThresholdPx) {
        readButtonCollapsed = false
        val positions = snapshotFlow {
            BookDetailsScrollSample(
                itemIndex = listState.firstVisibleItemIndex,
                itemOffset = listState.firstVisibleItemScrollOffset,
                inProgress = listState.isScrollInProgress,
            )
        }
        var previous: BookDetailsScrollSample? = null
        var accumulatedDelta = 0
        positions.collect { current ->
            if (!current.inProgress) {
                previous = current
                accumulatedDelta = 0
                return@collect
            }

            previous?.let { before ->
                val delta = when {
                    current.itemIndex > before.itemIndex -> readFabScrollThresholdPx
                    current.itemIndex < before.itemIndex -> -readFabScrollThresholdPx
                    else -> current.itemOffset - before.itemOffset
                }
                val directionChanged =
                    (accumulatedDelta > 0 && delta < 0) ||
                        (accumulatedDelta < 0 && delta > 0)
                if (directionChanged) accumulatedDelta = 0
                accumulatedDelta += delta
                when {
                    accumulatedDelta >= readFabScrollThresholdPx -> {
                        readButtonCollapsed = true
                        accumulatedDelta = 0
                    }

                    accumulatedDelta <= -readFabScrollThresholdPx -> {
                        readButtonCollapsed = false
                        accumulatedDelta = 0
                    }
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
                val wideEnough = maxWidth.supportsYomuTwoPane()
                val portraitTablet = wideEnough && maxHeight > maxWidth

                if (wideEnough && book != null) {
                    TwoPaneDetails(
                        book = book,
                        toc = toc,
                        listState = listState,
                        detailsScrollState = detailsScrollState,
                        navBottom = navBottom,
                        topInset = topBarHeight,
                        stackBookIdentity = portraitTablet,
                        onCoverClick = { if (book.coverImagePath != null) showCover = true },
                        onTocSortChange = onTocSortChange,
                        onOpenChapter = onOpenChapter,
                        onSetChapterRead = onSetChapterRead,
                        onEnterChapterSelection = onEnterChapterSelection,
                        onToggleChapterSelection = onToggleChapterSelection,
                        onToggleChapterBookmark = onToggleChapterBookmark,
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = navBottom + 28.dp),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            if (book == null) {
                                item { Spacer(Modifier.height(topBarHeight + 4.dp)) }
                                if (bookLoaded) {
                                    item {
                                        Text(
                                            text = "This book is no longer in your library.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
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
                        YomuVerticalScrollIndicator(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(
                                    top = topBarHeight + 8.dp,
                                    end = 4.dp,
                                    bottom = navBottom + 96.dp,
                                ),
                        )
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
                    enter = yomuPopupEnter(BookDetailsFabTransformOrigin),
                    exit = yomuPopupExit(BookDetailsFabTransformOrigin),
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
    var showActions by remember { mutableStateOf(false) }
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
                    HeaderIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                },
                actions = {
                    if (bookAvailable) {
                        HeaderIconButton(
                            icon = Icons.Rounded.Edit,
                            contentDescription = "Edit book",
                            onClick = onEdit,
                        )
                        Box {
                            HeaderIconButton(Icons.Rounded.MoreVert, "Book options", { showActions = true })
                            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                if (hasTimeline) {
                                    DropdownMenuItem(
                                        text = { Text("Reading timeline") },
                                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                        onClick = {
                                            showActions = false
                                            onTimeline()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Remove book", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showActions = false
                                        onRemove()
                                    },
                                )
                            }
                        }
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
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ),
    ) {
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
    stackBookIdentity: Boolean,
    onCoverClick: () -> Unit,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterChapterSelection: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
    onToggleChapterBookmark: (Int) -> Unit,
) {
    YomuTwoPane(
        modifier = Modifier.fillMaxSize(),
        startContent = {
            // Left pane: book identity + actions + description, independently scrollable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailsScrollState)
                        // The backdrop begins at the window edge beneath the transparent top bar.
                        .padding(bottom = navBottom + 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BookHeader(
                        book = book,
                        topInset = topInset,
                        onCoverClick = onCoverClick,
                        coverWidth = 176.dp,
                        stackIdentity = stackBookIdentity,
                    )
                }
                YomuVerticalScrollIndicator(
                    state = detailsScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(
                            top = topInset + 8.dp,
                            end = 4.dp,
                            bottom = navBottom + 28.dp,
                        ),
                )
            }
        },
        endContent = {
            // Right pane: the contents list (virtualized).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
                YomuVerticalScrollIndicator(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(
                            top = topInset + 8.dp,
                            end = 4.dp,
                            bottom = navBottom + 96.dp,
                        ),
                )
            }
        },
    )
}

/** The fixed, non-list portion of the screen: cover, metadata, progress, and description. */
@Composable
private fun BookHeader(
    book: BookDetailsUi,
    onCoverClick: () -> Unit,
    topInset: Dp = 0.dp,
    coverWidth: Dp = 128.dp,
    stackIdentity: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (stackIdentity || LocalDensity.current.fontScale > 1.3f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DetailCover(
                            book,
                            onClick = onCoverClick,
                            modifier = Modifier.width(coverWidth),
                        )
                        BookIdentityDetails(
                            book = book,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
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
                            modifier = Modifier.width(coverWidth),
                        )
                        BookIdentityDetails(
                            book = book,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp),
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
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    style = MaterialTheme.typography.titleLarge,
                )
                ExpandableBookDescription(description = description)
            }
        }
    }
}

@Composable
private fun BookIdentityDetails(
    book: BookDetailsUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = book.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
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
        shape = androidx.compose.foundation.shape.CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = description,
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = if (yomuAnimationsEnabled()) 320 else 0,
                    easing = YomuMotion.Emphasized,
                ),
                alignment = Alignment.TopStart,
            ),
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
    val label = if (reading) "Resume" else "Read"
    YomuExtendedFloatingActionButton(
        expanded = !collapsed,
        onClick = onClick,
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("book-details-read"),
        icon = Icons.Rounded.PlayArrow,
        label = label,
        contentDescription = label,
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
                style = MaterialTheme.typography.titleLarge,
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
            FilledTonalIconButton(
                onClick = { onTocSortChange(nextSort) },
                shapes = IconButtonDefaults.shapes(),
            ) {
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
                YomuLabeledIconAction(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Read",
                    onClick = onMarkRead,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                )
                YomuLabeledIconAction(
                    icon = Icons.Rounded.RemoveDone,
                    label = "Unread",
                    onClick = onMarkUnread,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                )
                YomuLabeledIconAction(
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

@Preview(widthDp = 720, heightDp = 900, showBackground = true)
@Composable
private fun TabletBookDetailsPreview() {
    YomuDesignTheme {
        BookDetailsScreen(
            book = BookDetailsUi(
                id = "preview",
                title = "A long book title that should remain readable beside the cover",
                author = "Open Reader",
                series = "Preview collection",
                description = "A quiet sample description for validating the tablet two-pane layout.",
                progress = 0.42f,
                remaining = "4h 12m remaining",
                readingTime = "2h 18m",
                addedDate = "14 Jul 2026",
                startedDate = "15 Jul 2026",
                lastReadDate = "16 Jul 2026",
                finishedDate = null,
                coverImagePath = null,
                coverColors = listOf(Color(0xFF284B63), Color(0xFF9B5DE5)),
                readingState = ReadingState.Reading,
            ),
            bookLoaded = true,
            toc = TocUiState(
                loading = false,
                items = List(8) { index ->
                    TocEntryUi(
                        uid = index,
                        chapterId = "chapter-$index",
                        title = "Chapter ${index + 1}: A sample chapter title",
                        locatorJson = "{}",
                        depth = 0,
                        read = index < 3,
                        selected = false,
                        bookmarked = index == 1,
                        percent = if (index == 3) 0.42f else null,
                    )
                },
            ),
            onBack = {},
            onRead = {},
            onEdit = {},
            onRemove = {},
            onSaveCover = {},
            onTocSortChange = {},
            onOpenChapter = {},
            onSetChapterRead = { _, _ -> },
            onEnterChapterSelection = {},
            onToggleChapterSelection = {},
            onToggleChapterBookmark = {},
            onExitChapterSelection = {},
            onSelectAllChapters = {},
            onDeselectAllChapters = {},
            onMarkSelectedChapters = {},
            onMarkPreviousRead = {},
        )
    }
}
