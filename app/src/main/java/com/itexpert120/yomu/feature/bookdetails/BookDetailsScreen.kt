@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.bookdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuExtendedFloatingActionButton
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuProgressBar
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuScreenTitle
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.YomuVerticalScrollIndicator
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.rememberSelectionMorphShape
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuDecorativeShape
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeShadow
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.feature.collections.OrganizeCollection
import com.itexpert120.yomu.feature.collections.OrganizeSheet
import com.itexpert120.yomu.feature.collections.StatusChoice
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import java.io.File

private data class BookDetailsScrollSample(
    val itemIndex: Int,
    val itemOffset: Int,
    val inProgress: Boolean,
)

/** Which whole-screen state is showing; drives the fade-through between them. */
private enum class BookDetailsContentState { Loading, Missing, Ready }

private val BookDetailsFabTransformOrigin = TransformOrigin(1f, 1f)
private val ReadFabScrollThreshold = 32.dp

/** Room kept clear at the end of a list for the floating Read button / selection toolbar. */
private val FloatingActionClearance = 96.dp

/** Single-pane list position of the pinned contents header (item 0 is the book header). */
private const val SinglePaneTocHeaderIndex = 1

/** Fixed-pane width from which the cover and title sit side by side instead of stacked. */
private val SideBySideIdentityMinWidth = 380.dp

private val TocRowMinHeight = 56.dp
private val TocTrailingSlotSize = 48.dp

/** Deeper TOC levels share the last indent so long hierarchies keep a readable line length. */
private const val TocMaxIndentLevels = 4

private const val TocRowContentType = "tocRow"

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
    organizeStatus: StatusChoice = StatusChoice.Auto,
    organizeCollections: List<OrganizeCollection> = emptyList(),
    onSetStatus: (ManualStatus?) -> Unit = {},
    onSetCollection: (id: String, member: Boolean) -> Unit = { _, _ -> },
    onCreateCollection: (String) -> Unit = {},
) {
    val space = YomuTheme.space
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val detailsScrollState = rememberScrollState()
    var showCover by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }
    val allChaptersSelected = toc.selectableCount > 0 &&
        toc.selectedCount == toc.selectableCount
    var readButtonCollapsed by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    // Where the book's own title block sits on screen versus what covers it (the app bar in one
    // pane, the details pane's top in two), so the bar reveals the title the moment the in-page
    // title scrolls out of view.
    var identityBottomPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var detailsPaneTopPx by remember { mutableFloatStateOf(0f) }
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
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

    val contentState = when {
        book != null -> BookDetailsContentState.Ready
        bookLoaded -> BookDetailsContentState.Missing
        else -> BookDetailsContentState.Loading
    }

    YomuAppSurface {
        // On a tablet-wide screen, split the cover/metadata/actions into a left column and the
        // contents into a right column so the two read side by side instead of the cover sitting
        // alone above a long scroll. Phones (and narrow medium windows) stay single-pane.
        val widthClass = YomuWidthClass.fromWidth(maxWidth)
        val gutter = widthClass.margin
        val twoPane = maxWidth.supportsYomuTwoPane() && book != null

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = contentState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { yomuFadeThroughEnter() togetherWith yomuFadeThroughExit() },
                label = "bookDetailsContent",
            ) { state ->
                when (state) {
                    BookDetailsContentState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        YomuLoadingState()
                    }

                    BookDetailsContentState.Missing -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topBarHeight, bottom = navBottom),
                        contentAlignment = Alignment.Center,
                    ) {
                        YomuEmptyState(
                            icon = Icons.Rounded.Book,
                            title = "No longer in your library",
                            body = "This book has been removed, so there is nothing to show here.",
                            shape = YomuBadgeShape.Clover,
                            tone = YomuBadgeTone.Neutral,
                            actions = {
                                YomuButton(
                                    text = "Go back",
                                    onClick = onBack,
                                    emphasis = YomuButtonEmphasis.Secondary,
                                )
                            },
                        )
                    }

                    // `book` can already be null while this state fades out.
                    BookDetailsContentState.Ready -> if (book != null) {
                        if (twoPane) {
                            TwoPaneDetails(
                                book = book,
                                toc = toc,
                                listState = listState,
                                detailsScrollState = detailsScrollState,
                                navBottom = navBottom,
                                topInset = topBarHeight,
                                onRead = onRead,
                                onIdentityBottom = { identityBottomPx = it },
                                onDetailsPaneTop = { detailsPaneTopPx = it },
                                onCoverClick = { if (book.coverImagePath != null) showCover = true },
                                onTocSortChange = onTocSortChange,
                                onOpenChapter = onOpenChapter,
                                onSetChapterRead = onSetChapterRead,
                                onEnterChapterSelection = onEnterChapterSelection,
                                onToggleChapterSelection = onToggleChapterSelection,
                                onToggleChapterBookmark = onToggleChapterBookmark,
                            )
                        } else {
                            // The app bar overlays the list and turns opaque once scrolled, so the
                            // pinned contents header is shifted (draw phase only) to stop just
                            // below the bar instead of pinning underneath it.
                            val pinnedHeaderModifier = Modifier.graphicsLayer {
                                val headerY = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == SinglePaneTocHeaderIndex }
                                    ?.offset
                                    // Not reported: pinned at the top once scrolled past, otherwise
                                    // still below the viewport.
                                    ?: if (listState.firstVisibleItemIndex >= SinglePaneTocHeaderIndex) 0 else Int.MAX_VALUE
                                translationY = (topBarHeightPx - headerY).coerceAtLeast(0).toFloat()
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(max = YomuReadableMaxWidth)
                                        .fillMaxWidth()
                                        // The list fades into the navigation bar instead of cutting off.
                                        .yomuScrollEdgeShadow(bottom = listState.canScrollForward),
                                    contentPadding = PaddingValues(bottom = navBottom + space.lg),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    item {
                                        BookHeader(
                                            book = book,
                                            gutter = gutter,
                                            topInset = topBarHeight,
                                            coverWidth = if (widthClass == YomuWidthClass.Compact) 128.dp else 160.dp,
                                            onCoverClick = {
                                                if (book.coverImagePath != null) showCover = true
                                            },
                                            identityModifier = Modifier.onWindowBounds {
                                                identityBottomPx = it.bottom.toFloat()
                                            },
                                        )
                                    }

                                    tocSection(
                                        toc = toc,
                                        gutter = gutter,
                                        onTocSortChange = onTocSortChange,
                                        onOpenChapter = onOpenChapter,
                                        onSetChapterRead = onSetChapterRead,
                                        onEnterSelection = onEnterChapterSelection,
                                        onToggleSelection = onToggleChapterSelection,
                                        onToggleBookmark = onToggleChapterBookmark,
                                        headerModifier = pinnedHeaderModifier,
                                    )

                                    // Trailing room for the floating Read button / selection toolbar
                                    // without shifting the rows above when selection toggles.
                                    item { Spacer(Modifier.height(FloatingActionClearance)) }
                                }
                                YomuVerticalScrollIndicator(
                                    state = listState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(
                                            top = topBarHeight + space.xs,
                                            end = space.xxs,
                                            bottom = navBottom + FloatingActionClearance,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            // Derived so scrolling only recomposes the screen when these flip, not every frame.
            val listScrolled by remember(listState) { derivedStateOf { listState.canScrollBackward } }
            val titleVisible by remember(twoPane, listState) {
                derivedStateOf {
                    if (twoPane) {
                        identityBottomPx <= detailsPaneTopPx
                    } else {
                        listState.firstVisibleItemIndex > 0 || identityBottomPx <= topBarBottomPx
                    }
                }
            }
            BookDetailsTopBar(
                title = book?.title.orEmpty(),
                bookAvailable = book != null,
                hasTimeline = hasTimeline,
                selectionMode = toc.selectionMode,
                selectedCount = toc.selectedCount,
                allChaptersSelected = allChaptersSelected,
                // In two panes nothing scrolls beneath the bar (panes start below it), so it stays
                // flat; the title appears only once the pane's own title has scrolled away.
                scrolled = book != null && !twoPane && listScrolled,
                titleVisible = titleVisible,
                onBack = onBack,
                onEdit = onEdit,
                onRemove = requestRemove,
                onTimeline = { showTimeline = true },
                onOrganize = { showOrganize = true },
                onExitChapterSelection = onExitChapterSelection,
                onSelectAllChapters = onSelectAllChapters,
                onDeselectAllChapters = onDeselectAllChapters,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height }
                    .onWindowBounds { topBarBottomPx = it.bottom.toFloat() },
            )

            // Primary action floats over the content (hidden while multi-selecting chapters). Two
            // panes dock it in the details pane instead, so it never covers the chapter list.
            if (book != null && !twoPane) {
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
                            .padding(end = gutter, bottom = navBottom + space.md),
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

    if (showRemoveConfirm) {
        YomuDialog(
            title = "Remove this book?",
            text = "This deletes the imported file and cover from the device. It can't be undone.",
            confirmLabel = "Remove",
            onConfirm = {
                showRemoveConfirm = false
                onRemove()
            },
            onDismiss = { showRemoveConfirm = false },
            icon = Icons.Rounded.DeleteOutline,
            destructive = true,
        )
    }

    OrganizeSheet(
        visible = showOrganize && book != null,
        title = "Organize book",
        status = organizeStatus,
        collections = organizeCollections,
        onSetStatus = onSetStatus,
        onSetCollection = onSetCollection,
        onCreateCollection = onCreateCollection,
        onDismiss = { showOrganize = false },
    )

    if (book != null) {
        YomuBottomSheet(
            visible = showTimeline,
            onDismiss = { showTimeline = false },
        ) { dismiss ->
            ReadingTimeline(book = book, onClose = dismiss)
        }
    }
}

@Composable
private fun BookDetailsTopBar(
    title: String,
    bookAvailable: Boolean,
    hasTimeline: Boolean,
    selectionMode: Boolean,
    selectedCount: Int,
    allChaptersSelected: Boolean,
    scrolled: Boolean,
    titleVisible: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTimeline: () -> Unit,
    onOrganize: () -> Unit,
    onExitChapterSelection: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onDeselectAllChapters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val animate = yomuAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    // One shared backdrop for both bars (normal and selection): transparent over the book header,
    // opaque once content scrolls beneath. Drawn in the draw phase so the fade never recomposes.
    val opaqueContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val containerColor = animateColorAsState(
        targetValue = opaqueContainerColor.copy(alpha = if (scrolled) 1f else 0f),
        animationSpec = if (animate) motion.defaultEffectsSpec<Color>() else snap<Color>(),
        label = "bookDetailsTopBarContainer",
    )
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )
    val titleEnter = if (animate) {
        fadeIn(motion.fastEffectsSpec()) +
            slideInVertically(motion.fastSpatialSpec()) { fullHeight -> fullHeight / 4 }
    } else {
        EnterTransition.None
    }
    val titleExit = if (animate) {
        fadeOut(motion.fastEffectsSpec()) +
            slideOutVertically(motion.fastEffectsSpec()) { fullHeight -> fullHeight / 4 }
    } else {
        ExitTransition.None
    }
    Box(modifier = modifier.drawBehind { drawRect(containerColor.value) }) {
        AnimatedVisibility(
            visible = !selectionMode,
            enter = yomuChromeEnter(fromBottom = false),
            exit = yomuChromeExit(toBottom = false),
        ) {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = titleVisible,
                        enter = titleEnter,
                        exit = titleExit,
                        label = "bookDetailsTopBarTitle",
                    ) {
                        YomuScreenTitle(text = title, maxLines = 1)
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
                            // M3 Expressive vertical menu: the destructive action sits in its own
                            // group, separated by the standard group gap.
                            DropdownMenuPopup(expanded = showActions, onDismissRequest = { showActions = false }) {
                                val groupCount = 2
                                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, groupCount)) {
                                    val itemCount = if (hasTimeline) 2 else 1
                                    DropdownMenuItem(
                                        text = { Text("Organize") },
                                        shape = MenuDefaults.itemShape(0, itemCount).shape,
                                        leadingIcon = { Icon(Icons.Rounded.LibraryAdd, contentDescription = null) },
                                        onClick = {
                                            showActions = false
                                            onOrganize()
                                        },
                                    )
                                    if (hasTimeline) {
                                        DropdownMenuItem(
                                            text = { Text("Reading timeline") },
                                            shape = MenuDefaults.itemShape(1, itemCount).shape,
                                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                            onClick = {
                                                showActions = false
                                                onTimeline()
                                            },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                                DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupCount - 1, groupCount)) {
                                    DropdownMenuItem(
                                        text = { Text("Remove book", color = MaterialTheme.colorScheme.error) },
                                        shape = MenuDefaults.itemShape(0, 1).shape,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showActions = false
                                            onRemove()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = barColors,
            )
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = yomuChromeEnter(fromBottom = false),
            exit = yomuChromeExit(toBottom = false),
        ) {
            TopAppBar(
                navigationIcon = {
                    HeaderIconButton(Icons.Rounded.Close, "Exit chapter selection", onExitChapterSelection)
                },
                title = {
                    YomuScreenTitle(
                        text = if (selectedCount > 0) "$selectedCount selected" else "Select chapters",
                        maxLines = 1,
                    )
                },
                actions = {
                    HeaderIconButton(
                        icon = if (allChaptersSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                        contentDescription = if (allChaptersSelected) {
                            "Deselect all chapters"
                        } else {
                            "Select all chapters"
                        },
                        onClick = if (allChaptersSelected) onDeselectAllChapters else onSelectAllChapters,
                    )
                },
                colors = barColors,
            )
        }
    }
}

/** Header action using the platform touch target; shared by the normal and selection bars. */
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
    navBottom: Dp,
    topInset: Dp,
    onRead: () -> Unit,
    onIdentityBottom: (Float) -> Unit,
    onDetailsPaneTop: (Float) -> Unit,
    onCoverClick: () -> Unit,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterChapterSelection: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
    onToggleChapterBookmark: (Int) -> Unit,
) {
    val space = YomuTheme.space
    // Both panes are the same contained surface, one step above the page.
    val paneColor = MaterialTheme.colorScheme.surfaceContainerLow
    // Material 3 fixed-and-flexible panes: both start below the app bar as contained surfaces, so
    // the bar never has content scrolling beneath it and each pane scrolls independently.
    YomuTwoPane(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset + space.xs),
        startContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navBottom + space.md),
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                // Left pane: book identity + progress + description, independently scrollable.
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onWindowBounds { onDetailsPaneTop(it.top.toFloat()) },
                    shape = YomuShapeTokens.hero,
                    color = paneColor,
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        // Only the widest fixed pane has room for the cover beside the title; the
                        // narrower ones stack a larger, centred cover above centred text.
                        val sideBySide = maxWidth >= SideBySideIdentityMinWidth
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(detailsScrollState)
                                .padding(bottom = space.lg),
                        ) {
                            BookHeader(
                                book = book,
                                gutter = space.lg,
                                onCoverClick = onCoverClick,
                                coverWidth = if (sideBySide) 132.dp else 160.dp,
                                stackIdentity = !sideBySide,
                                contained = true,
                                identityModifier = Modifier.onWindowBounds {
                                    onIdentityBottom(it.bottom.toFloat())
                                },
                            )
                        }
                        YomuVerticalScrollIndicator(
                            state = detailsScrollState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = space.sm, horizontal = space.xxs),
                        )
                    }
                }
                DockedReadButton(
                    reading = book.readingState == ReadingState.Reading,
                    enabled = !toc.selectionMode,
                    onClick = onRead,
                )
            }
        },
        endContent = {
            // Right pane: the contents list (virtualized) in its own contained surface.
            val listBottomPadding = if (toc.selectionMode) FloatingActionClearance else 0.dp
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navBottom + space.md),
                shape = YomuShapeTokens.hero,
                color = paneColor,
            ) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Only the floating selection toolbar overlaps this pane, so the list keeps
                        // trailing room for it while selecting and none otherwise.
                        contentPadding = PaddingValues(bottom = listBottomPadding),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        tocSection(
                            toc = toc,
                            gutter = space.md,
                            onTocSortChange = onTocSortChange,
                            onOpenChapter = onOpenChapter,
                            onSetChapterRead = onSetChapterRead,
                            onEnterSelection = onEnterChapterSelection,
                            onToggleSelection = onToggleChapterSelection,
                            onToggleBookmark = onToggleChapterBookmark,
                            surfaceColor = paneColor,
                        )
                    }
                    YomuVerticalScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(
                                top = space.sm,
                                end = space.xxs,
                                bottom = listBottomPadding + space.sm,
                            ),
                    )
                }
            }
        },
    )
}

/**
 * The fixed, non-list portion of the screen: cover, metadata, progress, and description.
 *
 * In a single pane the identity block is its own tonal surface that runs up under the app bar
 * ([topInset]); [contained] drops that surface because the enclosing pane already provides it.
 */
@Composable
private fun BookHeader(
    book: BookDetailsUi,
    gutter: Dp,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    coverWidth: Dp = 128.dp,
    stackIdentity: Boolean = false,
    contained: Boolean = false,
    identityModifier: Modifier = Modifier,
) {
    val space = YomuTheme.space
    // Large text needs the full width for the title, so it stacks too.
    val stacked = stackIdentity || LocalDensity.current.fontScale > 1.3f
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (contained) space.lg else space.md),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contained) {
                        Modifier
                    } else {
                        Modifier
                            .clip(
                                MaterialTheme.shapes.extraLarge.copy(
                                    topStart = ZeroCornerSize,
                                    topEnd = ZeroCornerSize,
                                ),
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    },
                )
                // Contained: the top inset also keeps the decorative shape behind the cover clear
                // of the pane's clip.
                .padding(
                    top = topInset + if (contained) space.xl else space.lg,
                    bottom = if (contained) 0.dp else space.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            if (stacked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter)
                        .then(identityModifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    DetailCover(
                        book,
                        onClick = onCoverClick,
                        modifier = Modifier.width(coverWidth),
                    )
                    BookIdentityDetails(
                        book = book,
                        centered = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter)
                        .then(identityModifier),
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailCover(
                        book,
                        onClick = onCoverClick,
                        modifier = Modifier.width(coverWidth),
                        // Inside a pane the shape tucks in so the pane's clip doesn't cut it.
                        decorationOffsetX = if (contained) -0.14f else -0.22f,
                    )
                    BookIdentityDetails(
                        book = book,
                        centered = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (book.progress > 0f) {
                val progressLabel = "${(book.progress.coerceIn(0f, 1f) * 100).toInt()}% read"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(space.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = progressLabel,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        // remaining can be the same "N% read" text as the headline above.
                        if (book.remaining != progressLabel) {
                            Text(
                                text = book.remaining,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    DetailProgress(book.progress)
                }
            }
        }

        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gutter),
                verticalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Text(
                    text = "About",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                ExpandableBookDescription(description = description)
            }
        }
    }
}

@Composable
private fun BookIdentityDetails(
    book: BookDetailsUi,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        Text(
            text = book.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = textAlign,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        book.series?.let { SeriesTag(it) }
        // Status, reading time and collections wrap as whole pills, so a long reading time or many
        // collections flow onto a new line instead of squeezing the status pill.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                YomuTheme.space.xxs,
                if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ReadingStatus(state = book.readingState, manualStatus = book.manualStatus)
            book.readingTime?.let { DetailsTag(icon = Icons.Rounded.Schedule, text = it) }
            book.collectionNames.forEach { DetailsTag(icon = Icons.Rounded.CollectionsBookmark, text = it) }
        }
    }
}

@Composable
private fun ReadingStatus(
    state: ReadingState,
    manualStatus: ManualStatus?,
) {
    val (containerColor, contentColor) = if (manualStatus != null) {
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        when (state) {
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
    }
    Surface(
        shape = YomuShapeTokens.pill,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xs),
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (manualStatus) {
                    ManualStatus.OnHold -> Icons.Rounded.PauseCircle
                    ManualStatus.Dropped -> Icons.Rounded.DoNotDisturbOn
                    null -> when (state) {
                        ReadingState.Unread -> Icons.Rounded.Book
                        ReadingState.Reading -> Icons.Rounded.PlayArrow
                        ReadingState.Finished -> Icons.Rounded.CheckCircle
                    }
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = manualStatus?.label ?: state.statusLabel(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
            )
        }
    }
}

/** Reading timeline shown in the details sheet; rows with no date are omitted. */
@Composable
private fun ReadingTimeline(book: BookDetailsUi, onClose: () -> Unit) {
    val rows = buildList {
        book.addedDate?.let { add("Added" to it) }
        book.startedDate?.let { add("Started" to it) }
        book.lastReadDate?.let { add("Last read" to it) }
        book.finishedDate?.let { add("Finished" to it) }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
        YomuSheetHeader(title = "Reading timeline", onClose = onClose)
        YomuSettingList {
            rows.forEachIndexed { index, (label, value) ->
                YomuSettingRow(
                    title = label,
                    position = yomuSettingPosition(index, rows.size),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailing = {
                        Text(
                            text = value,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpandableBookDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var expandable by remember(description) { mutableStateOf(false) }
    val animate = yomuAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    val chevronRotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (animate) motion.fastSpatialSpec<Float>() else snap<Float>(),
        label = "bookDescriptionChevron",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        Text(
            text = description,
            modifier = Modifier.animateContentSize(
                animationSpec = if (animate) motion.defaultSpatialSpec<IntSize>() else snap<IntSize>(),
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
            FilledTonalButton(
                onClick = { expanded = !expanded },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = if (expanded) "Show less" else "Show more")
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(ButtonDefaults.IconSize)
                        .graphicsLayer { rotationZ = chevronRotation.value },
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

/** Two-pane primary action, docked under the details pane instead of floating over the list. */
@Composable
private fun DockedReadButton(
    reading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = if (reading) "Resume" else "Read"
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { testTagsAsResourceId = true }
            .testTag("book-details-read"),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = label)
    }
}

/** Contents header (with sort control) followed by the flattened, virtualized TOC entries. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.tocSection(
    toc: TocUiState,
    gutter: Dp,
    onTocSortChange: (TocSortMode) -> Unit,
    onOpenChapter: (String) -> Unit,
    onSetChapterRead: (Int, Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    surfaceColor: Color? = null,
    headerModifier: Modifier = Modifier,
) {
    // Pin the "Contents" header + sort switcher so it stays reachable while the (potentially long)
    // chapter list scrolls underneath, instead of scrolling away with the first rows.
    stickyHeader {
        ContentsHeader(
            toc = toc,
            gutter = gutter,
            onTocSortChange = onTocSortChange,
            surfaceColor = surfaceColor,
            modifier = headerModifier,
        )
    }

    when {
        toc.loading -> item { TocLoading() }
        toc.items.isEmpty() -> item { TocEmpty() }
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
                onLongPress = {
                    if (entry.jumpable && !toc.selectionMode) onEnterSelection(entry.uid)
                },
                onToggleRead = { onSetChapterRead(entry.uid, !entry.read) },
                onToggleBookmark = { onToggleBookmark(entry.uid) },
                surfaceColor = surfaceColor,
            )
        }
    }
}

@Composable
private fun ContentsHeader(
    toc: TocUiState,
    gutter: Dp,
    onTocSortChange: (TocSortMode) -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color? = null,
) {
    Row(
        // Opaque background: as a pinned sticky header, scrolling TOC rows must not show through it.
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor ?: MaterialTheme.colorScheme.background)
            // The sort button's visible container is inset 4dp inside its touch target.
            .padding(
                start = gutter,
                top = YomuTheme.space.sm,
                end = gutter - YomuTheme.space.xxs,
                bottom = YomuTheme.space.xs,
            ),
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
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLargeEmphasized,
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
    gutter: Dp,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
    surfaceColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val colorSpec: AnimationSpec<Color> = if (yomuAnimationsEnabled()) {
        MaterialTheme.motionScheme.defaultEffectsSpec()
    } else {
        snap()
    }
    // Kept as State and read while drawing, so the selection fade doesn't recompose the row.
    val containerColor = animateColorAsState(
        targetValue = if (entry.selected) scheme.secondaryContainer else surfaceColor ?: scheme.background,
        animationSpec = colorSpec,
        label = "tocRowSelectionContainer",
    )
    val headlineColor by animateColorAsState(
        targetValue = when {
            entry.selected -> scheme.onSecondaryContainer
            !entry.jumpable -> scheme.onSurfaceVariant
            // Read chapters recede (like visited links) so unread ones stand out at a glance.
            entry.read -> scheme.outline
            else -> scheme.onSurface
        },
        animationSpec = colorSpec,
        label = "tocRowHeadlineColor",
    )
    val supportingColor by animateColorAsState(
        targetValue = if (entry.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "tocRowSupportingColor",
    )

    val rowContent: @Composable () -> Unit = {
        TocListItem(
            entry = entry,
            gutter = gutter,
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
        val actionBackground = scheme.primaryContainer
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
            backgroundUntilSwipeThreshold = scheme.surfaceContainerHigh,
        ) {
            rowContent()
        }
    }
}

/**
 * One chapter row. A plain [Row] rather than a Material list item so the gutters can follow the
 * window margin and the row stays cheap in a list of thousands.
 */
@Composable
private fun TocListItem(
    entry: TocEntryUi,
    gutter: Dp,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    containerColor: State<Color>,
    headlineColor: Color,
    supportingColor: Color,
) {
    val space = YomuTheme.space
    val progress = entry.percent?.coerceIn(0f, 1f) ?: 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(containerColor.value) }
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
            )
            .heightIn(min = TocRowMinHeight)
            // The trailing slot is a 48dp target around a 40dp-wide mark, so it lines up with the
            // header's sort button.
            .padding(start = gutter, end = if (entry.jumpable) gutter - space.xxs else gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            color = headlineColor,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = space.md * entry.depth.coerceIn(0, TocMaxIndentLevels),
                    top = space.xs,
                    bottom = space.xs,
                ),
        )
        if (entry.jumpable) {
            Box(
                modifier = Modifier.size(TocTrailingSlotSize),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionMode) {
                    SelectionCheckMark(selected = entry.selected)
                } else if (entry.read) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (progress > 0f) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = if (entry.selected) supportingColor else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                }
            }
        }
    }
}

/** Selection-mode mark: an outlined circle that morphs into a filled scallop with a check. */
@Composable
private fun SelectionCheckMark(selected: Boolean) {
    val shape = rememberSelectionMorphShape(
        selected = selected,
        unselected = MaterialShapes.Circle,
        selectedPolygon = MaterialShapes.Cookie9Sided,
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
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
            modifier = Modifier.padding(YomuTheme.space.md),
        )
    },
    background = background,
    onSwipe = onSwipe,
    isUndo = isUndo,
)

private val TocSwipeActionThreshold = 56.dp

@Composable
private fun ChapterSelectionToolbar(
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
            YomuToolbarAction(Icons.Rounded.CheckCircle, "Read", onMarkRead, enabled = hasSelection),
            YomuToolbarAction(Icons.Rounded.RemoveDone, "Unread", onMarkUnread, enabled = hasSelection),
            YomuToolbarAction(Icons.AutoMirrored.Rounded.PlaylistAddCheck, "To here", onMarkPrevious, enabled = hasSelection),
        ),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = YomuTheme.space.md),
    )
}

@Composable
private fun TocEmpty() {
    YomuEmptyState(
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        title = "No table of contents",
        body = "This book doesn't list its chapters.",
        tone = YomuBadgeTone.Neutral,
        compact = true,
    )
}

/** Loading state for the contents list, with a hint that big books take a moment the first time. */
@Composable
private fun TocLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YomuTheme.space.lg, vertical = YomuTheme.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        YomuLoadingState(message = "Building contents…")
        Text(
            text = "Large books can take a few moments the first time.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ReadingState.statusLabel(): String = when (this) {
    ReadingState.Unread -> "Not started"
    ReadingState.Reading -> "In progress"
    ReadingState.Finished -> "Finished"
}

/** A small quiet pill beside the status: reading time, or a collection the book is in. */
@Composable
private fun DetailsTag(icon: ImageVector, text: String) {
    Surface(
        shape = YomuShapeTokens.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeriesTag(series: String) {
    Surface(
        shape = YomuShapeTokens.pill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = series,
            style = MaterialTheme.typography.labelLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xs),
        )
    }
}

@Composable
private fun DetailProgress(progress: Float) {
    // The book's headline progress uses the expressive wavy style.
    YomuProgressBar(progress = progress, wavy = true)
}

/**
 * Text/accent colour for the generated placeholder cover. The gradient comes from the book, not
 * the theme, so pick whichever of the surface / on-surface roles contrasts with it.
 */
@Composable
private fun placeholderCoverContentColor(coverColors: List<Color>): Color {
    val scheme = MaterialTheme.colorScheme
    val surfaceIsLight = scheme.surface.luminance() > scheme.onSurface.luminance()
    val light = if (surfaceIsLight) scheme.surface else scheme.onSurface
    val dark = if (surfaceIsLight) scheme.onSurface else scheme.surface
    // The title sits at the bottom of the vertical gradient.
    val backdrop = coverColors.lastOrNull() ?: return light
    return if (backdrop.luminance() > 0.5f) dark else light
}

@Composable
private fun DetailCover(
    book: BookDetailsUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    decorationOffsetX: Float = -0.22f,
) {
    val clickable = book.coverImagePath != null
    Box(
        modifier = modifier
            // Hero moment: a soft scalloped shape peeks out behind the cover.
            .yomuDecorativeShape(
                polygon = MaterialShapes.Cookie12Sided,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                scale = 1.05f,
                rotation = 12f,
                offsetX = decorationOffsetX,
                offsetY = -0.42f,
            )
            .aspectRatio(YomuBookCoverAspectRatio)
            .clip(YomuShapeTokens.cover)
            .then(
                if (clickable) Modifier.yomuPressable(onClick = onClick) else Modifier,
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val contentColor = placeholderCoverContentColor(book.coverColors)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(book.coverColors))
                    .padding(YomuTheme.space.md),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.42f)
                        .height(3.dp)
                        .background(contentColor),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = book.title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLargeEmphasized,
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

/**
 * Window bounds for the app-bar title reveal. Throttled (with a trailing update) rather than
 * reported on every scroll frame like onGloballyPositioned.
 */
private fun Modifier.onWindowBounds(onBounds: (IntRect) -> Unit): Modifier = onLayoutRectChanged(throttleMillis = WindowBoundsThrottleMs) { onBounds(it.boundsInWindow) }

private const val WindowBoundsThrottleMs = 32L
