package com.itexpert120.yomu.feature.reader

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup

@Composable
internal fun SaveFailureNotice(message: String?, onRetry: () -> Unit) {
    if (message != null) {
        Popup(alignment = Alignment.TopCenter) {
            Snackbar(action = { TextButton(onClick = onRetry) { Text("Retry") } }) {
                Text(message)
            }
        }
    }
}
