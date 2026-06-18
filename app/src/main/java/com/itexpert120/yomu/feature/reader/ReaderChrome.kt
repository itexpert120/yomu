package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
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
import androidx.compose.ui.graphics.Brush
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
    onBack: () -> Unit,
    onSolidHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = YomuTheme.colors.appBackground
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onSolidHeight(it.height) }
                .background(bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YomuTheme.colors.surfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = YomuTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = chapter,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    onSolidHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = YomuTheme.colors.appBackground
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onSolidHeight(it.height) }
                .background(bg)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Battery on the left.
            if (settings.footerShowBattery) {
                Icon(
                    imageVector = if (battery.charging) {
                        Icons.Rounded.BatteryChargingFull
                    } else {
                        Icons.Rounded.BatteryStd
                    },
                    contentDescription = "Battery",
                    tint = YomuTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${battery.level}%",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
            }
            if (settings.footerShowClock) {
                if (settings.footerShowBattery) Spacer(Modifier.width(14.dp))
                Text(text = time, color = YomuTheme.colors.textMuted, style = YomuTheme.type.mono)
            }
            Spacer(Modifier.weight(1f))
            // Reading progress on the right.
            if (settings.footerShowProgress) {
                Text(
                    text = progressPercent?.let { "$it%" } ?: "",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
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
