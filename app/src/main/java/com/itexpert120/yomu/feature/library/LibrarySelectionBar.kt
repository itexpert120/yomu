package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

/** Floating bottom dock with labeled multi-select actions; the top library bar stays unchanged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySelectionDock(
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onInvert: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val panel = YomuTheme.colors.panel
    Box(
        modifier = modifier
            .padding(start = 12.dp, end = 12.dp, bottom = navBottom + 16.dp)
            .shadow(12.dp, RoundedCornerShape(YomuTheme.radius.panel))
            .clip(RoundedCornerShape(YomuTheme.radius.panel))
            .background(panel),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledAction(Icons.Rounded.Close, "Done", onClose)
            if (allSelected) {
                LabeledAction(Icons.Rounded.Deselect, "None", onDeselectAll)
            } else {
                LabeledAction(Icons.Rounded.SelectAll, "All", onSelectAll)
            }
            LabeledAction(Icons.Rounded.SwapHoriz, "Invert", onInvert)
            LabeledAction(Icons.Rounded.CheckCircle, "Read", onMarkRead)
            LabeledAction(Icons.Rounded.RemoveDone, "Unread", onMarkUnread)
            if (onOpenDetails != null) {
                LabeledAction(Icons.Rounded.Info, "Details", onOpenDetails)
            }
            LabeledAction(Icons.Rounded.Delete, "Remove", onDelete, tint = YomuTheme.colors.danger)
        }
        // Edge fades hint that the row scrolls when actions overflow. matchParentSize keeps the
        // dock sized to the row (a fillMaxHeight overlay would stretch it to the whole screen).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to panel,
                        0.05f to panel.copy(alpha = 0f),
                        0.95f to panel.copy(alpha = 0f),
                        1.00f to panel,
                    ),
                ),
        )
    }
}

@Composable
private fun LabeledAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = YomuTheme.colors.textSecondary,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .widthIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, color = tint, style = YomuTheme.type.caption)
    }
}
