package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ReaderTopBar(
    chapter: String,
    edgeShadow: Boolean,
    background: Color,
    content: Color,
    onBack: () -> Unit,
    onOpenSheet: () -> Unit,
    onContentHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = background
    Column(modifier = modifier.fillMaxWidth()) {
        // The navigator draws edge-to-edge, so inset content by the full solid bar (status backdrop
        // + the controls row); the fade below is excluded so it bleeds over the page.
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { onContentHeight(it.height) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(bg),
            )
            // Sleek, compact bar (always present).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                ReaderBarButton(Icons.Rounded.Tune, "Reader controls", content, onOpenSheet)
            }
        }
        // Small fade so the solid bar dissolves into the page instead of hard-cutting.
        if (edgeShadow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))),
            )
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
        if (settings.edgeShadows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg))),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { onContentHeight(it.height) }) {
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
                BatteryIndicator(level = battery.level, charging = battery.charging, color = muted)
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
private fun ReaderBarButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
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
