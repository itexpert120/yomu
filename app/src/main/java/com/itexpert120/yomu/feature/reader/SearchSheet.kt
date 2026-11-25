package com.itexpert120.yomu.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.web.openWebSearchSheet

/** Space the Search sheet needs around its results: field row, count, insets. */
private val SearchSheetReservedHeight = 240.dp

/**
 * Search in the book: a chevron to close beside Stack's search field, then the live state —
 * searching (2dp line), an error, no matches (with a way out to the web) or the ruled results,
 * each showing its chapter over the passage with the match marked as a highlight.
 */
@Composable
internal fun ReaderSearchSheet(
    visible: Boolean,
    searchQuery: String,
    searchResults: List<ReaderSearchResult>,
    searchInProgress: Boolean,
    searchError: String?,
    searchPerformed: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onJumpToSearchResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        scrollable = false,
        minHeight = 360.dp,
        horizontalContentPadding = 0.dp,
    ) { dismiss ->
        ReaderSearchSheetContent(
            searchQuery = searchQuery,
            searchResults = searchResults,
            searchInProgress = searchInProgress,
            searchError = searchError,
            searchPerformed = searchPerformed,
            onSearchQueryChange = onSearchQueryChange,
            onSubmitSearch = onSubmitSearch,
            onJumpToSearchResult = onJumpToSearchResult,
            dismiss = dismiss,
        )
    }
}

/** The search sheet's body, separate from its sheet so it can be previewed. */
@Composable
internal fun ReaderSearchSheetContent(
    searchQuery: String,
    searchResults: List<ReaderSearchResult>,
    searchInProgress: Boolean,
    searchError: String?,
    searchPerformed: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onJumpToSearchResult: (String) -> Unit,
    dismiss: () -> Unit,
) {
    run {
        val stack = YomuTheme.stack
        val activity = LocalActivity.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
        ) {
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            // Opening search means the user wants to type: focus the field (unless results are
            // already showing, e.g. when returning to a previous search).
            LaunchedEffect(Unit) { if (searchQuery.isEmpty()) focusRequester.requestFocus() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = YomuTheme.space.xs, end = YomuTheme.space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                IconButton(
                    onClick = dismiss,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = stack.ink),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(StackIcons.KeyboardArrowDown, contentDescription = "Close search")
                }
                ReaderSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = {
                        onSubmitSearch()
                        // Drop the keyboard so the results list has the room.
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }
            Column(
                modifier = Modifier
                    .padding(top = YomuTheme.space.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when {
                    searchInProgress -> ReaderLoadingBlock("Searching the whole book…")

                    searchError != null -> YomuNotice(
                        text = searchError,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.lg),
                        icon = StackIcons.Error,
                        tone = YomuNoticeTone.Error,
                    )

                    searchPerformed && searchResults.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = YomuTheme.space.xl, vertical = YomuTheme.space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(StackIcons.SearchOff, contentDescription = null, tint = stack.faint, modifier = Modifier.size(48.dp))
                        Text(
                            text = "No matches for “${searchQuery.trim()}”",
                            color = stack.ink,
                            style = StackType.Heading.copy(fontSize = 20.sp, lineHeight = 26.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = YomuTheme.space.md).semantics { heading() },
                        )
                        Text(
                            text = "Check the spelling, or try a shorter word or phrase.",
                            color = stack.muted,
                            style = StackType.Body,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = YomuTheme.space.xs),
                        )
                        if (activity != null && searchQuery.isNotBlank()) {
                            ReaderPillButton(
                                text = "Search the web",
                                onClick = { activity.openWebSearchSheet(searchQuery.trim()) },
                                style = ReaderPillStyle.Secondary,
                                icon = StackIcons.TravelExplore,
                                modifier = Modifier.padding(top = 22.dp),
                            )
                        }
                    }

                    searchResults.isNotEmpty() -> {
                        ReaderSectionLabel(
                            text = if (searchResults.size >= 150) {
                                "First ${searchResults.size} matches"
                            } else {
                                "${searchResults.size} ${if (searchResults.size == 1) "match" else "matches"}"
                            },
                            modifier = Modifier.padding(start = YomuTheme.space.lg, end = YomuTheme.space.lg, top = 4.dp, bottom = 4.dp),
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = readerSheetListMaxHeight(SearchSheetReservedHeight)),
                        ) {
                            itemsIndexed(searchResults) { index, result ->
                                SearchResultRow(
                                    result = result,
                                    divider = index != searchResults.lastIndex,
                                    onClick = { onJumpToSearchResult(result.locatorJson) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Stack's search field: raised, 52 tall, radius 16, a search glyph and a clear button inside. */
@Composable
private fun ReaderSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(stack.raised)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(StackIcons.Search, contentDescription = null, tint = stack.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(text = "Words or phrases", color = stack.faint, style = StackType.Body.copy(fontSize = 16.sp))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = StackType.Body.copy(fontSize = 16.sp, color = stack.ink),
                cursorBrush = SolidColor(stack.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search in book" },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                colors = IconButtonDefaults.iconButtonColors(contentColor = stack.muted),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(StackIcons.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A single in-book match: its chapter over the passage, the match marked like a highlight. */
@Composable
internal fun SearchResultRow(
    result: ReaderSearchResult,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    val mark = YomuTheme.reading.highlightYellow.copy(alpha = 0.55f)
    val ink = YomuTheme.stack.ink
    val context = remember(result, mark, ink) {
        buildAnnotatedString {
            // Trim long context so the match stays visible within two lines.
            val before = result.before.takeLast(48)
            if (before.length < result.before.length) append("…")
            append(before)
            withStyle(SpanStyle(background = mark, color = ink, fontWeight = FontWeight.Medium)) {
                append(result.match)
            }
            val after = result.after.take(64)
            append(after)
            if (after.length < result.after.length) append("…")
        }
    }
    ReaderBrowseRow(
        title = context,
        caption = result.chapterTitle?.takeIf { it.isNotBlank() },
        onClick = onClick,
        divider = divider,
        titleMaxLines = 3,
        titleStyle = StackType.Body.copy(fontFamily = rememberReadingSerif(), lineHeight = 22.sp, color = YomuTheme.stack.body),
    )
}
