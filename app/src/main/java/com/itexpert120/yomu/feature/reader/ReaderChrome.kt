package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuReadingTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.awaitCancellation
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
    val actionColor = lerp(background, content, ReaderChromeTonalStep)
    // The bar hangs from the top edge: square where it meets the status area, the theme's
    // extra-large corner below.
    val barShape = MaterialTheme.shapes.extraLarge.copy(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(0.dp),
    )
    Column(modifier = modifier.fillMaxWidth().onSizeChanged { onContentHeight(it.height) }) {
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
                .clip(barShape),
            title = {
                Text(
                    text = chapter.ifBlank { "Reading" },
                    color = content,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                FilledTonalIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = actionColor,
                        contentColor = content,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                    )
                }
            },
            actions = {
                FilledTonalIconToggleButton(
                    checked = isBookmarked,
                    onCheckedChange = { onToggleBookmark() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = actionColor,
                        contentColor = content,
                        checkedContainerColor = content,
                        checkedContentColor = background,
                    ),
                ) {
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
            // The title is a single line, so the default 64dp bar is mostly empty space.
            expandedHeight = ReaderTopBarHeight,
        )
    }
}

private val ReaderTopBarHeight = 52.dp

/**
 * How far reader chrome controls (top-bar buttons, the floating toolbar) step from the page colour
 * toward the text colour — one tonal step for every chrome surface.
 */
private const val ReaderChromeTonalStep = 0.10f

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
    val bg = Color(settings.backgroundArgb)
    val showBattery = settings.footerShowBattery && showDeviceStatus
    val showClock = settings.footerShowClock && showDeviceStatus
    val muted = Color(settings.colorPalette.secondaryTextArgb)
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
                if (showBattery) {
                    FooterBattery(color = muted, background = bg, active = deviceStatusActive)
                }
                if (showClock) {
                    if (showBattery) Spacer(Modifier.width(14.dp))
                    FooterClock(color = muted, active = deviceStatusActive)
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
                    if (showBattery || showClock) {
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        text = chapterRemainingLabel,
                        color = muted,
                        style = YomuReadingTokens.Mono,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Reading progress on the right.
                if (settings.footerShowProgress) {
                    Text(
                        text = progressPercent?.let { "$it%" } ?: "",
                        color = muted,
                        style = YomuReadingTokens.Mono,
                    )
                }
            }
        }
    }
}

/** A full-width bottom toolbar for reader actions; seeking stays in the Controls sheet. */
@Composable
internal fun BoxScope.ReaderChapterControlsBar(
    visible: Boolean,
    bottomPadding: Dp,
    footerVisible: Boolean,
    background: Color,
    content: Color,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onDisplay: () -> Unit,
) {
    val floating = !footerVisible
    val floatingBottomInset = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = if (floating) 0.dp else bottomPadding),
    ) {
        // Paint the spacing too: immersive EPUB content must not peek below the floating controls.
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(background)
                .padding(horizontal = if (floating) YomuTheme.space.sm else 0.dp)
                .padding(bottom = if (floating) floatingBottomInset + YomuTheme.space.sm else 0.dp),
        ) {
            ReaderActionBar(
                background = background,
                content = content,
                floating = floating,
                onBrowse = onBrowse,
                onSearch = onSearch,
                onDisplay = onDisplay,
            )
        }
    }
}

/** Reader actions as an M3 Expressive floating toolbar, coloured from the reading theme. */
// The bundled Material icon set has no AutoMirrored Toc symbol, so keep the semantic Toc glyph.
@Suppress("DEPRECATION")
@Composable
private fun ReaderActionBar(
    background: Color,
    content: Color,
    floating: Boolean,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onDisplay: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (floating) 0.dp else YomuTheme.space.xs),
        contentAlignment = Alignment.Center,
    ) {
        YomuFloatingToolbar(
            containerColor = lerp(background, content, ReaderChromeTonalStep),
            contentColor = content,
            actions = listOf(
                YomuToolbarAction(Icons.Rounded.Toc, "Browse", onBrowse),
                YomuToolbarAction(Icons.Rounded.Search, "Search", onSearch),
                YomuToolbarAction(Icons.Rounded.Tune, "Display", onDisplay),
            ),
        )
    }
}

/**
 * Wall clock, refreshed on each minute boundary while [active] and the app is started. Owning its
 * state here keeps a tick from recomposing the rest of the footer.
 */
@Composable
private fun FooterClock(color: Color, active: Boolean) {
    val format = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
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
    Text(text = time, color = color, style = YomuReadingTokens.Mono)
}

private const val MinuteMillis = 60_000L
private const val ClockTickMarginMillis = 50L

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
