@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itexpert120.yomu.core.designsystem.YomuFloatingToolbar
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuToolbarAction
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/**
 * Bulk actions as a vibrant M3 Expressive floating toolbar: it floats above the grid (the nav bar
 * steps aside while selecting) and its vibrant colours signal the temporary selection mode.
 */
@Composable
internal fun LibrarySelectionToolbar(
    onOrganize: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YomuFloatingToolbar(
        vibrant = true,
        actions = listOf(
            YomuToolbarAction(StackIcons.LibraryAdd, "Organize", onOrganize),
            YomuToolbarAction(StackIcons.CheckCircle, "Read", onMarkRead),
            YomuToolbarAction(StackIcons.RemoveDone, "Unread", onMarkUnread),
            YomuToolbarAction(StackIcons.Delete, "Remove", onDelete),
        ),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = YomuTheme.space.md),
    )
}
