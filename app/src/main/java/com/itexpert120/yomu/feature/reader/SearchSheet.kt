package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderSearchResult

/** In-book search: a query field plus a tappable list of matches with their surrounding context. */
@Composable
internal fun ReaderSearchSheet(
    visible: Boolean,
    query: String,
    results: List<ReaderSearchResult>,
    inProgress: Boolean,
    performed: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss, scrollable = false) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            YomuTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "Search in book",
                placeholder = "Search…",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            when {
                inProgress -> Text(
                    text = "Searching…",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )

                performed && results.isEmpty() -> Text(
                    text = "No results",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )

                results.isNotEmpty() -> {
                    Text(
                        text = if (results.size >= MAX_SHOWN) {
                            "First ${results.size} matches"
                        } else {
                            "${results.size} ${if (results.size == 1) "match" else "matches"}"
                        },
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(results) { _, result ->
                            SearchResultRow(
                                result = result,
                                accent = YomuTheme.colors.accent,
                                onClick = { onJump(result.locatorJson) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
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

// Matches the engine's MAX_SEARCH_RESULTS cap, for the "First N matches" label.
private const val MAX_SHOWN = 150
