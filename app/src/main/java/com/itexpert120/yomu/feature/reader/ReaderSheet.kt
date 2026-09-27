package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuValueChip
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderTocItem
import java.io.File
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily

private enum class SheetTab(val label: String) { Controls("Controls"), Display("Display") }

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
    ) { _ ->
        Column(
            // Animate the height as tab content of differing size swaps in.
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            YomuConnectedChoiceGroup(
                options = SheetTab.entries,
                selected = tab,
                label = { it.label },
                icon = {
                    when (it) {
                        SheetTab.Controls -> Icons.Rounded.Tune
                        SheetTab.Display -> Icons.Rounded.TextFields
                    }
                },
                onSelect = { tab = it },
                height = ButtonDefaults.MediumContainerHeight,
            )
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    yomuContentSwap(forward = targetState.ordinal > initialState.ordinal)
                },
                label = "readerSheetTab",
            ) { current ->
                Box {
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
    val reading = state.experience
    val s = reading.settings
    val chapters = remember(reading.tableOfContents) {
        reading.tableOfContents.filter { it.locatorJson != null }
    }
    val exactChapterIndex = chapters.indexOfFirst { it.id == reading.currentHref }
    val currentChapterIndex = if (exactChapterIndex >= 0) {
        exactChapterIndex
    } else {
        chapters.indexOfFirst { it.resourceHref == reading.currentHref }.coerceAtLeast(0)
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YomuSectionLabel("Chapter")
        YomuSettingContainer {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundIcon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    "Previous chapter",
                    onPreviousChapter,
                )
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
                RoundIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next chapter", onNextChapter)
            }
            Text(
                text = chapters.getOrNull(previewChapterIndex)?.let { chapter ->
                    "${previewChapterIndex + 1} of ${chapters.size} · ${chapter.title}"
                } ?: "No chapters available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Brightness + extra dim are contextual to the current reading session, so they stay here.
        YomuSectionLabel("Brightness")
        YomuSettingList {
            YomuSettingRow(title = "Use system brightness", position = YomuSettingPosition.First) {
                YomuTogglePill(
                    checked = s.useSystemBrightness,
                    onCheckedChange = { onUpdateSettings(s.copy(useSystemBrightness = it)) },
                )
            }
            if (!s.useSystemBrightness) {
                YomuSettingContainer(position = YomuSettingPosition.Middle) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.BrightnessLow, contentDescription = null)
                        ReaderSlider(
                            fraction = s.brightness,
                            onSeek = onCommitBrightness,
                            onDrag = onPreviewBrightness,
                            modifier = Modifier.weight(1f),
                            contentDescription = "Brightness",
                        )
                        Icon(Icons.Rounded.BrightnessHigh, contentDescription = null)
                    }
                }
            }
            YomuSettingContainer(position = YomuSettingPosition.Last) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Extra dim",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    YomuValueChip(
                        text = "${(s.dimLevel * 100).roundToInt()}%",
                        customized = s.dimLevel > 0f,
                        onReset = { onCommitDim(0f) },
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
            }
        }

        // Footer + screen toggles, rendered by the SAME shared composable as the global Reading
        // Defaults (ReaderChromeToggles) — a single renderer so the two surfaces can never drift out
        // of sync and no option is missed in one place but not the other.
        YomuSectionLabel("Footer & screen")
        ReaderChromeToggles(settings = s, onUpdateSettings = onUpdateSettings)
    }
}

/**
 * Quick per-book display overrides. The comprehensive defaults (advanced typography, chrome, etc.)
 * live on the global Reading Defaults screen in Settings — this sheet stays lean.
 */
@Composable
private fun DisplayTab(
    state: ReaderUiState,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
) {
    val s = state.experience.settings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YomuSectionLabel("Theme · this book")
        ReaderThemeRow(s, onUpdateSettings)
        if (s.theme == ReaderThemeMode.Custom) {
            ReaderCustomThemeRow(s, state.customThemes, onOpenCustomTheme, onApplyCustomTheme)
        }
        YomuSectionLabel("Layout")
        ReaderLayoutControl(s, onUpdateSettings)
        YomuSectionLabel("Font")
        // In-reader: switch among installed fonts; adding/removing is on the Reading Defaults screen.
        ReaderFontRow(s, onUpdateSettings, customFonts = state.installedFonts)
        YomuSectionLabel("Text")
        ReaderTextAlignControl(s, onUpdateSettings)
        YomuSettingList {
            ReaderFontSizeControl(s, onUpdateSettings)
            ReaderTypographySliders(s, onUpdateSettings)
        }

        // Drop this book's overrides and follow the global Reading Defaults again.
        OutlinedButton(
            onClick = onResetSettings,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Reset this book to defaults")
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
) {
    fun snap(v: Float): Float = (round(v / step) * step).coerceIn(min, max)
    val auto = value == null
    val current = value ?: default
    YomuSettingContainer(position = position) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIcon(Icons.Rounded.Remove, "Decrease $label") { onChange(snap(current - step)) }
            ReaderSlider(
                fraction = ((current - min) / (max - min)).coerceIn(0f, 1f),
                // Tick marks the default value's position on the track.
                markerFraction = ((default - min) / (max - min)).coerceIn(0f, 1f),
                onSeek = { onChange(snap(min + it * (max - min))) },
                contentDescription = label,
                modifier = Modifier.weight(1f),
            )
            RoundIcon(Icons.Rounded.Add, "Increase $label") { onChange(snap(current + step)) }
        }
    }
}

/**
 * A separate bottom sheet for building a custom theme: live colour pickers (applied to the page as
 * you edit), a name field to save the current colours as a reusable palette, and the saved list.
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
    val focusManager = LocalFocusManager.current
    fun save() {
        if (name.isBlank()) return
        onSave(name.trim())
        name = ""
        focusManager.clearFocus()
    }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Custom theme",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = "Changes apply to the page as you edit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            YomuSectionLabel("Background colour")
            YomuSettingContainer {
                YomuColorPicker(
                    color = Color(settings.backgroundArgb),
                    // customBackground is an ARGB Long; toArgb() yields an Int, mask to keep it unsigned.
                    onColorChange = { color ->
                        onUpdateSettings(
                            settings.copy(
                                customBackground = color.toArgb().toLong() and 0xFFFFFFFFL,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            YomuSectionLabel("Text colour")
            YomuSettingContainer {
                YomuColorPicker(
                    color = Color(settings.textArgb),
                    onColorChange = { color ->
                        onUpdateSettings(
                            settings.copy(
                                customText = color.toArgb().toLong() and 0xFFFFFFFFL,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            YomuSectionLabel("Save as a palette")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YomuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Theme name",
                    placeholder = "e.g. Midnight",
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    onImeAction = ::save,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = ::save,
                    enabled = name.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text("Save")
                }
            }

            if (customThemes.isNotEmpty()) {
                YomuSectionLabel("Saved themes")
                YomuSettingList {
                    customThemes.forEachIndexed { index, theme ->
                        SavedThemeRow(
                            theme = theme,
                            position = yomuSettingPosition(index, customThemes.size),
                            onApply = { onApply(theme) },
                            onDelete = { onDelete(theme.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedThemeRow(
    theme: CustomReaderTheme,
    position: YomuSettingPosition,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    YomuSettingRow(
        title = theme.name,
        position = position,
        onClick = onApply,
        leadingContent = {
            // Preview: page colour with the text colour as an inner dot.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(theme.background))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(theme.text)),
                )
            }
        },
    ) {
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${theme.name}")
        }
    }
}

@Composable
internal fun TocSheetRow(item: ReaderTocItem, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .yomuPressable(onClick = onClick, pressedScale = 1f)
            // Indent by TOC depth.
            .padding(start = (12 + item.depth * 14).dp, top = 11.dp, bottom = 11.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.title,
            color = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (current) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.let { style ->
                    family?.let { style.copy(fontFamily = it) } ?: style
                },
            )
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

/** "+ Add" chip in the font picker that opens the font-library sheet (Reading Defaults only). */
@Composable
internal fun AddFontChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text("Add font") },
        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@Composable
internal fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
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
    Slider(
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
