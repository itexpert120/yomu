package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.reader.ReaderSearchResult

/** Space the Search sheet needs around its results: drag handle, header, field, count, insets. */
private val SearchSheetReservedHeight = 288.dp

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
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSheetHeader(title = "Search in book", onClose = dismiss)
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            // Opening search means the user wants to type: focus the field (unless results are
            // already showing, e.g. when returning to a previous search).
            LaunchedEffect(Unit) { if (searchQuery.isEmpty()) focusRequester.requestFocus() }
            YomuTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = "Search in book",
                placeholder = "Words or phrases",
                imeAction = ImeAction.Search,
                onImeAction = {
                    onSubmitSearch()
                    // Drop the keyboard so the results list has the room.
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            ) {
                when {
                    searchInProgress -> YomuLoadingState(
                        modifier = Modifier.fillMaxWidth().padding(vertical = YomuTheme.space.md),
                        message = "Searching…",
                    )

                    searchError != null -> YomuNotice(
                        text = searchError,
                        modifier = Modifier.fillMaxWidth(),
                        icon = StackIcons.Error,
                        tone = YomuNoticeTone.Error,
                    )

                    searchPerformed && searchResults.isEmpty() -> YomuEmptyState(
                        icon = StackIcons.SearchOff,
                        title = "No results",
                        body = "Try a different word or a shorter phrase.",
                        compact = true,
                    )

                    searchResults.isNotEmpty() -> {
                        Text(
                            text = if (searchResults.size >= 150) {
                                "First ${searchResults.size} matches"
                            } else {
                                "${searchResults.size} ${if (searchResults.size == 1) "match" else "matches"}"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = YomuTheme.space.xxs),
                        )
                        val accent = MaterialTheme.colorScheme.primary
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = readerSheetListMaxHeight(SearchSheetReservedHeight)),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            itemsIndexed(searchResults) { index, result ->
                                SearchResultRow(
                                    result = result,
                                    accent = accent,
                                    position = yomuSettingPosition(index, searchResults.size),
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

/** A single in-book search match: the highlighted context, with its chapter as supporting text. */
@Composable
internal fun SearchResultRow(
    result: ReaderSearchResult,
    accent: Color,
    onClick: () -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.Single,
) {
    val matchWeight = MaterialTheme.typography.bodyLargeEmphasized.fontWeight
    val context = remember(result, accent, matchWeight) {
        buildAnnotatedString {
            // Trim long context so the match stays visible within two lines.
            append(result.before.takeLast(48))
            withStyle(SpanStyle(color = accent, fontWeight = matchWeight)) {
                append(result.match)
            }
            append(result.after.take(64))
        }
    }
    ReaderBrowseRow(
        title = context,
        onClick = onClick,
        position = position,
        supporting = result.chapterTitle?.takeIf { it.isNotBlank() },
    )
}
