package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.ChapterReadState
import com.itexpert120.yomu.data.dictionary.DictionaryEntry
import com.itexpert120.yomu.data.dictionary.DictionaryLicense
import com.itexpert120.yomu.data.dictionary.DictionaryPronunciation
import com.itexpert120.yomu.data.dictionary.DictionaryResponse
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.dictionary.DictionarySense
import com.itexpert120.yomu.data.dictionary.DictionarySource

// Design previews for the reader's Stack chrome and sheets (phone, light and dark app themes).

private const val SamplePage =
    "The Willow-Wren was twittering his thin little song, hidden himself in the dark selvedge of the " +
        "river bank. Though it was past ten o’clock at night, the sky still clung to and retained some " +
        "lingering skirts of light from the departed day; and the sullen heats of the torrid afternoon " +
        "broke up and rolled away at the dispersing touch of the cool fingers of the short midsummer night. " +
        "Mole lay stretched on the bank, still panting from the stress of the fierce day that had been " +
        "cloudless from dawn to late sunset, and waited for his friend to return."

private val SampleToc = listOf(
    "The River Bank", "The Open Road", "The Wild Wood", "Mr. Badger", "Dulce Domum", "Mr. Toad",
    "The Piper at the Gates of Dawn", "Toad’s Adventures", "Wayfarers All", "The Further Adventures of Toad",
).mapIndexed { index, title ->
    ReaderTocItem(id = "ch$index.xhtml", title = title, locatorJson = "{}", depth = 0)
}

private val SampleSettings = ReaderSettings(layout = ReaderLayout.Paged)

@Composable
private fun ReaderChromeSample(theme: ReaderThemeMode, controls: Boolean, belowHinge: Dp = 0.dp) {
    val settings = SampleSettings.copy(theme = theme)
    val colors = settings.chromeColors()
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize().padding(bottom = belowHinge)) {
            ReaderTopBar(
                chapter = "The Piper at the Gates of Dawn",
                bookTitle = "The Wind in the Willows",
                colors = colors,
                isBookmarked = theme == ReaderThemeMode.Dark,
                onBack = {},
                onToggleBookmark = {},
                onContentHeight = {},
            )
            Text(
                text = SamplePage,
                color = Color(settings.textArgb),
                style = StackType.Body.copy(fontFamily = rememberReadingSerif(), fontSize = 17.5.sp, lineHeight = 28.sp),
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 16.dp),
            )
        }
        ReaderFooter(
            progressPercent = 42,
            chapterPagesLeft = 3,
            chapterProgression = 0.4,
            settings = settings.copy(footerShowPagesLeft = true),
            onContentHeight = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = belowHinge),
        )
        val panel: @Composable BoxScope.(Boolean) -> Unit = { visible ->
            ReaderControlsPanel(
                visible = visible,
                colors = colors,
                remainingLabel = chapterRemainingLabel(ReaderLayout.Paged, 3, 0.4),
                progressPercent = 42,
                chapters = SampleToc,
                currentChapterIndex = 6,
                onSelectChapter = {},
                onContents = {},
                onSearch = {},
                onMarks = {},
                onDisplay = {},
            )
        }
        if (belowHinge > 0.dp) {
            // Where the fold crosses the screen (not drawn by the app; just marks it here).
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = belowHinge)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Red.copy(alpha = 0.5f)),
            )
            ReaderBelowHinge(belowHinge) { panel(true) }
        } else {
            panel(controls)
        }
    }
}

@Preview(name = "Tabletop fold", widthDp = 840, heightDp = 900)
@Composable
private fun ReaderTabletopPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    ReaderChromeSample(ReaderThemeMode.Light, controls = false, belowHinge = 450.dp)
}

@Preview(name = "Tabletop flip", widthDp = 412, heightDp = 900)
@Composable
private fun ReaderTabletopFlipPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    ReaderChromeSample(ReaderThemeMode.Dark, controls = false, belowHinge = 450.dp)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderChromePaperPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ReaderChromeSample(ReaderThemeMode.Light, controls = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderChromeSepiaPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ReaderChromeSample(ReaderThemeMode.Sepia, controls = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderChromeNightPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { ReaderChromeSample(ReaderThemeMode.Dark, controls = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderImmersiveBlackPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { ReaderChromeSample(ReaderThemeMode.Black, controls = false) }

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun ReaderChromeTabletPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ReaderChromeSample(ReaderThemeMode.Light, controls = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderFailurePreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    ReaderScreen(state = ReaderUiState(experience = ReadingExperienceState(failed = true, loading = false)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderOpeningNightPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    ReaderScreen(state = ReaderUiState(experience = ReadingExperienceState(settings = ReaderSettings(theme = ReaderThemeMode.Dark))), onAction = {})
}

/** A sheet as it sits on screen: white, 32dp top corners, the sheet's own gutters. */
@Composable
private fun PreviewSheet(gutter: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(YomuTheme.stack.sheet)
                .padding(top = 16.dp, bottom = 24.dp)
                .padding(horizontal = if (gutter) 24.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

private val SampleChapterNames = listOf(
    "Crimson", "Situation", "Melissa", "Divination", "The Beyonder Path", "Charm", "The Fool",
    "Tarot Club", "Night Watch", "Old Neil", "Mysticism", "Spirit Vision",
)

// A web-novel shaped book: three volumes of a dozen chapters, reading in the second.
private val SampleVolumes: List<ReaderTocItem> = listOf("Clown", "Faceless", "Traveler").flatMapIndexed { v, name ->
    listOf(ReaderTocItem(id = "v$v.xhtml", title = "Volume ${v + 1}: $name", locatorJson = "{}", depth = 0)) +
        (1..12).map { c ->
            val n = v * 12 + c
            ReaderTocItem(id = "c$n.xhtml", title = "Chapter $n: ${SampleChapterNames[n % SampleChapterNames.size]}", locatorJson = "{}", depth = 1)
        }
}

private val SampleReads = ChapterReadState.from(
    readRows = (1..12).map { "c$it.xhtml" } + (13..16).map { "c$it.xhtml" },
    progressRows = mapOf("c17.xhtml" to 0.34f, "c19.xhtml" to 0.6f),
)

@Composable
private fun BrowseSample(tab: BrowseTab, volumes: Boolean = true) = PreviewSheet(gutter = false) {
    ReaderBrowseSheetContent(
        current = tab,
        toc = if (volumes) SampleVolumes else SampleToc,
        tocLoading = false,
        currentHref = if (volumes) "c17.xhtml" else "ch6.xhtml",
        onJumpToLocator = {},
        bookmarks = listOf(
            ReaderBookmark("1", "{}", "ch6.xhtml", "The Piper at the Gates of Dawn", 0.42, System.currentTimeMillis()),
            ReaderBookmark(
                "3",
                """{"href":"ch6.xhtml","text":{"highlight":"Then suddenly the Mole felt a great Awe fall upon him"}}""",
                "ch6.xhtml",
                "The Piper at the Gates of Dawn",
                0.44,
                System.currentTimeMillis() - 86_400_000L,
            ),
            ReaderBookmark("2", "{}", "ch4.xhtml", "Dulce Domum", 0.31, System.currentTimeMillis() - 3 * 86_400_000L),
        ),
        onJumpToBookmark = {},
        onDeleteBookmark = {},
        highlights = listOf(
            ReaderHighlight("1", """{"href":"ch0.xhtml","locations":{"progression":0.2}}""", "the sullen heats of the torrid afternoon broke up and rolled away", 0xFFF6E27A.toInt(), 0L),
            ReaderHighlight("2", """{"href":"ch6.xhtml","locations":{"progression":0.5}}""", "Then the faces began.", 0xFFB4D4F5.toInt(), 0L),
            ReaderHighlight("4", """{"href":"ch0.xhtml","locations":{"progression":0.6}}""", "Believe me, my young friend, there is nothing—absolutely nothing—half so much worth doing as simply messing about in boats.", 0xFFB8E3A8.toInt(), 0L),
        ),
        onJumpToHighlight = {},
        onDeleteHighlight = {},
        onSelectTab = {},
        dismiss = {},
        chapterReads = if (volumes) SampleReads else ChapterReadState.Empty,
        chapterProgress = 0.34,
        currentPercent = 42,
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowseFlatContentsPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { BrowseSample(BrowseTab.Contents, volumes = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowseContentsPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { BrowseSample(BrowseTab.Contents) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowseContentsDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { BrowseSample(BrowseTab.Contents) }

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun BrowseBookmarksPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { BrowseSample(BrowseTab.Bookmarks, volumes = false) }

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun BrowseHighlightsDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { BrowseSample(BrowseTab.Highlights, volumes = false) }

@Composable
private fun SearchSample(results: Boolean) = PreviewSheet(gutter = false) {
    ReaderSearchSheetContent(
        searchQuery = if (results) "willow" else "lanterns",
        searchResults = if (results) {
            listOf(
                ReaderSearchResult("{}", "…the ", "willow", "-herb, the meadowsweet, the comfrey, all came…", "The River Bank"),
                ReaderSearchResult("{}", "a little way down the river, a ", "willow", " leaned out over the water", "The Wild Wood"),
                ReaderSearchResult("{}", "The ", "Willow", "-Wren was twittering his thin little song", "The Piper at the Gates of Dawn"),
            )
        } else {
            emptyList()
        },
        searchInProgress = false,
        searchError = null,
        searchPerformed = true,
        onSearchQueryChange = {},
        onSubmitSearch = {},
        onJumpToSearchResult = {},
        dismiss = {},
    )
}

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun SearchResultsPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { SearchSample(results = true) }

@Preview(widthDp = 390, heightDp = 640)
@Composable
private fun SearchNoResultsDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { SearchSample(results = false) }

@Composable
private fun ReadingSettingsSample(
    theme: ReaderThemeMode = ReaderThemeMode.Light,
    settings: ReaderSettings = SampleSettings.copy(theme = theme, useSystemBrightness = false, brightness = 0.6f, dimLevel = 0.2f, lineHeight = 1.7f),
) = PreviewSheet(gutter = false) {
    ReaderControlsSheetContent(
        state = ReaderUiState(
            experience = ReadingExperienceState(
                title = "The Wind in the Willows",
                tableOfContents = SampleToc,
                currentHref = "ch6.xhtml",
                settings = settings,
            ),
        ),
        dismiss = {},
        onUpdateSettings = {},
        onResetSettings = {},
        onOpenCustomTheme = {},
        onApplyCustomTheme = {},
        onOpenFontLibrary = {},
        onPreviewBrightness = {},
        onCommitBrightness = {},
        onPreviewDim = {},
        onCommitDim = {},
    )
}

// As it opens on a phone (capped, scrolling under its title).
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReadingSettingsPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { ReadingSettingsSample() }

// The whole sheet at once, to check every row.
@Preview(widthDp = 390, heightDp = 1800)
@Composable
private fun ReadingSettingsFullPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    ReadingSettingsSample(settings = SampleSettings.copy(useSystemBrightness = true, theme = ReaderThemeMode.Custom))
}

@Preview(widthDp = 390, heightDp = 1800)
@Composable
private fun ReadingSettingsFullDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    ReadingSettingsSample(ReaderThemeMode.Dark)
}

private val SampleLookup = DictionaryResult.Found(
    DictionaryResponse(
        word = "selvedge",
        entries = listOf(
            DictionaryEntry(
                partOfSpeech = "noun",
                pronunciations = listOf(DictionaryPronunciation(text = "/ˈsɛl.vɛdʒ/", tags = listOf("UK"))),
                senses = listOf(
                    DictionarySense("An edge woven into fabric during manufacture so that it does not unravel."),
                    DictionarySense("A border or edge of something, especially a strip of land along water."),
                ),
                synonyms = listOf("edge", "border", "margin", "fringe"),
            ),
        ),
        source = DictionarySource("https://en.wiktionary.org/wiki/selvedge", DictionaryLicense("CC BY-SA 4.0")),
    ),
)

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun WordLookupPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    PreviewSheet {
        WordLookupSheetContent(WordLookupUiState("selvedge", loading = false, result = SampleLookup), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 480)
@Composable
private fun WordLookupOfflineDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    PreviewSheet {
        WordLookupSheetContent(WordLookupUiState("selvedge", loading = false, result = DictionaryResult.Error), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 480)
@Composable
private fun HighlightEditPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    PreviewSheet {
        HighlightEditSheetContent(
            ReaderHighlight("1", "{}", "the sullen heats of the torrid afternoon broke up and rolled away", 0xFFF6E27A.toInt(), 0L),
            {},
            {},
            {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 300)
@Composable
private fun ReaderNoticesPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) { NoticesSample() }

@Preview(widthDp = 390, heightDp = 300)
@Composable
private fun ReaderNoticesDarkPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) { NoticesSample() }

@Composable
private fun NoticesSample() {
    Column(
        modifier = Modifier.fillMaxSize().background(YomuTheme.stack.desk).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReaderNoticeCard(text = "Couldn’t save your highlight", icon = StackIcons.Error, iconIsAlert = true) {
            ReaderNoticeAction("Retry", onClick = {})
        }
        ReaderNoticeCard(text = "Continue from Pixel Tablet at 63%?", icon = StackIcons.Devices) {
            ReaderNoticeAction("Stay here", onClick = {}, secondary = true)
            ReaderNoticeAction("Jump", onClick = {})
        }
    }
}

// From medium widths the reader's sheets are side sheets (YomuBottomSheet).

@Preview(name = "Display side sheet", widthDp = 1280, heightDp = 800)
@Composable
private fun DisplaySideSheetPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Light) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk)) {
        YomuBottomSheet(visible = true, onDismiss = {}, scrollable = false, showScrollEdgeShadow = false, horizontalContentPadding = 0.dp) { dismiss ->
            ReaderControlsSheetContent(
                state = ReaderUiState(
                    experience = ReadingExperienceState(
                        title = "The Wind in the Willows",
                        tableOfContents = SampleToc,
                        currentHref = "ch6.xhtml",
                        settings = SampleSettings.copy(useSystemBrightness = false, brightness = 0.6f, dimLevel = 0.2f),
                    ),
                ),
                dismiss = dismiss,
                onUpdateSettings = {},
                onResetSettings = {},
                onOpenCustomTheme = {},
                onApplyCustomTheme = {},
                onOpenFontLibrary = {},
                onPreviewBrightness = {},
                onCommitBrightness = {},
                onPreviewDim = {},
                onCommitDim = {},
            )
        }
    }
}

@Preview(name = "Browse side sheet", widthDp = 840, heightDp = 1000)
@Composable
private fun BrowseSideSheetPreview() = YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk)) {
        YomuBottomSheet(visible = true, onDismiss = {}, scrollable = false, horizontalContentPadding = 0.dp) { dismiss ->
            ReaderBrowseSheetContent(
                current = BrowseTab.Contents,
                toc = SampleVolumes,
                tocLoading = false,
                currentHref = "c17.xhtml",
                chapterReads = SampleReads,
                onJumpToLocator = {},
                bookmarks = emptyList(),
                onJumpToBookmark = {},
                onDeleteBookmark = {},
                highlights = emptyList(),
                onJumpToHighlight = {},
                onDeleteHighlight = {},
                onSelectTab = {},
                dismiss = dismiss,
                currentPercent = 42,
            )
        }
    }
}
