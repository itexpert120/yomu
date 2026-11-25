package com.itexpert120.yomu.feature.reader

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/** "Continue from <device> at 42%?": a newer position synced from another device. */
@Composable
internal fun SyncOfferNotice(offer: SyncOfferUi?, onJump: () -> Unit, onDismiss: () -> Unit) {
    ReaderFloatingNotice(value = offer) { shown ->
        YomuNotice(
            text = shown.device?.let { "Continue from $it at ${shown.percent}%?" }
                ?: "Continue from your other device at ${shown.percent}%?",
            tone = YomuNoticeTone.Floating,
            actionLabel = "Jump",
            onAction = onJump,
            // YomuNotice has a single action slot, so the dismiss control takes the leading one.
            leading = {
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    Icon(StackIcons.Close, contentDescription = "Stay here")
                }
            },
        )
    }
}
