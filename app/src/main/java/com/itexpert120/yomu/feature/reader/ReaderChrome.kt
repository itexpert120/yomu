package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@OptIn(
    ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
internal fun ReaderTopBar(
    chapter: String,
    background: Color,
    content: Color,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The navigator draws edge-to-edge. Keep the cutout/status backdrop solid, then use the
        // platform top app bar for native touch targets, semantics, and expressive motion defaults.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(
                    WindowInsets.displayCutout.union(WindowInsets.statusBarsIgnoringVisibility),
                )
                .background(background),
        )
        TopAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onContentHeight(it.height) },
            title = {
                Text(
                    text = chapter,
                    color = content,
                    style = YomuTheme.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = content,
                    )
                }
            },
            actions = {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Rounded.BookmarkBorder
                        },
                        contentDescription = if (isBookmarked) {
                            "Remove bookmark"
                        } else {
                            "Add bookmark"
                        },
                        tint = content,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = background,
                titleContentColor = content,
                navigationIconContentColor = content,
                actionIconContentColor = content,
            ),
            windowInsets = WindowInsets(0),
        )
    }
}

@Composable
internal fun ReaderFooter(
    progressPercent: Int?,
    chapterPagesLeft: Int?,
    chapterProgression: Double,
    settings: ReaderSettings,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = Color(settings.backgroundArgb)
    val muted = Color(settings.colorPalette.secondaryTextArgb)
    val time = rememberClock()
    val battery = rememberBattery()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onContentHeight(it.height) },
        ) {
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
                        color = muted,
                        background = bg,
                    )
                }
                if (settings.footerShowClock) {
                    if (settings.footerShowBattery) Spacer(Modifier.width(14.dp))
                    Text(text = time, color = muted, style = YomuTheme.type.mono)
                }
                val chapterRemainingLabel = when (settings.layout) {
                    ReaderLayout.Paged -> chapterPagesLeft?.let {
                        if (it == 0) "Last page" else "$it pages left"
                    }
                    ReaderLayout.Scroll -> {
                        val remaining = ceil(
                            (1.0 - chapterProgression.coerceIn(0.0, 1.0)) * 100.0,
                        ).toInt().coerceIn(0, 100)
                        "$remaining% chapter left"
                    }
                }
                if (settings.footerShowPagesLeft && chapterRemainingLabel != null) {
                    if (settings.footerShowBattery || settings.footerShowClock) {
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        text = chapterRemainingLabel,
                        color = muted,
                        style = YomuTheme.type.mono,
                    )
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
 * Bottom chapter-controls bar, revealed by a centre tap. Holds quick navigation: table of contents,
 * previous/next chapter, highlights, and reader settings. The top bar
 * stays static, so the chapter title remains visible at all times.
 */
@Composable
internal fun BoxScope.ReaderChapterControlsBar(
    visible: Boolean,
    bottomInset: Dp,
    background: Color,
    content: Color,
    border: Color,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBrowse: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDisplay: () -> Unit,
    onMore: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + 14.dp),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = background,
            contentColor = content,
            border = BorderStroke(1.dp, border),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    // Keep the controls reachable on narrow phones without clipping; tablets get
                    // the same native button group centered over the reading canvas.
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ControlButton(Icons.Rounded.Toc, "Browse", content, enabled = true, onBrowse)
                ControlButton(Icons.Rounded.SkipPrevious, "Previous", content, hasPrevious, onPrevious)
                ControlButton(Icons.Rounded.SkipNext, "Next", content, hasNext, onNext)
                ControlButton(Icons.Rounded.MoreHoriz, "More", content, enabled = true, onMore)
                ControlButton(Icons.Rounded.Tune, "Display", content, enabled = true, onDisplay)
            }
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
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = content,
            disabledContentColor = content.copy(alpha = 0.3f),
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, maxLines = 1)
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
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Horizontal battery icon: an outlined shell with a level-proportional fill + terminal nub. While
 *  charging, a bolt is cut into the icon (drawn in the page colour over the fill). */
@Composable
private fun BatteryIndicator(level: Int, charging: Boolean, color: Color, background: Color) {
    val fill = (level.coerceIn(0, 100)) / 100f
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
                    .background(color),
            )
            if (charging) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Charging",
                    tint = background,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(10.dp),
                )
            }
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
