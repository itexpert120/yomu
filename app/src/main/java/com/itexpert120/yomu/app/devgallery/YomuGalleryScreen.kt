package com.itexpert120.yomu.app.devgallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.ReaderPageSurface
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBookCard
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuChip
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuFloatingPanel
import com.itexpert120.yomu.core.designsystem.YomuPanel
import com.itexpert120.yomu.core.designsystem.YomuRangeRow
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode

@Composable
fun YomuGalleryApp(
    onThemeModeChange: (YomuThemeMode) -> Unit = {},
) {
    var themeMode by remember { mutableStateOf(YomuThemeMode.Light) }
    LaunchedEffect(themeMode) {
        onThemeModeChange(themeMode)
    }
    YomuDesignTheme(themeMode = themeMode) {
        YomuGalleryScreen(
            themeMode = themeMode,
            onThemeModeChange = { themeMode = it },
        )
    }
}

@Composable
fun YomuGalleryScreen(
    themeMode: YomuThemeMode,
    onThemeModeChange: (YomuThemeMode) -> Unit,
) {
    val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isExpanded = containerWidthDp >= 900.dp
    YomuAppSurface {
        if (isExpanded) {
            ExpandedGalleryLayout(themeMode, onThemeModeChange)
        } else {
            CompactGalleryLayout(themeMode, onThemeModeChange)
        }
    }
}


@Composable
private fun yomuScreenPadding(
    horizontal: androidx.compose.ui.unit.Dp,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    return PaddingValues(
        start = safeDrawing.calculateStartPadding(layoutDirection) + horizontal,
        top = safeDrawing.calculateTopPadding() + top,
        end = safeDrawing.calculateEndPadding(layoutDirection) + horizontal,
        bottom = safeDrawing.calculateBottomPadding() + bottom,
    )
}

@Composable
private fun CompactGalleryLayout(
    themeMode: YomuThemeMode,
    onThemeModeChange: (YomuThemeMode) -> Unit,
) {
    val contentPadding = yomuScreenPadding(horizontal = 18.dp, top = 18.dp, bottom = 24.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { GalleryHeader(themeMode, onThemeModeChange, compact = false) }
        item { ControlGallery() }
        item { ReaderGallery() }
        item { BookGallery() }
        item { ThemeGallery() }
    }
}

@Composable
private fun ExpandedGalleryLayout(
    themeMode: YomuThemeMode,
    onThemeModeChange: (YomuThemeMode) -> Unit,
) {
    val contentPadding = yomuScreenPadding(horizontal = 24.dp, top = 24.dp, bottom = 24.dp)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        YomuPanel(
            modifier = Modifier
                .width(300.dp)
                .fillMaxSize(),
            tonal = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                GalleryHeader(themeMode, onThemeModeChange, compact = false)
                Text(
                    text = "Sections",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
                listOf("Controls", "Reader", "Library", "Themes", "Settings").forEachIndexed { index, label ->
                    YomuChip(label, selected = index == 0, onClick = {})
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ControlGallery() }
            item { ReaderGallery() }
            item { ThemeGallery() }
            item { Spacer(Modifier.height(12.dp)) }
        }
        LazyColumn(
            modifier = Modifier.width(330.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { BookGallery() }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun GalleryHeader(
    themeMode: YomuThemeMode,
    onThemeModeChange: (YomuThemeMode) -> Unit,
    compact: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 18.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Yomu", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.display)
        Text(
            text = "Component gallery for the EPUB reader design system.",
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.body,
        )
        YomuSegmentedControl(
            options = YomuThemeMode.entries.map { it.label },
            selectedIndex = YomuThemeMode.entries.indexOf(themeMode),
            onSelected = { onThemeModeChange(YomuThemeMode.entries[it]) },
        )
    }
}

@Composable
private fun ControlGallery() {
    var selectedMode by remember { mutableIntStateOf(0) }
    var selectedChip by remember { mutableIntStateOf(1) }
    var fontSize by remember { mutableFloatStateOf(0.55f) }
    var margin by remember { mutableFloatStateOf(0.36f) }

    YomuPanel {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionTitle("Controls")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                YomuButton("Read", onClick = {})
                YomuButton("Library", onClick = {}, emphasis = YomuButtonEmphasis.Secondary)
                YomuButton("Ghost", onClick = {}, emphasis = YomuButtonEmphasis.Ghost)
            }
            YomuSegmentedControl(
                options = listOf("Paged", "Scrolled", "Focus"),
                selectedIndex = selectedMode,
                onSelected = { selectedMode = it },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TOC", "Brightness", "Font", "Bookmark", "Highlight").forEachIndexed { index, label ->
                    YomuChip(label, selected = selectedChip == index, onClick = { selectedChip = index })
                }
            }
            YomuRangeRow(
                label = "Reader font size",
                value = fontSize,
                valueLabel = "${(14 + fontSize * 18).toInt()}sp",
                onValueChange = { fontSize = it },
            )
            YomuRangeRow(
                label = "Page margin",
                value = margin,
                valueLabel = "${(12 + margin * 52).toInt()}dp",
                onValueChange = { margin = it },
            )
        }
    }
}

@Composable
private fun ReaderGallery() {
    YomuPanel(tonal = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Reader Surface")
            ReaderPageSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Chapter 03",
                        color = YomuTheme.colors.readerMuted,
                        style = YomuTheme.type.caption,
                    )
                    Text(
                        text = "The quiet reader",
                        color = YomuTheme.colors.readerInk,
                        style = YomuTheme.type.title,
                    )
                    Text(
                        text = "The page should feel intentional before it feels styled. Controls belong at the perimeter, stable and restrained, so the sentence remains the primary surface.",
                        color = YomuTheme.colors.readerInk,
                        style = YomuTheme.type.reader,
                    )
                    YomuFloatingPanel(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("42%", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.mono, modifier = Modifier.align(Alignment.CenterVertically))
                            YomuChip("TOC", selected = false, onClick = {})
                            YomuChip("Aa", selected = true, onClick = {})
                            YomuChip("Mark", selected = false, onClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookGallery() {
    val books = listOf(
        GalleryBook("Deep Work", "Cal Newport", 0.64f, listOf(Color(0xFF263A30), Color(0xFF587A5F))),
        GalleryBook("The Left Hand", "Ursula K. Le Guin", 0.28f, listOf(Color(0xFF1A2736), Color(0xFF526982))),
        GalleryBook("Designing Type", "Karen Cheng", 0.83f, listOf(Color(0xFF35211E), Color(0xFF9B5948))),
    )
    YomuPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Library Cards")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                books.forEach { book ->
                    YomuBookCard(
                        title = book.title,
                        author = book.author,
                        progress = book.progress,
                        coverColors = book.coverColors,
                        modifier = Modifier.width(126.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeGallery() {
    var selected by remember { mutableStateOf("Default") }
    val swatches = listOf(
        "Default" to YomuTheme.colors.readerPaper,
        "White" to Color.White,
        "Gray" to Color(0xFFE8E8E4),
        "Sepia" to Color(0xFFEAD8B5),
        "Dark" to Color(0xFF111210),
        "OLED" to Color.Black,
    )

    YomuPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Theme Swatches")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { (name, color) ->
                    YomuColorSwatch(
                        name = name,
                        color = color,
                        selected = selected == name,
                        onClick = { selected = name },
                        modifier = Modifier.width(86.dp),
                    )
                }
            }
            YomuSettingGroup("Appearance stack") {
                listOf("Font", "Paragraph", "Page", "Status", "Theme").forEach { label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = YomuTheme.colors.textPrimary,
                            style = YomuTheme.type.body,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Ready", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.section,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(1.dp)
                .background(YomuTheme.colors.textPrimary)
        )
    }
}

private data class GalleryBook(
    val title: String,
    val author: String,
    val progress: Float,
    val coverColors: List<Color>,
)

@Preview(widthDp = 390, heightDp = 980, showBackground = true)
@Composable
private fun CompactGalleryPreview() {
    YomuGalleryApp()
}

@Preview(widthDp = 1100, heightDp = 800, showBackground = true)
@Composable
private fun ExpandedGalleryPreview() {
    YomuGalleryApp()
}
