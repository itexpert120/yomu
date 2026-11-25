package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick as onClickLabel

/** Where a row sits in its group card: the ends take the card's corners, the rest share dividers. */
enum class YomuSettingPosition { Single, First, Middle, Last }

/** Stack's switch: 52×32, ink track with a white thumb when on, the grey track when off. */
@Composable
fun YomuTogglePill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val stack = YomuTheme.stack
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        // Stack: on is an ink track with a white thumb, off the grey track; no thumb icons.
        colors = SwitchDefaults.colors(
            checkedTrackColor = stack.ink,
            checkedThumbColor = stack.onInk,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = stack.track,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * A Stack settings row: 56dp tall, optional 24dp muted leading icon, the title in Body 15, an
 * optional supporting line (12.5 muted) and a trailing slot that reads in the muted value style
 * (13) — put a value, a chevron, a switch or a check there. With a [position] the row is a piece of
 * a sheet-coloured group card (radius 24 at the group's ends) on the desk, with rule-coloured
 * dividers between neighbours; on a sheet the card disappears into it and only the dividers show.
 * Without a position it is a plain row with no container.
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
    // Destructive actions carry the danger colour on their title.
    destructive: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val stack = YomuTheme.stack
    val shape = position?.cardShape() ?: RectangleShapeCompat
    val divider = position == YomuSettingPosition.First || position == YomuSettingPosition.Middle
    val rule = stack.rule
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                this.role = role
            }
            .clip(shape)
            .then(if (position != null) Modifier.background(stack.sheet) else Modifier)
            .then(
                if (onClick != null) Modifier.yomuPressable(onClick = onClick, enabled = enabled, role = role, pressedScale = 1f) else Modifier,
            )
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val inset = RowHorizontalPadding.toPx()
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(rule, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = if (position != null) RowHorizontalPadding else 0.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides stack.muted) { leadingContent() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = if (destructive) stack.danger else stack.ink,
                style = if (selected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = StackType.Supporting, color = stack.muted)
            }
        }
        CompositionLocalProvider(
            LocalContentColor provides stack.muted,
            LocalTextStyle provides StackType.Value,
        ) {
            Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

private val RowHorizontalPadding = 16.dp
private val RectangleShapeCompat: Shape = RoundedCornerShape(0.dp)

private val GroupCorner = 24.dp

private fun YomuSettingPosition.cardShape(): Shape = when (this) {
    YomuSettingPosition.Single -> RoundedCornerShape(GroupCorner)
    YomuSettingPosition.First -> RoundedCornerShape(topStart = GroupCorner, topEnd = GroupCorner)
    YomuSettingPosition.Middle -> RectangleShapeCompat
    YomuSettingPosition.Last -> RoundedCornerShape(bottomStart = GroupCorner, bottomEnd = GroupCorner)
}

/** Maps a row's index in a group of [count] to its position in the group card. */
fun yomuSettingPosition(index: Int, count: Int): YomuSettingPosition = when {
    count <= 1 -> YomuSettingPosition.Single
    index == 0 -> YomuSettingPosition.First
    index == count - 1 -> YomuSettingPosition.Last
    else -> YomuSettingPosition.Middle
}

/**
 * A piece of a group card for controls that aren't list rows (sliders, pickers, previews): the same
 * sheet colour and corners as [YomuSettingRow], so mixed groups of rows and controls read as one
 * card.
 */
@Composable
fun YomuSettingContainer(
    modifier: Modifier = Modifier,
    position: YomuSettingPosition = YomuSettingPosition.Single,
    // Tighter padding/spacing for transient surfaces (e.g. the in-reader sheet) over full pages.
    dense: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stack = YomuTheme.stack
    val rule = stack.rule
    val divider = position == YomuSettingPosition.First || position == YomuSettingPosition.Middle
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(position.cardShape())
            .background(stack.sheet)
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val inset = RowHorizontalPadding.toPx()
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(rule, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = RowHorizontalPadding, vertical = if (dense) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 8.dp),
        content = content,
    )
}

/** Stack's section label: 13/600 muted, sentence case — heads a cluster of rows or controls. */
@Composable
fun YomuSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = YomuTheme.stack.muted,
        style = StackType.Label,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * A compact value readout that doubles as a reset: the muted value (13) at rest; while a custom
 * value is set it becomes a raised chip in ink, and tapping it returns the setting to its default.
 */
@Composable
fun YomuValueChip(
    text: String,
    customized: Boolean,
    onReset: () -> Unit,
    resetLabel: String,
) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (customized) {
                    Modifier
                        .yomuPressable(onClick = onReset)
                        .semantics { onClickLabel(resetLabel, action = null) }
                        .background(stack.raised)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 28.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (customized) StackType.Chip else StackType.Value,
            color = if (customized) stack.ink else stack.muted,
        )
    }
}

/** A group card's rows, stacked with no gap: each row's position draws the card and dividers. */
@Composable
fun YomuSettingList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/** Divider between related rows in uncontained lists (rule colour). */
@Composable
fun YomuSettingDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = YomuTheme.stack.rule,
    )
}
