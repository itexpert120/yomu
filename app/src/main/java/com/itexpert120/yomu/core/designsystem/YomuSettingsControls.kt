package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick as onClickLabel

enum class YomuSettingPosition { Single, First, Middle, Last }

/**
 * Material 3 Expressive switch: the handle carries a check/close icon so on and off read at a
 * glance, not just by colour.
 */
@Composable
fun YomuTogglePill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

/**
 * A settings row. With a [position] it renders as an expressive segmented list item — rounded
 * outer corners on the group ends, tight inner corners between neighbours — and morphs toward the
 * selected shape when [selected]. Without a position it is a plain, uncontained list item.
 */
@Composable
fun YomuSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: YomuSettingPosition? = null,
    role: Role = Role.Button,
    trailing: @Composable () -> Unit,
) {
    val headline: @Composable () -> Unit = {
        Text(
            text = title,
            style = if (selected) {
                MaterialTheme.typography.bodyLargeEmphasized
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
    val supporting: (@Composable () -> Unit)? = subtitle?.let { text ->
        { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
    }
    val semanticsModifier = modifier
        .fillMaxWidth()
        .semantics {
            this.selected = selected
            this.role = role
        }

    if (position == null) {
        ListItem(
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = leadingContent,
            trailingContent = { trailing() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = semanticsModifier.then(
                if (onClick != null) Modifier.yomuPressable(onClick = onClick, enabled = enabled, role = role) else Modifier,
            ),
        )
        return
    }

    val shapes = position.segmentedShapes()
    if (onClick != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            modifier = semanticsModifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = { trailing() },
            supportingContent = supporting,
            colors = yomuSegmentedColors(),
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = semanticsModifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = { trailing() },
            supportingContent = supporting,
            colors = yomuSegmentedColors(),
            content = headline,
        )
    }
}

@Composable
private fun YomuSettingPosition.segmentedShapes(): ListItemShapes = when (this) {
    YomuSettingPosition.Single -> ListItemDefaults.segmentedShapes(index = 0, count = 1)
    YomuSettingPosition.First -> ListItemDefaults.segmentedShapes(index = 0, count = 3)
    YomuSettingPosition.Middle -> ListItemDefaults.segmentedShapes(index = 1, count = 3)
    YomuSettingPosition.Last -> ListItemDefaults.segmentedShapes(index = 2, count = 3)
}

/** Maps a row's index in a group of [count] to its segmented position. */
fun yomuSettingPosition(index: Int, count: Int): YomuSettingPosition = when {
    count <= 1 -> YomuSettingPosition.Single
    index == 0 -> YomuSettingPosition.First
    index == count - 1 -> YomuSettingPosition.Last
    else -> YomuSettingPosition.Middle
}

@Composable
private fun yomuSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * A segmented container for controls that aren't list rows (sliders, pickers, previews). It shares
 * the segmented list item shape and surface, so mixed groups of rows and controls read as one unit.
 */
@Composable
fun YomuSettingContainer(
    modifier: Modifier = Modifier,
    position: YomuSettingPosition = YomuSettingPosition.Single,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = position.segmentedShapes().shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Primary, emphasized label that heads a cluster of controls inside sheets and panes. */
@Composable
fun YomuSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLargeEmphasized,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/**
 * A compact value readout that doubles as a reset: tinted while a custom value is set, and tapping
 * it returns the setting to its default.
 */
@Composable
fun YomuValueChip(
    text: String,
    customized: Boolean,
    onReset: () -> Unit,
    resetLabel: String,
) {
    Surface(
        onClick = onReset,
        enabled = customized,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (customized) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (customized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { if (customized) onClickLabel(resetLabel, action = null) },
    ) {
        Text(
            text = text,
            style = if (customized) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A contiguous segmented list: rows separated by the M3 Expressive segmented gap. */
@Composable
fun YomuSettingList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

/** Divider used between related rows in uncontained lists. */
@Composable
fun YomuSettingDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
