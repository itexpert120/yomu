package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.data.collections.LibraryTabKeys

/**
 * The body of library search: recent searches while the field is empty, then live results as rows
 * with a count (and, when a shelf other than All is open, a chip naming it that widens the search).
 */
@Composable
internal fun LibrarySearchResults(
    state: LibraryUiState,
    listState: LazyListState,
    gutter: Dp,
    bottomPadding: Dp,
    onQueryChange: (String) -> Unit,
    onForgetSearch: (String) -> Unit,
    onOpen: (LibraryBook) -> Unit,
    onLongPress: (LibraryBook) -> Unit,
    onShowAll: () -> Unit,
) {
    val query = state.searchQuery.trim()
    val shelf = state.tabs.firstOrNull { it.key == state.selectedTab }
        ?.takeIf { it.key != LibraryTabKeys.ALL }
    if (query.isEmpty()) {
        RecentSearches(
            recent = state.recentSearches,
            gutter = gutter,
            onPick = onQueryChange,
            onForget = onForgetSearch,
        )
        return
    }
    val books = state.books
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = gutter, end = gutter, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shelf != null) ShelfFilterChip(label = shelf.label, onRemove = onShowAll)
            Text(
                text = if (books.size == 1) "1 result" else "${books.size} results",
                style = MaterialTheme.typography.labelLarge,
                color = YomuTheme.stack.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (books.isEmpty()) {
            NoSearchResults(
                query = query,
                tabLabel = shelf?.label,
                onShowAll = shelf?.let { onShowAll },
                onClear = { onQueryChange("") },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomPadding),
            ) {
                items(books, key = { it.id }) { book ->
                    BookListRow(
                        book = book,
                        onClick = { onOpen(book) },
                        onLongPress = { onLongPress(book) },
                        title = book.title.emphasizing(query),
                        titleWeight = FontWeight.Medium,
                        caption = listOf(book.author, book.shelf.label).filter { it.isNotBlank() }.joinToString(" · "),
                        showProgress = false,
                        divider = book !== books.last(),
                    )
                }
            }
        }
    }
}

/** Bolds every case-insensitive occurrence of [query]. */
private fun String.emphasizing(query: String): AnnotatedString = buildAnnotatedString {
    append(this@emphasizing)
    if (query.isEmpty()) return@buildAnnotatedString
    var from = 0
    while (true) {
        val at = this@emphasizing.indexOf(query, startIndex = from, ignoreCase = true)
        if (at < 0) break
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + query.length)
        from = at + query.length
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    gutter: Dp,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    val stack = YomuTheme.stack
    Column(Modifier.fillMaxSize()) {
        Text(
            text = if (recent.isEmpty()) "Search your books by title or author." else "Recent",
            style = if (recent.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
            color = stack.muted,
            modifier = Modifier.padding(start = gutter, end = gutter, top = 24.dp, bottom = 4.dp),
        )
        recent.forEach { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .yomuPressable(onClick = { onPick(term) }, role = Role.Button)
                    .padding(start = gutter, end = gutter - 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(StackIcons.History, contentDescription = null, tint = stack.faint)
                Text(
                    text = term,
                    style = MaterialTheme.typography.bodyLarge,
                    color = stack.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onForget(term) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        StackIcons.Close,
                        contentDescription = "Remove $term from recent",
                        tint = stack.faint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** "In To read ×": the open shelf narrowing the search; removing it searches every book. */
@Composable
private fun ShelfFilterChip(label: String, onRemove: () -> Unit) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .yomuPressable(onClick = onRemove, role = Role.Button, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { drawRect(stack.raised) }
            .semantics(mergeDescendants = true) { contentDescription = "Remove filter: $label" }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("In $label", style = MaterialTheme.typography.labelLarge, color = stack.ink)
        Icon(StackIcons.Close, contentDescription = null, tint = stack.ink, modifier = Modifier.size(18.dp))
    }
}

/** No results: names the query (and the shelf it was limited to) and offers a way out. */
@Composable
internal fun NoSearchResults(
    query: String,
    tabLabel: String?,
    onShowAll: (() -> Unit)?,
    onClear: () -> Unit,
) {
    YomuEmptyState(
        icon = StackIcons.SearchOff,
        title = if (tabLabel != null) "Nothing called “$query” in $tabLabel" else "Nothing called “$query”",
        body = if (onShowAll != null) {
            "Check the spelling, or look across your whole library."
        } else {
            "Check the spelling, or try the author’s name."
        },
        modifier = Modifier.padding(top = 64.dp),
        actions = {
            if (onShowAll != null) {
                YomuButton(text = "Search all books", onClick = onShowAll)
                YomuButton(text = "Clear", onClick = onClear, emphasis = YomuButtonEmphasis.Secondary)
            } else {
                YomuButton(text = "Clear search", onClick = onClear, emphasis = YomuButtonEmphasis.Secondary)
            }
        },
    )
}
