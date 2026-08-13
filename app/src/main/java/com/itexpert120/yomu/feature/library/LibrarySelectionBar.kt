@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuLabeledIconAction

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
            IconButton(onClick = onClose) {
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

/** Direct bulk actions moved out of the selection top-bar overflow menu. */
@Composable
internal fun LibrarySelectionToolbar(
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YomuLabeledIconAction(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Read",
                    onClick = onMarkRead,
                    modifier = Modifier.weight(1f),
                )
                YomuLabeledIconAction(
                    icon = Icons.Rounded.RemoveDone,
                    label = "Unread",
                    onClick = onMarkUnread,
                    modifier = Modifier.weight(1f),
                )
                YomuLabeledIconAction(
                    icon = Icons.Rounded.Delete,
                    label = "Remove",
                    onClick = onDelete,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}
