package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/** "Continue from <device> at 42%?": a newer position synced from another device. */
@Composable
internal fun SyncOfferNotice(offer: SyncOfferUi?, onJump: () -> Unit, onDismiss: () -> Unit) {
    ReaderFloatingNotice(value = offer, placement = ReaderNoticePlacement.Bottom) { shown ->
        ReaderNoticeCard(
            text = shown.device?.let { "Continue from $it at ${shown.percent}%?" }
                ?: "Continue from your other device at ${shown.percent}%?",
            icon = StackIcons.Devices,
        ) {
            ReaderNoticeAction("Stay here", onClick = onDismiss, secondary = true)
            ReaderNoticeAction("Jump", onClick = onJump)
        }
    }
}
