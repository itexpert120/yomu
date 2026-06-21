package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderTopBar(
    chapter: String,
    background: Color,
    content: Color,
    onBack: () -> Unit,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = background
    Column(modifier = modifier.fillMaxWidth()) {
        // The navigator draws edge-to-edge, so inset content by the full solid bar (status backdrop
        // + the controls row); the fade below is excluded so it bleeds over the page.
        Column(modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Clear the camera cutout (and the status bar when it's shown). When the status
                    // bar is hidden for full-screen reading, the cutout still keeps the title clear.
                    .windowInsetsTopHeight(
                        WindowInsets.displayCutout.union(WindowInsets.statusBarsIgnoringVisibility),
                    )
                    .background(bg),
            )
            // Sleek, compact bar (always present).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReaderBarButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", content, onBack)
                Text(
                    text = chapter,
                    color = content,
                    style = YomuTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ReaderFooter(
    progressPercent: Int?,
    settings: com.itexpert120.yomu.core.model.ReaderSettings,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = Color(settings.backgroundArgb)
    val muted = Color(settings.textArgb).copy(alpha = 0.6f)
    val time = rememberClock()
    val battery = rememberBattery()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    // Extra bottom padding keeps the row clear of the device's rounded corners.
                    .padding(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Battery on the left: a horizontal bar whose fill tracks the level.
                if (settings.footerShowBattery) {
                    BatteryIndicator(
                        level = battery.level,
                        charging = battery.charging,
                        color = muted
                    )
                }
                if (settings.footerShowClock) {
                    if (settings.footerShowBattery) Spacer(Modifier.width(14.dp))
                    Text(text = time, color = muted, style = YomuTheme.type.mono)
                }
                Spacer(Modifier.weight(1f))
                // Reading progress on the right.
                if (settings.footerShowProgress) {
                    Text(
                        text = progressPercent?.let { "$it%" } ?: "",
                        color = muted,
                        style = YomuTheme.type.mono,
                    )
                }
            }
        }
    }
}

/**
 * A "Next chapter" button that slides up at the end of a chapter. Styled to the reading theme so it
 * reads clearly on any page; opaque so text doesn't bleed through.
 */
@Composable
internal fun BoxScope.ReaderChapterButtons(
    chapterProgression: Double,
    hasNext: Boolean,
    bottomInset: Dp,
    background: Color,
    content: Color,
    onNext: () -> Unit,
) {
    // Slides up into view as the chapter end is reached — the cue that tapping advances a chapter.
    AnimatedVisibility(
        visible = hasNext && chapterProgression >= 1.0 - CHAPTER_EDGE,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + 14.dp),
    ) {
        ChapterPill(
            text = "Next chapter",
            icon = Icons.Rounded.KeyboardArrowDown,
            background = background,
            content = content,
            onClick = onNext,
        )
    }
}

private const val CHAPTER_EDGE = 0.01

/**
 * Bottom chapter-controls bar, revealed by a centre tap. Holds quick navigation: table of contents,
 * previous/next chapter, scroll to the top/bottom of the chapter, and reader settings. The top bar
 * stays static, so the chapter title remains visible at all times.
 */
@Composable
internal fun BoxScope.ReaderChapterControlsBar(
    visible: Boolean,
    bottomInset: Dp,
    background: Color,
    content: Color,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onToc: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + 14.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(background)
                .border(1.dp, content.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ControlButton(Icons.Rounded.Toc, "Contents", content, enabled = true, onToc)
            ControlButton(Icons.Rounded.SkipPrevious, "Previous", content, hasPrevious, onPrevious)
            ControlButton(Icons.Rounded.VerticalAlignTop, "Top", content, enabled = true, onScrollToTop)
            ControlButton(Icons.Rounded.VerticalAlignBottom, "Bottom", content, enabled = true, onScrollToBottom)
            ControlButton(Icons.Rounded.SkipNext, "Next", content, hasNext, onNext)
            ControlButton(Icons.Rounded.Tune, "Settings", content, enabled = true, onSettings)
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) content else content.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, color = tint, style = YomuTheme.type.caption, maxLines = 1)
    }
}

@Composable
private fun ChapterPill(
    text: String,
    icon: ImageVector,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            // Opaque page-coloured fill + subtle border, so text never bleeds through the pill.
            .background(background)
            .border(1.dp, content.copy(alpha = 0.22f), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
        Text(text = text, color = content, style = YomuTheme.type.caption, maxLines = 1)
    }
}

/** Wall-clock string, refreshed every 20s so the minute flip is prompt. */
@Composable
private fun rememberClock(): String {
    val format = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var time by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = format.format(Date())
            delay(20_000)
        }
    }
    return time
}

@Composable
private fun ReaderBarButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
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
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Horizontal battery icon: an outlined shell with a level-proportional fill + terminal nub. */
@Composable
private fun BatteryIndicator(level: Int, charging: Boolean, color: Color) {
    val fill = (level.coerceIn(0, 100)) / 100f
    val fillColor = if (charging) color.copy(alpha = 1f) else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 11.dp)
                .border(1.dp, color, RoundedCornerShape(3.dp))
                .padding(1.5.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(fillColor),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 2.dp, height = 5.dp)
                .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
                .background(color),
        )
    }
}

private data class BatteryStatus(val level: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): BatteryStatus {
    val context = LocalContext.current
    return produceState(initialValue = readBattery(context), context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                value = batteryFrom(intent) ?: value
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitDispose { runCatching { context.unregisterReceiver(receiver) } }
    }.value
}

private fun readBattery(context: Context): BatteryStatus {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return batteryFrom(intent) ?: BatteryStatus(100, false)
}

private fun batteryFrom(intent: Intent?): BatteryStatus? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus(level = (level * 100 / scale), charging = charging)
}
