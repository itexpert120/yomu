package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Stack's reader top bar, in the page colour: back · book title over the chapter · bookmark, on a
 * hairline rule. Its full measured height (status area included) is reported for the page inset.
 */
@Composable
internal fun ReaderTopBar(
    chapter: String,
    colors: ReaderChromeColors,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bookTitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }
            .background(colors.page),
    ) {
        // The navigator draws edge-to-edge: keep the cutout/status area solid page colour.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.displayCutout.union(WindowInsets.statusBarsIgnoringVisibility)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ReaderTopBarHeight)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.ink),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(StackIcons.ArrowBack, contentDescription = "Back")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .semantics(mergeDescendants = true) { heading() },
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                bookTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        color = colors.muted,
                        style = StackType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = chapter.ifBlank { "Reading" },
                    color = colors.ink,
                    style = StackType.Heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconToggleButton(
                checked = isBookmarked,
                onCheckedChange = { onToggleBookmark() },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = colors.ink,
                    checkedContentColor = colors.ink,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isBookmarked) StackIcons.BookmarkFilled else StackIcons.Bookmark,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

private val ReaderTopBarHeight = 56.dp

/** "3 pages left in chapter" (paged) or "12% of chapter left" (scroll); null while unknown. */
internal fun chapterRemainingLabel(layout: ReaderLayout, pagesLeft: Int?, chapterProgression: Double): String? = when (layout) {
    ReaderLayout.Paged -> pagesLeft?.let {
        when (it) {
            0 -> "Last page of chapter"
            1 -> "1 page left in chapter"
            else -> "$it pages left in chapter"
        }
    }
    ReaderLayout.Scroll -> "${chapterPercentLeft(chapterProgression)}% of chapter left"
}

/** The footer's shorter form of [chapterRemainingLabel]. */
private fun footerRemainingLabel(layout: ReaderLayout, pagesLeft: Int?, chapterProgression: Double): String? = when (layout) {
    ReaderLayout.Paged -> pagesLeft?.let {
        when (it) {
            0 -> "Last page"
            1 -> "1 page left"
            else -> "$it pages left"
        }
    }
    ReaderLayout.Scroll -> "${chapterPercentLeft(chapterProgression)}% chapter left"
}

private fun chapterPercentLeft(chapterProgression: Double): Int = ceil((1.0 - chapterProgression.coerceIn(0.0, 1.0)) * 100.0).toInt().coerceIn(0, 100)

/**
 * The quiet footer under the page: battery and clock on the left, chapter remaining and book
 * progress on the right, in the page's muted ink with tabular figures.
 */
@Composable
internal fun ReaderFooter(
    progressPercent: Int?,
    chapterPagesLeft: Int?,
    chapterProgression: Double,
    settings: ReaderSettings,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // False while the system status bar is showing (reader menu open): it already has both.
    showDeviceStatus: Boolean = true,
    // False while the footer is faded out (immersive chrome hidden): the clock and battery keep
    // their last value instead of ticking and listening for nobody.
    deviceStatusActive: Boolean = true,
) {
    val colors = settings.chromeColors()
    val showBattery = settings.footerShowBattery && showDeviceStatus
    val showClock = settings.footerShowClock && showDeviceStatus
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }
            .background(colors.page)
            // Extra bottom padding keeps the row clear of the device's rounded corners.
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBattery) FooterBattery(color = colors.muted, background = colors.page, active = deviceStatusActive)
        if (showClock) FooterClock(color = colors.muted, active = deviceStatusActive)
        Spacer(Modifier.weight(1f))
        val remaining = footerRemainingLabel(settings.layout, chapterPagesLeft, chapterProgression)
        if (settings.footerShowPagesLeft && remaining != null) {
            Text(text = remaining, color = colors.muted, style = ReaderFigures, maxLines = 1)
        }
        if (settings.footerShowProgress) {
            Text(text = progressPercent?.let { "$it%" } ?: "", color = colors.muted, style = ReaderFigures)
        }
    }
}

/**
 * The reader menu's controls: where you are in the chapter and the book, a position slider that
 * scrubs by chapter, and the four reader actions. A sheet rising from the bottom edge on phones; a
 * centred floating card (max 640) on wider windows. Drawn in the page's colours.
 */
@Composable
internal fun BoxScope.ReaderControlsPanel(
    visible: Boolean,
    colors: ReaderChromeColors,
    remainingLabel: String?,
    progressPercent: Int?,
    chapters: List<ReaderTocItem>,
    currentChapterIndex: Int,
    onSelectChapter: (String) -> Unit,
    onContents: () -> Unit,
    onSearch: () -> Unit,
    onMarks: () -> Unit,
    onDisplay: () -> Unit,
) {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val floating = YomuWidthClass.fromWidth(windowWidth).isWide
    val navInset = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
    ) {
        val shape = if (floating) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (floating) navInset + 28.dp else 0.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .then(if (floating) Modifier.padding(horizontal = 24.dp).widthIn(max = 640.dp) else Modifier)
                    .fillMaxWidth()
                    .then(
                        if (colors.isLight) {
                            Modifier.dropShadow(
                                shape,
                                Shadow(
                                    radius = 28.dp,
                                    spread = (-14).dp,
                                    offset = DpOffset(0.dp, if (floating) 12.dp else (-12).dp),
                                    color = Color.Black.copy(alpha = 0.22f),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(shape)
                    .background(colors.panel)
                    .then(
                        if (floating) {
                            Modifier
                        } else {
                            Modifier.drawBehind { drawRect(colors.rule, size = Size(size.width, 1.dp.toPx())) }
                        },
                    )
                    // Swallow taps so they never reach the page under the panel.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (floating) 18.dp else 20.dp,
                        bottom = if (floating) 8.dp else navInset + 8.dp,
                    ),
            ) {
                ReaderPositionControl(
                    colors = colors,
                    remainingLabel = remainingLabel,
                    progressPercent = progressPercent,
                    chapters = chapters,
                    currentChapterIndex = currentChapterIndex,
                    onSelectChapter = onSelectChapter,
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ReaderPanelAction("Contents", onContents, colors.muted) {
                        Icon(StackIcons.Toc, contentDescription = null, tint = colors.muted)
                    }
                    ReaderPanelAction("Search", onSearch, colors.muted) {
                        Icon(StackIcons.Search, contentDescription = null, tint = colors.muted)
                    }
                    ReaderPanelAction("Marks", onMarks, colors.muted, description = "Bookmarks") {
                        Icon(StackIcons.Bookmark, contentDescription = null, tint = colors.muted)
                    }
                    ReaderPanelAction("Display", onDisplay, colors.ink) {
                        Text(
                            text = "Aa",
                            color = colors.ink,
                            style = StackType.Heading.copy(fontSize = 20.sp, lineHeight = 24.sp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The caption row and position slider. The thumb shows book progress; dragging previews the
 * chapter it will land on and releasing opens that chapter. The book's progress weighting is not
 * known here, so the slider scrubs chapters evenly, like the Controls sheet's chapter slider.
 */
@Composable
private fun ReaderPositionControl(
    colors: ReaderChromeColors,
    remainingLabel: String?,
    progressPercent: Int?,
    chapters: List<ReaderTocItem>,
    currentChapterIndex: Int,
    onSelectChapter: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var preview by remember { mutableStateOf<Int?>(null) }
    fun chapterAt(fraction: Float): Int = if (chapters.size <= 1) 0 else (fraction.coerceIn(0f, 1f) * chapters.lastIndex).roundToInt()
    val previewIndex = preview
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = previewIndex?.let { index -> chapters.getOrNull(index)?.title?.let { "${index + 1} · $it" } }
                ?: remainingLabel.orEmpty(),
            color = if (previewIndex != null) colors.ink else colors.muted,
            style = ReaderFigures,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = progressPercent?.let { "$it%" }.orEmpty(),
            color = colors.muted,
            style = ReaderFigures,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    ReaderChromeSlider(
        fraction = (progressPercent ?: 0) / 100f,
        fill = colors.ink,
        track = colors.track,
        contentDescription = "Position in book",
        stateDescription = chapters.getOrNull(currentChapterIndex)?.let { "Chapter ${currentChapterIndex + 1} of ${chapters.size}" },
        enabled = chapters.size > 1,
        onDrag = { fraction ->
            val index = chapterAt(fraction)
            if (index != preview && chapters.size <= MaxTickedChapters) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            preview = index
        },
        onDragEnd = { preview = null },
        onSeek = { fraction ->
            preview = null
            chapters.getOrNull(chapterAt(fraction))?.locatorJson?.let(onSelectChapter)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

/** Above this many chapters, per-chapter ticks become a buzz rather than feedback. */
private const val MaxTickedChapters = 60

@Composable
private fun RowScope.ReaderPanelAction(
    label: String,
    onClick: () -> Unit,
    color: Color,
    description: String = label,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .yomuPressable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.Center) { icon() }
        Text(text = label, color = color, style = StackType.Caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** The chapters a reader can land on: TOC entries with a target. */
internal fun List<ReaderTocItem>.readerChapters(): List<ReaderTocItem> = filter { it.locatorJson != null }

/** The chapter being read: an exact section match first, else the first entry in its resource. */
internal fun List<ReaderTocItem>.currentChapterIndex(currentHref: String?): Int {
    val exact = indexOfFirst { it.id == currentHref }
    return if (exact >= 0) exact else indexOfFirst { it.resourceHref == currentHref }.coerceAtLeast(0)
}

/**
 * Wall clock, refreshed on each minute boundary while [active] and the app is started. Owning its
 * state here keeps a tick from recomposing the rest of the footer.
 */
@Composable
private fun FooterClock(color: Color, active: Boolean) {
    val context = LocalContext.current
    val format = remember(context) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    var time by remember { mutableStateOf(format.format(Date())) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                time = format.format(Date(now))
                // A small margin past the boundary so the next format lands on the new minute.
                delay(MinuteMillis - now % MinuteMillis + ClockTickMarginMillis)
            }
        }
    }
    Text(text = time, color = color, style = ReaderFigures)
}

private const val MinuteMillis = 60_000L
private const val ClockTickMarginMillis = 50L

/**
 * Horizontal battery icon (an outlined shell with a level-proportional fill) and the level. While
 * charging, a bolt is cut into the icon (drawn in the page colour over the fill).
 */
@Composable
private fun BatteryIndicator(level: Int, charging: Boolean, color: Color, background: Color) {
    val fill = (level.coerceIn(0, 100)) / 100f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = if (charging) "Battery $level%, charging" else "Battery $level%"
        },
    ) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 9.dp)
                .border(1.2.dp, color, RoundedCornerShape(2.dp))
                .padding(1.5.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
            if (charging) {
                Icon(
                    imageVector = StackIcons.Bolt,
                    contentDescription = null,
                    tint = background,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(9.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(width = 1.5.dp, height = 4.dp)
                .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
                .background(color),
        )
        Text(text = "$level%", color = color, style = ReaderFigures, modifier = Modifier.padding(start = 4.dp))
    }
}

private data class BatteryStatus(val level: Int, val charging: Boolean)

/** Battery level, listened to only while [active] and the app is started. */
@Composable
private fun FooterBattery(color: Color, background: Color, active: Boolean) {
    val context = LocalContext.current
    var battery by remember(context) { mutableStateOf(readBattery(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(context, active, lifecycleOwner) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    battery = batteryFrom(intent) ?: battery
                }
            }
            // Registering returns the sticky broadcast, so the level is current again straight away
            // after a spell in the background.
            batteryFrom(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                ?.let { battery = it }
            try {
                awaitCancellation()
            } finally {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }
    BatteryIndicator(
        level = battery.level,
        charging = battery.charging,
        color = color,
        background = background,
    )
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
