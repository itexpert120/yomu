package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import java.io.File
import kotlin.math.roundToInt

private enum class SheetTab(val label: String) { Controls("Controls"), Theme("Theme"), Fonts("Fonts") }

@Composable
internal fun ReaderControlsSheet(
    visible: Boolean,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSeek: (Double) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onAbout: () -> Unit,
) {
    var tab by remember { mutableStateOf(SheetTab.Controls) }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { _ ->
        Column(
            // Animate the height as tab content of differing size swaps in.
            modifier = Modifier.fillMaxWidth().animateContentSize(),
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "readerSheetTab",
            ) { current ->
                when (current) {
                    SheetTab.Controls -> ControlsTab(
                        state = state,
                        onSeek = onSeek,
                        onNextChapter = onNextChapter,
                        onPreviousChapter = onPreviousChapter,
                        onAbout = onAbout,
                    )
                    SheetTab.Theme -> ThemeTab(
                        state = state,
                        onUpdateSettings = onUpdateSettings,
                        onPreviewBrightness = onPreviewBrightness,
                        onCommitBrightness = onCommitBrightness,
                    )
                    SheetTab.Fonts -> FontsTab(state = state, onUpdateSettings = onUpdateSettings)
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
    onAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous chapter", onPreviousChapter)
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
        AboutRow(coverPath = state.coverImagePath, title = state.title, onClick = onAbout)
    }
}

@Composable
private fun ThemeTab(
    state: ReaderUiState,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
) {
    val s = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Theme", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderThemeMode.entries.forEach { mode ->
                val pageColor = Color(ReaderSettings().copy(theme = mode).backgroundArgb)
                YomuColorSwatch(
                    name = mode.name,
                    color = pageColor,
                    selected = s.theme == mode,
                    onClick = { onUpdateSettings(s.copy(theme = mode)) },
                )
            }
        }

        if (s.theme == ReaderThemeMode.Custom) {
            Text(text = "Background colour", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
            YomuColorPicker(
                color = Color(s.backgroundArgb),
                // customBackground is an ARGB Long; toArgb() yields an Int, mask to keep it unsigned.
                onColorChange = { color ->
                    onUpdateSettings(s.copy(customBackground = color.toArgb().toLong() and 0xFFFFFFFFL))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = "Text colour", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
            YomuColorPicker(
                color = Color(s.textArgb),
                onColorChange = { color ->
                    onUpdateSettings(s.copy(customText = color.toArgb().toLong() and 0xFFFFFFFFL))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(text = "Brightness", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
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

        Text(text = "Chrome", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        YomuSettingRow(title = "Show footer") {
            YomuTogglePill(checked = s.showFooter, onCheckedChange = { onUpdateSettings(s.copy(showFooter = it)) })
        }
        YomuSettingRow(title = "Edge shadows", subtitle = "Soft fade behind the bars") {
            YomuTogglePill(checked = s.edgeShadows, onCheckedChange = { onUpdateSettings(s.copy(edgeShadows = it)) })
        }
        if (s.showFooter) {
            Text(text = "Footer", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
            YomuSettingRow(title = "Battery") {
                YomuTogglePill(checked = s.footerShowBattery, onCheckedChange = { onUpdateSettings(s.copy(footerShowBattery = it)) })
            }
            YomuSettingRow(title = "Clock") {
                YomuTogglePill(checked = s.footerShowClock, onCheckedChange = { onUpdateSettings(s.copy(footerShowClock = it)) })
            }
            YomuSettingRow(title = "Reading progress") {
                YomuTogglePill(checked = s.footerShowProgress, onCheckedChange = { onUpdateSettings(s.copy(footerShowProgress = it)) })
            }
        }
    }
}

@Composable
private fun FontsTab(state: ReaderUiState, onUpdateSettings: (ReaderSettings) -> Unit) {
    val s = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Layout", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        val modes = ReaderLayout.entries
        YomuSegmentedControl(
            options = modes.map { it.name },
            selectedIndex = modes.indexOf(s.layout),
            onSelected = { onUpdateSettings(s.copy(layout = modes[it])) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "Font", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderFont.entries.forEach { font ->
                FontChip(
                    font = font,
                    selected = s.font == font,
                    onClick = { onUpdateSettings(s.copy(font = font)) },
                )
            }
        }

        val min = ReaderSettings.MIN_FONT_SCALE
        val max = ReaderSettings.MAX_FONT_SCALE
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Font size",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(s.fontScale * 100).roundToInt()}%",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.mono,
            )
        }
        ReaderSlider(
            fraction = (s.fontScale - min) / (max - min),
            onSeek = { onUpdateSettings(s.copy(fontScale = min + it * (max - min))) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Font picker chip whose label is rendered in that bundled font as a live preview. */
@Composable
private fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
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
private fun AboutRow(coverPath: String?, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .aspectRatio(1f / 1.6f)
                .clip(RoundedCornerShape(6.dp))
                .background(YomuTheme.colors.surfaceRaised),
        ) {
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "About this book", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
            Text(
                text = title,
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = YomuTheme.colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RoundIcon(
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
        Icon(imageVector = icon, contentDescription = description, tint = YomuTheme.colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Minimal custom slider: drag the thumb or tap the track to seek (0..1). */
@Composable
private fun ReaderSlider(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDrag: ((Float) -> Unit)? = null,
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
