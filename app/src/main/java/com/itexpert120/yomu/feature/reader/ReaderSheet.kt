package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.LocalYomuSheetBottomInset
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeRule
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode

/**
 * Reading settings: one sheet, ordered by how often each thing is reached for —
 * 1. the page theme, the font (with "Get fonts" at the end of the row) and the text size;
 * 2. the page: layout, alignment, line spacing, margins, paragraph spacing;
 * 3. light: brightness (or the system's) and extra dim;
 * 4. what shows on the page: the footer and its items, keep-on, scrollbar, immersive;
 * then the way back to the Reading defaults. Chapter moves live in the menu's own panel. The title
 * row stays put while the settings scroll under it.
 */
@Composable
internal fun ReaderControlsSheet(
    visible: Boolean,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    YomuBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        scrollable = false,
        showScrollEdgeShadow = false,
        horizontalContentPadding = 0.dp,
    ) { dismiss ->
        ReaderControlsSheetContent(
            state = state,
            dismiss = dismiss,
            onUpdateSettings = onUpdateSettings,
            onResetSettings = onResetSettings,
            onOpenCustomTheme = onOpenCustomTheme,
            onApplyCustomTheme = onApplyCustomTheme,
            onOpenFontLibrary = onOpenFontLibrary,
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
    dismiss: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    val s = state.experience.settings
    val gutter = YomuTheme.space.lg
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .readerSheetHeight(fraction = 0.7f, fill = false),
    ) {
        YomuSheetHeader(title = "Reading settings", onClose = dismiss, modifier = Modifier.padding(horizontal = gutter))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .yomuScrollEdgeRule(top = scroll.canScrollBackward)
                .verticalScroll(scroll)
                // Custom theme's palettes come and go; the sheet's height follows on the spring.
                .animateContentSize(if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap())
                .padding(top = YomuTheme.space.sm, bottom = YomuTheme.space.xs + LocalYomuSheetBottomInset.current),
        ) {
            Gutter {
                ReaderThemeRow(s, onUpdateSettings, compact = true)
                if (s.theme == ReaderThemeMode.Custom) {
                    ReaderCustomThemes(
                        settings = s,
                        customThemes = state.customThemes,
                        onOpenCustomTheme = onOpenCustomTheme,
                        onApplyCustomTheme = onApplyCustomTheme,
                        modifier = Modifier.padding(top = YomuTheme.space.sm),
                    )
                }
            }
            ReaderFontPicker(
                settings = s,
                onUpdateSettings = onUpdateSettings,
                customFonts = state.installedFonts,
                onGetFonts = onOpenFontLibrary,
                gutter = gutter,
                modifier = Modifier.padding(top = YomuTheme.space.md, bottom = YomuTheme.space.xxs),
            )
            Gutter { ReaderFontSizeLine(s, onUpdateSettings) }

            SheetSection("Page")
            Gutter {
                ReaderLayoutLine(s, onUpdateSettings)
                ReaderAlignmentLine(s, onUpdateSettings)
                ReaderSpacingLines(s, onUpdateSettings)
            }

            SheetSection("Light")
            Gutter {
                BrightnessLine(s, onUpdateSettings, onPreviewBrightness, onCommitBrightness)
                ExtraDimLine(s.dimLevel, onPreviewDim, onCommitDim)
            }

            SheetSection("On the page")
            Gutter { ReaderChromeToggles(settings = s, onUpdateSettings = onUpdateSettings, compact = true) }

            // Drop this book's overrides and follow the Reading defaults again.
            Box(
                modifier = Modifier
                    .padding(start = gutter - 12.dp, top = YomuTheme.space.sm)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .yomuPressable(onClick = onResetSettings, shape = RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Reset this book to the reading defaults",
                    color = YomuTheme.stack.muted,
                    style = StackType.Label.copy(fontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun Gutter(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.lg), content = content)
}

/** A group's label inside the sheet: Stack's 13/600 muted, with room above it. */
@Composable
private fun SheetSection(title: String) {
    ReaderSectionLabel(
        text = title,
        modifier = Modifier.padding(start = YomuTheme.space.lg, end = YomuTheme.space.lg, top = YomuTheme.space.lg, bottom = 2.dp),
    )
}

/**
 * Brightness: the slider, or "Follows the system" while "Auto" is on — both in the one line, so
 * switching never moves the rest of the sheet.
 */
@Composable
private fun BrightnessLine(
    s: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
) {
    val stack = YomuTheme.stack
    ReaderSettingLine("Brightness") {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (s.useSystemBrightness) {
                Text("Follows the system", color = stack.muted, style = StackType.BodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                ReaderChromeSlider(
                    fraction = s.brightness,
                    onSeek = onCommitBrightness,
                    onDrag = onPreviewBrightness,
                    fill = stack.ink,
                    track = stack.track,
                    contentDescription = "Brightness",
                    stateDescription = s.brightness.asPercent(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ReaderChip(
            label = "Auto",
            selected = s.useSystemBrightness,
            onClick = { onUpdateSettings(s.copy(useSystemBrightness = !s.useSystemBrightness)) },
            showCheck = true,
            modifier = Modifier.semantics { contentDescription = "Use system brightness" },
        )
    }
}

/** Extra dim below the device's lowest brightness: a slider and its level ("Off" at none). */
@Composable
private fun ExtraDimLine(level: Float, onPreviewDim: (Float) -> Unit, onCommitDim: (Float) -> Unit) {
    val stack = YomuTheme.stack
    ReaderSettingLine("Extra dim") {
        ReaderChromeSlider(
            fraction = level,
            onSeek = onCommitDim,
            onDrag = onPreviewDim,
            fill = stack.ink,
            track = stack.track,
            contentDescription = "Extra dim",
            stateDescription = if (level > 0f) level.asPercent() else "Off",
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (level > 0f) level.asPercent() else "Off",
            color = if (level > 0f) stack.ink else stack.muted,
            style = ReaderFigures.copy(fontSize = 13.sp),
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
    }
}

/** A saved page theme: an "Aa" disc in its colours and its name, with an optional delete. */
@Composable
internal fun SavedThemeChip(
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
            .yomuPressable(onClick = onApply, role = Role.RadioButton, shape = CircleShape)
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
            YomuSheetHeader(title = "Custom theme", onClose = dismiss)
            // The page as it will look; the page behind the sheet changes along with it.
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
                height = ReaderControlHeight,
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

            AnimatedVisibility(
                visible = customThemes.isNotEmpty(),
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
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
}
