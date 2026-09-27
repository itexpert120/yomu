@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction

/** Contextual top bar that keeps selection state and primary selection controls visible. */
@Composable
internal fun LibrarySelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit selection",
                )
            }
        },
        title = {
            Text(
                text = if (selectedCount == 1) "1 book selected" else "$selectedCount books selected",
            )
        },
        actions = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = if (allSelected) onDeselectAll else onSelectAll,
            ) {
                Icon(
                    imageVector = if (allSelected) {
                        Icons.Rounded.Deselect
                    } else {
                        Icons.Rounded.SelectAll
                    },
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

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
