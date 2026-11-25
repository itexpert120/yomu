package com.itexpert120.yomu.app.devgallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBookCard
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuButtonSize
import com.itexpert120.yomu.core.designsystem.YomuCard
import com.itexpert120.yomu.core.designsystem.YomuCardTone
import com.itexpert120.yomu.core.designsystem.YomuChip
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuDialogCard
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuExtendedFloatingActionButton
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuIconButton
import com.itexpert120.yomu.core.designsystem.YomuLabeledIconAction
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuProgressBar
import com.itexpert120.yomu.core.designsystem.YomuRangeRow
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuSectionHeader
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSelectableChip
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuSkeleton
import com.itexpert120.yomu.core.designsystem.YomuSkeletonCover
import com.itexpert120.yomu.core.designsystem.YomuSkeletonLine
import com.itexpert120.yomu.core.designsystem.YomuSlider
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction
import com.itexpert120.yomu.core.designsystem.YomuValueChip
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition

/**
 * Dev-only gallery of every Stack primitive, for checking them in isolation. The theme switch at the
 * top flips Light / Dark / Pure black; the previews below render each page in light and dark.
 */
@Composable
fun YomuGalleryApp(
    onThemeModeChange: (YomuThemeMode) -> Unit = {},
) {
    var themeMode by remember { mutableStateOf(YomuThemeMode.Light) }
    LaunchedEffect(themeMode) { onThemeModeChange(themeMode) }
    YomuDesignTheme(themeMode = themeMode) {
        YomuAppSurface {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    YomuSegmentedControl(
                        options = YomuThemeMode.entries.map { it.label },
                        selectedIndex = YomuThemeMode.entries.indexOf(themeMode),
                        onSelected = { themeMode = YomuThemeMode.entries[it] },
                    )
                }
                GalleryPages.forEach { page -> page.content(this) }
            }
        }
    }
}

private class GalleryPage(val content: @Composable ColumnScope.() -> Unit)

private val GalleryPages = listOf(
    GalleryPage { ControlsPage() },
    GalleryPage { SettingsPage() },
    GalleryPage { FeedbackPage() },
    GalleryPage { SurfacesPage() },
)

@Composable
private fun GallerySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    YomuSectionLabel(title, modifier = Modifier.padding(start = 20.dp))
    YomuCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ControlsPage() {
    var segment by remember { mutableIntStateOf(2) }
    var layout by remember { mutableStateOf("Paged") }
    var slider by remember { mutableFloatStateOf(0.42f) }
    var chip by remember { mutableIntStateOf(2) }
    var footer by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("The Wind in the Willows") }
    var author by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    YomuScreenHeader(title = "Library", trailing = {
        YomuIconButton(StackIcons.Search, "Search", onClick = {})
        YomuIconButton(StackIcons.Add, "Import book", onClick = {})
        YomuIconButton(StackIcons.Tune, "Arrange library", onClick = {})
    })
    YomuScreenHeader(title = "Privacy", onBack = {})

    GallerySection("Buttons · 48dp targets") {
        YomuButton("Resume", onClick = {}, icon = StackIcons.PlayArrowFilled)
        YomuButton("Add to a shelf", onClick = {}, emphasis = YomuButtonEmphasis.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            YomuButton("Read", onClick = {}, size = YomuButtonSize.InRow, modifier = Modifier.weight(1f))
            YomuButton("Unread", onClick = {}, size = YomuButtonSize.InRow, emphasis = YomuButtonEmphasis.Secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            YomuButton("Remove book", onClick = {}, emphasis = YomuButtonEmphasis.Danger)
            YomuButton("Cancel", onClick = {}, emphasis = YomuButtonEmphasis.Ghost)
            YomuButton("Disabled", onClick = {}, enabled = false, size = YomuButtonSize.Small)
        }
        Row {
            YomuIconButton(StackIcons.Search, "Search", onClick = {})
            YomuIconButton(StackIcons.Add, "Import book", onClick = {})
            YomuIconButton(StackIcons.MoreVert, "More", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            YomuExtendedFloatingActionButton(expanded = true, onClick = {}, icon = StackIcons.PlayArrowFilled, label = "Resume", contentDescription = "Resume")
            YomuExtendedFloatingActionButton(expanded = false, onClick = {}, icon = StackIcons.PlayArrowFilled, label = "Resume", contentDescription = "Resume")
        }
    }

    GallerySection("Segmented choice · slider") {
        YomuSegmentedControl(listOf("Light", "Dark", "Auto"), segment, { segment = it }, modifier = Modifier.width(220.dp))
        YomuConnectedChoiceGroup(
            options = listOf("Scroll", "Paged"),
            selected = layout,
            label = { it },
            onSelect = { layout = it },
            icon = { if (it == "Scroll") StackIcons.SwipeVertical else StackIcons.MenuBook },
        )
        YomuConnectedChoiceGroup(
            options = listOf("Left", "Justify", "Auto", "Wide"),
            selected = "Justify",
            label = { it },
            onSelect = {},
            icon = { StackIcons.FormatAlignLeft },
            iconOnTop = true,
        )
        YomuSlider(value = slider, onValueChange = { slider = it })
        YomuRangeRow(label = "Font size", value = slider, valueLabel = "${(14 + slider * 18).toInt()}sp", onValueChange = { slider = it })
    }

    GallerySection("Chips · tags") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Reading", "Classics", "Bedtime").forEachIndexed { i, label ->
                YomuChip(label, selected = chip == i, onClick = { chip = i })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            YomuSelectableChip("Footer", StackIcons.CallToAction, footer, { footer = it })
            YomuSelectableChip("Clock", StackIcons.Schedule, clock, { clock = it })
            YomuSelectableChip("Off", StackIcons.Schedule, false, {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            YomuValueChip("100%", customized = false, onReset = {}, resetLabel = "Reset")
            YomuValueChip("120%", customized = true, onReset = {}, resetLabel = "Reset")
        }
    }

    GallerySection("Text field") {
        YomuTextField(title, { title = it }, label = "Title")
        YomuTextField(author, { author = it }, label = "Author", placeholder = "Add an author", isError = true, supportingText = "An author is required to sort by author.")
        YomuTextField(search, { search = it }, label = "Search in book", showLabel = false, leadingIcon = StackIcons.Search)
    }
}

@Composable
private fun SettingsPage() {
    var black by remember { mutableStateOf(false) }
    var keepOn by remember { mutableStateOf(true) }
    var theme by remember { mutableIntStateOf(2) }
    var brightness by remember { mutableFloatStateOf(0.6f) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        YomuSettingGroup("Appearance") {
            YomuSettingList {
                YomuSettingRow(
                    title = "Theme",
                    position = YomuSettingPosition.First,
                    leadingContent = { Icon(StackIcons.DarkMode, null) },
                ) { YomuSegmentedControl(listOf("Light", "Dark", "Auto"), theme, { theme = it }, Modifier.width(190.dp)) }
                YomuSettingRow(
                    title = "Pure black at night",
                    subtitle = "For OLED screens",
                    position = YomuSettingPosition.Last,
                    leadingContent = { Icon(StackIcons.DarkMode, null) },
                    onClick = { black = !black },
                ) { YomuTogglePill(black, { black = it }) }
            }
        }
        YomuSettingGroup("Reading") {
            YomuSettingList {
                val rows = listOf("Reading defaults" to "Lora · Paper", "Fonts" to "7 installed")
                rows.forEachIndexed { i, (label, value) ->
                    YomuSettingRow(
                        title = label,
                        position = yomuSettingPosition(i, rows.size + 1),
                        leadingContent = { Icon(if (i == 0) StackIcons.AutoStories else StackIcons.TextFields, null) },
                        onClick = {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(value)
                            Icon(StackIcons.KeyboardArrowRight, null)
                        }
                    }
                }
                YomuSettingContainer(position = YomuSettingPosition.Last) {
                    Text("Brightness", style = MaterialTheme.typography.bodyLarge)
                    YomuSlider(brightness, { brightness = it })
                }
            }
        }
        YomuSettingGroup("Diagnostics", subtitle = "Logged on this device only.") {
            YomuSettingList {
                YomuSettingRow(title = "Keep screen on", position = YomuSettingPosition.First, onClick = { keepOn = !keepOn }) {
                    YomuTogglePill(keepOn, { keepOn = it })
                }
                YomuSettingRow(title = "Share crash log", subtitle = "Nothing logged yet", enabled = false, position = YomuSettingPosition.Middle, onClick = {}) {}
                YomuSettingRow(title = "Clear crash log", destructive = true, position = YomuSettingPosition.Last, onClick = {}) {}
            }
        }
        YomuSettingGroup("Outlined group", outlined = true) {
            Text("Content that isn't rows sits in one card.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FeedbackPage() {
    GallerySection("Notices") {
        YomuNotice("Added to Classics", tone = YomuNoticeTone.Floating)
        YomuNotice("Couldn’t save your place.", tone = YomuNoticeTone.Floating, icon = StackIcons.Error, actionLabel = "Retry", onAction = {})
        YomuNotice("Importing 3 books…", tone = YomuNoticeTone.Floating, loading = true)
        YomuNotice("Older sessions only kept totals.", icon = StackIcons.Info)
        YomuNotice("Couldn’t reach the font library.", icon = StackIcons.Error, tone = YomuNoticeTone.Error, actionLabel = "Retry", onAction = {})
    }
    GallerySection("Dialog") {
        YomuDialogCard(
            title = "Remove this book?",
            text = "The Wind in the Willows and its highlights leave this device.",
            confirmLabel = "Remove",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
        YomuDialogCard(title = "Turn sync off?", text = "This phone stops syncing.", confirmLabel = "Turn off", onConfirm = {}, onDismiss = {})
    }
    GallerySection("Empty states · loading") {
        YomuEmptyState(
            icon = StackIcons.SearchOff,
            title = "No matches",
            body = "Try a different word or check the spelling.",
            actions = { YomuButton("Clear search", onClick = {}, size = YomuButtonSize.Small) },
        )
        YomuEmptyState(icon = StackIcons.Book, title = "Nothing on this shelf", body = "Books you finish land here.", coverOutline = true, compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YomuSkeletonCover(Modifier.fillMaxWidth())
                    YomuSkeletonLine(Modifier.fillMaxWidth(0.8f))
                    YomuSkeletonLine(Modifier.fillMaxWidth(0.4f), height = 10.dp)
                }
            }
        }
        YomuSkeleton(Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp))
        YomuLoadingState(Modifier.fillMaxWidth(), message = "Building contents…")
        YomuProgressBar(progress = 0.42f)
    }
}

@Composable
private fun SurfacesPage() {
    GallerySection("Sheet title row") {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(YomuTheme.stack.sheet)
                .padding(horizontal = 24.dp),
        ) {
            YomuSheetHeader(title = "Arrange library", onClose = {}, trailing = { YomuIconButton(StackIcons.RestartAlt, "Reset", onClick = {}) })
            YomuSheetHeader(title = "Contents", subtitle = "24 chapters")
        }
    }
    GallerySection("Toolbar · actions") {
        YomuFloatingToolbar(
            actions = listOf(
                YomuToolbarAction(StackIcons.LibraryAdd, "Organize", {}),
                YomuToolbarAction(StackIcons.PlaylistAddCheck, "Read", {}),
                YomuToolbarAction(StackIcons.Delete, "Remove", {}),
            ),
        )
        Row {
            YomuLabeledIconAction("Browse", {}, StackIcons.Toc)
            YomuLabeledIconAction("Search", {}, StackIcons.Search)
            YomuLabeledIconAction("Display", {}, StackIcons.TextFields, selected = true, containerColor = YomuTheme.stack.raised)
        }
    }
    GallerySection("Cards · headers · progress") {
        YomuSectionHeader("Time by book", subtitle = "Lifetime totals", trailing = { YomuButton("Show all", onClick = {}, emphasis = YomuButtonEmphasis.Ghost) })
        YomuCard(tone = YomuCardTone.Raised, modifier = Modifier.fillMaxWidth()) {
            Text("Raised inset inside a sheet", style = StackType.Heading)
            Text("3 entries · 18 KB", style = StackType.Caption, color = YomuTheme.stack.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            YomuBookCard("Deep Work", "Cal Newport", 0.64f, listOf(Color(0xFF263A30), Color(0xFF587A5F)), Modifier.weight(1f))
            YomuBookCard("The Left Hand", "Ursula K. Le Guin", 0.28f, listOf(Color(0xFF1A2736), Color(0xFF526982)), Modifier.weight(1f))
            YomuBookCard("Designing Type", "Karen Cheng", 0.83f, listOf(Color(0xFF35211E), Color(0xFF9B5948)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YomuColorSwatch("Paper", Color.White, selected = true, onClick = {}, modifier = Modifier.weight(1f))
            YomuColorSwatch("Sepia", Color(0xFFF5EBDA), selected = false, onClick = {}, modifier = Modifier.weight(1f))
            YomuColorSwatch("Night", Color(0xFF121416), selected = false, onClick = {}, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GalleryPreviewFrame(mode: YomuThemeMode, content: @Composable ColumnScope.() -> Unit) {
    YomuDesignTheme(themeMode = mode) {
        YomuAppSurface {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), content = content)
            }
        }
    }
}

@Preview(name = "Controls · light", widthDp = 390, heightDp = 1720)
@Composable
private fun ControlsLightPreview() = GalleryPreviewFrame(YomuThemeMode.Light) { ControlsPage() }

@Preview(name = "Controls · dark", widthDp = 390, heightDp = 1720)
@Composable
private fun ControlsDarkPreview() = GalleryPreviewFrame(YomuThemeMode.Dark) { ControlsPage() }

@Preview(name = "Settings · light", widthDp = 390, heightDp = 900)
@Composable
private fun SettingsLightPreview() = GalleryPreviewFrame(YomuThemeMode.Light) { SettingsPage() }

@Preview(name = "Settings · dark", widthDp = 390, heightDp = 900)
@Composable
private fun SettingsDarkPreview() = GalleryPreviewFrame(YomuThemeMode.Dark) { SettingsPage() }

@Preview(name = "Settings · black", widthDp = 390, heightDp = 900)
@Composable
private fun SettingsBlackPreview() = GalleryPreviewFrame(YomuThemeMode.Oled) { SettingsPage() }

@Preview(name = "Feedback · light", widthDp = 390, heightDp = 1900)
@Composable
private fun FeedbackLightPreview() = GalleryPreviewFrame(YomuThemeMode.Light) { FeedbackPage() }

@Preview(name = "Feedback · dark", widthDp = 390, heightDp = 1900)
@Composable
private fun FeedbackDarkPreview() = GalleryPreviewFrame(YomuThemeMode.Dark) { FeedbackPage() }

@Preview(name = "Surfaces · light", widthDp = 390, heightDp = 1000)
@Composable
private fun SurfacesLightPreview() = GalleryPreviewFrame(YomuThemeMode.Light) { SurfacesPage() }

@Preview(name = "Surfaces · dark", widthDp = 390, heightDp = 1000)
@Composable
private fun SurfacesDarkPreview() = GalleryPreviewFrame(YomuThemeMode.Dark) { SurfacesPage() }
