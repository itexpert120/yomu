package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
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
                    SheetTab.Theme -> ThemeTab(state = state, onUpdateSettings = onUpdateSettings)
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
private fun ThemeTab(state: ReaderUiState, onUpdateSettings: (ReaderSettings) -> Unit) {
    val s = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Theme colours + brightness land in Phase 2; chrome visibility lives here for now.
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
        Text(
            text = "Themes & brightness coming next.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
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
        Text(
            text = "Font family & size coming next.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
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
private fun ReaderSlider(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: fraction).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> drag = (offset.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { onSeek(it) }; drag = null },
                    onDragCancel = { drag = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumb = 16.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(YomuTheme.colors.border),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .height(4.dp)
                .clip(CircleShape)
                .background(YomuTheme.colors.accent),
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset((shown * widthPx - thumb.toPx() / 2f).roundToInt(), 0)
                }
                .size(thumb)
                .clip(CircleShape)
                .background(YomuTheme.colors.accent),
        )
    }
}
