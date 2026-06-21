package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
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

/** The five theme swatches in one even row. */
@Composable
internal fun ReaderThemeRow(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderThemeMode.entries.forEach { mode ->
            YomuColorSwatch(
                name = mode.name,
                color = Color(ReaderSettings().copy(theme = mode).backgroundArgb),
                selected = settings.theme == mode,
                onClick = { onUpdateSettings(settings.copy(theme = mode)) },
                modifier = Modifier.weight(1f),
            )
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
    onUpdateSettings: (ReaderSettings) -> Unit
) {
    val modes = ReaderLayout.entries
    YomuSegmentedControl(
        options = modes.map { it.name },
        selectedIndex = modes.indexOf(settings.layout),
        onSelected = { onUpdateSettings(settings.copy(layout = modes[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ReaderFontRow(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReaderFont.entries.forEach { font ->
            FontChip(
                font = font,
                selected = settings.font == font,
                onClick = { onUpdateSettings(settings.copy(font = font)) },
            )
        }
    }
}

@Composable
internal fun ReaderFontSizeControl(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !atDefault,
                    onClick = { onUpdateSettings(settings.copy(fontScale = ReaderSettings.DEFAULT_FONT_SCALE)) },
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
    onUpdateSettings: (ReaderSettings) -> Unit
) {
    val aligns = ReaderTextAlign.entries
    YomuSegmentedControl(
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
    onUpdateSettings: (ReaderSettings) -> Unit
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
    onUpdateSettings: (ReaderSettings) -> Unit
) {
    YomuSettingRow(
        title = "Tap edges to turn pages",
        subtitle = "Off: only chapter buttons navigate"
    ) {
        YomuTogglePill(
            checked = settings.tapNavigation,
            onCheckedChange = { onUpdateSettings(settings.copy(tapNavigation = it)) },
        )
    }
    YomuSettingRow(
        title = "Tap centre for controls",
        subtitle = "The top-bar button always works too"
    ) {
        YomuTogglePill(
            checked = settings.centerTapOpensSheet,
            onCheckedChange = { onUpdateSettings(settings.copy(centerTapOpensSheet = it)) },
        )
    }
    YomuSettingRow(title = "Show footer") {
        YomuTogglePill(
            checked = settings.showFooter,
            onCheckedChange = { onUpdateSettings(settings.copy(showFooter = it)) })
    }
    if (settings.showFooter) {
        YomuSettingRow(title = "Battery") {
            YomuTogglePill(
                checked = settings.footerShowBattery,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowBattery = it)) })
        }
        YomuSettingRow(title = "Clock") {
            YomuTogglePill(
                checked = settings.footerShowClock,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowClock = it)) })
        }
        YomuSettingRow(title = "Reading progress") {
            YomuTogglePill(
                checked = settings.footerShowProgress,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowProgress = it)) })
        }
    }
}

/**
 * The full set of reader preferences (theme, typography, chrome) used by the global Reading Defaults
 * screen. Brightness/extra-dim are intentionally excluded — they're contextual, edited in-reader.
 */
@Composable
internal fun ReaderPreferenceControls(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Theme")
        ReaderThemeRow(settings, onUpdateSettings)
        if (settings.theme == ReaderThemeMode.Custom) {
            ReaderCustomThemeRow(settings, customThemes, onOpenCustomTheme, onApplyCustomTheme)
        }

        SectionLabel("Layout")
        ReaderLayoutControl(settings, onUpdateSettings)

        SectionLabel("Font")
        ReaderFontRow(settings, onUpdateSettings)
        ReaderFontSizeControl(settings, onUpdateSettings)

        SectionLabel("Text")
        ReaderTextAlignControl(settings, onUpdateSettings)
        ReaderTypographySliders(settings, onUpdateSettings)

        SectionLabel("Chrome")
        ReaderChromeToggles(settings, onUpdateSettings)
    }
}
