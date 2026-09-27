package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
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

/**
 * One sheet consolidating the three navigation surfaces — Contents, Bookmarks and Highlights —
 * behind a tab control. Search has its own dedicated sheet so results can use a taller surface.
 * Each tab reuses the existing row composables; switching tabs uses the unified directional motion
 * + blur. [tab] null = closed.
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
) {
    YomuBottomSheet(
        visible = tab != null,
        onDismiss = onDismiss,
        scrollable = false,
        horizontalContentPadding = 0.dp,
        minHeight = if (tab == BrowseTab.Bookmarks || tab == BrowseTab.Highlights) {
            360.dp
        } else {
            0.dp
        },
    ) { _ ->
        val current = tab ?: BrowseTab.Contents
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tabs = BrowseTab.entries
            YomuSegmentedControl(
                options = tabs.map { it.label },
                selectedIndex = tabs.indexOf(current),
                onSelected = { onSelectTab(tabs[it]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
        loading -> BrowseLoading()
        entries.isEmpty() -> BrowseEmpty("No table of contents.")
        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries) { item ->
                TocSheetRow(
                    item = item,
                    current = item.id == currentHref,
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
        BrowseEmpty("No bookmarks yet. Tap the bookmark icon to save your place.")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(bookmarks, key = { it.id }) { item ->
                BookmarkRow(
                    bookmark = item,
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
        BrowseEmpty("No highlights yet. Select text and tap Highlight.")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(highlights, key = { it.id }) { item ->
                HighlightRow(
                    highlight = item,
                    onClick = { onJump(item.locatorJson) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}

@Composable
private fun BrowseLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text("Building contents…", color = YomuTheme.colors.textMuted, style = YomuTheme.type.body)
    }
}

@Composable
private fun BrowseEmpty(text: String) {
    Text(
        text = text,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.caption,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
