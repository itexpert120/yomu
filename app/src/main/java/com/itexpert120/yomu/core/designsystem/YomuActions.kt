package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared labeled action used by reader chrome, selection bars, and compact toolbars. */
@Composable
fun YomuLabeledIconAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    containerColor: androidx.compose.ui.graphics.Color? = null,
) {
    if (containerColor != null) {
        androidx.compose.material3.FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = modifier.semantics { this.selected = selected },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null)
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }
    val resolvedColor = if (enabled) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        modifier = modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 48.dp)
            .yomuPressable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = label
                this.selected = selected
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = resolvedColor)
        Text(text = label, color = resolvedColor, style = YomuTheme.type.caption, maxLines = 1)
    }
}

/** Material navigation container with Yomu surface roles and selection semantics. */
@Composable
fun YomuNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
fun RowScope.YomuNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/** Material rail container with the same role mapping as the compact navigation bar. */
@Composable
fun YomuNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
fun ColumnScope.YomuNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/** Small selected-state indicator for custom option rows that are not Material list items. */
@Composable
fun YomuSelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = modifier,
        )
    }
}
