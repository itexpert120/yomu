package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderSearchResult

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
        wideAsSideSheet = true,
        minHeight = 360.dp,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Search in book",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.title,
            )
            YomuTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = "Search in book",
                placeholder = "Search…",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when {
                    searchInProgress -> SearchMessage("Searching…", showProgress = true)
                    searchError != null -> Text(
                        text = searchError,
                        color = YomuTheme.colors.danger,
                        style = YomuTheme.type.caption,
                    )

                    searchPerformed && searchResults.isEmpty() -> Text(
                        text = "No results",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption,
                    )

                    searchResults.isNotEmpty() -> {
                        Text(
                            text = if (searchResults.size >= 150) {
                                "First ${searchResults.size} matches"
                            } else {
                                "${searchResults.size} ${if (searchResults.size == 1) "match" else "matches"}"
                            },
                            color = YomuTheme.colors.textMuted,
                            style = YomuTheme.type.caption,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(searchResults) { _, result ->
                                SearchResultRow(
                                    result = result,
                                    accent = YomuTheme.colors.accent,
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

@Composable
private fun SearchMessage(text: String, showProgress: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                color = YomuTheme.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
    }
}

/** A single in-book search match with chapter and highlighted context. */
@Composable
internal fun SearchResultRow(
    result: ReaderSearchResult,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        result.chapterTitle?.takeIf { it.isNotBlank() }?.let { chapter ->
            Text(
                text = chapter,
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildAnnotatedString {
                // Trim long context so the match stays visible within two lines.
                append(result.before.takeLast(48))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(result.match)
                }
                append(result.after.take(64))
            },
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
