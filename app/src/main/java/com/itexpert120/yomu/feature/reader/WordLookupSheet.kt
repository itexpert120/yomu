package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.data.dictionary.DictionaryResult

/** A single rendered line in the definitions list. */
private sealed interface LookupRow {
    data class Part(val text: String) : LookupRow
    data class Def(val number: Int, val text: String, val example: String?) : LookupRow
}

/** Bottom sheet showing a word's dictionary definition (or loading / not-found / error states). */
@Composable
internal fun WordLookupSheet(state: WordLookupUiState?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss, scrollable = false) { _ ->
        if (state == null) return@YomuBottomSheet
        val found = state.result as? DictionaryResult.Found
        val phonetic =
            found?.entries?.firstNotNullOfOrNull { it.phonetic?.takeIf { p -> p.isNotBlank() } }
        val rows = remember(found) { found?.toRows().orEmpty() }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = state.word,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display
                )
                if (phonetic != null) {
                    Text(
                        text = phonetic,
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.mono
                    )
                }
            }

            when {
                state.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = YomuTheme.colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Looking up…",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.body
                    )
                }

                rows.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows) { row ->
                        when (row) {
                            is LookupRow.Part -> Text(
                                text = row.text,
                                color = YomuTheme.colors.accent,
                                style = YomuTheme.type.caption.copy(fontStyle = FontStyle.Italic),
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            is LookupRow.Def -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${row.number}. ${row.text}",
                                    color = YomuTheme.colors.textPrimary,
                                    style = YomuTheme.type.body,
                                )
                                if (row.example != null) {
                                    Text(
                                        text = "“${row.example}”",
                                        color = YomuTheme.colors.textMuted,
                                        style = YomuTheme.type.body.copy(
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(start = 14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                state.result is DictionaryResult.Error -> Text(
                    text = "Couldn't reach the dictionary. Check your connection and try again.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body,
                )

                else -> Text(
                    text = "No definition found for “${state.word}”.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body,
                )
            }
        }
    }
}

private fun DictionaryResult.Found.toRows(): List<LookupRow> = buildList {
    entries.forEach { entry ->
        entry.meanings.forEach { meaning ->
            if (meaning.partOfSpeech.isNotBlank()) add(LookupRow.Part(meaning.partOfSpeech))
            meaning.definitions.forEachIndexed { index, definition ->
                if (definition.definition.isNotBlank()) {
                    add(
                        LookupRow.Def(
                            number = index + 1,
                            text = definition.definition,
                            example = definition.example?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }
}
