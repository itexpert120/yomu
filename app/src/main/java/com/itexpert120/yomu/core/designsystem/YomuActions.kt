package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/**
 * A labelled icon action (reader chrome, compact toolbars): the icon over a 12/600 label, in
 * [contentColor] (ink by default). [containerColor] sets it on a pill of that colour.
 */
@Composable
fun YomuLabeledIconAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    contentColor: Color = YomuTheme.stack.ink,
    containerColor: Color? = null,
) {
    val shape = if (containerColor != null) RoundedCornerShape(16.dp) else CircleShape
    Column(
        modifier = modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .yomuPressable(onClick = onClick, enabled = enabled, role = Role.Button, shape = shape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = label
                this.selected = selected
            }
            .then(if (containerColor != null) Modifier.background(containerColor) else Modifier)
            .padding(horizontal = 8.dp, vertical = if (containerColor != null) 10.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Text(text = label, color = contentColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Material navigation container with Yomu surface roles and selection semantics. */
@Composable
fun YomuNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stackRule = YomuTheme.stack.rule
    // Stack bar A: a white sheet with a top rule; no tonal container.
    ShortNavigationBar(
        windowInsets = yomuStableSystemBarInsets().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        modifier = modifier.drawBehind {
            drawLine(stackRule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
    val stack = YomuTheme.stack
    ShortNavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { YomuNavigationLabel(label) },
        // Active is ink with the filled icon; there is no indicator.
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIconColor = stack.ink,
            selectedTextColorTopIconPosition = stack.ink,
            selectedTextColorStartIconPosition = stack.ink,
            selectedIndicatorColor = Color.Transparent,
            unselectedIconColor = stack.muted,
            unselectedTextColor = stack.muted,
        ),
    )
}

@Composable
private fun YomuNavigationLabel(label: String) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
}

/**
 * Stack's rail (medium widths and up): no surface of its own — it stands straight on the desk, so
 * the desk shows through in every theme; muted items, the active one in ink with its filled icon.
 * (The phone bar, by contrast, is a sheet-coloured strip with a top rule, on the library's sheet.)
 */
@Composable
fun YomuNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    WideNavigationRail(
        windowInsets = yomuStableSystemBarInsets().only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        modifier = modifier,
        // Items from the top, as on the tablet board.
        arrangement = Arrangement.Top,
        colors = WideNavigationRailDefaults.colors(containerColor = Color.Transparent),
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
    val stack = YomuTheme.stack
    WideNavigationRailItem(
        railExpanded = false,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { YomuNavigationLabel(label) },
        colors = WideNavigationRailItemDefaults.colors(
            selectedIconColor = stack.ink,
            selectedTextColorTopIconPosition = stack.ink,
            selectedTextColorStartIconPosition = stack.ink,
            selectedIndicatorColor = Color.Transparent,
            unselectedIconColor = stack.muted,
            unselectedTextColor = stack.muted,
        ),
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
            imageVector = StackIcons.Check,
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
 * Stack's floating toolbar for selection and contextual actions: an ink pill floating above the
 * gesture area, each action an on-ink icon + 13/600 label (48dp tall). It replaces the nav bar while
 * a temporary mode (selection) is active. [vibrant] is kept for source compatibility; surfaces
 * themed by something other than the app (the reader page) pass their own colours.
 */
@Composable
fun YomuFloatingToolbar(
    actions: List<YomuToolbarAction>,
    modifier: Modifier = Modifier,
    vibrant: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val stack = YomuTheme.stack
    val container = containerColor ?: stack.ink
    val content = contentColor ?: stack.onInk
    Row(
        modifier = modifier
            .dropShadow(CircleShape, ToolbarShadow)
            .clip(CircleShape)
            .background(container)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .alpha(if (action.enabled) 1f else 0.4f)
                    .clip(CircleShape)
                    .yomuPressable(onClick = action.onClick, enabled = action.enabled, role = Role.Button, shape = CircleShape)
                    .semantics { this.selected = action.selected }
                    .then(if (action.selected) Modifier.background(content.copy(alpha = 0.14f)) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(action.label, style = StackType.Label, color = content, maxLines = 1)
            }
        }
    }
}

private val ToolbarShadow = Shadow(radius = 24.dp, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp), color = Color(0xFF16191C).copy(alpha = 0.45f))
