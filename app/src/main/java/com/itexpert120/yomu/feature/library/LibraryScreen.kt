@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.library

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.snap
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.app.navigation.LocalYomuNavigationBarShown
import com.itexpert120.yomu.core.designsystem.LocalYomuWidthMove
import com.itexpert120.yomu.core.designsystem.StackCompactHeader
import com.itexpert120.yomu.core.designsystem.StackCompactHeaderHeight
import com.itexpert120.yomu.core.designsystem.StackHeaderFadeDistance
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuContentMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.stackHeaderProgress
import com.itexpert120.yomu.core.designsystem.stackSnapRest
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuGlidePlacement
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.data.collections.LibraryTabKeys
import com.itexpert120.yomu.feature.collections.ManageTabsSheet
import com.itexpert120.yomu.feature.collections.OrganizeSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the shelf sheet is showing. */
private enum class ShelfContent {
    Loading,
    Empty,
    Shelf,
    Search,
}

/** Below this height (a landscape phone) the pile moves beside the shelf instead of above it. */
private val SideBySideMaxHeight = 480.dp

/** The pile column's width beside the shelf on a landscape phone (cover 120 + its fan + margins). */
private val SideBySidePileWidth = 216.dp

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onPortraitGridColumnsChange: (Int) -> Unit,
    onLandscapeGridColumnsChange: (Int) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onImport: () -> Unit,
    onEnterSelection: (String) -> Unit = {},
    onToggleSelect: (String) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onRemoveSelected: () -> Unit = {},
    onMarkSelectedRead: () -> Unit = {},
    onMarkSelectedUnread: () -> Unit = {},
    onRetryImport: () -> Unit = {},
    onSelectTab: (String) -> Unit = {},
    onCreateCollection: (name: String, addSelected: Boolean) -> Unit = { _, _ -> },
    onRenameCollection: (id: String, name: String) -> Unit = { _, _ -> },
    onDeleteCollection: (String) -> Unit = {},
    onReorderTabs: (order: List<String>) -> Unit = {},
    onSetTabHidden: (key: String, hidden: Boolean) -> Unit = { _, _ -> },
    onSetSelectedInCollection: (id: String, member: Boolean) -> Unit = { _, _ -> },
    onSetSelectedStatus: (ManualStatus?) -> Unit = {},
    onRememberSearch: (String) -> Unit = {},
    onForgetSearch: (String) -> Unit = {},
    // Shown on the empty library when the app can take the user to join sync.
    onHaveSyncWords: (() -> Unit)? = null,
    // Previews only: start with the shelf lifted and its tabs pinned under the compact header.
    startPinned: Boolean = false,
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }
    var showManageCollections by remember { mutableStateOf(false) }
    // One scroll position per tab, so swiping between tabs doesn't reset where you were.
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val searchListState = remember { LazyListState() }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeColumns = if (isLandscape) state.landscapeGridColumns else state.portraitGridColumns

    // Scrolling the results means the user is done typing: drop the keyboard so it doesn't cover them.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchListState) {
        snapshotFlow { searchListState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) focusManager.clearFocus() }
    }

    // Tap opens book details; long-press starts multi-select. While selecting, both toggle.
    val onCardClick: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onOpenDetails(book.id)
    }
    val onCardLongPress: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onEnterSelection(book.id)
    }

    // Local library states have priority over route navigation. This is a regular back callback;
    // it deliberately does not expose gesture progress.
    BackHandler(enabled = state.selectionMode || state.searchActive) {
        if (state.selectionMode) {
            onExitSelection()
        } else if (state.searchActive) {
            onSearchToggle()
        }
    }

    val selecting = state.selectionMode
    val searching = state.searchActive && !selecting
    val content = when {
        state.isLoading -> ShelfContent.Loading
        state.totalCount == 0 -> ShelfContent.Empty
        searching -> ShelfContent.Search
        else -> ShelfContent.Shelf
    }
    val hasShelves = !state.isLoading && state.totalCount > 0
    // Created once the shelves exist, so it starts on the remembered tab instead of reporting
    // page 0 as a fresh selection.
    val selectedIndex = state.tabs.indexOfFirst { it.key == state.selectedTab }.coerceAtLeast(0)
    val pagerState = key(hasShelves) {
        rememberPagerState(initialPage = selectedIndex) { state.tabs.size }
    }
    val pagerScope = rememberCoroutineScope()
    val showAllBooks: () -> Unit = {
        val all = state.tabs.indexOfFirst { it.key == LibraryTabKeys.ALL }
        onSelectTab(LibraryTabKeys.ALL)
        // From search the pager isn't on screen: just move it there for when it returns.
        if (all >= 0 && searching) {
            pagerState.requestScrollToPage(all)
        } else if (all >= 0) {
            pagerScope.launch { pagerState.animateScrollToPage(all) }
        }
    }

    YomuAppSurface {
        // One width class from the measured width (a navigation rail beside the library is already
        // accounted for) drives the gutters, grid and list, so their edges line up. Beside a book
        // pane the library is narrowed (LocalYomuWidthMove): while it stays in the same class it
        // keeps every decision of its full width — gutter, sheet margins, insets, grid density — so
        // opening and closing a pane only changes how many covers fit in a row.
        val fullExtra = with(LocalDensity.current) { LocalYomuWidthMove.current.fullExtra().toDp() }
        val designWidth = (maxWidth + fullExtra).let {
            if (YomuWidthClass.fromWidth(it).isWide == YomuWidthClass.fromWidth(maxWidth).isWide) it else maxWidth
        }
        val widthClass = YomuWidthClass.fromWidth(designWidth)
        val gutter = if (widthClass.isWide) 32.dp else 24.dp
        // The boards: a phone's pile has its info at the far edge; a tall (portrait) tablet the same,
        // larger; a wide landscape column the info right beside the pile.
        val pileLayout = when {
            !widthClass.isWide -> PileLayout.Phone
            maxHeight > designWidth * 1.25f -> PileLayout.Tall
            else -> PileLayout.Beside
        }
        // On tablets the shelf sheet stands 16dp off the rail (and off the far edge in portrait).
        val sheetStart = if (widthClass.isWide) 16.dp else 0.dp
        val sheetEnd = if (pileLayout == PileLayout.Tall) 16.dp else 0.dp
        val sheetWidth = maxWidth - sheetStart - sheetEnd
        val designSheetWidth = designWidth - sheetStart - sheetEnd
        // Capped content is centred inside the sheet: its leading edge is 24dp in until the cap bites.
        // (From the full width, so the gutter is the same beside a pane.)
        val gridInset = ((designSheetWidth - YomuContentMaxWidth) / 2).coerceAtLeast(24.dp)
        val listInset = ((designSheetWidth - YomuReadableMaxWidth) / 2).coerceAtLeast(24.dp)
        val headerInset = if (state.viewMode == LibraryViewMode.List) listInset else gridInset
        // Over the navigation bar the content already ends above the gesture area; without it (rail,
        // selection toolbar, search) the last row must clear that area, and the toolbar too.
        val navBottom = if (LocalYomuNavigationBarShown.current) {
            0.dp
        } else {
            WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        }
        val contentBottom = navBottom + if (selecting) SelectionToolbarClearance + YomuTheme.space.md else YomuTheme.space.lg
        val flat = selecting || searching || content == ShelfContent.Empty
        val sideBySide = maxHeight < SideBySideMaxHeight &&
            maxWidth > maxHeight &&
            !flat &&
            content == ShelfContent.Shelf &&
            state.pile.isNotEmpty()
        // Beside the pile on a landscape phone the shelf is phone-sized, so it keeps phone density.
        // Beside the pile a landscape phone has room for one row of covers: the Comfortable view
        // shows them bare there (the board's landscape shelf), five across.
        val shelfViewMode = if (sideBySide && state.viewMode == LibraryViewMode.ComfortableGrid) {
            LibraryViewMode.CoverOnlyGrid
        } else {
            state.viewMode
        }
        val grid = if (sideBySide) {
            if (shelfViewMode == LibraryViewMode.CoverOnlyGrid) {
                GridSpec(inset = 24.dp, top = 16.dp, rowGap = 14.dp, columnGap = 14.dp, autoMinSize = 88.dp)
            } else {
                gridSpec(shelfViewMode, PileLayout.Phone, 24.dp)
            }
        } else {
            gridSpec(state.viewMode, pileLayout, gridInset)
        }
        val autoColumns = grid.autoColumns(if (sideBySide) maxWidth - SideBySidePileWidth else sheetWidth)

        val bookPage: @Composable (LibraryPage) -> Unit = { page ->
            if (state.viewMode != LibraryViewMode.List) {
                LibraryGrid(
                    state = gridStates.getOrPut(page.key) { LazyGridState() },
                    viewMode = shelfViewMode,
                    columns = activeColumns,
                    spec = grid,
                    contentPadding = PaddingValues(
                        start = grid.inset,
                        end = (grid.inset - fullExtra).coerceAtLeast(minOf(grid.inset, 24.dp)),
                        top = grid.top,
                        bottom = contentBottom,
                    ),
                    books = page.books,
                    selectedIds = state.selectedIds,
                    selecting = selecting,
                    onBookClick = onCardClick,
                    onBookLongPress = onCardLongPress,
                )
            } else {
                LibraryList(
                    state = listStates.getOrPut(page.key) { LazyListState() },
                    chapters = state.chapters,
                    contentPadding = PaddingValues(start = listInset, end = listInset, top = 4.dp, bottom = contentBottom),
                    books = page.books,
                    selectedIds = state.selectedIds,
                    selecting = selecting,
                    onBookClick = onCardClick,
                    onBookLongPress = onCardLongPress,
                )
            }
        }
        val shelf: @Composable ColumnScope.(trailing: (@Composable RowScope.() -> Unit)?) -> Unit = { trailing ->
            LibraryTabRow(
                tabs = state.tabs,
                pagerState = pagerState,
                onSelect = { index -> pagerScope.launch { pagerState.animateScrollToPage(index) } },
                onEditTabs = { showManageCollections = true },
                horizontalInset = headerInset,
                gap = if (widthClass.isWide) 26.dp else 22.dp,
                modifier = Modifier.yomuGlidePlacement().padding(top = if (widthClass.isWide) 4.dp else 6.dp),
                trailing = trailing,
            )
            LibraryTabPager(
                state = state,
                pagerState = pagerState,
                onSelectTab = onSelectTab,
                onClearSearch = { onSearchQueryChange("") },
                onShowAll = showAllBooks,
                modifier = Modifier.weight(1f),
                pageContent = bookPage,
            )
        }

        if (sideBySide) {
            SideBySideLibrary(
                state = state,
                gutter = gutter,
                onOpenDetails = onOpenDetails,
                onOpenReader = onOpenReader,
                onSearch = onSearchToggle,
                onImport = onImport,
                onArrange = { showOptionsSheet = true },
                shelf = shelf,
            )
        } else {
            StackedLibrary(
                state = state,
                content = content,
                flat = flat,
                gutter = gutter,
                pileLayout = pileLayout,
                sheetStart = sheetStart,
                sheetEnd = sheetEnd,
                sheetHeightCap = maxHeight,
                onSearchToggle = onSearchToggle,
                onImport = onImport,
                onArrange = { showOptionsSheet = true },
                onOpenDetails = onOpenDetails,
                onOpenReader = onOpenReader,
                onExitSelection = onExitSelection,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onHaveSyncWords = onHaveSyncWords,
                shelf = shelf,
                search = {
                    LibrarySearchResults(
                        state = state,
                        listState = searchListState,
                        gutter = listInset,
                        bottomPadding = contentBottom,
                        onQueryChange = onSearchQueryChange,
                        onForgetSearch = onForgetSearch,
                        onOpen = { book ->
                            onRememberSearch(state.searchQuery)
                            onOpenDetails(book.id)
                        },
                        onLongPress = { book -> onEnterSelection(book.id) },
                        onShowAll = showAllBooks,
                    )
                },
                onSearchQueryChange = onSearchQueryChange,
                onRememberSearch = onRememberSearch,
                startPinned = startPinned,
            )
        }

        LibraryOptionsSheet(
            visible = showOptionsSheet,
            sortMode = state.sortMode,
            viewMode = state.viewMode,
            portraitColumns = state.portraitGridColumns,
            landscapeColumns = state.landscapeGridColumns,
            onSortModeChange = onSortModeChange,
            onViewModeChange = onViewModeChange,
            onPortraitColumnsChange = onPortraitGridColumnsChange,
            onLandscapeColumnsChange = onLandscapeGridColumnsChange,
            onDismiss = { showOptionsSheet = false },
            currentAutoColumns = autoColumns,
            landscape = isLandscape,
        )

        AnimatedVisibility(
            visible = selecting,
            enter = yomuChromeEnter(),
            exit = yomuChromeExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LibrarySelectionToolbar(
                selectedCount = state.selectedIds.size,
                onOrganize = { showOrganize = true },
                onMarkRead = onMarkSelectedRead,
                onMarkUnread = onMarkSelectedUnread,
                onDelete = { showDeleteConfirm = true },
            )
        }

        ImportNotice(
            importing = state.isImporting,
            importingCount = state.importingCount,
            notice = state.importNotice,
            canRetry = state.canRetryImport,
            onRetry = onRetryImport,
            horizontalMargin = YomuTheme.space.md,
            aboveBottomChrome = selecting,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        OrganizeSheet(
            visible = showOrganize && selecting,
            title = if (state.selectedIds.size == 1) "Organize 1 book" else "Organize ${state.selectedIds.size} books",
            status = state.organize.status,
            collections = state.organize.collections,
            onSetStatus = onSetSelectedStatus,
            onSetCollection = onSetSelectedInCollection,
            onCreateCollection = { onCreateCollection(it, true) },
            onDismiss = { showOrganize = false },
        )

        ManageTabsSheet(
            visible = showManageCollections,
            tabs = state.allTabs,
            hiddenKeys = state.hiddenTabs,
            onCreate = { onCreateCollection(it, false) },
            onRename = onRenameCollection,
            onDelete = onDeleteCollection,
            onReorder = onReorderTabs,
            onSetHidden = onSetTabHidden,
            onDismiss = { showManageCollections = false },
        )

        ConfirmRemoveDialog(
            visible = showDeleteConfirm,
            titles = if (showDeleteConfirm) state.selectedTitles() else emptyList(),
            onCancel = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onRemoveSelected()
            },
        )
    }
}

private fun LibraryUiState.selectedTitles(): List<String> = pages.asSequence()
    .flatMap { it.books }
    .filter { it.id in selectedIds }
    .distinctBy { it.id }
    .map { it.title }
    .toList()

/**
 * Stack's library on a phone or tablet: the title and the pile of books in progress lie on the desk;
 * the shelf (tabs + grid) is a white sheet over them. Scrolling first lifts the sheet over the still
 * pile until its tabs pin under the compact header (its corners square off on the way, and the
 * header — "Library" with search, import and arrange — fades in as the large title is covered),
 * then scrolls the shelf. Search, selection and the empty library are
 * flat: the whole screen turns sheet, their bar takes the title's place and the sheet glides up
 * under it.
 */
@Composable
private fun StackedLibrary(
    state: LibraryUiState,
    content: ShelfContent,
    flat: Boolean,
    gutter: Dp,
    pileLayout: PileLayout,
    sheetStart: Dp,
    sheetEnd: Dp,
    sheetHeightCap: Dp,
    onSearchToggle: () -> Unit,
    onImport: () -> Unit,
    onArrange: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onHaveSyncWords: (() -> Unit)?,
    shelf: @Composable ColumnScope.(trailing: (@Composable RowScope.() -> Unit)?) -> Unit,
    search: @Composable () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRememberSearch: (String) -> Unit,
    startPinned: Boolean = false,
) {
    val density = LocalDensity.current
    val stack = YomuTheme.stack
    val animate = yomuAnimationsEnabled()
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    // The tabs pin under the compact header, which sits under the status bar.
    val collapsedTop = with(density) { (statusTop + StackCompactHeaderHeight).toPx() }
    val headerFade = with(density) { StackHeaderFadeDistance.toPx() }
    var deskHeight by remember { mutableIntStateOf(0) }
    val travel = if (flat) 0f else (deskHeight - collapsedTop).coerceAtLeast(0f)
    val sheetLift = remember { mutableFloatStateOf(0f) }
    // The sheet's resting top follows the desk: it glides when the desk changes (the pile arriving,
    // a bar replacing the title) instead of jumping. Lift the desk can no longer take (e.g. selection
    // starting while the shelf is pinned) moves into the glide's start, so the sheet never jumps.
    val sheetTop = remember { Animatable(0f) }
    val glide = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(deskHeight, travel) {
        if (startPinned && deskHeight > 0) sheetLift.floatValue = -travel
        val lift = sheetLift.floatValue
        val clamped = lift.coerceIn(-travel, 0f)
        if (clamped != lift) {
            sheetLift.floatValue = clamped
            sheetTop.snapTo(sheetTop.value + lift - clamped)
        }
        val target = deskHeight.toFloat()
        if (sheetTop.value == 0f || !animate) sheetTop.snapTo(target) else sheetTop.animateTo(target, glide)
    }
    // The lift has two rests — the pile shown (0) and the sheet pinned under the compact header
    // (-travel). A release or fling that would leave it between them carries on, from its own
    // speed, to the nearer one on the spatial spring (stackSnapRest); a fling past the pin hands its
    // remaining speed to the grid, and one that ends in the grid's top carries the sheet down.
    val liftDecay = rememberSplineBasedDecay<Float>()
    val sheetScroll = remember(travel, liftDecay, glide, animate) {
        suspend fun settleLift(velocity: Float): Float {
            val start = sheetLift.floatValue
            if (travel <= 0f) return velocity
            val projected = liftDecay.calculateTargetValue(start, velocity)
            val rest = stackSnapRest(projected, -travel, 0f)
            if (rest != null) {
                if (animate) {
                    AnimationState(start, velocity).animateTo(rest, glide, sequentialAnimation = false) { sheetLift.floatValue = value }
                } else {
                    sheetLift.floatValue = rest
                }
                return 0f
            }
            // Lands past an end: decay to it, and give back whatever speed is left there.
            var left = velocity
            AnimationState(start, velocity).animateDecay(liftDecay) {
                val clamped = value.coerceIn(-travel, 0f)
                sheetLift.floatValue = clamped
                left = velocity
                if (clamped != value) cancelAnimation()
            }
            return left
        }
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                val lift = sheetLift.floatValue
                // Pinned and flung up, or resting with the pile shown and flung down: the grid's.
                if (lift <= -travel && available.y <= 0f) return Velocity.Zero
                if (lift >= 0f && available.y >= 0f) return Velocity.Zero
                val left = settleLift(available.y)
                return Velocity(0f, available.y - left)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // The grid reached its top still moving down: the sheet comes down with it.
                if (available.y <= 0f || sheetLift.floatValue >= 0f) return Velocity.Zero
                val left = settleLift(available.y)
                return Velocity(0f, available.y - left)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (sheetLift.floatValue + available.y).coerceAtLeast(-travel)
                val used = next - sheetLift.floatValue
                sheetLift.floatValue = next
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (sheetLift.floatValue + available.y).coerceAtMost(0f)
                val used = next - sheetLift.floatValue
                sheetLift.floatValue = next
                return Offset(0f, used)
            }
        }
    }
    // 0 at rest, 1 once the tabs have pinned under the compact header; read in layers only.
    val headerProgress: () -> Float = if (flat || travel <= 0f) {
        { 0f }
    } else {
        { stackHeaderProgress(sheetTop.value + sheetLift.floatValue, collapsedTop, headerFade) }
    }
    // 0 on the desk, 1 when the whole screen is sheet (search, selection, the empty library).
    val flatness = animateFloatAsState(
        targetValue = if (flat) 1f else 0f,
        animationSpec = if (animate) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "libraryFlat",
    )
    val sheetRadius = with(density) { 32.dp.toPx() }
    val deskHeightDp = with(density) { deskHeight.toDp() }
    val sheetHeight = if (flat) sheetHeightCap - deskHeightDp else sheetHeightCap - statusTop - StackCompactHeaderHeight
    val showPile = content == ShelfContent.Shelf && !flat && state.pile.isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(lerp(stack.desk, stack.sheet, flatness.value)) }
            .nestedScroll(sheetScroll),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { deskHeight = it.height }
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        ) {
            when {
                state.selectionMode -> LibrarySelectionHeader(
                    selectedCount = state.selectedIds.size,
                    allSelected = state.selectedIds.isNotEmpty() && state.selectedIds.size == state.selectableCount,
                    onExitSelection = onExitSelection,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                )

                state.searchActive -> LibrarySearchBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onSearchToggle,
                    onSubmit = onRememberSearch,
                )

                else -> LibraryTitleRow(
                    onSearch = onSearchToggle,
                    onImport = onImport,
                    onArrange = onArrange,
                    gutter = gutter,
                    showActions = content != ShelfContent.Empty,
                    collapse = headerProgress,
                )
            }
            when {
                content == ShelfContent.Loading -> LibraryPileSkeleton(Modifier.padding(top = pileLayout.topGap), gutter = gutter)
                showPile -> LibraryPile(
                    books = state.pile,
                    chapters = state.chapters,
                    onOpenDetails = onOpenDetails,
                    onResume = onOpenReader,
                    layout = pileLayout,
                    gutter = gutter,
                    modifier = Modifier.yomuGlidePlacement().padding(top = pileLayout.topGap),
                )
            }
            val pileBelow = showPile || content == ShelfContent.Loading
            Spacer(
                Modifier.height(
                    if (pileBelow) {
                        pileLayout.sheetGap
                    } else if (flat) {
                        YomuTheme.space.xs
                    } else {
                        YomuTheme.space.md
                    },
                ),
            )
        }
        Column(
            Modifier
                .padding(start = sheetStart, end = sheetEnd)
                .fillMaxWidth()
                .height(sheetHeight.coerceAtLeast(0.dp))
                .offset { IntOffset(0, (sheetTop.value + sheetLift.floatValue).roundToInt()) }
                .graphicsLayer {
                    // Corners square off over the last stretch before the tabs pin, and are square
                    // whenever the whole screen is sheet.
                    val remaining = sheetTop.value + sheetLift.floatValue - collapsedTop
                    val radius = sheetRadius * (remaining / sheetRadius).coerceIn(0f, 1f) * (1f - flatness.value)
                    shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                    clip = true
                }
                .drawBehind { drawRect(stack.sheet) },
        ) {
            AnimatedContent(
                targetState = content,
                transitionSpec = {
                    // Data resolving behind the splash should settle in place; a directional slide
                    // makes the first library frame feel late.
                    yomuFadeThroughEnter() togetherWith yomuFadeThroughExit()
                },
                label = "libraryContent",
                modifier = Modifier.fillMaxSize(),
            ) { mode ->
                when (mode) {
                    ShelfContent.Loading -> LibraryShelfSkeleton(gutter = gutter)
                    ShelfContent.Empty -> EmptyLibrary(
                        onImport = onImport,
                        onHaveSyncWords = onHaveSyncWords,
                        gutter = gutter,
                    )
                    ShelfContent.Search -> search()
                    ShelfContent.Shelf -> Column(Modifier.fillMaxSize()) { shelf(null) }
                }
            }
        }
        if (!flat) {
            StackCompactHeader(
                title = "Library",
                progress = headerProgress,
                gutter = gutter,
                actions = if (content == ShelfContent.Empty) {
                    null
                } else {
                    { LibraryActions(onSearch = onSearchToggle, onImport = onImport, onArrange = onArrange) }
                },
            )
        }
    }
}

/**
 * A landscape phone: the pile, 120dp wide with where you are underneath, stands on the desk at the
 * start; the shelf is a sheet beside it with its actions at the end of the tab row.
 */
@Composable
private fun SideBySideLibrary(
    state: LibraryUiState,
    gutter: Dp,
    onOpenDetails: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onArrange: () -> Unit,
    shelf: @Composable ColumnScope.(trailing: (@Composable RowScope.() -> Unit)?) -> Unit,
) {
    val stack = YomuTheme.stack
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxHeight()) {
            LibraryPile(
                books = state.pile,
                chapters = state.chapters,
                onOpenDetails = onOpenDetails,
                onResume = onOpenReader,
                layout = PileLayout.Column,
                gutter = 12.dp,
                modifier = Modifier.padding(top = 40.dp),
            )
        }
        Spacer(Modifier.width(gutter - 16.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 28.dp))
                .drawBehind { drawRect(stack.sheet) },
        ) {
            shelf { LibraryActions(onSearch = onSearch, onImport = onImport, onArrange = onArrange) }
        }
    }
}

/** Grid density per view: gaps, insets and the automatic cell size. */
internal data class GridSpec(
    val inset: Dp,
    val top: Dp,
    val rowGap: Dp,
    val columnGap: Dp,
    val autoMinSize: Dp,
) {
    /** How many columns Automatic fits in [width]. */
    fun autoColumns(width: Dp): Int {
        val available = width - inset * 2 + columnGap
        return (available / (autoMinSize + columnGap)).toInt().coerceAtLeast(1)
    }
}

private fun gridSpec(mode: LibraryViewMode, layout: PileLayout, gridInset: Dp): GridSpec {
    // Automatic cell sizes from the boards, so a cover stays about board size whatever width the
    // library is given (a phone 3 across, a portrait tablet 4, a 752dp landscape column 6) and
    // wider columns simply fit more of them.
    val comfortable = if (layout == PileLayout.Tall) 136.dp else 100.dp
    val wide = layout != PileLayout.Phone
    return when (mode) {
        LibraryViewMode.CompactGrid -> GridSpec(
            inset = gridInset - 4.dp,
            top = 16.dp,
            rowGap = if (wide) 18.dp else 16.dp,
            columnGap = 12.dp,
            autoMinSize = comfortable * 0.76f,
        )

        LibraryViewMode.CoverOnlyGrid -> GridSpec(
            inset = gridInset - 4.dp,
            top = 16.dp,
            rowGap = if (wide) 16.dp else 12.dp,
            columnGap = if (wide) 16.dp else 12.dp,
            autoMinSize = comfortable,
        )

        else -> when (layout) {
            PileLayout.Tall -> GridSpec(inset = gridInset, top = 22.dp, rowGap = 24.dp, columnGap = 18.dp, autoMinSize = comfortable)
            PileLayout.Beside -> GridSpec(inset = gridInset, top = 20.dp, rowGap = 22.dp, columnGap = 16.dp, autoMinSize = comfortable)
            else -> GridSpec(inset = gridInset, top = 18.dp, rowGap = 20.dp, columnGap = 14.dp, autoMinSize = comfortable)
        }
    }
}
