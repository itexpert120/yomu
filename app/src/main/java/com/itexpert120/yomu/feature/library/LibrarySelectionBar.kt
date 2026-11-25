package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable

private val ToolbarShape = RoundedCornerShape(34.dp)

/** Height the selection toolbar takes above the gesture area, so the grid can scroll clear of it. */
internal val SelectionToolbarClearance = 68.dp + 28.dp

/** The pill's bottom sits 28dp above the screen's bottom edge, just clear of the gesture handle. */
private val ToolbarBottom = 28.dp

/**
 * Stack's selection toolbar: an ink pill floating above the gesture area, with each bulk action as
 * an icon over its label. It replaces the navigation bar while books are selected.
 */
@Composable
internal fun LibrarySelectionToolbar(
    selectedCount: Int,
    onOrganize: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    // A gesture bar (~24dp) fits under the 28dp gap; taller three-button bars push the pill up.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = maxOf(ToolbarBottom, navBottom + 4.dp))
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(68.dp)
            .dropShadow(ToolbarShape, Shadow(radius = 20.dp, spread = (-8).dp, offset = DpOffset(0.dp, 8.dp), color = Color.Black.copy(alpha = 0.4f)))
            .clip(ToolbarShape)
            .background(stack.ink)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Actions for $selectedCount ${if (selectedCount == 1) "book" else "books"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(StackIcons.LibraryAdd, "Organize", onOrganize)
        ToolbarAction(StackIcons.DoneAll, "Mark read", onMarkRead)
        ToolbarAction(StackIcons.RemoveDone, "Unread", onMarkUnread)
        ToolbarAction(StackIcons.Delete, "Remove", onDelete)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val onInk = YomuTheme.stack.onInk
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .yomuPressable(onClick = onClick, role = Role.Button, shape = RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = onInk)
        Text(text = label, style = StackType.Caption.copy(fontWeight = FontWeight.SemiBold), color = onInk, maxLines = 1)
    }
}
