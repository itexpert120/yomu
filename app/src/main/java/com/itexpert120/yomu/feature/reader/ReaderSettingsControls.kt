package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSelectableChip
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuValueChip
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.model.ReaderThemeMode
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Page themes share the grouped settings treatment in defaults and reading controls. [compact]
 * renders a single row of "Aa" tiles instead of one list row per theme (used by the reader sheet).
 */
@Composable
internal fun ReaderThemeRow(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        ReaderThemeTiles(settings, onUpdateSettings)
        return
    }
    YomuSettingList(modifier = Modifier.selectableGroup()) {
        ReaderThemeMode.entries.forEachIndexed { index, mode ->
            val palette = settings.copy(theme = mode)
            YomuSettingRow(
                title = mode.name,
                selected = settings.theme == mode,
                role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = { onUpdateSettings(palette) },
                position = when (index) {
                    0 -> YomuSettingPosition.First
                    ReaderThemeMode.entries.lastIndex -> YomuSettingPosition.Last
                    else -> YomuSettingPosition.Middle
                },
                leadingContent = {
                    androidx.compose.material3.Surface(
                        color = Color(palette.backgroundArgb),
                        contentColor = Color(palette.textArgb),
                        shape = YomuShapeTokens.tile,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text("Aa", modifier = Modifier.padding(YomuTheme.space.sm), style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                },
            ) {
                androidx.compose.material3.RadioButton(selected = settings.theme == mode, onClick = null)
            }
        }
    }
}

@Composable
private fun ReaderThemeTiles(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        ReaderThemeMode.entries.forEach { mode ->
            val palette = settings.copy(theme = mode)
            val selected = settings.theme == mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(YomuShapeTokens.tile)
                    .selectable(
                        selected = selected,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onUpdateSettings(palette) },
                    )
                    .padding(vertical = YomuTheme.space.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                androidx.compose.material3.Surface(
                    color = Color(palette.backgroundArgb),
                    contentColor = Color(palette.textArgb),
                    shape = YomuShapeTokens.tile,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aa", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
                Text(
                    text = mode.name,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Saved custom palettes + the "Customise" entry; only meaningful when the Custom theme is active. */
@Composable
internal fun ReaderCustomThemeRow(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
) {
    if (customThemes.isNotEmpty()) {
        YomuSectionLabel("Saved")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            customThemes.forEach { theme ->
                YomuColorSwatch(
                    name = theme.name,
                    color = Color(theme.background),
                    selected = settings.customBackground == theme.background && settings.customText == theme.text,
                    onClick = { onApplyCustomTheme(theme) },
                )
            }
        }
    }
    FilledTonalButton(
        onClick = onOpenCustomTheme,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Customise theme")
    }
}

@Composable
internal fun ReaderLayoutControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    YomuConnectedChoiceGroup(
        options = ReaderLayout.entries,
        selected = settings.layout,
        label = { it.name },
        icon = { it.icon },
        onSelect = { onUpdateSettings(settings.copy(layout = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ReaderFontRow(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    customFonts: List<CustomFontRef> = emptyList(),
    onManageFonts: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        ReaderFont.entries.forEach { font ->
            FontChip(
                font = font,
                // A bundled font is selected only when no custom font is active.
                selected = settings.customFont == null && settings.font == font,
                onClick = { onUpdateSettings(settings.copy(font = font, customFont = null)) },
            )
        }
        customFonts.forEach { custom ->
            CustomFontChip(
                font = custom,
                selected = settings.customFont?.family == custom.family,
                onClick = { onUpdateSettings(settings.copy(customFont = custom)) },
            )
        }
        if (onManageFonts != null) AddFontChip(onClick = onManageFonts)
    }
}

@Composable
internal fun ReaderFontSizeControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.First,
    compact: Boolean = false,
) {
    val min = ReaderSettings.MIN_FONT_SCALE
    val max = ReaderSettings.MAX_FONT_SCALE
    val step = ReaderSettings.FONT_SCALE_STEP
    fun snap(value: Float): Float = (round(value / step) * step).coerceIn(min, max)
    val atDefault = settings.fontScale == ReaderSettings.DEFAULT_FONT_SCALE
    YomuSettingContainer(position = position, dense = compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Font size",
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            YomuValueChip(
                text = "${(settings.fontScale * 100).roundToInt()}%",
                customized = !atDefault,
                onReset = { onUpdateSettings(settings.copy(fontScale = ReaderSettings.DEFAULT_FONT_SCALE)) },
                resetLabel = "Reset font size",
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            RoundIcon(Icons.Rounded.TextDecrease, "Decrease font size", compact = compact) {
                onUpdateSettings(settings.copy(fontScale = snap(settings.fontScale - step)))
            }
            ReaderSlider(
                fraction = (settings.fontScale - min) / (max - min),
                onSeek = { onUpdateSettings(settings.copy(fontScale = snap(min + it * (max - min)))) },
                modifier = Modifier.weight(1f),
                contentDescription = "Font size",
            )
            RoundIcon(Icons.Rounded.TextIncrease, "Increase font size", compact = compact) {
                onUpdateSettings(settings.copy(fontScale = snap(settings.fontScale + step)))
            }
        }
    }
}

@Composable
internal fun ReaderTextAlignControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    YomuConnectedChoiceGroup(
        options = ReaderTextAlign.entries,
        selected = settings.textAlign,
        label = { it.label },
        icon = { it.icon },
        onSelect = { onUpdateSettings(settings.copy(textAlign = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val ReaderLayout.icon: ImageVector
    get() = when (this) {
        ReaderLayout.Scroll -> Icons.Rounded.SwipeVertical
        ReaderLayout.Paged -> Icons.Rounded.AutoStories
    }

private val ReaderTextAlign.icon: ImageVector
    get() = when (this) {
        // Auto keeps the book's own alignment, so it shows ragged publisher text.
        ReaderTextAlign.Default -> Icons.AutoMirrored.Rounded.Notes
        ReaderTextAlign.Left -> Icons.AutoMirrored.Rounded.FormatAlignLeft
        ReaderTextAlign.Justify -> Icons.Rounded.FormatAlignJustify
    }

/** Icons shared by the list and chip forms of [ReaderChromeToggles]. */
private object ChromeIcons {
    val Footer = Icons.Rounded.CallToAction
    val Battery = Icons.Rounded.BatteryFull
    val Clock = Icons.Rounded.Schedule
    val Progress = Icons.Rounded.Percent
    val ChapterLeft = Icons.Rounded.HourglassBottom
    val KeepOn = Icons.Rounded.Lightbulb
    val Scrollbar = Icons.Rounded.Height
    val Immersive = Icons.Rounded.Fullscreen
}

@Composable
private fun ChromeRowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null)
}

/** Line height, page margins and paragraph spacing — each with an "Auto" default. */
@Composable
internal fun ReaderTypographySliders(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    compact: Boolean = false,
) {
    AutoSlider(
        label = "Line height",
        value = settings.lineHeight,
        min = ReaderSettings.MIN_LINE_HEIGHT,
        max = ReaderSettings.MAX_LINE_HEIGHT,
        default = ReaderSettings.DEFAULT_LINE_HEIGHT,
        step = ReaderSettings.LINE_HEIGHT_STEP,
        valueText = { String.format(Locale.US, "%.2f", it) },
        onChange = { onUpdateSettings(settings.copy(lineHeight = it)) },
        position = YomuSettingPosition.Middle,
        compact = compact,
    )
    AutoSlider(
        label = "Page margins",
        value = settings.pageMargins,
        min = ReaderSettings.MIN_PAGE_MARGINS,
        max = ReaderSettings.MAX_PAGE_MARGINS,
        default = ReaderSettings.DEFAULT_PAGE_MARGINS,
        step = ReaderSettings.PAGE_MARGINS_STEP,
        valueText = { String.format(Locale.US, "%.1f", it) },
        onChange = { onUpdateSettings(settings.copy(pageMargins = it)) },
        position = YomuSettingPosition.Middle,
        compact = compact,
    )
    AutoSlider(
        label = "Paragraph spacing",
        value = settings.paragraphSpacing,
        min = ReaderSettings.MIN_PARAGRAPH_SPACING,
        max = ReaderSettings.MAX_PARAGRAPH_SPACING,
        default = ReaderSettings.DEFAULT_PARAGRAPH_SPACING,
        step = ReaderSettings.PARAGRAPH_SPACING_STEP,
        valueText = { String.format(Locale.US, "%.1f", it) },
        onChange = { onUpdateSettings(settings.copy(paragraphSpacing = it)) },
        position = YomuSettingPosition.Last,
        compact = compact,
    )
}

@Composable
internal fun ReaderChromeToggles(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        CompactChromeToggles(settings, onUpdateSettings)
        return
    }
    YomuSettingList {
        YomuSettingRow(
            title = "Show footer",
            position = YomuSettingPosition.First,
            leadingContent = { ChromeRowIcon(ChromeIcons.Footer) },
        ) {
            YomuTogglePill(
                checked = settings.showFooter,
                onCheckedChange = { onUpdateSettings(settings.copy(showFooter = it)) },
            )
        }
        // The per-item toggles stay visible but disable when the footer is off, so the layout doesn't
        // jump and it's clear what the footer would contain.
        // Immersive mode only reveals the footer alongside the system status bar, which already shows
        // battery and time, so those two items can never appear there and their toggles go away.
        AnimatedVisibility(
            visible = !settings.immersiveChrome,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            YomuSettingList {
                YomuSettingRow(
                    title = "Battery",
                    position = YomuSettingPosition.Middle,
                    enabled = settings.showFooter,
                    leadingContent = { ChromeRowIcon(ChromeIcons.Battery) },
                ) {
                    YomuTogglePill(
                        checked = settings.footerShowBattery,
                        onCheckedChange = { onUpdateSettings(settings.copy(footerShowBattery = it)) },
                        enabled = settings.showFooter,
                    )
                }
                YomuSettingRow(
                    title = "Clock",
                    position = YomuSettingPosition.Middle,
                    enabled = settings.showFooter,
                    leadingContent = { ChromeRowIcon(ChromeIcons.Clock) },
                ) {
                    YomuTogglePill(
                        checked = settings.footerShowClock,
                        onCheckedChange = { onUpdateSettings(settings.copy(footerShowClock = it)) },
                        enabled = settings.showFooter,
                    )
                }
            }
        }
        YomuSettingRow(
            title = "Reading progress",
            position = YomuSettingPosition.Middle,
            enabled = settings.showFooter,
            leadingContent = { ChromeRowIcon(ChromeIcons.Progress) },
        ) {
            YomuTogglePill(
                checked = settings.footerShowProgress,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowProgress = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(
            title = "Chapter remaining",
            position = YomuSettingPosition.Middle,
            enabled = settings.showFooter,
            leadingContent = { ChromeRowIcon(ChromeIcons.ChapterLeft) },
        ) {
            YomuTogglePill(
                checked = settings.footerShowPagesLeft,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowPagesLeft = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(
            title = "Keep screen on",
            position = YomuSettingPosition.Middle,
            leadingContent = { ChromeRowIcon(ChromeIcons.KeepOn) },
        ) {
            YomuTogglePill(
                checked = settings.keepScreenOn,
                onCheckedChange = { onUpdateSettings(settings.copy(keepScreenOn = it)) },
            )
        }
        // The scrollbar only exists in scroll mode (a native WebView scroll), so disable the row in paged.
        YomuSettingRow(
            title = "Show scrollbar",
            position = YomuSettingPosition.Middle,
            enabled = settings.layout == ReaderLayout.Scroll,
            leadingContent = { ChromeRowIcon(ChromeIcons.Scrollbar) },
        ) {
            YomuTogglePill(
                checked = settings.showScrollbar,
                onCheckedChange = { onUpdateSettings(settings.copy(showScrollbar = it)) },
                enabled = settings.layout == ReaderLayout.Scroll,
            )
        }
        YomuSettingRow(
            title = "Immersive",
            position = YomuSettingPosition.Last,
            subtitle = "Hide the bars on tap for a full-screen page",
            leadingContent = { ChromeRowIcon(ChromeIcons.Immersive) },
        ) {
            YomuTogglePill(
                checked = settings.immersiveChrome,
                onCheckedChange = { onUpdateSettings(settings.copy(immersiveChrome = it)) },
            )
        }
    }
}

/**
 * The same options as [ReaderChromeToggles], as chips: the footer switch with its contents beneath,
 * then the screen behaviours. Keeps the in-reader sheet short without dropping any option.
 */
@Composable
private fun CompactChromeToggles(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    YomuSettingList {
        YomuSettingContainer(position = YomuSettingPosition.First, dense = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Footer",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                YomuTogglePill(
                    checked = settings.showFooter,
                    onCheckedChange = { onUpdateSettings(settings.copy(showFooter = it)) },
                )
            }
            // Items stay visible but disable when the footer is off, so the layout doesn't jump.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                // Immersive mode never shows these in the footer (the status bar has them instead).
                if (!settings.immersiveChrome) {
                    ToggleChip("Battery", ChromeIcons.Battery, settings.footerShowBattery, settings.showFooter) {
                        onUpdateSettings(settings.copy(footerShowBattery = it))
                    }
                    ToggleChip("Clock", ChromeIcons.Clock, settings.footerShowClock, settings.showFooter) {
                        onUpdateSettings(settings.copy(footerShowClock = it))
                    }
                }
                ToggleChip("Progress", ChromeIcons.Progress, settings.footerShowProgress, settings.showFooter) {
                    onUpdateSettings(settings.copy(footerShowProgress = it))
                }
                ToggleChip("Chapter left", ChromeIcons.ChapterLeft, settings.footerShowPagesLeft, settings.showFooter) {
                    onUpdateSettings(settings.copy(footerShowPagesLeft = it))
                }
            }
        }
        YomuSettingContainer(position = YomuSettingPosition.Last, dense = true) {
            Text(text = "Screen", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                ToggleChip("Keep on", ChromeIcons.KeepOn, settings.keepScreenOn) {
                    onUpdateSettings(settings.copy(keepScreenOn = it))
                }
                // The scrollbar only exists in scroll mode (a native WebView scroll).
                ToggleChip("Scrollbar", ChromeIcons.Scrollbar, settings.showScrollbar, settings.layout == ReaderLayout.Scroll) {
                    onUpdateSettings(settings.copy(showScrollbar = it))
                }
                ToggleChip("Immersive", ChromeIcons.Immersive, settings.immersiveChrome) {
                    onUpdateSettings(settings.copy(immersiveChrome = it))
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    YomuSelectableChip(
        label = label,
        icon = icon,
        selected = checked,
        onSelectedChange = onCheckedChange,
        enabled = enabled,
    )
}

/**
 * The full set of reader preferences (theme, typography, chrome) used by the app Settings screen.
 * Brightness/extra-dim are intentionally excluded — they're contextual, edited in-reader.
 */
@Composable
internal fun ReaderPreferenceControls(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
    customFonts: List<CustomFontRef> = emptyList(),
    onManageFonts: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
        YomuSettingGroup(
            title = "Theme",
            subtitle = "Choose the page mood that welcomes you into every book.",
        ) {
            ReaderThemeRow(settings, onUpdateSettings)
            if (settings.theme == ReaderThemeMode.Custom) {
                ReaderCustomThemeRow(settings, customThemes, onOpenCustomTheme, onApplyCustomTheme)
            }
        }

        YomuSettingGroup(
            title = "Layout",
            subtitle = "Decide how chapters move under your hand.",
        ) {
            ReaderLayoutControl(settings, onUpdateSettings)
        }

        YomuSettingGroup(
            title = "Typography",
            subtitle = "Tune the voice, scale, and breathing room of the page.",
        ) {
            ReaderFontRow(
                settings,
                onUpdateSettings,
                customFonts = customFonts,
                onManageFonts = onManageFonts,
            )
            ReaderTextAlignControl(settings, onUpdateSettings)
            YomuSettingList {
                ReaderFontSizeControl(settings, onUpdateSettings)
                ReaderTypographySliders(settings, onUpdateSettings)
            }
        }

        YomuSettingGroup(
            title = "Reader chrome",
            subtitle = "Keep the controls and context you want close at hand.",
        ) {
            ReaderChromeToggles(settings, onUpdateSettings)
        }
    }
}
