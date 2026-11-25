package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuSlider
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuValueChip
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.model.ReaderThemeMode
import java.io.File
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily

internal enum class SheetTab(val label: String) { Controls("Controls"), Display("Display") }

/**
 * Reading settings, in two word tabs: Controls (chapter, brightness and extra dim, footer and screen
 * toggles) and Display (theme, layout, font, size, alignment, typography, reset).
 */
@Composable
internal fun ReaderControlsSheet(
    visible: Boolean,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSelectChapter: (String) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    var tab by remember { mutableStateOf(SheetTab.Controls) }
    YomuBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        showScrollEdgeShadow = false,
        horizontalContentPadding = 0.dp,
    ) { dismiss ->
        ReaderControlsSheetContent(
            state = state,
            tab = tab,
            onSelectTab = { tab = it },
            dismiss = dismiss,
            onSelectChapter = onSelectChapter,
            onNextChapter = onNextChapter,
            onPreviousChapter = onPreviousChapter,
            onUpdateSettings = onUpdateSettings,
            onResetSettings = onResetSettings,
            onOpenCustomTheme = onOpenCustomTheme,
            onApplyCustomTheme = onApplyCustomTheme,
            onPreviewBrightness = onPreviewBrightness,
            onCommitBrightness = onCommitBrightness,
            onPreviewDim = onPreviewDim,
            onCommitDim = onCommitDim,
        )
    }
}

/** The reading settings sheet's body, separate from its sheet so it can be previewed. */
@Composable
internal fun ReaderControlsSheetContent(
    state: ReaderUiState,
    tab: SheetTab,
    onSelectTab: (SheetTab) -> Unit,
    dismiss: () -> Unit,
    onSelectChapter: (String) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    run {
        Column(
            // Animate the height as tab content of differing size swaps in.
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
                ),
        ) {
            YomuSheetHeader(
                title = "Reading settings",
                subtitle = state.experience.title.takeIf { it.isNotBlank() },
                onClose = dismiss,
                modifier = Modifier.padding(horizontal = YomuTheme.space.lg),
            )
            ReaderSheetTabs(
                tabs = SheetTab.entries,
                selected = tab,
                label = { it.label },
                onSelect = onSelectTab,
            )
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    yomuContentSwap(forward = targetState.ordinal > initialState.ordinal)
                },
                label = "readerSheetTab",
            ) { current ->
                Box(Modifier.padding(horizontal = YomuTheme.space.lg).padding(top = 14.dp)) {
                    when (current) {
                        SheetTab.Controls -> ControlsTab(
                            state = state,
                            onSelectChapter = onSelectChapter,
                            onNextChapter = onNextChapter,
                            onPreviousChapter = onPreviousChapter,
                            onUpdateSettings = onUpdateSettings,
                            onPreviewBrightness = onPreviewBrightness,
                            onCommitBrightness = onCommitBrightness,
                            onPreviewDim = onPreviewDim,
                            onCommitDim = onCommitDim,
                        )

                        SheetTab.Display -> DisplayTab(
                            state = state,
                            onUpdateSettings = onUpdateSettings,
                            onResetSettings = onResetSettings,
                            onOpenCustomTheme = onOpenCustomTheme,
                            onApplyCustomTheme = onApplyCustomTheme,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsTab(
    state: ReaderUiState,
    onSelectChapter: (String) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    val stack = YomuTheme.stack
    val reading = state.experience
    val s = reading.settings
    val chapters = remember(reading.tableOfContents) { reading.tableOfContents.readerChapters() }
    val currentChapterIndex = chapters.currentChapterIndex(reading.currentHref)
    var previewChapterIndex by remember(chapters, currentChapterIndex) {
        mutableIntStateOf(currentChapterIndex)
    }
    fun chapterIndex(fraction: Float): Int = if (chapters.size <= 1) {
        0
    } else {
        (fraction.coerceIn(0f, 1f) * chapters.lastIndex).roundToInt()
    }
    val chapterFraction = if (chapters.size <= 1) {
        0f
    } else {
        currentChapterIndex.toFloat() / chapters.lastIndex
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSectionLabel("Chapter")
        Text(
            text = chapters.getOrNull(previewChapterIndex)?.let { chapter ->
                "${previewChapterIndex + 1} of ${chapters.size} · ${chapter.title}"
            } ?: "No chapters available",
            color = stack.body,
            style = StackType.BodySmall.copy(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
        ) {
            SheetIconButton(StackIcons.KeyboardArrowLeft, "Previous chapter", onClick = onPreviousChapter)
            ReaderSlider(
                fraction = chapterFraction,
                onSeek = { fraction ->
                    val index = chapterIndex(fraction)
                    previewChapterIndex = index
                    chapters.getOrNull(index)?.locatorJson?.let(onSelectChapter)
                },
                modifier = Modifier.weight(1f),
                onDrag = { previewChapterIndex = chapterIndex(it) },
                enabled = chapters.size > 1,
                snapPoints = chapters.size,
                contentDescription = "Chapter selector",
            )
            SheetIconButton(StackIcons.KeyboardArrowRight, "Next chapter", onClick = onNextChapter)
        }

        // Brightness + extra dim are contextual to the current reading session, so they stay here.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = YomuTheme.space.xs).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderSectionLabel("Brightness", Modifier.weight(1f))
            Text(
                text = "Use system",
                color = stack.body,
                style = StackType.Label.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier.padding(end = 10.dp),
            )
            YomuTogglePill(
                checked = s.useSystemBrightness,
                onCheckedChange = { onUpdateSettings(s.copy(useSystemBrightness = it)) },
                modifier = Modifier.semantics { contentDescription = "Use system brightness" },
            )
        }
        if (!s.useSystemBrightness) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(StackIcons.BrightnessLow, contentDescription = null, tint = stack.muted, modifier = Modifier.size(18.dp))
                ReaderSlider(
                    fraction = s.brightness,
                    onSeek = onCommitBrightness,
                    onDrag = onPreviewBrightness,
                    modifier = Modifier.weight(1f),
                    contentDescription = "Brightness",
                )
                Icon(StackIcons.BrightnessHigh, contentDescription = null, tint = stack.muted, modifier = Modifier.size(22.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Extra dim",
                color = stack.body,
                style = StackType.BodySmall.copy(fontSize = 14.sp),
                modifier = Modifier.weight(1f),
            )
            ReaderValuePill(
                text = if (s.dimLevel > 0f) s.dimLevel.asPercent() else "Off",
                onReset = if (s.dimLevel > 0f) ({ onCommitDim(0f) }) else null,
                resetLabel = "Turn off extra dim",
            )
        }
        ReaderSlider(
            fraction = s.dimLevel,
            onSeek = onCommitDim,
            onDrag = onPreviewDim,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Extra dim",
        )

        // Footer + screen toggles, rendered by the SAME shared composable as the global Reading
        // Defaults (ReaderChromeToggles) — a single renderer so the two surfaces can never drift out
        // of sync and no option is missed in one place but not the other.
        Box(Modifier.padding(top = YomuTheme.space.sm)) {
            ReaderChromeToggles(settings = s, onUpdateSettings = onUpdateSettings, compact = true)
        }
    }
}

/**
 * Quick per-book display overrides: theme tiles, layout, font, size, alignment and the three
 * typography sliders, then the reset back to the Reading Defaults.
 */
@Composable
private fun DisplayTab(
    state: ReaderUiState,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
) {
    val stack = YomuTheme.stack
    val s = state.experience.settings
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSectionLabel("Theme")
        Box(Modifier.padding(top = 10.dp)) { ReaderThemeRow(s, onUpdateSettings, compact = true) }
        if (s.theme == ReaderThemeMode.Custom) {
            SheetCustomThemes(s, state.customThemes, onOpenCustomTheme, onApplyCustomTheme)
        }
        ReaderSheetRow("Layout", Modifier.padding(top = 14.dp)) {
            YomuConnectedChoiceGroup(
                options = ReaderLayout.entries,
                selected = s.layout,
                label = { it.name },
                onSelect = { onUpdateSettings(s.copy(layout = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        ReaderSectionLabel("Font", Modifier.padding(top = YomuTheme.space.sm))
        // In-reader: switch among installed fonts; adding/removing is on the Reading Defaults screen.
        Box(Modifier.padding(top = 4.dp)) {
            ReaderFontRow(s, onUpdateSettings, customFonts = state.installedFonts)
        }
        SheetFontSizeRow(s, onUpdateSettings)
        ReaderSheetRow("Align") {
            YomuConnectedChoiceGroup(
                options = ReaderTextAlign.entries,
                selected = s.textAlign,
                label = { it.label },
                onSelect = { onUpdateSettings(s.copy(textAlign = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        SheetAutoSliderRow(
            label = "Line height",
            shortLabel = "Line height",
            value = s.lineHeight,
            min = ReaderSettings.MIN_LINE_HEIGHT,
            max = ReaderSettings.MAX_LINE_HEIGHT,
            default = ReaderSettings.DEFAULT_LINE_HEIGHT,
            step = ReaderSettings.LINE_HEIGHT_STEP,
            valueText = { String.format(Locale.US, "%.2f", it) },
            onChange = { onUpdateSettings(s.copy(lineHeight = it)) },
        )
        SheetAutoSliderRow(
            label = "Page margins",
            shortLabel = "Margins",
            value = s.pageMargins,
            min = ReaderSettings.MIN_PAGE_MARGINS,
            max = ReaderSettings.MAX_PAGE_MARGINS,
            default = ReaderSettings.DEFAULT_PAGE_MARGINS,
            step = ReaderSettings.PAGE_MARGINS_STEP,
            valueText = { String.format(Locale.US, "%.1f", it) },
            onChange = { onUpdateSettings(s.copy(pageMargins = it)) },
        )
        SheetAutoSliderRow(
            label = "Paragraph spacing",
            shortLabel = "Paragraphs",
            value = s.paragraphSpacing,
            min = ReaderSettings.MIN_PARAGRAPH_SPACING,
            max = ReaderSettings.MAX_PARAGRAPH_SPACING,
            default = ReaderSettings.DEFAULT_PARAGRAPH_SPACING,
            step = ReaderSettings.PARAGRAPH_SPACING_STEP,
            valueText = { String.format(Locale.US, "%.1f", it) },
            onChange = { onUpdateSettings(s.copy(paragraphSpacing = it)) },
        )

        // Drop this book's overrides and follow the global Reading Defaults again.
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .yomuPressable(onClick = onResetSettings),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Reset this book to defaults",
                color = stack.muted,
                style = StackType.Label.copy(fontSize = 14.sp),
            )
        }
    }
}

/** Size: an outlined − and + around the current scale; tapping the value returns it to 100%. */
@Composable
private fun SheetFontSizeRow(s: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    val stack = YomuTheme.stack
    val min = ReaderSettings.MIN_FONT_SCALE
    val max = ReaderSettings.MAX_FONT_SCALE
    val step = ReaderSettings.FONT_SCALE_STEP
    fun snapScale(value: Float): Float = (round(value / step) * step).coerceIn(min, max)
    val atDefault = s.fontScale == ReaderSettings.DEFAULT_FONT_SCALE
    ReaderSheetRow("Size", Modifier.padding(top = 6.dp)) {
        SheetOutlinedStep(StackIcons.Remove, "Smaller text") { onUpdateSettings(s.copy(fontScale = snapScale(s.fontScale - step))) }
        Text(
            text = "${(s.fontScale * 100).roundToInt()}%",
            color = stack.ink,
            style = StackType.Heading.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (atDefault) {
                        Modifier
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .yomuPressable(onClick = { onUpdateSettings(s.copy(fontScale = ReaderSettings.DEFAULT_FONT_SCALE)) })
                    },
                )
                .semantics {
                    contentDescription = "Font size ${(s.fontScale * 100).roundToInt()}%" +
                        if (atDefault) "" else ". Reset font size"
                },
        )
        SheetOutlinedStep(StackIcons.Add, "Larger text") { onUpdateSettings(s.copy(fontScale = snapScale(s.fontScale + step))) }
    }
}

@Composable
private fun SheetOutlinedStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = stack.ink),
        modifier = Modifier.size(44.dp).border(1.5.dp, stack.track, CircleShape),
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun SheetIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = YomuTheme.stack.ink),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * A typography slider row: label, slider, and a value tag that reads "Auto" at the engine default;
 * tapping a concrete value returns it to Auto. Seeks snap to [step].
 */
@Composable
private fun SheetAutoSliderRow(
    label: String,
    shortLabel: String,
    value: Float?,
    min: Float,
    max: Float,
    default: Float,
    step: Float,
    valueText: (Float) -> String,
    onChange: (Float?) -> Unit,
) {
    fun snapValue(v: Float): Float = (round(v / step) * step).coerceIn(min, max)
    val current = value ?: default
    ReaderSheetRow(shortLabel) {
        ReaderSlider(
            fraction = ((current - min) / (max - min)).coerceIn(0f, 1f),
            onSeek = { onChange(snapValue(min + it * (max - min))) },
            contentDescription = label,
            modifier = Modifier.weight(1f),
        )
        ReaderValuePill(
            text = if (value == null) "Auto" else valueText(value),
            onReset = if (value != null) ({ onChange(null) }) else null,
            resetLabel = "Reset $label to auto",
        )
    }
}

/** The Custom theme's saved palettes as chips, then the way into the theme editor. */
@Composable
private fun SheetCustomThemes(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(top = YomuTheme.space.sm), verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
        if (customThemes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            ) {
                customThemes.forEach { theme ->
                    SavedThemeChip(
                        theme = theme,
                        selected = settings.customBackground == theme.background && settings.customText == theme.text,
                        onApply = { onApplyCustomTheme(theme) },
                    )
                }
            }
        }
        ReaderPillButton(
            text = "Customise theme",
            onClick = onOpenCustomTheme,
            style = ReaderPillStyle.Secondary,
            icon = StackIcons.Palette,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A saved page theme: an "Aa" disc in its colours and its name, with an optional delete. */
@Composable
private fun SavedThemeChip(
    theme: CustomReaderTheme,
    selected: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val serif = rememberReadingSerif()
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(stack.raised)
            .then(if (selected) Modifier.border(2.dp, stack.ink, CircleShape) else Modifier)
            .yomuPressable(onClick = onApply, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(start = 6.dp, end = if (onDelete != null) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(theme.background)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = Color(theme.text), style = StackType.Label.copy(fontFamily = serif))
        }
        Text(text = theme.name, color = stack.ink, style = StackType.Label, maxLines = 1)
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = stack.muted),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(StackIcons.Close, contentDescription = "Delete ${theme.name}", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * A labelled slider for an optional numeric setting. [value] of null shows "Auto" (engine default)
 * and parks the thumb at [default]; tapping the value chip resets back to Auto. Seeks snap to [step].
 */
@Composable
internal fun AutoSlider(
    label: String,
    value: Float?,
    min: Float,
    max: Float,
    default: Float,
    step: Float,
    valueText: (Float) -> String,
    onChange: (Float?) -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.Single,
    compact: Boolean = false,
) {
    fun snap(v: Float): Float = (round(v / step) * step).coerceIn(min, max)
    val auto = value == null
    val current = value ?: default
    YomuSettingContainer(position = position, dense = compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // "Auto" when at the engine default; tap a concrete value to reset back to Auto.
            YomuValueChip(
                text = if (auto) "Auto" else valueText(value),
                customized = !auto,
                onReset = { onChange(null) },
                resetLabel = "Reset $label to auto",
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            RoundIcon(StackIcons.Remove, "Decrease $label", compact = compact) { onChange(snap(current - step)) }
            ReaderSlider(
                fraction = ((current - min) / (max - min)).coerceIn(0f, 1f),
                onSeek = { onChange(snap(min + it * (max - min))) },
                contentDescription = label,
                modifier = Modifier.weight(1f),
            )
            RoundIcon(StackIcons.Add, "Increase $label", compact = compact) { onChange(snap(current + step)) }
        }
    }
}

private enum class CustomColorTarget(val label: String) { Background("Background"), Text("Text") }

/**
 * The custom theme editor: a live preview of the page, one colour picker for the background or the
 * text (applied to the page as you edit), saving the pair as a named palette, and the saved list.
 */
@Composable
internal fun CustomThemeSheet(
    visible: Boolean,
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onDismiss: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onSave: (String) -> Unit,
    onApply: (CustomReaderTheme) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(CustomColorTarget.Background) }
    val focusManager = LocalFocusManager.current
    fun save() {
        if (name.isBlank()) return
        onSave(name.trim())
        name = ""
        focusManager.clearFocus()
    }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        val serif = rememberReadingSerif()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSheetHeader(
                title = "Custom theme",
                subtitle = "Changes apply to the page as you edit.",
                onClose = dismiss,
            )
            Text(
                text = "Mole lay stretched on the bank, still panting from the stress of the fierce day…",
                color = Color(settings.textArgb),
                style = StackType.Body.copy(fontFamily = serif, lineHeight = 23.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(settings.backgroundArgb))
                    .border(1.dp, YomuTheme.stack.rule, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .semantics { contentDescription = "Preview" },
            )
            YomuConnectedChoiceGroup(
                options = CustomColorTarget.entries,
                selected = target,
                label = { it.label },
                onSelect = { target = it },
                modifier = Modifier.fillMaxWidth(),
            )
            when (target) {
                CustomColorTarget.Background -> YomuColorPicker(
                    color = Color(settings.backgroundArgb),
                    // customBackground is an ARGB Long; toArgb() yields an Int, mask to keep it unsigned.
                    onColorChange = { color ->
                        onUpdateSettings(settings.copy(customBackground = color.toArgb().toLong() and 0xFFFFFFFFL))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                CustomColorTarget.Text -> YomuColorPicker(
                    color = Color(settings.textArgb),
                    onColorChange = { color ->
                        onUpdateSettings(settings.copy(customText = color.toArgb().toLong() and 0xFFFFFFFFL))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ReaderSectionLabel("Save as a palette", Modifier.padding(top = YomuTheme.space.xs))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                YomuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Theme name",
                    placeholder = "e.g. Harbour night",
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    onImeAction = ::save,
                    modifier = Modifier.weight(1f),
                )
                ReaderPillButton(
                    text = "Save",
                    onClick = ::save,
                    enabled = name.isNotBlank(),
                    height = 52.dp,
                )
            }

            if (customThemes.isNotEmpty()) {
                ReaderSectionLabel("Saved", Modifier.padding(top = YomuTheme.space.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                    verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                ) {
                    customThemes.forEach { theme ->
                        SavedThemeChip(
                            theme = theme,
                            selected = settings.customBackground == theme.background && settings.customText == theme.text,
                            onApply = { onApply(theme) },
                            onDelete = { onDelete(theme.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Font picker chip whose label is rendered in that bundled font as a live preview. */
@Composable
internal fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
    val assets = LocalContext.current.assets
    val family = remember(font) {
        ComposeFontFamily(Font(path = "fonts/${font.name}-Regular.ttf", assetManager = assets))
    }
    ReaderFontFilterChip(label = font.displayName, family = family, selected = selected, onClick = onClick)
}

/** Font picker chip for a user-installed custom font, previewed in that font (loaded from its file). */
@Composable
internal fun CustomFontChip(font: CustomFontRef, selected: Boolean, onClick: () -> Unit) {
    val family = remember(font.regularPath) {
        runCatching { ComposeFontFamily(Font(file = File(font.regularPath))) }.getOrNull()
    }
    ReaderFontFilterChip(label = font.family, family = family, selected = selected, onClick = onClick)
}

@Composable
private fun ReaderFontFilterChip(
    label: String,
    family: ComposeFontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ReaderChip(
        label = label,
        selected = selected,
        onClick = onClick,
        role = Role.RadioButton,
        labelStyle = StackType.Body.copy(fontFamily = family ?: StackType.Body.fontFamily),
    )
}

/** "+ Add font" chip in the font picker that opens the font library (Reading Defaults only). */
@Composable
internal fun AddFontChip(onClick: () -> Unit) {
    ReaderChip(
        label = "Add font",
        selected = false,
        onClick = onClick,
        role = Role.Button,
        leadingIcon = StackIcons.Add,
        dashed = true,
    )
}

@Composable
internal fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    if (compact) {
        // A quiet step button: the slider carries the emphasis; a tonal circle on each side is noise.
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
        return
    }
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Material 3 slider compatibility wrapper for reader seeking and typography controls. */
@Composable
internal fun ReaderSlider(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDrag: ((Float) -> Unit)? = null,
    markerFraction: Float? = null,
    enabled: Boolean = true,
    snapPoints: Int = 0,
    contentDescription: String = "Slider",
) {
    var pending by remember(fraction) { mutableStateOf(fraction.coerceIn(0f, 1f)) }
    val haptics = LocalHapticFeedback.current
    YomuSlider(
        value = pending,
        onValueChange = {
            val raw = it.coerceIn(0f, 1f)
            val next = if (snapPoints > 1) {
                (raw * (snapPoints - 1)).roundToInt().toFloat() / (snapPoints - 1)
            } else {
                raw
            }
            // A light tick each time a snapped slider crosses a stop (chapters, steps).
            if (snapPoints in 2..MaxTickedSnapPoints && next != pending) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            pending = next
            onDrag?.invoke(pending)
        },
        onValueChangeFinished = { onSeek(pending) },
        enabled = enabled,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    )
}

/** Above this many stops, per-stop ticks become a buzz rather than feedback. */
private const val MaxTickedSnapPoints = 60
