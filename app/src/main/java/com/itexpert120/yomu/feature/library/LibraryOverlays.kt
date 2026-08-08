package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ConfirmRemoveDialog(
    visible: Boolean,
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Remove ${if (count == 1) "this book" else "$count books"}?")
        },
        text = {
            Text(
                "This deletes the imported file${if (count == 1) "" else "s"} and cover from " +
                    "the device. It can't be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportNotice(
    importing: Boolean,
    notice: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val text = if (importing) "Importing…" else notice ?: return

    Snackbar(
        modifier = modifier
            .widthIn(max = 568.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = navBottom + 20.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        action = if (!importing && canRetry) {
            {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        } else {
            null
        },
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FloatingResumeButton(
    book: LibraryBook,
    collapsed: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    ExtendedFloatingActionButton(
        expanded = !collapsed,
        onClick = onResume,
        modifier = modifier.padding(end = 16.dp, bottom = navBottom + 16.dp),
        icon = {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Resume ${book.title}",
            )
        },
        text = { Text("Resume") },
    )
}
