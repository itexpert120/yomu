package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.feature.settings.SettingsSectionLabel
import com.itexpert120.yomu.feature.settings.SettingsTopRow
import java.io.File

private const val ReaderDefaultsTitle = "Reading defaults"
private const val ReaderDefaultsExplainer =
    "Every book starts with these. Changes you make while reading stay with that book."

@Composable
fun ReaderDefaultsRoute(onBack: () -> Unit, onOpenFontLibrary: () -> Unit) {
    val viewModel: ReaderDefaultsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SaveFailureNotice(state.settingsError, viewModel::onRetrySettings)
    ReaderDefaultsScreen(
        settings = state.settings,
        customThemes = state.customThemes,
        customFonts = state.installedFonts,
        onUpdate = viewModel::onUpdate,
        onOpenCustomTheme = viewModel::onOpenCustomTheme,
        onApplyCustomTheme = viewModel::onApplyCustomTheme,
        onOpenFontLibrary = onOpenFontLibrary,
        onBack = onBack,
    )
    CustomThemeSheet(
        visible = state.customSheetVisible,
        settings = state.settings,
        customThemes = state.customThemes,
        onDismiss = viewModel::onCloseCustomTheme,
        onUpdateSettings = viewModel::onUpdate,
        onSave = viewModel::onSaveCustomTheme,
        onApply = viewModel::onApplyCustomTheme,
        onDelete = viewModel::onDeleteCustomTheme,
    )
}

/**
 * The app-wide reading defaults on a sheet: a live page preview, then theme, layout, font, size
 * and spacing, and the while-reading toggles. From the expanded width the preview stands beside
 * the controls. [onBack] null (the tablet settings pane) swaps the back row for a pane title.
 */
@Composable
internal fun ReaderDefaultsScreen(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdate: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val controls: @Composable ColumnScope.(Dp) -> Unit = { gutter ->
        ReaderDefaultsControls(
            gutter = gutter,
            settings = settings,
            customThemes = customThemes,
            customFonts = customFonts,
            onUpdate = onUpdate,
            onOpenCustomTheme = onOpenCustomTheme,
            onApplyCustomTheme = onApplyCustomTheme,
            onOpenFontLibrary = onOpenFontLibrary,
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(stack.sheet),
    ) {
        val sideBySide = YomuWidthClass.fromWidth(maxWidth).isExpanded
        val gutter = if (onBack == null || sideBySide) 32.dp else 24.dp
        Column(Modifier.fillMaxSize().then(if (onBack != null) Modifier.statusBarsPadding() else Modifier)) {
            if (onBack != null) SettingsTopRow(title = ReaderDefaultsTitle, onBack = onBack)
            if (sideBySide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    DefaultsColumn(gutter = gutter, endGutter = 24.dp, modifier = Modifier.weight(1f)) {
                        if (onBack == null) PaneTitle()
                        Explainer()
                        controls(gutter)
                    }
                    // Supporting pane: the preview stays put while the controls scroll beside it.
                    Column(
                        Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = if (onBack == null) 28.dp else 16.dp, end = gutter, bottom = 24.dp),
                    ) {
                        SettingsSectionLabel("Preview", top = 0.dp)
                        Spacer(Modifier.height(8.dp))
                        ReaderDefaultsPreview(settings = settings, showChapter = true)
                    }
                }
            } else {
                DefaultsColumn(gutter = gutter, endGutter = gutter, modifier = Modifier.weight(1f)) {
                    if (onBack == null) PaneTitle()
                    Explainer()
                    ReaderDefaultsPreview(settings = settings, modifier = Modifier.padding(top = 14.dp))
                    controls(gutter)
                }
            }
        }
    }
}

@Composable
private fun DefaultsColumn(
    gutter: Dp,
    endGutter: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = endGutter)
            .widthIn(max = YomuReadableMaxWidth)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
            .padding(bottom = 24.dp),
        content = content,
    )
}

@Composable
private fun PaneTitle() {
    Text(
        text = ReaderDefaultsTitle,
        style = StackType.Title,
        color = YomuTheme.stack.ink,
        modifier = Modifier
            .padding(top = 28.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Explainer() {
    Text(
        text = ReaderDefaultsExplainer,
        style = StackType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 18.sp),
        color = YomuTheme.stack.muted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** The reader's own controls, grouped under Stack section labels. */
@Composable
private fun ColumnScope.ReaderDefaultsControls(
    gutter: Dp,
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdate: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
) {
    // The same controls, in the same order, as the in-reader Reading settings sheet.
    DefaultsSection("Theme") {
        ReaderThemeRow(settings, onUpdate, compact = true)
        if (settings.theme == ReaderThemeMode.Custom) {
            ReaderCustomThemes(settings, customThemes, onOpenCustomTheme, onApplyCustomTheme)
        }
    }
    DefaultsSection("Font", spacing = 4.dp) {
        // The row scrolls edge to edge, through the column's gutter.
        ReaderFontPicker(
            settings = settings,
            onUpdateSettings = onUpdate,
            customFonts = customFonts,
            onGetFonts = onOpenFontLibrary,
            gutter = gutter,
            modifier = Modifier.layout { measurable, constraints ->
                val bleed = gutter.roundToPx() * 2
                val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + bleed, minWidth = constraints.maxWidth + bleed))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
            },
        )
        ReaderFontSizeLine(settings, onUpdate)
    }
    DefaultsSection("Page", spacing = 0.dp) {
        ReaderLayoutLine(settings, onUpdate)
        ReaderAlignmentLine(settings, onUpdate)
        ReaderSpacingLines(settings, onUpdate)
    }
    DefaultsSection("While reading") { ReaderChromeToggles(settings, onUpdate) }
}

@Composable
private fun DefaultsSection(title: String, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        SettingsSectionLabel(title, top = 0.dp)
        content()
    }
}

/** A miniature of the page itself: reading-theme colours, the chosen font, size and spacing. */
@Composable
internal fun ReaderDefaultsPreview(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    showChapter: Boolean = false,
) {
    // Reading-theme colours on purpose: this card is the page, not app chrome.
    val pageBackground = Color(settings.backgroundArgb)
    val pageText = Color(settings.textArgb)
    val pageMuted = Color(settings.colorPalette.secondaryTextArgb)
    val family = rememberReadingFontFamily(settings)
    val lineHeight = settings.lineHeight ?: PreviewAutoLineHeight
    val fontName = settings.customFont?.family ?: settings.font.displayName
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(pageBackground, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, Color(settings.colorPalette.borderArgb)), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Preview: $fontName at ${(settings.fontScale * 100).toInt()}%"
            },
    ) {
        if (showChapter) {
            Text(
                text = "Chapter VII",
                style = StackType.Caption,
                color = pageMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = PreviewPassage,
            color = pageText,
            style = TextStyle(
                fontFamily = family,
                fontSize = (16 * settings.fontScale).sp,
                lineHeight = lineHeight.em,
                textAlign = if (settings.textAlign == ReaderTextAlign.Justify) TextAlign.Justify else TextAlign.Start,
                hyphens = Hyphens.Auto,
            ),
        )
    }
}

@Composable
private fun rememberReadingFontFamily(settings: ReaderSettings): FontFamily? {
    val assets = LocalContext.current.assets
    val custom = settings.customFont
    return remember(settings.font, custom?.regularPath) {
        runCatching {
            if (custom != null) {
                FontFamily(Font(file = File(custom.regularPath)))
            } else {
                FontFamily(Font(path = "fonts/${settings.font.name}-Regular.ttf", assetManager = assets))
            }
        }.getOrNull()
    }
}

private const val PreviewAutoLineHeight = 1.6f

// The Wind in the Willows (Kenneth Grahame, 1908), public domain.
private const val PreviewPassage =
    "The Rat’s light footfall was presently heard approaching over the parched grass. " +
        "“O, the blessed coolness!” he said, and sat down, gazing thoughtfully into the river."

@Composable
private fun DefaultsPreview(mode: YomuThemeMode, settings: ReaderSettings) {
    YomuDesignTheme(themeMode = mode) {
        ReaderDefaultsScreen(
            settings = settings,
            customThemes = emptyList(),
            customFonts = emptyList(),
            onUpdate = {},
            onOpenCustomTheme = {},
            onApplyCustomTheme = {},
            onOpenFontLibrary = {},
            onBack = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun ReaderDefaultsLightPreview() = DefaultsPreview(YomuThemeMode.Light, ReaderSettings(theme = ReaderThemeMode.Light))

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun ReaderDefaultsDarkPreview() = DefaultsPreview(YomuThemeMode.Dark, ReaderSettings(theme = ReaderThemeMode.Sepia))

@Preview(widthDp = 1100, heightDp = 800)
@Composable
private fun ReaderDefaultsWidePreview() = DefaultsPreview(YomuThemeMode.Light, ReaderSettings(theme = ReaderThemeMode.Light))
