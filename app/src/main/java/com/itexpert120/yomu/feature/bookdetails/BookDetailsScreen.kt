@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.bookdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeRule
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.data.collections.Organize
import com.itexpert120.yomu.data.collections.StatusChoice
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction
import com.itexpert120.yomu.feature.annotations.AnnotationTransferMenuItems
import com.itexpert120.yomu.feature.collections.OrganizeSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationSpec as CoreAnimationSpec
import androidx.compose.animation.core.animate as animateValue

/** Which whole-screen state is showing; drives the fade-through between them. */
private enum class BookDetailsContentState { Loading, Missing, Ready }

/** Wide windows without the library beside the book: the sheet starts just under the status bar. */
private val WideSheetTopGap = 24.dp

/** Width from which the book is a pane beside the library (spec: medium and up). */
private val PaneMinWindowWidth = 600.dp

/** Expanded and up: the book pane is 400 wide (spec), half the window on medium. */
private val ExpandedPaneWidth = 400.dp
private val ExpandedMinWindowWidth = 840.dp

/** Room kept clear at the end of a list for the selection toolbar. */
private val FloatingActionClearance = 96.dp

/** A drag-down past this (or a downward fling) closes the phone sheet. */
private val DismissDistance = 140.dp
private const val DismissVelocityDp = 1100f

/** The book pane's width beside the library: half the window on medium, 400dp from expanded. */
fun bookPaneWidth(windowWidth: Dp): Dp = when {
    windowWidth >= ExpandedMinWindowWidth -> ExpandedPaneWidth
    else -> windowWidth / 2
}

/** Whether a window this wide shows the book as a pane beside the library. */
fun bookPaneBesideLibrary(windowWidth: Dp): Boolean = windowWidth >= PaneMinWindowWidth

/**
 * Book details as Stack's book sheet. On a phone the sheet stands over the dimmed library (drawn
 * by the navigation frame behind this screen), and drags down to close. With [libraryBeside] on a
 * wide window the book is a pane at the end edge with the library beside it; a wide window without
 * the library behind it (a restored page) gets one wide sheet with the contents in their own column.
 * On Resume, navigation moves the phone sheet on into the reader through [resume].
 */
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
    libraryBeside: Boolean = false,
    resume: BookSheetResume? = null,
    onStartChapterSelection: () -> Unit = {},
    organize: Organize = Organize(StatusChoice.Auto, emptyList()),
    onSetStatus: (ManualStatus?) -> Unit = {},
    onSetCollection: (id: String, member: Boolean) -> Unit = { _, _ -> },
    onCreateCollection: (String) -> Unit = {},
    onToggleVolume: (Int) -> Unit = {},
    onCollapseAllVolumes: () -> Unit = {},
    onExpandAllVolumes: () -> Unit = {},
    calibreHighlights: Boolean = false,
    onAnnotationAction: (AnnotationTransferAction) -> Unit = {},
) {
    var showCover by remember { mutableStateOf(false) }
    // The sheet's (and pane's) corner, echoing the display's (32dp where it reports none).
    val SheetCorner = rememberBookSheetCorner()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }
    // Chapter selection is a local mode, so it consumes ordinary back before the sheet closes.
    BackHandler(enabled = toc.selectionMode) { onExitChapterSelection() }

    val contentState = when {
        book != null -> BookDetailsContentState.Ready
        bookLoaded -> BookDetailsContentState.Missing
        else -> BookDetailsContentState.Loading
    }
    val hasTimeline = book != null &&
        (book.addedDate != null || book.startedDate != null || book.lastReadDate != null || book.finishedDate != null)

    val actions = SheetActions(
        onBack = onBack,
        onEdit = onEdit,
        onRead = onRead,
        onOrganize = { showOrganize = true },
        onTimeline = { showTimeline = true },
        onRemove = { showRemoveConfirm = true },
        onCover = { if (book?.coverImagePath != null) showCover = true },
        onStartSelection = onStartChapterSelection,
        onExitSelection = onExitChapterSelection,
        onSelectAll = onSelectAllChapters,
        onDeselectAll = onDeselectAllChapters,
        calibreHighlights = calibreHighlights,
        onAnnotationAction = onAnnotationAction,
    )
    val contents = TocActions(
        onTocSortChange = onTocSortChange,
        onOpenChapter = onOpenChapter,
        onSetChapterRead = onSetChapterRead,
        onEnterSelection = onEnterChapterSelection,
        onToggleSelection = onToggleChapterSelection,
        onToggleBookmark = onToggleChapterBookmark,
        onToggleVolume = onToggleVolume,
        onCollapseAllVolumes = onCollapseAllVolumes,
        onExpandAllVolumes = onExpandAllVolumes,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
        val windowWidth = maxWidth
        val wide = bookPaneBesideLibrary(windowWidth)
        when {
            !wide -> PhoneSheet(
                top = bookSheetTop(statusTop),
                onDismiss = onBack,
                resume = resume,
            ) {
                SingleColumnBody(contentState, book, toc, hasTimeline, actions, contents, gutter = 24.dp, closeIcon = StackIcons.KeyboardArrowDown)
            }

            libraryBeside -> {
                // List-detail: the library beside the pane is live (navigation narrows it); this
                // page draws only the pane, so touches outside it reach the library.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(bookPaneWidth(windowWidth))
                        .fillMaxHeight()
                        .padding(top = statusTop)
                        .bookPaneEdge()
                        .clip(RoundedCornerShape(topStart = SheetCorner))
                        .background(MaterialTheme.colorScheme.surface)
                        // The pane swallows taps so they never reach the close area beneath it.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                ) {
                    SingleColumnBody(contentState, book, toc, hasTimeline, actions, contents, gutter = 28.dp, closeIcon = StackIcons.Close)
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusTop + WideSheetTopGap)
                    .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                WideBody(
                    contentState = contentState,
                    book = book,
                    toc = toc,
                    hasTimeline = hasTimeline,
                    actions = actions,
                    contents = contents,
                    leadingWidth = bookPaneWidth(windowWidth),
                )
            }
        }

        AnimatedVisibility(
            visible = toc.selectionMode,
            enter = yomuChromeEnter(),
            exit = yomuChromeExit(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(if (wide && libraryBeside) Modifier.width(bookPaneWidth(maxWidth)) else Modifier.fillMaxWidth()),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                ChapterSelectionToolbar(
                    selectedCount = toc.selectedCount,
                    onMarkRead = { onMarkSelectedChapters(true) },
                    onMarkUnread = { onMarkSelectedChapters(false) },
                    onMarkPrevious = onMarkPreviousRead,
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
            title = book?.title?.let { "Remove $it?" } ?: "Remove this book?",
            text = "This deletes the imported file and cover from this device. It can't be undone.",
            confirmLabel = "Remove",
            onConfirm = {
                showRemoveConfirm = false
                onRemove()
            },
            onDismiss = { showRemoveConfirm = false },
            destructive = true,
        )
    }

    OrganizeSheet(
        visible = showOrganize && book != null,
        title = "Organize book",
        status = organize.status,
        collections = organize.collections,
        onSetStatus = onSetStatus,
        onSetCollection = onSetCollection,
        onCreateCollection = onCreateCollection,
        onDismiss = { showOrganize = false },
    )

    if (book != null) {
        YomuBottomSheet(visible = showTimeline, onDismiss = { showTimeline = false }) { dismiss ->
            ReadingTimeline(book = book, onClose = dismiss)
        }
    }
}

/** The screen-level actions the sheet's bar, header and menu raise. */
private class SheetActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onRead: () -> Unit,
    val onOrganize: () -> Unit,
    val onTimeline: () -> Unit,
    val onRemove: () -> Unit,
    val onCover: () -> Unit,
    val onStartSelection: () -> Unit,
    val onExitSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onDeselectAll: () -> Unit,
    val calibreHighlights: Boolean,
    val onAnnotationAction: (AnnotationTransferAction) -> Unit,
)

/**
 * [actions] for a list whose contents header is item [headerIndex]: collapsing or expanding every
 * volume while the header is pinned brings the header back to the top of the list first, so the
 * reader lands at the start of the reshaped contents rather than somewhere inside them.
 */
@Composable
private fun rememberListTocActions(actions: TocActions, listState: LazyListState, headerIndex: Int): TocActions {
    val scope = rememberCoroutineScope()
    return remember(actions, listState, headerIndex) {
        fun atHeader(change: () -> Unit) = {
            change()
            if (listState.firstVisibleItemIndex > headerIndex) scope.launch { listState.scrollToItem(headerIndex) }
        }
        TocActions(
            onTocSortChange = actions.onTocSortChange,
            onOpenChapter = actions.onOpenChapter,
            onSetChapterRead = actions.onSetChapterRead,
            onEnterSelection = actions.onEnterSelection,
            onToggleSelection = actions.onToggleSelection,
            onToggleBookmark = actions.onToggleBookmark,
            onToggleVolume = actions.onToggleVolume,
            onCollapseAllVolumes = atHeader(actions.onCollapseAllVolumes),
            onExpandAllVolumes = atHeader(actions.onExpandAllVolumes),
        )
    }
}

/**
 * The phone sheet: starts [top] down, follows a drag down (from its bar, or from the top of its
 * list) and closes past [DismissDistance] or on a downward fling; otherwise it settles back.
 *
 * On Resume ([resume]) the sheet keeps rising until its top meets the window's top, squaring its
 * corners, while the book info scrolls up off it and fades: the sheet becomes the reader's page.
 * Its surface then runs taller than its box, so its bottom never lifts off the window's edge.
 */
@Composable
private fun PhoneSheet(
    top: Dp,
    onDismiss: () -> Unit,
    resume: BookSheetResume?,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val hold = LocalBookSheetHold.current
    val drag = remember(scope) {
        SheetDrag(
            scope = scope,
            dismissPx = with(density) { DismissDistance.toPx() },
            flingPx = with(density) { DismissVelocityDp.dp.toPx() },
            onDismiss = onDismiss,
            isShown = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
        )
    }
    drag.spec = settleSpec
    drag.onDismiss = onDismiss
    drag.hold = hold
    val surface = MaterialTheme.colorScheme.surface
    val topPx = with(density) { top.toPx() }
    val cornerPx = with(density) { rememberBookSheetCorner().toPx() }
    val scrollPx = with(density) { BookSheetResumeScroll.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = top)
            .onSizeChanged { drag.height = it.height.toFloat() }
            .graphicsLayer { translationY = drag.offset - topPx * (resume?.rise ?: 0f).coerceIn(0f, 1f) }
            .drawBehind {
                val lift = topPx * (resume?.rise ?: 0f).coerceIn(0f, 1f)
                drawOutline(sheetOutline(Size(size.width, size.height + lift), cornerPx * (1f - (resume?.square ?: 0f)).coerceIn(0f, 1f)), surface)
            }
            .graphicsLayer {
                shape = SheetTopShape(cornerPx * (1f - (resume?.square ?: 0f)).coerceIn(0f, 1f))
                clip = true
            }
            .nestedScroll(drag.connection)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { drag.dragBy(it) },
                onDragStopped = { velocity -> drag.release(velocity) },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (resume == null) return@graphicsLayer
                    translationY = -scrollPx * resume.rise
                    alpha = (1f - resume.away).coerceIn(0f, 1f)
                },
        ) { content() }
    }
}

/** The sheet's outline: its top corners rounded by [cornerPx], square at the bottom. */
private fun sheetOutline(size: Size, cornerPx: Float): Outline = Outline.Rounded(
    RoundRect(
        rect = Rect(Offset.Zero, size),
        topLeft = CornerRadius(cornerPx),
        topRight = CornerRadius(cornerPx),
    ),
)

private class SheetTopShape(private val cornerPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = sheetOutline(size, cornerPx)
}

@Stable
private class SheetDrag(
    private val scope: CoroutineScope,
    private val dismissPx: Float,
    private val flingPx: Float,
    var onDismiss: () -> Unit,
    private val isShown: () -> Boolean,
) {
    var offset by mutableFloatStateOf(0f)
    var height = 0f
    var spec: CoreAnimationSpec<Float>? = null

    // Navigation's close motion, which a dismissal hands the sheet to (null in previews).
    var hold: BookSheetHold? = null
    private var dismissing = false
    private var settle: Job? = null

    fun dragBy(delta: Float) {
        if (dismissing) return
        settle?.cancel()
        offset = (offset + delta).coerceAtLeast(0f)
    }

    fun release(velocity: Float) {
        if (dismissing || offset <= 0f) return
        val close = offset > dismissPx || velocity > flingPx
        settle?.cancel()
        val handOver = hold
        if (close && handOver != null) {
            // Navigation carries the sheet on from exactly here (and pops once the page has
            // settled, if it is still opening), so it never animates away twice or comes back.
            dismissing = true
            handOver.offsetPx = offset
            offset = 0f
            onDismiss()
            return
        }
        settle = scope.launch {
            if (close) {
                dismissing = true
                onDismiss()
                animateTo(height.coerceAtLeast(offset), velocity)
                // Nothing popped (the page was still settling): bring the sheet back.
                if (isShown()) {
                    dismissing = false
                    animateTo(0f, 0f)
                }
            } else {
                animateTo(0f, velocity)
            }
        }
    }

    private suspend fun animateTo(target: Float, velocity: Float) {
        val animation = spec
        if (animation == null) {
            offset = target
            return
        }
        animateValue(offset, target, velocity, animation) { value, _ -> offset = value.coerceAtLeast(0f) }
    }

    // The list scrolls first; a pull past its top moves the sheet, and scrolling back up takes the
    // sheet back to rest before the list moves again.
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || offset <= 0f || available.y >= 0f) return Offset.Zero
            val used = available.y.coerceAtLeast(-offset)
            dragBy(used)
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || dismissing) return Offset.Zero
            dragBy(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            release(available.y)
            return available
        }
    }
}

/** Bar + list: the phone sheet and the book pane beside the library. */
@Composable
private fun SingleColumnBody(
    contentState: BookDetailsContentState,
    book: BookDetailsUi?,
    toc: TocUiState,
    hasTimeline: Boolean,
    actions: SheetActions,
    contents: TocActions,
    gutter: Dp,
    closeIcon: ImageVector,
) {
    val listState = rememberLazyListState()
    // Item 0 is the book's header; the contents header follows it.
    val tocActions = rememberListTocActions(contents, listState, headerIndex = 1)
    val volumeMotion = rememberTocVolumeMotion(toc, listState)
    val edge = remember(listState) { ContentsEdge(listState, headerIndex = 1) }
    val sheet = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val revealPx = with(density) { 180.dp.toPx() }
    val titleVisible by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > revealPx }
    }
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        SheetBar(
            book = book,
            toc = toc,
            hasTimeline = hasTimeline,
            titleVisible = titleVisible,
            closeIcon = closeIcon,
            actions = actions,
            horizontalPadding = gutter - 14.dp,
        )
        StateCrossfade(contentState, Modifier.weight(1f)) { state ->
            when (state) {
                BookDetailsContentState.Loading -> BookSheetSkeleton(gutter = gutter, coverWidth = 132.dp, modifier = Modifier.padding(top = 8.dp))
                BookDetailsContentState.Missing -> MissingBook(onBack = actions.onBack)
                BookDetailsContentState.Ready -> if (book != null) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val stacked = headerStacked(maxWidth - gutter * 2 - 152.dp)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .contentsTopFade(edge, YomuTheme.stack.rule),
                            contentPadding = PaddingValues(top = 8.dp, bottom = navBottom + 24.dp),
                        ) {
                            item {
                                BookSheetHeader(
                                    book = book,
                                    toc = toc,
                                    gutter = gutter,
                                    coverWidth = if (stacked) 148.dp else 132.dp,
                                    stacked = stacked,
                                    onCoverClick = actions.onCover,
                                    onRead = actions.onRead,
                                    onOrganize = actions.onOrganize,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            tocSection(toc = toc, gutter = gutter, actions = tocActions, motion = volumeMotion, edge = edge)
                            // Room for the selection toolbar without shifting rows when selection toggles.
                            item { Spacer(Modifier.height(FloatingActionClearance)) }
                        }
                        ContentsCurrentChapterPill(
                            listState = listState,
                            toc = toc,
                            motion = volumeMotion,
                            onToggleVolume = contents.onToggleVolume,
                            firstRowIndex = 2,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + YomuTheme.space.md),
                        )
                    }
                }
            }
        }
    }
}

/** A wide window without the library beside it: the book on the left, its contents on the right. */
@Composable
private fun WideBody(
    contentState: BookDetailsContentState,
    book: BookDetailsUi?,
    toc: TocUiState,
    hasTimeline: Boolean,
    actions: SheetActions,
    contents: TocActions,
    leadingWidth: Dp,
) {
    val gutter = 32.dp
    val listState: LazyListState = rememberLazyListState()
    // The contents header is the list's first item.
    val tocActions = rememberListTocActions(contents, listState, headerIndex = 0)
    val volumeMotion = rememberTocVolumeMotion(toc, listState)
    val edge = remember(listState) { ContentsEdge(listState, headerIndex = 0) }
    val detailsScroll: ScrollState = rememberScrollState()
    val revealPx = with(LocalDensity.current) { 180.dp.toPx() }
    val titleVisible by remember(detailsScroll) { derivedStateOf { detailsScroll.value > revealPx } }
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        SheetBar(
            book = book,
            toc = toc,
            hasTimeline = hasTimeline,
            titleVisible = titleVisible,
            closeIcon = StackIcons.KeyboardArrowDown,
            actions = actions,
            horizontalPadding = gutter - 14.dp,
        )
        StateCrossfade(contentState, Modifier.weight(1f)) { state ->
            when (state) {
                BookDetailsContentState.Loading -> BookSheetSkeleton(gutter = gutter, coverWidth = 132.dp, modifier = Modifier.padding(top = 8.dp).width(leadingWidth + gutter))
                BookDetailsContentState.Missing -> MissingBook(onBack = actions.onBack)
                BookDetailsContentState.Ready -> if (book != null) {
                    Row(Modifier.fillMaxSize()) {
                        val stacked = headerStacked(leadingWidth - gutter * 2 - 152.dp)
                        BookSheetHeader(
                            book = book,
                            toc = toc,
                            gutter = gutter,
                            coverWidth = if (stacked) 148.dp else 132.dp,
                            stacked = stacked,
                            onCoverClick = actions.onCover,
                            onRead = actions.onRead,
                            onOrganize = actions.onOrganize,
                            modifier = Modifier
                                .width(leadingWidth)
                                .fillMaxHeight()
                                // The details slide under the sheet's bar: fade them at that edge.
                                .yomuScrollEdgeRule(top = detailsScroll.canScrollBackward)
                                .verticalScroll(detailsScroll)
                                .padding(top = 8.dp, bottom = navBottom + 24.dp),
                        )
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = navBottom + if (toc.selectionMode) FloatingActionClearance else 24.dp),
                            ) {
                                tocSection(toc = toc, gutter = gutter - 8.dp, actions = tocActions, motion = volumeMotion, edge = edge)
                            }
                            ContentsCurrentChapterPill(
                                listState = listState,
                                toc = toc,
                                motion = volumeMotion,
                                onToggleVolume = contents.onToggleVolume,
                                firstRowIndex = 1,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + YomuTheme.space.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateCrossfade(
    state: BookDetailsContentState,
    modifier: Modifier,
    content: @Composable (BookDetailsContentState) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { yomuFadeThroughEnter() togetherWith yomuFadeThroughExit() },
        label = "bookDetailsContent",
    ) { content(it) }
}

/**
 * The sheet's bar: close on the start, Edit and options on the end; the book's title appears in
 * the middle once its own title has scrolled away. While choosing chapters it becomes the
 * selection bar (exit, count, select all).
 */
@Composable
private fun SheetBar(
    book: BookDetailsUi?,
    toc: TocUiState,
    hasTimeline: Boolean,
    titleVisible: Boolean,
    closeIcon: ImageVector,
    actions: SheetActions,
    horizontalPadding: Dp,
) {
    val animate = yomuAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = toc.selectionMode,
        transitionSpec = {
            if (animate) {
                fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "bookSheetBar",
    ) { selecting ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                SheetIconButton(StackIcons.Close, "Exit chapter selection", actions.onExitSelection)
                val count = toc.selectedCount
                Text(
                    text = if (count > 0) "$count ${if (count == 1) "chapter" else "chapters"}" else "Select chapters",
                    style = MaterialTheme.typography.titleLarge,
                    color = YomuTheme.stack.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                )
                val all = toc.selectableCount > 0 && toc.selectedCount == toc.selectableCount
                TextAction(
                    text = if (all) "Deselect all" else "Select all",
                    onClick = if (all) actions.onDeselectAll else actions.onSelectAll,
                )
            } else {
                SheetIconButton(closeIcon, "Close book", actions.onBack)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = titleVisible && book != null,
                        enter = if (animate) fadeIn(motion.fastEffectsSpec()) else EnterTransition.None,
                        exit = if (animate) fadeOut(motion.fastEffectsSpec()) else ExitTransition.None,
                    ) {
                        Text(
                            text = book?.title.orEmpty(),
                            style = StackType.Heading,
                            color = YomuTheme.stack.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (book != null) {
                    SheetIconButton(StackIcons.Edit, "Edit book", actions.onEdit)
                    BookOptions(hasTimeline = hasTimeline, canSelect = toc.selectableCount > 0, actions = actions)
                }
            }
        }
    }
}

/** Book options: the everyday actions, then Remove set apart in the danger colour. */
@Composable
private fun BookOptions(hasTimeline: Boolean, canSelect: Boolean, actions: SheetActions) {
    var expanded by remember { mutableStateOf(false) }
    val stack = YomuTheme.stack
    Box {
        SheetIconButton(
            icon = StackIcons.MoreHoriz,
            contentDescription = "Book options",
            onClick = {
                expanded = true
                actions.onAnnotationAction(AnnotationTransferAction.MenuOpened)
            },
            background = if (expanded) stack.raised else Color.Transparent,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.width(264.dp),
        ) {
            OptionItem(StackIcons.LibraryAdd, "Organize") {
                expanded = false
                actions.onOrganize()
            }
            if (hasTimeline) {
                OptionItem(StackIcons.History, "Reading timeline") {
                    expanded = false
                    actions.onTimeline()
                }
            }
            if (canSelect) {
                OptionItem(StackIcons.PlaylistAddCheck, "Select chapters") {
                    expanded = false
                    actions.onStartSelection()
                }
            }
            AnnotationTransferMenuItems(
                calibreAvailable = actions.calibreHighlights,
                onAction = actions.onAnnotationAction,
                closeMenu = { expanded = false },
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = stack.rule)
            OptionItem(StackIcons.Delete, "Remove book", color = stack.danger) {
                expanded = false
                actions.onRemove()
            }
        }
    }
}

@Composable
private fun OptionItem(icon: ImageVector, text: String, color: Color = YomuTheme.stack.ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = StackType.Body, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StackType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = YomuTheme.stack.ink)
    }
}

/** The book was removed (maybe on another device) while this page was open. */
@Composable
private fun MissingBook(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
        YomuEmptyState(
            icon = StackIcons.Book,
            title = "No longer in your library",
            body = "This book was removed, maybe from another of your devices.",
            actions = { YomuButton(text = "Back to library", onClick = onBack) },
        )
    }
}

/** Reading timeline, stacked on the book as its own sheet; dates not reached yet say so. */
@Composable
private fun ReadingTimeline(book: BookDetailsUi, onClose: () -> Unit) {
    val stack = YomuTheme.stack
    val rows = listOf(
        Triple(StackIcons.Add, "Added", book.addedDate),
        Triple(StackIcons.AutoStories, "Started", book.startedDate),
        Triple(StackIcons.Schedule, "Last read", book.lastReadDate),
        Triple(StackIcons.DoneAll, "Finished", book.finishedDate),
    ).filter { (_, label, value) -> value != null || label != "Added" }
    Column {
        YomuSheetHeader(title = "Reading timeline", onClose = onClose)
        rows.forEachIndexed { index, (icon, label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(icon, contentDescription = null, tint = stack.muted)
                Text(label, style = StackType.Body, color = stack.ink, modifier = Modifier.weight(1f))
                Text(
                    text = value ?: "Not yet",
                    style = if (value != null) StackType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else StackType.Body,
                    color = if (value != null) stack.ink else stack.muted,
                )
            }
            if (index < rows.lastIndex) HorizontalDivider(color = stack.rule)
        }
        book.readingTime?.let {
            Text(
                text = "$it of reading so far.",
                style = StackType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = stack.muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
        }
    }
}

// region Previews

private val PreviewBook = BookDetailsUi(
    id = "preview",
    title = "The Wind in the Willows",
    author = "Kenneth Grahame",
    series = null,
    description = "Mole leaves his spring-cleaning for the river, and with the Water Rat, Badger and the irrepressible Toad finds a life of boats, picnics, motor-cars and, at last, a battle to win back Toad Hall.",
    progress = 0.42f,
    readingTime = "4h 10m",
    addedDate = "Oct 12, 2026",
    startedDate = "Oct 14, 2026",
    lastReadDate = "Nov 25, 2026",
    finishedDate = null,
    coverImagePath = null,
    coverColors = listOf(Color(0xFF3E5641), Color(0xFF22352A)),
    shelfStatus = ShelfStatus.Reading,
    collectionNames = listOf("Classics", "Bedtime"),
)

private val PreviewChapters = listOf(
    "The River Bank", "The Open Road", "The Wild Wood", "Mr. Badger", "Dulce Domum", "Mr. Toad",
    "The Piper at the Gates of Dawn", "Toad's Adventures", "Wayfarers All", "The Further Adventures of Toad",
    "Like Summer Tempests Came His Tears", "The Return of Ulysses",
)

private fun previewToc(selection: Boolean = false) = TocUiState(
    loading = false,
    selectionMode = selection,
    // Out of selection the six read chapters before the current one fold into "1–6 Read".
    items = (
        if (selection) {
            emptyList()
        } else {
            listOf(
                TocEntryUi(
                    uid = READ_RUN_UID, chapterId = "", title = "Read", locatorJson = null, depth = 0, read = true,
                    selected = false, bookmarked = false, percent = null, children = 6, expanded = false,
                    chapterCount = 6, chapterReadCount = 6, readRun = true, numberLabel = "1–6",
                ),
            )
        }
        ) + PreviewChapters.mapIndexed { index, title ->
        TocEntryUi(
            uid = index,
            chapterId = "c$index",
            title = title,
            locatorJson = "{}",
            depth = 0,
            read = index < 6,
            selected = selection && index in 3..5,
            bookmarked = false,
            percent = if (index == 6) 0.38f else null,
            number = index + 1,
            current = index == 6,
        )
    }.filter { selection || it.uid >= 6 },
    selectedCount = if (selection) 3 else 0,
    selectableCount = PreviewChapters.size,
    readCount = 6,
    unreadCount = 6,
    currentNumber = 7,
    currentTitle = PreviewChapters[6],
    chapterTotal = PreviewChapters.size,
    flat = true,
)

/** A serial in volumes: front matter, an open volume holding the reading position, closed ones. */
private fun previewVolumesToc(): TocUiState {
    fun entry(uid: Int, title: String, depth: Int = 0, read: Boolean = false) = TocEntryUi(
        uid = uid,
        chapterId = "c$uid",
        title = title,
        locatorJson = "{}",
        depth = depth,
        read = read,
        selected = false,
        bookmarked = false,
        percent = null,
    )
    return TocUiState(
        loading = false,
        items = listOf(
            entry(0, "Foreword", read = true),
            entry(1, "Credits", read = true),
            entry(2, "Volume 1: Clown").copy(children = 4, chapterCount = 4, chapterReadCount = 2),
            entry(3, "Chapter 1: Crimson", depth = 1, read = true),
            entry(4, "Chapter 2: Situation", depth = 1, read = true),
            entry(5, "Chapter 3: Melissa", depth = 1).copy(current = true, percent = 0.4f),
            entry(6, "Chapter 4: Divination", depth = 1),
            entry(7, "Volume 2: Faceless").copy(children = 213, chapterCount = 213, expanded = false),
            entry(221, "Volume 3: Traveler").copy(children = 249, chapterCount = 249, expanded = false),
            entry(471, "Volume 4: Undying").copy(children = 250, chapterCount = 250, chapterReadCount = 250, expanded = false, read = true),
        ),
        selectableCount = 720,
        readCount = 256,
        unreadCount = 464,
        currentNumber = 5,
        currentTitle = "Chapter 3: Melissa",
        chapterTotal = 718,
        collapsibleCount = 4,
        collapsedCount = 3,
    )
}

/** Stands in for the dimmed library the navigation frame draws behind the sheet. */
@Composable
private fun PreviewBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(YomuTheme.stack.desk),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        content()
    }
}

@Composable
private fun PreviewDetails(
    book: BookDetailsUi? = PreviewBook,
    loaded: Boolean = true,
    toc: TocUiState = previewToc(),
    libraryBeside: Boolean = false,
    resume: BookSheetResume? = null,
) {
    PreviewBackdrop {
        BookDetailsScreen(
            book = book,
            bookLoaded = loaded,
            toc = toc,
            libraryBeside = libraryBeside,
            resume = resume,
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

@Preview(name = "Details light", widthDp = 390, heightDp = 844)
@Composable
private fun BookDetailsLightPreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails() }
}

@Preview(name = "Details dark", widthDp = 390, heightDp = 844)
@Composable
private fun BookDetailsDarkPreview() {
    YomuDesignTheme(darkTheme = true) { PreviewDetails() }
}

@Preview(name = "Details new, no contents", widthDp = 390, heightDp = 844)
@Composable
private fun BookDetailsNewPreview() {
    YomuDesignTheme(darkTheme = false) {
        PreviewDetails(
            book = PreviewBook.copy(title = "Notes from the Field", author = "Ada Lindqvist", progress = 0f, readingTime = null, shelfStatus = ShelfStatus.ToRead, collectionNames = emptyList(), description = null),
            toc = TocUiState(loading = false),
        )
    }
}

@Preview(name = "Details volumes", widthDp = 390, heightDp = 1400)
@Composable
private fun BookDetailsVolumesPreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails(toc = previewVolumesToc()) }
}

@Preview(name = "Details volumes dark", widthDp = 390, heightDp = 1400)
@Composable
private fun BookDetailsVolumesDarkPreview() {
    YomuDesignTheme(darkTheme = true) { PreviewDetails(toc = previewVolumesToc()) }
}

@Preview(name = "Details building contents", widthDp = 390, heightDp = 844)
@Composable
private fun BookDetailsBuildingPreview() {
    YomuDesignTheme(darkTheme = true) { PreviewDetails(toc = TocUiState(loading = true)) }
}

@Preview(name = "Details selecting", widthDp = 390, heightDp = 844)
@Composable
private fun BookDetailsSelectionPreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails(toc = previewToc(selection = true)) }
}

@Preview(name = "Details loading / missing", widthDp = 780, heightDp = 844)
@Composable
private fun BookDetailsStatesPreview() {
    YomuDesignTheme(darkTheme = false) {
        Row {
            Box(Modifier.width(390.dp)) { PreviewDetails(book = null, loaded = false) }
            Box(Modifier.width(390.dp)) { PreviewDetails(book = null, loaded = true) }
        }
    }
}

@Preview(name = "Details tablet pane", widthDp = 1280, heightDp = 800)
@Composable
private fun BookDetailsTabletPanePreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails(libraryBeside = true) }
}

@Preview(name = "Details foldable pane dark", widthDp = 840, heightDp = 900)
@Composable
private fun BookDetailsFoldPanePreview() {
    YomuDesignTheme(darkTheme = true) { PreviewDetails(libraryBeside = true) }
}

@Preview(name = "Details wide, restored", widthDp = 1280, heightDp = 800)
@Composable
private fun BookDetailsWidePreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails() }
}

@Preview(name = "Details medium pane", widthDp = 700, heightDp = 1000)
@Composable
private fun BookDetailsMediumPanePreview() {
    YomuDesignTheme(darkTheme = false) { PreviewDetails(libraryBeside = true) }
}

/** Three frames of Resume: just after the tap, halfway, and the sheet risen into a blank page. */
@Composable
private fun ResumeFrames() {
    Row {
        listOf(Triple(0.15f, 0.3f, 0.5f), Triple(0.5f, 0.8f, 0.95f), Triple(1f, 1f, 1f)).forEach { (rise, square, away) ->
            Box(Modifier.width(390.dp)) {
                PreviewDetails(
                    resume = BookSheetResume(
                        riseState = mutableFloatStateOf(rise),
                        squareState = mutableFloatStateOf(square),
                        awayState = mutableFloatStateOf(away),
                    ),
                )
            }
        }
    }
}

@Preview(name = "Details resuming", widthDp = 1170, heightDp = 844)
@Composable
private fun BookDetailsResumePreview() {
    YomuDesignTheme(darkTheme = false) { ResumeFrames() }
}

@Preview(name = "Details resuming dark", widthDp = 1170, heightDp = 844)
@Composable
private fun BookDetailsResumeDarkPreview() {
    YomuDesignTheme(darkTheme = true) { ResumeFrames() }
}

// endregion
