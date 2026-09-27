package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
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
    content: @Composable () -> Unit,
) {
    ShortNavigationBar(
        windowInsets = yomuStableSystemBarInsets().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        modifier = modifier,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
fun YomuNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    ShortNavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { YomuNavigationLabel(label, selected) },
    )
}

@Composable
private fun YomuNavigationLabel(label: String, selected: Boolean) {
    Text(
        text = label,
        style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
    )
}

/** Material rail container with the same role mapping as the compact navigation bar. */
@Composable
fun YomuNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    WideNavigationRail(
        windowInsets = yomuStableSystemBarInsets().only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        modifier = modifier,
        arrangement = Arrangement.Center,
        colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
fun YomuNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    WideNavigationRailItem(
        railExpanded = false,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { YomuNavigationLabel(label, selected) },
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

/** One labelled action in a [YomuFloatingToolbar]. */
data class YomuToolbarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

/**
 * M3 Expressive floating toolbar for contextual page actions. [vibrant] switches to the
 * high-emphasis colour scheme that marks a temporary mode (e.g. selection); standard keeps focus on
 * the content underneath. Actions stay labelled — icon-only bulk actions tested worse for clarity.
 */
@Composable
fun YomuFloatingToolbar(
    actions: List<YomuToolbarAction>,
    modifier: Modifier = Modifier,
    vibrant: Boolean = false,
    // Surfaces themed by something other than the app scheme (the reader page) pass their own.
    containerColor: androidx.compose.ui.graphics.Color? = null,
    contentColor: androidx.compose.ui.graphics.Color? = null,
) {
    val base = if (vibrant) {
        FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    } else {
        FloatingToolbarDefaults.standardFloatingToolbarColors()
    }
    val colors = base.copy(
        toolbarContainerColor = containerColor ?: base.toolbarContainerColor,
        toolbarContentColor = contentColor ?: base.toolbarContentColor,
    )
    HorizontalFloatingToolbar(
        expanded = true,
        colors = colors,
        modifier = modifier,
    ) {
        actions.forEach { action ->
            val contentColor = colors.toolbarContentColor
            if (action.selected) {
                FilledTonalButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.semantics { this.selected = true },
                ) { ToolbarActionContent(action) }
            } else {
                TextButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { ToolbarActionContent(action) }
            }
        }
    }
}

@Composable
private fun ToolbarActionContent(action: YomuToolbarAction) {
    Icon(action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(action.label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
}
