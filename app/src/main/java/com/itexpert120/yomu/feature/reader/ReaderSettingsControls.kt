package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
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

/*
 * The reading controls shared by the in-reader Reading settings sheet and the Reading defaults
 * screen, so the two can't drift. They sit on one grid: a label column of [ReaderLabelWidth] on the
 * left and the control filling the rest, every control the same height ([ReaderControlHeight]), so
 * labels and controls line up row to row.
 */

/** The label column of a settings line. */
internal val ReaderLabelWidth = 104.dp

/** Height of the segmented choices and steppers, so every control on the grid lines up. */
internal val ReaderControlHeight = 44.dp

/** A settings line: [label] in the label column, the control in the rest, centred on one baseline. */
@Composable
internal fun ReaderSettingLine(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = YomuTheme.stack.body,
            style = StackType.BodySmall,
            maxLines = 2,
            modifier = Modifier.width(ReaderLabelWidth).padding(end = 8.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            content = content,
        )
    }
}

// region Theme

/**
 * Page themes. [compact] (the sheet and Reading defaults) is one row of "Aa" tiles; the long form
 * is a list row per theme.
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
        ReaderThemeOrder.forEachIndexed { index, mode ->
            val palette = settings.copy(theme = mode)
            YomuSettingRow(
                title = mode.displayName,
                selected = settings.theme == mode,
                role = Role.RadioButton,
                onClick = { onUpdateSettings(palette) },
                position = when (index) {
                    0 -> YomuSettingPosition.First
                    ReaderThemeOrder.lastIndex -> YomuSettingPosition.Last
                    else -> YomuSettingPosition.Middle
                },
            ) {
                androidx.compose.material3.RadioButton(selected = settings.theme == mode, onClick = null)
            }
        }
    }
}

/** Stack's names for the page themes; the stored enum keeps its original names. */
internal val ReaderThemeMode.displayName: String
    get() = when (this) {
        ReaderThemeMode.Light -> "Paper"
        ReaderThemeMode.Sepia -> "Sepia"
        ReaderThemeMode.Dark -> "Night"
        ReaderThemeMode.Black -> "Black"
        ReaderThemeMode.Custom -> "Custom"
    }

/** Page themes in the order Stack presents them: light to dark, then Custom. */
internal val ReaderThemeOrder = listOf(
    ReaderThemeMode.Light,
    ReaderThemeMode.Sepia,
    ReaderThemeMode.Dark,
    ReaderThemeMode.Black,
    ReaderThemeMode.Custom,
)

/**
 * The page themes as tiles: each is a small page ("Aa" in the reading serif over the theme's name)
 * in its own colours; the current one wears a 2dp ink outline. Custom shows a palette glyph on a
 * dashed outline until it is chosen.
 */
@Composable
private fun ReaderThemeTiles(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    val stack = YomuTheme.stack
    val serif = rememberReadingSerif()
    val tileShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        ReaderThemeOrder.forEach { mode ->
            val palette = settings.copy(theme = mode)
            val selected = settings.theme == mode
            val custom = mode == ReaderThemeMode.Custom
            val background = if (custom && !selected) stack.sheet else Color(palette.backgroundArgb)
            val content = if (custom && !selected) stack.body else Color(palette.textArgb)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .clip(tileShape)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onUpdateSettings(palette) },
                    )
                    .then(
                        // The chosen tile: an ink ring with a sheet-coloured gap, so the choice
                        // also shows on Night, Black and a dark Custom page.
                        if (selected) {
                            Modifier
                                .border(2.dp, stack.ink, tileShape)
                                .padding(3.dp)
                                .clip(SelectedTileInnerShape)
                        } else {
                            Modifier
                        },
                    )
                    .background(background)
                    .then(
                        when {
                            selected -> Modifier
                            custom -> Modifier.readerDashedBorder(stack.track, 14.dp)
                            else -> Modifier.border(1.5.dp, stack.track, tileShape)
                        },
                    )
                    .padding(vertical = if (selected) YomuTheme.space.xs - 3.dp else YomuTheme.space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                if (custom) {
                    Icon(StackIcons.Palette, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                } else {
                    Text("Aa", color = content, style = StackType.Body.copy(fontFamily = serif, fontSize = 20.sp, lineHeight = 24.sp))
                }
                Text(
                    text = mode.displayName,
                    color = content,
                    style = StackType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
        }
    }
}

private val SelectedTileInnerShape = RoundedCornerShape(11.dp)

/** The Custom theme's saved palettes as chips, then the way into the theme editor. */
@Composable
internal fun ReaderCustomThemes(
    settings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
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
            height = ReaderControlHeight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// endregion

// region Font and size

/**
 * The reading fonts as chips that wrap, each named in its own face, ending in "Get fonts" (the font
 * library) when [onGetFonts] is given: every font in view, none clipped, as on the board. [gutter]
 * is the screen's side padding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderFontPicker(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
    customFonts: List<CustomFontRef>,
    onGetFonts: (() -> Unit)?,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    // One row that scrolls sideways, however many fonts are installed; it opens on the chosen one.
    val selectedIndex = remember(settings.font, settings.customFont, customFonts) {
        settings.customFont?.let { chosen -> ReaderFont.entries.size + customFonts.indexOfFirst { it.family == chosen.family }.coerceAtLeast(0) }
            ?: ReaderFont.entries.indexOf(settings.font)
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    LazyRow(
        state = rowState,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        items(ReaderFont.entries) { font ->
            FontChip(
                font = font,
                selected = settings.customFont == null && settings.font == font,
                onClick = { onUpdateSettings(settings.copy(font = font, customFont = null)) },
            )
        }
        items(customFonts) { custom ->
            CustomFontChip(
                font = custom,
                selected = settings.customFont?.family == custom.family,
                onClick = { onUpdateSettings(settings.copy(customFont = custom)) },
            )
        }
        if (onGetFonts != null) item { GetFontsChip(onClick = onGetFonts) }
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

/** "+ Get fonts": the font library, right at the end of the font row. */
@Composable
private fun GetFontsChip(onClick: () -> Unit) {
    ReaderChip(
        label = "Get fonts",
        selected = false,
        onClick = onClick,
        role = Role.Button,
        leadingIcon = StackIcons.Add,
        dashed = true,
    )
}

/** Text size: − and + around the scale, in 5% steps; tapping the value returns it to 100%. */
@Composable
internal fun ReaderFontSizeLine(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    val min = ReaderSettings.MIN_FONT_SCALE
    val max = ReaderSettings.MAX_FONT_SCALE
    val step = ReaderSettings.FONT_SCALE_STEP
    fun snapScale(value: Float): Float = (round(value / step) * step).coerceIn(min, max)
    val scale = settings.fontScale
    ReaderSettingLine("Text size") {
        ReaderStepper(
            value = "${(scale * 100).roundToInt()}%",
            customised = scale != ReaderSettings.DEFAULT_FONT_SCALE,
            description = "Text size",
            onDecrease = { onUpdateSettings(settings.copy(fontScale = snapScale(scale - step))) }.takeIf { scale > min + 0.001f },
            onIncrease = { onUpdateSettings(settings.copy(fontScale = snapScale(scale + step))) }.takeIf { scale < max - 0.001f },
            onReset = { onUpdateSettings(settings.copy(fontScale = ReaderSettings.DEFAULT_FONT_SCALE)) },
            decreaseIcon = StackIcons.TextDecrease,
            increaseIcon = StackIcons.TextIncrease,
            modifier = Modifier.weight(1f),
        )
    }
}

// endregion

// region Page

@Composable
internal fun ReaderLayoutLine(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    ReaderSettingLine("Layout") {
        YomuConnectedChoiceGroup(
            options = LayoutChoice.entries,
            selected = if (settings.layoutFollowsScreen) LayoutChoice.Auto else LayoutChoice.of(settings.layout),
            // Words only: three options with icons don't fit the control's width on a phone.
            label = { it.name },
            onSelect = { choice ->
                onUpdateSettings(
                    when (choice) {
                        LayoutChoice.Auto -> settings.copy(layoutFollowsScreen = true)
                        LayoutChoice.Scroll -> settings.copy(layoutFollowsScreen = false, layout = ReaderLayout.Scroll)
                        LayoutChoice.Paged -> settings.copy(layoutFollowsScreen = false, layout = ReaderLayout.Paged)
                    },
                )
            },
            height = ReaderControlHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ReaderAlignmentLine(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    ReaderSettingLine("Alignment") {
        YomuConnectedChoiceGroup(
            options = ReaderTextAlign.entries,
            selected = settings.textAlign,
            label = { it.label },
            onSelect = { onUpdateSettings(settings.copy(textAlign = it)) },
            height = ReaderControlHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Line spacing, margins and paragraph spacing as steppers. Each shows a small drawing of what it
 * changes (lines drawn at the chosen spacing, a page at the chosen margins, paragraphs at the
 * chosen gap) next to the value; "Auto" leaves it to the book, and tapping a set value returns it
 * to Auto.
 */
@Composable
internal fun ReaderSpacingLines(settings: ReaderSettings, onUpdateSettings: (ReaderSettings) -> Unit) {
    SpacingLine(
        label = "Line spacing",
        kind = SpacingGlyph.Lines,
        value = settings.lineHeight,
        min = ReaderSettings.MIN_LINE_HEIGHT,
        max = ReaderSettings.MAX_LINE_HEIGHT,
        default = ReaderSettings.DEFAULT_LINE_HEIGHT,
        step = 0.1f,
        format = { formatSpacing(it) },
        onChange = { onUpdateSettings(settings.copy(lineHeight = it)) },
    )
    SpacingLine(
        label = "Margins",
        kind = SpacingGlyph.Margins,
        value = settings.pageMargins,
        min = ReaderSettings.MIN_PAGE_MARGINS,
        max = ReaderSettings.MAX_PAGE_MARGINS,
        default = ReaderSettings.DEFAULT_PAGE_MARGINS,
        step = 0.25f,
        format = { "${formatSpacing(it)}×" },
        onChange = { onUpdateSettings(settings.copy(pageMargins = it)) },
    )
    SpacingLine(
        label = "Paragraphs",
        kind = SpacingGlyph.Paragraphs,
        value = settings.paragraphSpacing,
        min = ReaderSettings.MIN_PARAGRAPH_SPACING,
        max = ReaderSettings.MAX_PARAGRAPH_SPACING,
        default = ReaderSettings.DEFAULT_PARAGRAPH_SPACING,
        step = 0.25f,
        format = { "${formatSpacing(it)} em" },
        onChange = { onUpdateSettings(settings.copy(paragraphSpacing = it)) },
    )
}

/** "1.5", "1.25", "0" — at most two decimals, no trailing zeros. */
internal fun formatSpacing(value: Float): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

@Composable
private fun SpacingLine(
    label: String,
    kind: SpacingGlyph,
    value: Float?,
    min: Float,
    max: Float,
    default: Float,
    step: Float,
    format: (Float) -> String,
    onChange: (Float?) -> Unit,
) {
    val current = value ?: default
    fun stepTo(next: Float) = onChange((round(next / step) * step).coerceIn(min, max))
    ReaderSettingLine(label) {
        ReaderStepper(
            value = if (value == null) "Auto" else format(value),
            customised = value != null,
            description = label,
            onDecrease = { stepTo(current - step) }.takeIf { current > min + 0.001f },
            onIncrease = { stepTo(current + step) }.takeIf { current < max - 0.001f },
            onReset = { onChange(null) },
            glyph = { SpacingDrawing(kind, ((current - min) / (max - min)).coerceIn(0f, 1f)) },
            modifier = Modifier.weight(1f),
        )
    }
}

internal enum class SpacingGlyph { Lines, Margins, Paragraphs }

/** A 24dp drawing of a spacing setting at [amount] (0 = its minimum, 1 = its maximum). */
@Composable
private fun SpacingDrawing(kind: SpacingGlyph, amount: Float) {
    val color = YomuTheme.stack.muted
    Box(
        Modifier
            .size(24.dp)
            .drawBehind {
                val bar = 2.dp.toPx()
                val r = CornerRadius(bar / 2)
                fun line(x: Float, y: Float, w: Float) = drawRoundRect(color, Offset(x, y - bar / 2), Size(w, bar), r)
                when (kind) {
                    SpacingGlyph.Lines -> {
                        val gap = (3f + 5f * amount).dp.toPx()
                        val mid = size.height / 2
                        for (i in -1..1) line(2.dp.toPx(), mid + i * gap, size.width - 4.dp.toPx())
                    }

                    SpacingGlyph.Margins -> {
                        val stroke = 1.5.dp.toPx()
                        val page = Size(size.width - 6.dp.toPx(), size.height - 2.dp.toPx())
                        val origin = Offset(3.dp.toPx(), 1.dp.toPx())
                        drawRoundRect(color, origin, page, CornerRadius(3.dp.toPx()), style = Stroke(stroke))
                        val inset = (2.5f + 4f * amount).dp.toPx()
                        val width = page.width - inset * 2
                        for (i in 0..3) line(origin.x + inset, origin.y + 6.dp.toPx() + i * 4.dp.toPx(), width)
                    }

                    SpacingGlyph.Paragraphs -> {
                        val gap = (2f + 6f * amount).dp.toPx()
                        val block = 3.5.dp.toPx()
                        val top = (size.height - (block * 2 + gap) * 1f - block) / 2
                        val w = size.width - 4.dp.toPx()
                        line(2.dp.toPx(), top, w)
                        line(2.dp.toPx(), top + block, w * 0.6f)
                        line(2.dp.toPx(), top + block + gap + block, w)
                        line(2.dp.toPx(), top + block + gap + block * 2, w * 0.6f)
                    }
                }
            },
    )
}

/**
 * Stack's stepper: a raised pill as tall as the segmented choices, − and + at its ends and the value
 * (with an optional [glyph]) between. When [customised], tapping the value calls [onReset]. A null
 * step callback disables that end.
 */
@Composable
internal fun ReaderStepper(
    value: String,
    customised: Boolean,
    description: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseIcon: ImageVector = StackIcons.Remove,
    increaseIcon: ImageVector = StackIcons.Add,
    glyph: (@Composable () -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .height(ReaderControlHeight)
            .clip(CircleShape)
            .background(stack.raised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperEnd(decreaseIcon, "Decrease ${description.lowercase()}", onDecrease)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(ReaderControlHeight)
                .clip(RoundedCornerShape(12.dp))
                .then(if (customised) Modifier.yomuPressable(onClick = onReset, shape = RoundedCornerShape(12.dp)) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = if (customised) "$value. Tap to reset" else value
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph?.invoke()
            Text(
                text = value,
                color = if (customised) stack.ink else stack.muted,
                style = StackType.Label.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
        StepperEnd(increaseIcon, "Increase ${description.lowercase()}", onIncrease)
    }
}

@Composable
private fun StepperEnd(icon: ImageVector, description: String, onClick: (() -> Unit)?) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(ReaderControlHeight)
            .graphicsLayer { alpha = if (onClick != null) 1f else 0.38f }
            .clip(CircleShape)
            .yomuPressable(onClick = { onClick?.invoke() }, enabled = onClick != null, shape = CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = stack.ink, modifier = Modifier.size(20.dp))
    }
}

/** Layout as offered: Auto (scroll on an upright phone, paged elsewhere), or a fixed one. */
private enum class LayoutChoice {
    Auto,
    Scroll,
    Paged,
    ;

    companion object {
        fun of(layout: ReaderLayout): LayoutChoice = if (layout == ReaderLayout.Scroll) Scroll else Paged
    }
}

// endregion

// region On the page

/** Icons shared by the list and chip forms of [ReaderChromeToggles]. */
private object ChromeIcons {
    val Footer = StackIcons.CallToAction
    val Battery = StackIcons.BatteryFull
    val Clock = StackIcons.Schedule
    val Progress = StackIcons.Percent
    val ChapterLeft = StackIcons.HourglassBottom
    val PageNumber = StackIcons.Book
    val TimeLeft = StackIcons.HourglassEmpty
    val BookTimeLeft = StackIcons.AutoStories
    val KeepOn = StackIcons.Lightbulb
    val Scrollbar = StackIcons.Height
    val Immersive = StackIcons.Fullscreen
    val TwoPages = StackIcons.MenuBook
    val AdaptPictures = StackIcons.Image
    val TapTurn = StackIcons.TouchApp
}

@Composable
private fun ChromeRowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null)
}

/**
 * What shows on the page while reading: the footer and its items, and the screen behaviours. The
 * list form is Reading defaults' "While reading"; [compact] is the sheet's chip form. One renderer
 * for both so no option is missed in either place.
 */
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
            title = "Page number",
            subtitle = "For books with printed page numbers",
            position = YomuSettingPosition.Middle,
            enabled = settings.showFooter,
            leadingContent = { ChromeRowIcon(ChromeIcons.PageNumber) },
        ) {
            YomuTogglePill(
                checked = settings.footerShowPageNumber,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowPageNumber = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(
            title = "Time left in chapter",
            position = YomuSettingPosition.Middle,
            enabled = settings.showFooter,
            leadingContent = { ChromeRowIcon(ChromeIcons.TimeLeft) },
        ) {
            YomuTogglePill(
                checked = settings.footerShowTimeLeft,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowTimeLeft = it)) },
                enabled = settings.showFooter,
            )
        }
        YomuSettingRow(
            title = "Time left in book",
            position = YomuSettingPosition.Middle,
            enabled = settings.showFooter,
            leadingContent = { ChromeRowIcon(ChromeIcons.BookTimeLeft) },
        ) {
            YomuTogglePill(
                checked = settings.footerShowBookTimeLeft,
                onCheckedChange = { onUpdateSettings(settings.copy(footerShowBookTimeLeft = it)) },
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
        // Paged only: a tap on the page's outer thirds turns it; off, any tap shows the controls.
        YomuSettingRow(
            title = "Tap the edges to turn pages",
            position = YomuSettingPosition.Middle,
            enabled = settings.layout == ReaderLayout.Paged,
            leadingContent = { ChromeRowIcon(ChromeIcons.TapTurn) },
        ) {
            YomuTogglePill(
                checked = settings.tapNavigation,
                onCheckedChange = { onUpdateSettings(settings.copy(tapNavigation = it)) },
                enabled = settings.layout == ReaderLayout.Paged,
            )
        }
        // Spreads only exist in paged mode, on a landscape window wide enough for two pages.
        YomuSettingRow(
            title = "Two pages in landscape",
            position = YomuSettingPosition.Middle,
            enabled = settings.layout == ReaderLayout.Paged,
            leadingContent = { ChromeRowIcon(ChromeIcons.TwoPages) },
        ) {
            YomuTogglePill(
                checked = settings.twoPageSpread,
                onCheckedChange = { onUpdateSettings(settings.copy(twoPageSpread = it)) },
                enabled = settings.layout == ReaderLayout.Paged,
            )
        }
        // Dark themes only: light line art (ornaments, scene breaks) drawn light on the dark page.
        YomuSettingRow(
            title = "Adapt pictures to dark pages",
            position = YomuSettingPosition.Middle,
            subtitle = "Draw black-on-white ornaments and line art light",
            enabled = !settings.isLightBackground,
            leadingContent = { ChromeRowIcon(ChromeIcons.AdaptPictures) },
        ) {
            YomuTogglePill(
                checked = settings.adaptPictures,
                onCheckedChange = { onUpdateSettings(settings.copy(adaptPictures = it)) },
                enabled = !settings.isLightBackground,
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
 * The same options as [ReaderChromeToggles] on the sheet's grid: the footer switch with its items
 * as chips under it, then the screen behaviours as chips.
 */
@Composable
private fun CompactChromeToggles(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingLine("Footer") {
            Text(
                text = if (settings.showFooter) "Shown" else "Hidden",
                color = YomuTheme.stack.muted,
                style = StackType.BodySmall,
                modifier = Modifier.weight(1f),
            )
            YomuTogglePill(
                checked = settings.showFooter,
                onCheckedChange = { onUpdateSettings(settings.copy(showFooter = it)) },
                modifier = Modifier.semantics { contentDescription = "Show footer" },
            )
        }
        // Items stay visible but disable when the footer is off, so the layout doesn't jump.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            // Immersive mode never shows these in the footer (the status bar has them instead).
            if (!settings.immersiveChrome) {
                ToggleChip("Battery", settings.footerShowBattery, settings.showFooter) {
                    onUpdateSettings(settings.copy(footerShowBattery = it))
                }
                ToggleChip("Clock", settings.footerShowClock, settings.showFooter) {
                    onUpdateSettings(settings.copy(footerShowClock = it))
                }
            }
            ToggleChip("Progress", settings.footerShowProgress, settings.showFooter) {
                onUpdateSettings(settings.copy(footerShowProgress = it))
            }
            ToggleChip("Chapter left", settings.footerShowPagesLeft, settings.showFooter) {
                onUpdateSettings(settings.copy(footerShowPagesLeft = it))
            }
            ToggleChip("Page number", settings.footerShowPageNumber, settings.showFooter) {
                onUpdateSettings(settings.copy(footerShowPageNumber = it))
            }
            ToggleChip("Time left", settings.footerShowTimeLeft, settings.showFooter) {
                onUpdateSettings(settings.copy(footerShowTimeLeft = it))
            }
            ToggleChip("Book time left", settings.footerShowBookTimeLeft, settings.showFooter) {
                onUpdateSettings(settings.copy(footerShowBookTimeLeft = it))
            }
        }
        Text(
            text = "Screen",
            color = YomuTheme.stack.body,
            style = StackType.BodySmall,
            modifier = Modifier.padding(top = YomuTheme.space.md, bottom = YomuTheme.space.xxs),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            ToggleChip("Keep screen on", settings.keepScreenOn) {
                onUpdateSettings(settings.copy(keepScreenOn = it))
            }
            // The scrollbar only exists in scroll mode (a native WebView scroll).
            ToggleChip("Scrollbar", settings.showScrollbar, settings.layout == ReaderLayout.Scroll) {
                onUpdateSettings(settings.copy(showScrollbar = it))
            }
            ToggleChip("Tap to turn", settings.tapNavigation, settings.layout == ReaderLayout.Paged) {
                onUpdateSettings(settings.copy(tapNavigation = it))
            }
            ToggleChip("Two pages", settings.twoPageSpread, settings.layout == ReaderLayout.Paged) {
                onUpdateSettings(settings.copy(twoPageSpread = it))
            }
            ToggleChip("Adapt pictures to dark pages", settings.adaptPictures, !settings.isLightBackground) {
                onUpdateSettings(settings.copy(adaptPictures = it))
            }
            ToggleChip("Immersive", settings.immersiveChrome) {
                onUpdateSettings(settings.copy(immersiveChrome = it))
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ReaderChip(
        label = label,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        showCheck = true,
    )
}

// endregion
