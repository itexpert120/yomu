package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem

/** The tabs of the consolidated reader "Browse" sheet. */
enum class BrowseTab(val label: String) {
    Contents("Contents"),
    Bookmarks("Bookmarks"),
    Highlights("Highlights"),
}

// The bundled Material icon set has no AutoMirrored Toc symbol, so keep the semantic Toc glyph.
@Suppress("DEPRECATION")
private val BrowseTab.icon: ImageVector
    get() = when (this) {
        BrowseTab.Contents -> Icons.Rounded.Toc
        BrowseTab.Bookmarks -> Icons.Rounded.Bookmark
        BrowseTab.Highlights -> Icons.Rounded.BorderColor
    }

/** Below this width three icon + label tabs would truncate, so the tabs fall back to labels only. */
private val BrowseTabIconsMinWidth = 344.dp

/** Space the Browse sheet needs around its list: drag handle, header, tabs and bottom inset. */
private val BrowseSheetReservedHeight = 216.dp

/**
 * One sheet consolidating the three navigation surfaces — Contents, Bookmarks and Highlights —
 * behind a tab control. Search has its own dedicated sheet so results can use a taller surface.
 * Every tab renders the shared [ReaderBrowseRow]; switching tabs uses the unified directional
 * motion. [tab] null = closed.
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
) {
    YomuBottomSheet(
        visible = tab != null,
        onDismiss = onDismiss,
        scrollable = false,
        horizontalContentPadding = YomuTheme.space.md,
        minHeight = if (tab == BrowseTab.Bookmarks || tab == BrowseTab.Highlights) {
            360.dp
        } else {
            0.dp
        },
    ) { dismiss ->
        val current = tab ?: BrowseTab.Contents
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSheetHeader(
                title = "Browse",
                subtitle = bookTitle?.takeIf { it.isNotBlank() },
                onClose = dismiss,
                modifier = Modifier.padding(horizontal = YomuTheme.space.xxs),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                YomuConnectedChoiceGroup(
                    options = BrowseTab.entries,
                    selected = current,
                    label = { it.label },
                    icon = if (maxWidth >= BrowseTabIconsMinWidth) {
                        { option: BrowseTab -> option.icon }
                    } else {
                        null
                    },
                    onSelect = onSelectTab,
                )
            }
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
                            ContentsBody(toc, tocLoading, currentHref, onJumpToLocator)

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
    onJump: (String) -> Unit,
) {
    val entries = remember(toc) { toc.filter { it.locatorJson != null } }
    val listState = rememberLazyListState()
    // Land on the chapter currently being read.
    LaunchedEffect(entries, currentHref) {
        val index = entries.indexOfFirst { it.id == currentHref }
        if (index >= 0) listState.scrollToItem(index)
    }
    when {
        loading -> YomuLoadingState(
            modifier = Modifier.fillMaxWidth().padding(vertical = YomuTheme.space.lg),
            message = "Building contents…",
        )

        entries.isEmpty() -> YomuEmptyState(
            icon = Icons.Rounded.Toc,
            title = "No table of contents",
            body = "This book doesn't list its chapters.",
            compact = true,
        )

        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            // Unkeyed on purpose: chapter hrefs can legitimately repeat.
            itemsIndexed(entries) { index, item ->
                TocSheetRow(
                    item = item,
                    current = item.id == currentHref,
                    position = yomuSettingPosition(index, entries.size),
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
            icon = Icons.Rounded.BookmarkBorder,
            title = "No bookmarks yet",
            body = "Tap the bookmark icon to save your place.",
            compact = true,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(bookmarks, key = { _, item -> item.id }) { index, item ->
                BookmarkRow(
                    bookmark = item,
                    position = yomuSettingPosition(index, bookmarks.size),
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
            icon = Icons.Rounded.BorderColor,
            title = "No highlights yet",
            body = "Select text and tap Highlight.",
            compact = true,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = readerSheetListMaxHeight(BrowseSheetReservedHeight)),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(highlights, key = { _, item -> item.id }) { index, item ->
                HighlightRow(
                    highlight = item,
                    position = yomuSettingPosition(index, highlights.size),
                    onClick = { onJump(item.locatorJson) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}

/** A table-of-contents entry: indented by depth, emphasized when it is the chapter being read. */
@Composable
internal fun TocSheetRow(
    item: ReaderTocItem,
    current: Boolean,
    onClick: () -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.Single,
) {
    ReaderBrowseRow(
        title = AnnotatedString(item.title),
        onClick = onClick,
        position = position,
        current = current,
        indent = YomuTheme.space.sm * item.depth.coerceIn(0, ReaderTocMaxIndentLevels),
    )
}
