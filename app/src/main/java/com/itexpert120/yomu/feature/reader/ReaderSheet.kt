package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.yomuChromeBlur
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily

private enum class SheetTab(val label: String) { Controls("Controls"), Display("Display") }

@Composable
internal fun ReaderControlsSheet(
    visible: Boolean,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSeek: (Double) -> Unit,
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
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { _ ->
        Column(
            // Animate the height as tab content of differing size swaps in.
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val tabs = SheetTab.entries
            YomuSegmentedControl(
                options = tabs.map { it.label },
                selectedIndex = tabs.indexOf(tab),
                onSelected = { tab = tabs[it] },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    yomuContentSwap(forward = targetState.ordinal > initialState.ordinal)
                },
                label = "readerSheetTab",
            ) { current ->
                // The blur (driven by this content's enter/exit transition) is what makes the swap
                // read as smooth rather than a plain fade.
                Box(modifier = Modifier.yomuChromeBlur(this)) {
                when (current) {
                    SheetTab.Controls -> ControlsTab(
                        state = state,
                        onSeek = onSeek,
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
    onSeek: (Double) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    val s = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoundIcon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                "Previous chapter",
                onPreviousChapter
            )
            ReaderSlider(
                fraction = state.totalProgression.toFloat(),
                onSeek = { onSeek(it.toDouble()) },
                modifier = Modifier.weight(1f),
            )
            RoundIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next chapter", onNextChapter)
        }
        Text(
            text = "${((state.totalProgression) * 100).roundToInt()}% through the book",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.mono,
        )

        // Brightness + extra dim are contextual to the current reading session, so they stay here.
        Text(
            text = "Brightness",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption
        )
        YomuSettingRow(title = "Use system brightness") {
            YomuTogglePill(
                checked = s.useSystemBrightness,
                onCheckedChange = { onUpdateSettings(s.copy(useSystemBrightness = it)) },
            )
        }
        if (!s.useSystemBrightness) {
            ReaderSlider(
                fraction = s.brightness,
                onSeek = onCommitBrightness,
                onDrag = onPreviewBrightness,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Extra dim",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(s.dimLevel * 100).roundToInt()}%",
                color = if (s.dimLevel > 0f) YomuTheme.colors.accent else YomuTheme.colors.textMuted,
                style = YomuTheme.type.mono,
            )
        }
        ReaderSlider(
            fraction = s.dimLevel,
            onSeek = onCommitDim,
            onDrag = onPreviewDim,
            modifier = Modifier.fillMaxWidth(),
        )

        // Footer + screen toggles, rendered by the SAME shared composable as the global Reading
        // Defaults (ReaderChromeToggles) — a single renderer so the two surfaces can never drift out
        // of sync and no option is missed in one place but not the other.
        Text(
            text = "Footer",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
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
    val s = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Theme · this book",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption
        )
        ReaderThemeRow(s, onUpdateSettings)
        if (s.theme == ReaderThemeMode.Custom) {
            ReaderCustomThemeRow(s, state.customThemes, onOpenCustomTheme, onApplyCustomTheme)
        }
        Text(text = "Layout", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        ReaderLayoutControl(s, onUpdateSettings)
        Text(text = "Font", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        ReaderFontRow(s, onUpdateSettings)
        ReaderFontSizeControl(s, onUpdateSettings)
        Text(text = "Text", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        ReaderTextAlignControl(s, onUpdateSettings)
        ReaderTypographySliders(s, onUpdateSettings)

        // Drop this book's overrides and follow the global Reading Defaults again.
        YomuButton(
            text = "Reset to defaults",
            onClick = onResetSettings,
            emphasis = YomuButtonEmphasis.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
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
) {
    fun snap(v: Float): Float = (round(v / step) * step).coerceIn(min, max)
    val auto = value == null
    val current = value ?: default
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                modifier = Modifier.weight(1f),
            )
            // "Auto" when at the engine default; tap a concrete value to reset back to Auto.
            Text(
                text = if (auto) "Auto" else valueText(value),
                color = if (auto) YomuTheme.colors.textMuted else YomuTheme.colors.accent,
                style = YomuTheme.type.mono,
                modifier = Modifier
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !auto,
                        onClick = { onChange(null) },
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIcon(Icons.Rounded.Remove, "Decrease $label") { onChange(snap(current - step)) }
            ReaderSlider(
                fraction = ((current - min) / (max - min)).coerceIn(0f, 1f),
                // Tick marks the default value's position on the track.
                markerFraction = ((default - min) / (max - min)).coerceIn(0f, 1f),
                onSeek = { onChange(snap(min + it * (max - min))) },
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
    // scrollable=false: the colour picker handles its own vertical drag, which would fight a parent scroll.
    YomuBottomSheet(visible = visible, onDismiss = onDismiss, scrollable = false) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Custom theme",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body
            )

            Text(
                text = "Background colour",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption
            )
            YomuColorPicker(
                color = Color(settings.backgroundArgb),
                // customBackground is an ARGB Long; toArgb() yields an Int, mask to keep it unsigned.
                onColorChange = { color ->
                    onUpdateSettings(
                        settings.copy(
                            customBackground = color.toArgb().toLong() and 0xFFFFFFFFL
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Text colour",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption
            )
            YomuColorPicker(
                color = Color(settings.textArgb),
                onColorChange = { color ->
                    onUpdateSettings(
                        settings.copy(
                            customText = color.toArgb().toLong() and 0xFFFFFFFFL
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            YomuTextField(
                value = name,
                onValueChange = { name = it },
                label = "Theme name",
                placeholder = "e.g. Midnight",
            )
            YomuButton(
                text = "Save theme",
                onClick = {
                    onSave(name)
                    name = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (customThemes.isNotEmpty()) {
                Text(
                    text = "Saved themes",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption
                )
                customThemes.forEach { theme ->
                    SavedThemeRow(
                        theme = theme,
                        onApply = { onApply(theme) },
                        onDelete = { onDelete(theme.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedThemeRow(theme: CustomReaderTheme, onApply: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onApply,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preview: page colour with the text colour as an inner dot.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(theme.background))
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(theme.text)),
            )
        }
        Text(
            text = theme.name,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete theme",
                tint = YomuTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * In-reader table of contents in its own sheet: a height-capped lazy list (TOCs can be thousands of
 * entries) of jumpable chapters; the current chapter is highlighted, tapping one navigates there.
 */
@Composable
internal fun ReaderTocSheet(
    visible: Boolean,
    toc: List<ReaderTocItem>,
    loading: Boolean,
    currentHref: String?,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit,
) {
    // Only entries with a resolvable position are jumpable.
    val entries = remember(toc) { toc.filter { it.locatorJson != null } }
    val listState = rememberLazyListState()
    // On open, jump the list to the chapter currently being read so the user lands in context.
    LaunchedEffect(visible, entries, currentHref) {
        if (visible) {
            val index = entries.indexOfFirst { it.id == currentHref }
            if (index >= 0) listState.scrollToItem(index)
        }
    }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss, scrollable = false) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Contents",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body
            )
            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = YomuTheme.colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Building contents…",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.body,
                    )
                }
            } else if (entries.isEmpty()) {
                Text(
                    text = "No table of contents.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries) { item ->
                        TocSheetRow(
                            item = item,
                            current = item.id == currentHref,
                            onClick = { item.locatorJson?.let(onJump) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TocSheetRow(item: ReaderTocItem, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(if (current) YomuTheme.colors.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // Indent by TOC depth.
            .padding(start = (12 + item.depth * 14).dp, top = 11.dp, bottom = 11.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.title,
            color = if (current) YomuTheme.colors.textPrimary else YomuTheme.colors.textSecondary,
            style = YomuTheme.type.body,
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(if (selected) YomuTheme.colors.accentSoft else YomuTheme.colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) YomuTheme.colors.accent else YomuTheme.colors.border,
                shape = RoundedCornerShape(YomuTheme.radius.pill),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = font.displayName,
            color = if (selected) YomuTheme.colors.textPrimary else YomuTheme.colors.textSecondary,
            style = YomuTheme.type.body.copy(fontFamily = family),
        )
    }
}


@Composable
internal fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = YomuTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Minimal custom slider: drag the thumb or tap the track to seek (0..1). */
@Composable
internal fun ReaderSlider(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDrag: ((Float) -> Unit)? = null,
    markerFraction: Float? = null,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val shown = (drag ?: fraction).coerceIn(0f, 1f)
    val thumb = 18.dp
    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                fun valueFor(x: Float): Float {
                    val thumbPx = thumb.toPx()
                    val trackWidth = (size.width - thumbPx).coerceAtLeast(1f)
                    return ((x - thumbPx / 2f) / trackWidth).coerceIn(0f, 1f)
                }
                detectTapGestures { offset ->
                    val value = valueFor(offset.x)
                    currentOnDrag?.invoke(value)
                    currentOnSeek(value)
                }
            }
            .pointerInput(Unit) {
                fun valueFor(x: Float): Float {
                    val thumbPx = thumb.toPx()
                    val trackWidth = (size.width - thumbPx).coerceAtLeast(1f)
                    return ((x - thumbPx / 2f) / trackWidth).coerceIn(0f, 1f)
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        val value = valueFor(offset.x)
                        drag = value
                        currentOnDrag?.invoke(value)
                    },
                    onDrag = { change, _ ->
                        val value = valueFor(change.position.x)
                        drag = value
                        currentOnDrag?.invoke(value)
                        change.consume()
                    },
                    onDragEnd = { drag?.let { currentOnSeek(it) }; drag = null },
                    onDragCancel = { drag = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val thumbPx = with(density) { thumb.toPx() }
        val trackWidthPx = (constraints.maxWidth.toFloat() - thumbPx).coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .padding(horizontal = thumb / 2f)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(YomuTheme.colors.border),
        )
        Box(
            modifier = Modifier
                .padding(start = thumb / 2f)
                .width(with(density) { (shown * trackWidthPx).toDp() })
                .height(4.dp)
                .clip(CircleShape)
                .background(YomuTheme.colors.accent),
        )
        // Default-position indicator: a faint tick showing where the setting's default sits.
        markerFraction?.let { m ->
            val markerWidth = 2.dp
            val markerWidthPx = with(density) { markerWidth.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (m.coerceIn(
                                0f,
                                1f
                            ) * trackWidthPx + thumbPx / 2f - markerWidthPx / 2f).roundToInt(),
                            0,
                        )
                    }
                    .size(width = markerWidth, height = 12.dp)
                    .clip(CircleShape)
                    .background(YomuTheme.colors.textMuted.copy(alpha = 0.7f)),
            )
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset((shown * trackWidthPx).roundToInt(), 0)
                }
                .size(thumb)
                .clip(CircleShape)
                .background(YomuTheme.colors.accent),
        )
    }
}
