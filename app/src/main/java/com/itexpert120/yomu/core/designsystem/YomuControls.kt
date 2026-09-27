package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun YomuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: YomuButtonEmphasis = YomuButtonEmphasis.Primary,
    enabled: Boolean = true,
) {
    val buttonHeight = ButtonDefaults.MinHeight
    val label: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text(text = text, style = ButtonDefaults.textStyleFor(buttonHeight))
    }
    when (emphasis) {
        YomuButtonEmphasis.Primary -> Button(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )

        YomuButtonEmphasis.Secondary -> FilledTonalButton(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )

        YomuButtonEmphasis.Ghost -> TextButton(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )
    }
}

enum class YomuButtonEmphasis { Primary, Secondary, Ghost }

@Composable
fun YomuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
fun YomuSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val selected = selectedIndex.coerceIn(0, options.lastIndex)
    PrimaryTabRow(
        selectedTabIndex = selected,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
    ) {
        options.forEachIndexed { index, option ->
            Tab(
                selected = index == selected,
                onClick = { onSelected(index) },
                modifier = Modifier.semantics {
                    this.selected = index == selected
                },
                text = {
                    Text(
                        text = option,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Material 3 segmented choice control for compact, mutually exclusive settings. */
@Composable
fun YomuSingleChoiceSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val selected = selectedIndex.coerceIn(0, options.lastIndex)
    YomuConnectedChoiceGroup(
        options = options.indices.toList(),
        selected = selected,
        label = { options[it] },
        onSelect = onSelected,
        modifier = modifier,
    )
}

/**
 * M3 Expressive connected button group for mutually exclusive options (the replacement for
 * segmented buttons). The selected toggle morphs to its square selected shape and takes the
 * emphasized label style, so selection reads from shape and weight as well as colour.
 */
@Composable
fun <T> YomuConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MinHeight,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(option)
                },
                enabled = enabled,
                shapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapesFor(height)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
            ) {
                if (icon != null) {
                    Icon(icon(option), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(
                    text = label(option),
                    style = if (checked) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun YomuRangeRow(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = YomuTheme.type.body,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = YomuTheme.type.caption,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange((value - 0.05f).coerceIn(0f, 1f)) },
                modifier = Modifier.semantics {
                    contentDescription = "Decrease $label"
                },
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = null)
            }
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
                modifier = Modifier.weight(1f),
                steps = 19,
            )
            IconButton(
                onClick = { onValueChange((value + 0.05f).coerceIn(0f, 1f)) },
                modifier = Modifier.semantics {
                    contentDescription = "Increase $label"
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun YomuColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.shapes.medium,
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = YomuTheme.type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
