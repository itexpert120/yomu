package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.data.annotations.AnnotationImportPreview
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.encyclopedia.WikipediaResult
import com.itexpert120.yomu.data.encyclopedia.WikipediaSummary
import com.itexpert120.yomu.feature.annotations.AnnotationExportSheetContent
import com.itexpert120.yomu.feature.annotations.AnnotationImportDialogCard
import com.itexpert120.yomu.feature.annotations.AnnotationImportUi

// Design previews for the highlight sheet's note and style, the look-up sheet's language and
// Wikipedia states, and the highlights export / import surfaces (light and dark).

@Composable
private fun SheetSample(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(YomuTheme.stack.sheet)
                .padding(top = 16.dp, bottom = 24.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

private val NotedHighlight = ReaderHighlight(
    id = "1",
    locatorJson = "{}",
    text = "the sullen heats of the torrid afternoon broke up and rolled away",
    colorArgb = 0xFFB4D4F5.toInt(),
    createdAt = 0L,
    note = "The heat breaking like a storm.",
    style = HighlightStyle.Squiggly,
)

@Composable
private fun HighlightSample() = SheetSample {
    HighlightEditSheetContent(
        highlight = NotedHighlight,
        onSelectColor = {},
        onSelectStyle = {},
        onNoteChange = { _, _ -> },
        onCopy = {},
        onShare = {},
        onDelete = {},
        dismiss = {},
    )
}

@Preview(widthDp = 390, heightDp = 760)
@Composable
private fun HighlightNoteStylePreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { HighlightSample() }

@Preview(widthDp = 390, heightDp = 760)
@Composable
private fun HighlightNoteStyleDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { HighlightSample() }

private val SampleWikipedia = WikipediaResult.Found(
    WikipediaSummary(
        title = "Selvage",
        description = "Self-finished edge of fabric",
        extract = "A selvage (US English) or selvedge (British English) is a \"self-finished\" edge of a piece of fabric, " +
            "which keeps it from unraveling and fraying. The term \"self-finished\" means that the edge does not need " +
            "additional finishing work, such as hem or bias tape, to prevent fraying.",
        thumbnailUrl = null,
        pageUrl = "https://en.m.wikipedia.org/wiki/Selvage",
        language = "en",
    ),
)

@Composable
private fun LookupSample(state: WordLookupUiState) = SheetSample {
    WordLookupSheetContent(state, {}, WordLookupActions())
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun LookupWikipediaPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    LookupSample(
        WordLookupUiState(
            "selvedge",
            loading = false,
            result = DictionaryResult.NotFound,
            tab = LookupTab.Wikipedia,
            wikipedia = WikipediaUiState(loading = false, result = SampleWikipedia),
            canTranslate = true,
        ),
    )
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun LookupWikipediaDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    LookupSample(
        WordLookupUiState(
            "toad hall",
            query = "Toad Hall",
            phrase = true,
            loading = false,
            wikipedia = WikipediaUiState(loading = false, result = WikipediaResult.NotFound),
            canTranslate = true,
        ),
    )
}

@Preview(widthDp = 390, heightDp = 560)
@Composable
private fun LookupOtherLanguagePreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    LookupSample(WordLookupUiState("crapaud", loading = false, result = DictionaryResult.NotFound, language = "fr"))
}

@Preview(widthDp = 390, heightDp = 560)
@Composable
private fun LookupOtherLanguageDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    LookupSample(WordLookupUiState("crapaud", loading = false, result = DictionaryResult.NotFound, language = "fr", canTranslate = true))
}

@Composable
private fun ExportSample() = SheetSample {
    AnnotationExportSheetContent(working = false, onPick = {}, dismiss = {})
}

@Preview(widthDp = 390, heightDp = 420)
@Composable
private fun ExportSheetPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ExportSample() }

@Preview(widthDp = 390, heightDp = 420)
@Composable
private fun ExportSheetDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { ExportSample() }

private val SampleImport = AnnotationImportPreview(
    fileBookTitle = "The Wind in the Willows",
    fileBookAuthor = "Kenneth Grahame",
    bookMatches = false,
    highlights = 12,
    notes = 4,
    bookmarks = 3,
    newHighlights = 9,
    updatedHighlights = 2,
    newBookmarks = 1,
)

@Composable
private fun ImportSample() {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk), contentAlignment = Alignment.Center) {
        AnnotationImportDialogCard(AnnotationImportUi(SampleImport, fromCalibre = false))
    }
}

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun ImportDialogPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ImportSample() }

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun ImportDialogDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { ImportSample() }
