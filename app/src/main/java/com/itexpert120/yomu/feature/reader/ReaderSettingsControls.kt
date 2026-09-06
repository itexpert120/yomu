package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuSingleChoiceSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.yomuPressable
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

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
}

/** Page themes share the grouped settings treatment in defaults and reading controls. */
@Composable
internal fun ReaderThemeRow(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
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
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Aa", modifier = Modifier.padding(10.dp), style = YomuTheme.type.body)
                    }
                },
            ) {
                androidx.compose.material3.RadioButton(selected = settings.theme == mode, onClick = null)
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
        SectionLabel("Saved")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    YomuButton(
        text = "Customise theme",
        onClick = onOpenCustomTheme,
        emphasis = YomuButtonEmphasis.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ReaderLayoutControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    val modes = ReaderLayout.entries
    YomuSingleChoiceSegmentedControl(
        options = modes.map { it.name },
        selectedIndex = modes.indexOf(settings.layout),
        onSelected = { onUpdateSettings(settings.copy(layout = modes[it])) },
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
) {
    val min = ReaderSettings.MIN_FONT_SCALE
    val max = ReaderSettings.MAX_FONT_SCALE
    val step = ReaderSettings.FONT_SCALE_STEP
    fun snap(value: Float): Float = (round(value / step) * step).coerceIn(min, max)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Font size",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier.weight(1f),
        )
        val atDefault = settings.fontScale == ReaderSettings.DEFAULT_FONT_SCALE
        Text(
            text = "${(settings.fontScale * 100).roundToInt()}%",
            color = if (atDefault) YomuTheme.colors.textMuted else YomuTheme.colors.accent,
            style = YomuTheme.type.mono,
            modifier = Modifier
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .yomuPressable(
                    enabled = !atDefault,
                    onClick = { onUpdateSettings(settings.copy(fontScale = ReaderSettings.DEFAULT_FONT_SCALE)) },
                    pressedScale = 1f,
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundIcon(Icons.Rounded.Remove, "Decrease font size") {
            onUpdateSettings(settings.copy(fontScale = snap(settings.fontScale - step)))
        }
        ReaderSlider(
            fraction = (settings.fontScale - min) / (max - min),
            onSeek = { onUpdateSettings(settings.copy(fontScale = snap(min + it * (max - min)))) },
            modifier = Modifier.weight(1f),
        )
        RoundIcon(Icons.Rounded.Add, "Increase font size") {
            onUpdateSettings(settings.copy(fontScale = snap(settings.fontScale + step)))
        }
    }
}

@Composable
internal fun ReaderTextAlignControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    val aligns = ReaderTextAlign.entries
    YomuSingleChoiceSegmentedControl(
        options = aligns.map { it.label },
        selectedIndex = aligns.indexOf(settings.textAlign),
        onSelected = { onUpdateSettings(settings.copy(textAlign = aligns[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Line height, page margins and paragraph spacing — each with an "Auto" default. */
@Composable
internal fun ReaderTypographySliders(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
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
    )
}

@Composable
internal fun ReaderChromeToggles(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    YomuSettingList {
        YomuSettingRow(title = "Show footer", position = YomuSettingPosition.First) {
            YomuTogglePill(
                checked = settings.showFooter,
                onCheckedChange = { onUpdateSettings(settings.copy(showFooter = it)) },
            )
        }
        // The per-item toggles stay visible but disable when the footer is off, so the layout doesn't
        // jump and it's clear what the footer would contain.
        YomuSettingRow(title = "Battery", position = YomuSettingPosition.Middle, enabled = settings.showFooter) {
            YomuTogglePill(
                checked = settings.footerShowBattery,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowBattery = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(title = "Clock", position = YomuSettingPosition.Middle, enabled = settings.showFooter) {
            YomuTogglePill(
                checked = settings.footerShowClock,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowClock = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(title = "Reading progress", position = YomuSettingPosition.Middle, enabled = settings.showFooter) {
            YomuTogglePill(
                checked = settings.footerShowProgress,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowProgress = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(title = "Chapter remaining", position = YomuSettingPosition.Middle, enabled = settings.showFooter) {
            YomuTogglePill(
                checked = settings.footerShowPagesLeft,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowPagesLeft = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(title = "Keep screen on", position = YomuSettingPosition.Middle) {
            YomuTogglePill(
                checked = settings.keepScreenOn,
                onCheckedChange = { onUpdateSettings(settings.copy(keepScreenOn = it)) },
            )
        }
        // The scrollbar only exists in scroll mode (a native WebView scroll), so disable the row in paged.
        YomuSettingRow(title = "Show scrollbar", position = YomuSettingPosition.Middle, enabled = settings.layout == ReaderLayout.Scroll) {
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
        ) {
            YomuTogglePill(
                checked = settings.immersiveChrome,
                onCheckedChange = { onUpdateSettings(settings.copy(immersiveChrome = it)) },
            )
        }
    }
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            ReaderFontSizeControl(settings, onUpdateSettings)
            ReaderTextAlignControl(settings, onUpdateSettings)
            ReaderTypographySliders(settings, onUpdateSettings)
        }

        YomuSettingGroup(
            title = "Reader chrome",
            subtitle = "Keep the controls and context you want close at hand.",
        ) {
            ReaderChromeToggles(settings, onUpdateSettings)
        }
    }
}
