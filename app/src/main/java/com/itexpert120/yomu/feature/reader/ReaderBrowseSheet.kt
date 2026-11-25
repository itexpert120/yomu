package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem

/** The tabs of the consolidated reader "Browse" sheet. */
enum class BrowseTab(val label: String) {
    Contents("Contents"),
    Bookmarks("Bookmarks"),
    Highlights("Highlights"),
}

/** Space the Browse sheet needs around its list: header, tabs and bottom inset. */
private val BrowseSheetReservedHeight = 216.dp

/**
 * One sheet for the three ways around a book — Contents, Bookmarks and Highlights — behind Stack's
 * word tabs. Search has its own sheet so results can use a taller surface. Lists run full bleed
 * with ruled rows; switching tabs uses the shared directional swap. [tab] null = closed.
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
    bookTitle: String? = null,
    currentPercent: Int? = null,
) {
    YomuBottomSheet(
        visible = tab != null,
        onDismiss = onDismiss,
        scrollable = false,
        horizontalContentPadding = 0.dp,
        minHeight = if (tab == BrowseTab.Bookmarks || tab == BrowseTab.Highlights) 360.dp else 0.dp,
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
            bookTitle = bookTitle,
            currentPercent = currentPercent,
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
    bookTitle: String? = null,
    currentPercent: Int? = null,
) {
    run {
        Column(modifier = Modifier.fillMaxWidth()) {
            YomuSheetHeader(
                title = "Browse",
                subtitle = bookTitle?.takeIf { it.isNotBlank() },
                onClose = dismiss,
                modifier = Modifier.padding(horizontal = YomuTheme.space.lg),
            )
            ReaderSheetTabs(
                tabs = BrowseTab.entries,
                selected = current,
                label = { it.label },
                onSelect = onSelectTab,
            )
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    yomuContentSwap(forward = targetState.ordinal > initialState.ordinal)
                },
                label = "browseTab",
            ) { t ->
                Box {
                    when (t) {
                        BrowseTab.Contents ->
                            ContentsBody(toc, tocLoading, currentHref, currentPercent, onJumpToLocator)

                        BrowseTab.Bookmarks ->
                            BookmarksBody(bookmarks, onJumpToBookmark, onDeleteBookmark)

                        BrowseTab.Highlights ->
                            HighlightsBody(highlights, onJumpToHighlight, onDeleteHighlight)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsBody(
    toc: List<ReaderTocItem>,
    loading: Boolean,
    currentHref: String?,
    currentPercent: Int?,
    onJump: (String) -> Unit,
) {
    val entries = remember(toc) { toc.readerChapters() }
    val listState = rememberLazyListState()
    // Land on the chapter currently being read, with the one before it still in view.
    LaunchedEffect(entries, currentHref) {
        val index = entries.indexOfFirst { it.id == currentHref }
        if (index >= 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    when {
        loading -> ReaderLoadingBlock("Building contents…")

        entries.isEmpty() -> YomuEmptyState(
            icon = StackIcons.Toc,
            title = "No table of contents",
            body = "This book doesn't list its chapters.",
            compact = true,
        )

        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
        ) {
            // Unkeyed on purpose: chapter hrefs can legitimately repeat.
            itemsIndexed(entries) { index, item ->
                val isCurrent = item.id == currentHref
                TocSheetRow(
                    item = item,
                    current = isCurrent,
                    currentPercent = currentPercent.takeIf { isCurrent },
                    // The row above the current one runs straight into its inset, without a rule.
                    divider = index != entries.lastIndex && entries.getOrNull(index + 1)?.id != currentHref,
                    onClick = { item.locatorJson?.let(onJump) },
                )
            }
        }
    }
}

@Composable
private fun BookmarksBody(
    bookmarks: List<ReaderBookmark>,
    onJump: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        YomuEmptyState(
            icon = StackIcons.Bookmark,
            title = "No bookmarks yet",
            body = "Tap the bookmark at the top of a page to keep your place.",
            compact = true,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
        ) {
            itemsIndexed(bookmarks, key = { _, item -> item.id }) { index, item ->
                BookmarkRow(
                    bookmark = item,
                    divider = index != bookmarks.lastIndex,
                    onClick = { onJump(item.locatorJson) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}

@Composable
private fun HighlightsBody(
    highlights: List<ReaderHighlight>,
    onJump: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (highlights.isEmpty()) {
        YomuEmptyState(
            icon = StackIcons.BorderColor,
            title = "No highlights yet",
            body = "Select text and tap Highlight.",
            compact = true,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
        ) {
            itemsIndexed(highlights, key = { _, item -> item.id }) { index, item ->
                HighlightRow(
                    highlight = item,
                    divider = index != highlights.lastIndex,
                    onClick = { onJump(item.locatorJson) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}

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

/** A table-of-contents entry: indented by depth, on a raised inset when it is the chapter being read. */
@Composable
internal fun TocSheetRow(
    item: ReaderTocItem,
    current: Boolean,
    onClick: () -> Unit,
    currentPercent: Int? = null,
    divider: Boolean = true,
) {
    val stack = YomuTheme.stack
    ReaderBrowseRow(
        title = AnnotatedString(item.title),
        onClick = onClick,
        current = current,
        divider = divider,
        indent = YomuTheme.space.sm * item.depth.coerceIn(0, ReaderTocMaxIndentLevels),
        trailing = currentPercent?.let { percent ->
            {
                Text(text = "$percent%", color = stack.muted, style = ReaderFigures)
            }
        },
    )
}
