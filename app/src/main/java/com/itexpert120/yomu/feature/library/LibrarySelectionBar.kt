@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction

/**
 * Bulk actions as a vibrant M3 Expressive floating toolbar: it floats above the grid (the nav bar
 * steps aside while selecting) and its vibrant colours signal the temporary selection mode.
 */
@Composable
internal fun LibrarySelectionToolbar(
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YomuFloatingToolbar(
        vibrant = true,
        actions = listOf(
            YomuToolbarAction(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            YomuToolbarAction(Icons.Rounded.RemoveDone, "Unread", onMarkUnread),
            YomuToolbarAction(Icons.Rounded.Delete, "Remove", onDelete),
        ),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp),
    )
}
