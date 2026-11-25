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
import androidx.compose.ui.graphics.lerp
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

/**
 * Stack's reader top bar, in the sheet colour, on a hairline rule: back · titles · bookmark. On a
 * phone the titles are the chapter's label over its name ("Chapter VII" / "The Piper at the Gates
 * of Dawn"); on a wide window, where the bar spans a spread, the book over the whole chapter title.
 * Its full measured height (status area included) is reported for the page inset.
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
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val wide = YomuWidthClass.fromWidth(windowWidth).isWide
    val chapterTitle = chapter.ifBlank { "Reading" }
    val book = bookTitle?.takeIf { it.isNotBlank() }
    val (caption, title) = if (wide) {
        (if (book != null) chapterTitle else null) to (book ?: chapterTitle)
    } else {
        chapterTitleParts(chapterTitle)?.let { (label, name) -> label to name } ?: (book to chapterTitle)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }
            // The page's own colour, with no line under it: the bar is part of the page, not a band
            // over it.
            .background(colors.page),
    ) {
        // The navigator draws edge-to-edge: keep the cutout/status area solid bar colour.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.displayCutout.union(WindowInsets.statusBarsIgnoringVisibility)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (wide) ReaderTopBarWideHeight else ReaderTopBarHeight)
                .padding(
                    start = if (wide) 16.dp else 6.dp,
                    end = if (wide) 16.dp else 6.dp,
                    top = if (wide) 0.dp else 14.dp,
                ),
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
                val captionText: @Composable () -> Unit = {
                    caption?.let {
                        Text(text = it, color = colors.muted, style = StackType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val titleText: @Composable () -> Unit = {
                    Text(text = title, color = colors.ink, style = StackType.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Phone: the label above the name. Wide: the book above the chapter.
                if (wide) {
                    titleText()
                    captionText()
                } else {
                    captionText()
                    titleText()
                }
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
    }
}

// Below the status area: 14 of air over a 62 row on a phone; a 64 row across a spread.
private val ReaderTopBarHeight = 76.dp
private val ReaderTopBarWideHeight = 64.dp

private val ChapterLabel = Regex(
    """^((?:chapter|part|book|volume|vol\.|section|act|episode)\s+[\p{L}\p{N}.]+)\s*[:.\-–—]\s*(.+)$""",
    RegexOption.IGNORE_CASE,
)

/** "Chapter 3: Melissa" as ("Chapter 3", "Melissa"); null when the title has no such label. */
internal fun chapterTitleParts(title: String): Pair<String, String>? = ChapterLabel.matchEntire(title.trim())?.let {
    it.groupValues[1] to it.groupValues[2].trim()
}?.takeIf { it.second.isNotEmpty() }

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
 * The quiet footer under the page: battery and clock on the left; on the right the printed page,
 * chapter remaining, reading time left and book progress, in the page's muted ink with tabular
 * figures.
 */
@Composable
internal fun ReaderFooter(
    progressPercent: Int?,
    chapterPagesLeft: Int?,
    chapterProgression: Double,
    settings: ReaderSettings,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // The printed page on screen ("112", "112–113"), for a book with a page list.
    pageLabel: String? = null,
    minutesLeftInChapter: Int? = null,
    minutesLeftInBook: Int? = null,
    // False while the system status bar is showing (reader menu open): it already has both.
    showDeviceStatus: Boolean = true,
    // False while the footer is faded out (immersive chrome hidden): the clock and battery keep
    // their last value instead of ticking and listening for nobody.
    deviceStatusActive: Boolean = true,
) {
    val colors = settings.chromeColors()
    // A step quieter than the captions: the footer stays out of the reading.
    val footerInk = lerp(colors.muted, colors.page, if (colors.isLight) 0.1f else 0.32f)
    val showBattery = settings.footerShowBattery && showDeviceStatus
    val showClock = settings.footerShowClock && showDeviceStatus
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onContentHeight(it.height) }
            .background(colors.page)
            // Extra bottom padding keeps the row clear of the device's rounded corners.
            .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBattery) FooterBattery(color = footerInk, background = colors.page, active = deviceStatusActive)
        if (showClock) FooterClock(color = footerInk, active = deviceStatusActive)
        Spacer(Modifier.weight(1f))
        val page = printedPageLabel(pageLabel)
        if (settings.footerShowPageNumber && page != null) {
            Text(text = page, color = footerInk, style = ReaderFigures, maxLines = 1)
        }
        val remaining = footerRemainingLabel(settings.layout, chapterPagesLeft, chapterProgression)
        if (settings.footerShowPagesLeft && remaining != null) {
            Text(text = remaining, color = footerInk, style = ReaderFigures, maxLines = 1)
        }
        val chapterTime = ReadingTime.footerChapterLabel(minutesLeftInChapter)
        if (settings.footerShowTimeLeft && chapterTime != null) {
            Text(text = chapterTime, color = footerInk, style = ReaderFigures, maxLines = 1)
        }
        val bookTime = ReadingTime.footerBookLabel(minutesLeftInBook)
        if (settings.footerShowBookTimeLeft && bookTime != null) {
            Text(text = bookTime, color = footerInk, style = ReaderFigures, maxLines = 1)
        }
        if (settings.footerShowProgress) {
            Text(text = progressPercent?.let { "$it%" } ?: "", color = footerInk, style = ReaderFigures)
        }
    }
}

/**
 * The reader menu's controls: where you are in the chapter and the book, a position slider that
 * scrubs within the chapter, and the four reader actions. A sheet rising from the bottom edge on phones; a
 * centred floating card (max 640) on wider windows. Drawn in the page's colours.
 */
@Composable
internal fun BoxScope.ReaderControlsPanel(
    visible: Boolean,
    colors: ReaderChromeColors,
    remainingLabel: String?,
    progressPercent: Int?,
    chapterProgression: Double,
    onSeekChapter: (Double) -> Unit,
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
                        start = if (floating) 28.dp else 24.dp,
                        end = if (floating) 28.dp else 24.dp,
                        top = if (floating) 18.dp else 20.dp,
                        bottom = if (floating) 8.dp else maxOf(navInset + 8.dp, 46.dp),
                    ),
            ) {
                ReaderPositionControl(
                    colors = colors,
                    remainingLabel = remainingLabel,
                    progressPercent = progressPercent,
                    chapterProgression = chapterProgression,
                    onSeekChapter = onSeekChapter,
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = if (floating) 6.dp else 10.dp)) {
                    ReaderPanelAction("Contents", onContents, colors.muted) {
                        Icon(StackIcons.Toc, contentDescription = null, tint = colors.muted)
                    }
                    ReaderPanelAction("Search", onSearch, colors.muted) {
                        Icon(StackIcons.Search, contentDescription = null, tint = colors.muted)
                    }
                    ReaderPanelAction("Marks", onMarks, colors.muted, description = "Bookmarks") {
                        Icon(StackIcons.Bookmarks, contentDescription = null, tint = colors.muted)
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
 * The caption row and position slider. The slider scrubs the chapter being read: the thumb shows
 * how far through it you are, dragging previews how much of it would be left, and releasing goes
 * there. Until the page reports the new place, the thumb stays where it was let go.
 */
@Composable
private fun ReaderPositionControl(
    colors: ReaderChromeColors,
    remainingLabel: String?,
    progressPercent: Int?,
    chapterProgression: Double,
    onSeekChapter: (Double) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var preview by remember { mutableStateOf<Float?>(null) }
    var seekTarget by remember { mutableStateOf<Float?>(null) }
    // The page has reported a new place: follow it again.
    LaunchedEffect(chapterProgression) { seekTarget = null }
    // A seek that lands where it started reports nothing new; don't hold the thumb for ever.
    LaunchedEffect(seekTarget) {
        if (seekTarget != null) {
            delay(SeekHoldMillis)
            seekTarget = null
        }
    }
    val previewFraction = preview
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = previewFraction?.let { "${chapterPercentLeft(it.toDouble())}% of chapter left" } ?: remainingLabel.orEmpty(),
            color = if (previewFraction != null) colors.ink else colors.muted,
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
    val shown = seekTarget ?: chapterProgression.toFloat().coerceIn(0f, 1f)
    ReaderChromeSlider(
        fraction = shown,
        fill = colors.ink,
        track = colors.track,
        contentDescription = "Position in chapter",
        stateDescription = "${100 - chapterPercentLeft(shown.toDouble())}% of chapter read",
        onDrag = { fraction ->
            val step = (fraction * SeekTicks).toInt()
            if (preview?.let { (it * SeekTicks).toInt() } != step) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            preview = fraction
        },
        onDragEnd = { preview = null },
        onSeek = { fraction ->
            preview = null
            seekTarget = fraction
            onSeekChapter(fraction.toDouble())
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

/** A tick each tenth of the chapter while scrubbing. */
private const val SeekTicks = 10

private const val SeekHoldMillis = 1_500L

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
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(16.dp))
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
