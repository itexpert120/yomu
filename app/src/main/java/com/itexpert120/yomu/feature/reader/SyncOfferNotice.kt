package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/** "Continue from <device> at 42%?": a newer position synced from another device. */
@Composable
internal fun SyncOfferNotice(offer: SyncOfferUi?, onJump: () -> Unit, onDismiss: () -> Unit) {
    if (offer == null) return
    Popup(alignment = Alignment.TopCenter) {
        Snackbar(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            action = { TextButton(onClick = onJump) { Text("Jump") } },
            dismissAction = {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Stay here") }
            },
        ) {
            Text(
                offer.device?.let { "Continue from $it at ${offer.percent}%?" }
                    ?: "Continue from your other device at ${offer.percent}%?",
            )
        }
    }
}
