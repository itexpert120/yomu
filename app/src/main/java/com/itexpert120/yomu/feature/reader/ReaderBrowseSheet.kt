package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.LocalYomuSheetBottomInset
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuIconButton
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeRule
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderPageTarget
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.ChapterReadState
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction
import com.itexpert120.yomu.feature.annotations.AnnotationTransferMenuItems
import com.itexpert120.yomu.feature.contents.CurrentChapterPill
import com.itexpert120.yomu.feature.contents.revealItem
import com.itexpert120.yomu.feature.contents.showsItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/** The tabs of the reader's Browse sheet. */
enum class BrowseTab(val label: String) {
    Contents("Contents"),
    Bookmarks("Bookmarks"),
    Highlights("Highlights"),
}

/**
 * The ways around a book, as one tall sheet whose tabs are its header (close × at their end) and
 * whose pages swipe sideways:
 * - Contents: volumes that fold open (the one being read starts open), each chapter's read state,
 *   the chapter being read as a "you are here" row with its progress, a pill back to it once it has
 *   scrolled away, and a filter for long books.
 * - Bookmarks and Highlights: grouped under their chapters in book order, each with its passage.
 * [tab] null = closed. Search has its own sheet.
 */
@Composable
internal fun ReaderBrowseSheet(
    tab: BrowseTab?,
    toc: List<ReaderTocItem>,
    tocLoading: Boolean,
    currentHref: String?,
    onJumpToLocator: (String) -> Unit,
    bookmarks: List<ReaderBookmark>,
    onJumpToBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    highlights: List<ReaderHighlight>,
    onJumpToHighlight: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onSelectTab: (BrowseTab) -> Unit,
    onDismiss: () -> Unit,
    chapterReads: ChapterReadState = ChapterReadState.Empty,
    chapterProgress: Double? = null,
    currentPercent: Int? = null,
    // The book's printed pages, for Go to page at the top of Contents (empty: none).
    pages: List<ReaderPageTarget> = emptyList(),
    onGoToPage: (String) -> Unit = {},
    calibreAvailable: Boolean = false,
    onAnnotationAction: (AnnotationTransferAction) -> Unit = {},
) {
    YomuBottomSheet(
        visible = tab != null,
        onDismiss = onDismiss,
        scrollable = false,
        horizontalContentPadding = 0.dp,
        showScrollEdgeShadow = false,
    ) { dismiss ->
        ReaderBrowseSheetContent(
            current = tab ?: BrowseTab.Contents,
            toc = toc,
            tocLoading = tocLoading,
            currentHref = currentHref,
            onJumpToLocator = onJumpToLocator,
            bookmarks = bookmarks,
            onJumpToBookmark = onJumpToBookmark,
            onDeleteBookmark = onDeleteBookmark,
            highlights = highlights,
            onJumpToHighlight = onJumpToHighlight,
            onDeleteHighlight = onDeleteHighlight,
            onSelectTab = onSelectTab,
            dismiss = dismiss,
            chapterReads = chapterReads,
            chapterProgress = chapterProgress,
            currentPercent = currentPercent,
            pages = pages,
            onGoToPage = onGoToPage,
            calibreAvailable = calibreAvailable,
            onAnnotationAction = onAnnotationAction,
        )
    }
}

/** The Browse sheet's body, separate from its sheet so it can be previewed. */
@Composable
internal fun ReaderBrowseSheetContent(
    current: BrowseTab,
    toc: List<ReaderTocItem>,
    tocLoading: Boolean,
    currentHref: String?,
    onJumpToLocator: (String) -> Unit,
    bookmarks: List<ReaderBookmark>,
    onJumpToBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    highlights: List<ReaderHighlight>,
    onJumpToHighlight: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onSelectTab: (BrowseTab) -> Unit,
    dismiss: () -> Unit,
    chapterReads: ChapterReadState = ChapterReadState.Empty,
    chapterProgress: Double? = null,
    currentPercent: Int? = null,
    pages: List<ReaderPageTarget> = emptyList(),
    onGoToPage: (String) -> Unit = {},
    calibreAvailable: Boolean = false,
    onAnnotationAction: (AnnotationTransferAction) -> Unit = {},
) {
    val tabs = BrowseTab.entries
    val pager = rememberPagerState(initialPage = current.ordinal) { tabs.size }
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    // The reader's state names the tab (the menu's Marks opens on Bookmarks); the pager follows it,
    // and reports where a swipe or tap lands so the two never disagree.
    LaunchedEffect(current) {
        // Only follow a change made elsewhere (the menu's Marks); never jump a page the finger holds.
        if (!pager.isScrollInProgress && pager.targetPage != current.ordinal) pager.scrollToPage(current.ordinal)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).distinctUntilChanged().collect { currentOnSelectTab(tabs[it]) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .readerSheetHeight(fraction = 0.82f, fill = true),
    ) {
        ReaderPagerTabs(tabs = tabs, label = { it.label }, pager = pager, onClose = dismiss)
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            key = { tabs[it] },
            // Keep every tab composed: a page rebuilt mid-swipe (outline of thousands of chapters, its
            // list re-placing at the current chapter) made the incoming tab jitter as it slid in.
            beyondViewportPageCount = tabs.size - 1,
        ) { page ->
            when (tabs[page]) {
                BrowseTab.Contents -> ContentsPage(
                    toc = toc,
                    loading = tocLoading,
                    currentHref = currentHref,
                    reads = chapterReads,
                    chapterProgress = chapterProgress,
                    currentPercent = currentPercent,
                    onJump = onJumpToLocator,
                    pages = pages,
                    onGoToPage = onGoToPage,
                )

                BrowseTab.Bookmarks -> BookmarksPage(bookmarks, toc, onJumpToBookmark, onDeleteBookmark)

                BrowseTab.Highlights -> HighlightsPage(highlights, toc, onJumpToHighlight, onDeleteHighlight, calibreAvailable, onAnnotationAction)
            }
        }
    }
}

// region Contents

@Composable
private fun ContentsPage(
    toc: List<ReaderTocItem>,
    loading: Boolean,
    currentHref: String?,
    reads: ChapterReadState,
    chapterProgress: Double?,
    currentPercent: Int?,
    onJump: (String) -> Unit,
    pages: List<ReaderPageTarget> = emptyList(),
    onGoToPage: (String) -> Unit = {},
) {
    val outline = remember(toc, currentHref) { contentsOutline(toc, currentHref) }
    when {
        loading -> {
            ReaderLoadingBlock("Building contents…", Modifier.padding(top = YomuTheme.space.xs))
            return
        }

        outline.chapterCount == 0 -> {
            YomuEmptyState(
                icon = StackIcons.Toc,
                title = "No table of contents",
                body = "This book doesn't list its chapters.",
                compact = true,
                modifier = Modifier.padding(top = YomuTheme.space.lg),
            )
            return
        }
    }
    var expanded by remember(outline) { mutableStateOf(setOfNotNull(outline.currentSection.takeIf { it >= 0 })) }
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(outline, expanded, reads, query) { contentsRows(outline, expanded, reads, query) }
    val currentRow = rows.indexOfFirst { it is ContentsRow.Chapter && it.current }
    // Open on the chapter being read, the one before it still in view (no scroll on the first frame);
    // with volumes, one more row sits under the pinned volume heading.
    val lead = if (outline.grouped) 2 else 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentRow - lead).coerceAtLeast(0))
    var revealCurrent by remember { mutableStateOf(false) }
    val revealSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(revealCurrent, rows) {
        if (!revealCurrent) return@LaunchedEffect
        val index = rows.indexOfFirst { it is ContentsRow.Chapter && it.current }
        if (index >= 0) listState.revealItem(index, lead, revealSpec)
        revealCurrent = false
    }
    Column(Modifier.fillMaxSize()) {
        if (pages.isNotEmpty()) {
            ReaderGoToPageField(
                pages = pages,
                onGoToPage = onGoToPage,
                modifier = Modifier.padding(start = YomuTheme.space.lg, end = YomuTheme.space.lg, top = YomuTheme.space.sm, bottom = YomuTheme.space.xxs),
            )
        }
        if (outline.chapterCount >= ContentsFilterThreshold) {
            ReaderFilterField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Find a chapter",
                modifier = Modifier.padding(start = YomuTheme.space.lg, end = YomuTheme.space.lg, top = YomuTheme.space.sm, bottom = YomuTheme.space.xxs),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Text(
                    text = "No chapter titles contain “${query.trim()}”",
                    color = YomuTheme.stack.muted,
                    style = StackType.Body,
                    modifier = Modifier.padding(horizontal = YomuTheme.space.lg, vertical = YomuTheme.space.lg),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .yomuScrollEdgeRule(top = listState.canScrollBackward),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp + LocalYomuSheetBottomInset.current),
                ) {
                    contentsItems(
                        rows = rows,
                        chapterProgress = chapterProgress,
                        currentPercent = currentPercent,
                        onToggle = { section -> expanded = if (section in expanded) expanded - section else expanded + section },
                        onJump = onJump,
                    )
                }
            }
            // Back to the chapter being read once it has scrolled away (or is folded or filtered out).
            val away by remember(rows, currentRow) {
                derivedStateOf { outline.current >= 0 && !listState.showsItem(currentRow) }
            }
            // Where it is (or its folded volume), to point the pill's arrow.
            val target = if (currentRow >= 0) {
                currentRow
            } else {
                rows.indexOfFirst { it is ContentsRow.Volume && it.section == outline.currentSection }
            }
            val below by remember(target) { derivedStateOf { target < 0 || target > listState.firstVisibleItemIndex } }
            CurrentChapterPill(
                visible = away,
                below = below,
                onClick = {
                    query = ""
                    if (outline.currentSection >= 0) expanded = expanded + outline.currentSection
                    revealCurrent = true
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = YomuTheme.space.md + LocalYomuSheetBottomInset.current),
            )
        }
    }
}

private fun LazyListScope.contentsItems(
    rows: List<ContentsRow>,
    chapterProgress: Double?,
    currentPercent: Int?,
    onToggle: (Int) -> Unit,
    onJump: (String) -> Unit,
) {
    // Volumes pin while their chapters scroll under them; runs of chapters go in as one interval.
    var run = ArrayList<ContentsRow.Chapter>()
    fun flush() {
        if (run.isEmpty()) return
        val chapters = run
        items(chapters, key = { it.key }, contentType = { if (it.current) "current" else "chapter" }) { row ->
            ContentsChapterRow(
                row = row,
                chapterProgress = chapterProgress.takeIf { row.current },
                bookPercent = currentPercent.takeIf { row.current },
                onClick = { row.locatorJson?.let(onJump) },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                ),
            )
        }
        run = ArrayList()
    }
    rows.forEach { row ->
        when (row) {
            is ContentsRow.Chapter -> run += row
            is ContentsRow.Volume -> {
                flush()
                stickyHeader(key = row.key, contentType = "volume") {
                    ContentsVolumeRow(row, onClick = { onToggle(row.section) })
                }
            }
        }
    }
    flush()
}

/** A volume: its title, how much of it is read, and a chevron that turns as it opens. */
@Composable
private fun ContentsVolumeRow(row: ContentsRow.Volume, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    val turn by animateFloatAsState(
        targetValue = if (row.expanded) 180f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "volumeChevron",
    )
    val caption = when {
        row.chapters == 0 -> null
        row.read >= row.chapters -> "All ${row.chapters} read"
        row.read > 0 -> "${row.read} of ${row.chapters} read"
        else -> if (row.chapters == 1) "1 chapter" else "${row.chapters} chapters"
    }
    Column(Modifier.fillMaxWidth().background(stack.sheet)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .yomuPressable(onClick = onClick, pressedScale = 1f)
                .semantics {
                    heading()
                    stateDescription = if (row.expanded) "Expanded" else "Collapsed"
                }
                .padding(start = YomuTheme.space.lg, end = YomuTheme.space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.title,
                    color = stack.ink,
                    style = StackType.Heading,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = listOfNotNull(if (row.holdsCurrent) "Reading" else null, caption).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(text = line, color = stack.muted, style = StackType.Caption, maxLines = 1)
                }
            }
            Icon(
                imageVector = StackIcons.ExpandMore,
                contentDescription = null,
                tint = stack.muted,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn },
            )
        }
        ReaderRule()
    }
}

/**
 * A chapter. The one being read sits on a raised inset with how far through it you are; read
 * chapters fade back with a check; started ones show how far they got.
 */
@Composable
private fun ContentsChapterRow(
    row: ContentsRow.Chapter,
    chapterProgress: Double?,
    bookPercent: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val title = if (row.volumeStart) "Title page" else row.title
    val indent: Dp = 14.dp * row.indent
    if (row.current) {
        val progress = (chapterProgress ?: row.progress.toDouble()).toFloat().coerceIn(0f, 1f)
        val shape = RoundedCornerShape(16.dp)
        Box(modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.sm, vertical = 6.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(stack.raised)
                    .yomuPressable(onClick = onClick, pressedScale = 1f, shape = shape)
                    .semantics { contentDescription = "$title. You are here, ${(progress * 100).roundToInt()}% through the chapter" }
                    .padding(start = YomuTheme.space.md + indent, end = YomuTheme.space.md, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val caption = buildList {
                    add("You are here")
                    row.volume?.let(::add)
                    add("${(progress * 100).roundToInt()}% of chapter")
                    bookPercent?.let { add("$it% of book") }
                }.joinToString(" · ")
                Text(text = caption, color = stack.muted, style = ReaderFigures, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = title,
                    color = stack.ink,
                    style = StackType.Body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ReaderProgressLine(progress, Modifier.padding(top = 6.dp))
            }
        }
        return
    }
    val started = !row.read && row.progress in 0.01f..0.995f
    val trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = when {
        row.read -> {
            { Icon(StackIcons.Check, contentDescription = null, tint = stack.faint, modifier = Modifier.size(18.dp)) }
        }
        started -> {
            { Text("${(row.progress * 100).roundToInt()}%", color = stack.muted, style = ReaderFigures) }
        }
        else -> null
    }
    ReaderBrowseRow(
        title = AnnotatedString(title),
        caption = row.volume,
        onClick = onClick,
        indent = indent,
        titleStyle = if (row.read) StackType.Body.copy(color = stack.muted) else StackType.Body,
        trailing = trailing,
        modifier = modifier.semantics {
            stateDescription = when {
                row.read -> "Read"
                started -> "${(row.progress * 100).roundToInt()}% read"
                else -> "Unread"
            }
        },
    )
}

/** A thin 3dp line: how far through the current chapter. Drawn, so it costs no layout. */
@Composable
private fun ReaderProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(stack.track, cornerRadius = r)
                drawRoundRect(stack.ink, size = Size(size.width * progress, size.height), cornerRadius = r)
            },
    )
}

// endregion

// region Bookmarks and highlights

@Composable
private fun BookmarksPage(
    bookmarks: List<ReaderBookmark>,
    toc: List<ReaderTocItem>,
    onJump: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        YomuEmptyState(
            icon = StackIcons.Bookmarks,
            title = "No bookmarks yet",
            body = "Tap the bookmark at the top of a page to keep your place.",
            compact = true,
            modifier = Modifier.padding(top = YomuTheme.space.lg),
        )
        return
    }
    val groups = remember(bookmarks, toc) { groupBookmarks(bookmarks, toc) }
    GroupedList(groups) { item, divider ->
        BookmarkRow(
            bookmark = item,
            divider = divider,
            onClick = { onJump(item.locatorJson) },
            onDelete = { onDelete(item.id) },
        )
    }
}

@Composable
private fun HighlightsPage(
    highlights: List<ReaderHighlight>,
    toc: List<ReaderTocItem>,
    onJump: (String) -> Unit,
    onDelete: (String) -> Unit,
    calibreAvailable: Boolean,
    onAnnotationAction: (AnnotationTransferAction) -> Unit,
) {
    val summary = remember(highlights) { highlightsSummary(highlights) }
    val actions: @Composable () -> Unit = { HighlightsActionsRow(summary, calibreAvailable, onAnnotationAction) }
    if (highlights.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            actions()
            YomuEmptyState(
                icon = StackIcons.BorderColor,
                title = "No highlights yet",
                body = "Select text and tap Highlight.",
                compact = true,
                modifier = Modifier.padding(top = YomuTheme.space.sm),
            )
        }
        return
    }
    val groups = remember(highlights, toc) { groupHighlights(highlights, toc) }
    GroupedList(groups, header = actions) { item, divider ->
        HighlightRow(
            highlight = item,
            divider = divider,
            onClick = { onJump(item.locatorJson) },
            onDelete = { onDelete(item.id) },
        )
    }
}

/** Annotations under pinned chapter headings. */
@Composable
private fun <T> GroupedList(
    groups: List<AnnotationGroup<T>>,
    header: (@Composable () -> Unit)? = null,
    row: @Composable (item: T, divider: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .yomuScrollEdgeRule(top = listState.canScrollBackward),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = YomuTheme.space.lg + LocalYomuSheetBottomInset.current),
    ) {
        if (header != null) item(key = "header", contentType = "header") { header() }
        groups.forEachIndexed { groupIndex, group ->
            stickyHeader(key = "g$groupIndex", contentType = "chapter") {
                AnnotationChapterHeader(group.title ?: "Elsewhere in the book", group.items.size)
            }
            items(group.items.size, contentType = { "annotation" }) { index ->
                row(group.items[index], index != group.items.lastIndex)
            }
        }
    }
}

@Composable
private fun AnnotationChapterHeader(title: String, count: Int) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(stack.sheet)
            .padding(start = YomuTheme.space.lg, end = YomuTheme.space.lg, top = YomuTheme.space.sm, bottom = 6.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
    ) {
        Text(
            text = title,
            color = stack.ink,
            style = StackType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 1) Text(text = "$count", color = stack.faint, style = ReaderFigures)
    }
}

/**
 * The Highlights tab's quiet header: how many there are (and how many carry notes), and a ⋯ menu
 * to export them or import more.
 */
@Composable
private fun HighlightsActionsRow(
    summary: String?,
    calibreAvailable: Boolean,
    onAnnotationAction: (AnnotationTransferAction) -> Unit,
) {
    val stack = YomuTheme.stack
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = YomuTheme.space.lg, end = YomuTheme.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary.orEmpty(),
            color = stack.muted,
            style = StackType.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            YomuIconButton(
                icon = StackIcons.MoreHoriz,
                contentDescription = "Highlight options",
                tint = stack.muted,
                onClick = {
                    menuOpen = true
                    onAnnotationAction(AnnotationTransferAction.MenuOpened)
                },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = stack.sheet,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.width(264.dp),
            ) {
                AnnotationTransferMenuItems(
                    calibreAvailable = calibreAvailable,
                    onAction = onAnnotationAction,
                    closeMenu = { menuOpen = false },
                )
            }
        }
    }
}

/** "12 highlights · 3 notes"; null when there are none. */
internal fun highlightsSummary(highlights: List<ReaderHighlight>): String? {
    if (highlights.isEmpty()) return null
    val notes = highlights.count { !it.note.isNullOrBlank() }
    val count = if (highlights.size == 1) "1 highlight" else "${highlights.size} highlights"
    return when (notes) {
        0 -> count
        1 -> "$count · 1 note"
        else -> "$count · $notes notes"
    }
}

// endregion

/** A short wait inside a sheet: Stack's 2dp line over a caption. */
@Composable
internal fun ReaderLoadingBlock(
    message: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = YomuTheme.space.lg,
) {
    val stack = YomuTheme.stack
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = YomuTheme.space.md)) {
        ReaderLoadingLine(color = stack.ink, track = stack.track, modifier = Modifier.fillMaxWidth())
        Text(
            text = message,
            color = stack.muted,
            style = StackType.Label.copy(fontWeight = FontWeight.Normal),
            modifier = Modifier.padding(top = YomuTheme.space.sm),
        )
    }
}
