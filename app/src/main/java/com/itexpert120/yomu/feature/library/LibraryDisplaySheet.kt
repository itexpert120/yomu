package com.itexpert120.yomu.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode

/** Typical automatic column counts, used as the step-off point for the orientation not on screen. */
private const val TYPICAL_PORTRAIT_COLUMNS = 3
private const val TYPICAL_LANDSCAPE_COLUMNS = 5

/**
 * Arrange: sort, view and columns, all applying live underneath. Columns step from the automatic
 * count, and a Reset link returns a customised orientation to Automatic.
 */
@Composable
internal fun LibraryOptionsSheet(
    visible: Boolean,
    sortMode: SortMode,
    viewMode: LibraryViewMode,
    portraitColumns: Int,
    landscapeColumns: Int,
    onSortModeChange: (SortMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onPortraitColumnsChange: (Int) -> Unit,
    onLandscapeColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    // How many columns Automatic shows right now, in the current orientation.
    currentAutoColumns: Int = TYPICAL_PORTRAIT_COLUMNS,
    landscape: Boolean = false,
) {
    YomuBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        wideMaxWidth = 460.dp,
    ) { dismiss ->
        YomuSheetHeader(title = "Arrange", onClose = dismiss)
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            YomuSectionLabel(text = "Sort by")
            YomuConnectedChoiceGroup(
                options = SortMode.entries,
                selected = sortMode,
                label = { it.label },
                onSelect = onSortModeChange,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            YomuSectionLabel(text = "View")
            ViewTiles(selected = viewMode, onSelect = onViewModeChange)
        }
        val gridView = viewMode != LibraryViewMode.List
        Column {
            YomuSectionLabel(text = "Columns", modifier = Modifier.padding(bottom = YomuTheme.space.xxs))
            ColumnsStepper(
                label = "Portrait",
                columns = portraitColumns,
                autoColumns = if (landscape) TYPICAL_PORTRAIT_COLUMNS else currentAutoColumns,
                enabled = gridView,
                divider = true,
                onColumnsChange = onPortraitColumnsChange,
            )
            ColumnsStepper(
                label = "Landscape",
                columns = landscapeColumns,
                autoColumns = if (landscape) currentAutoColumns else TYPICAL_LANDSCAPE_COLUMNS,
                enabled = gridView,
                divider = false,
                onColumnsChange = onLandscapeColumnsChange,
            )
            ColumnsFootnote(
                gridView = gridView,
                portraitCustom = portraitColumns > LibraryPreferences.AUTO_COLUMNS,
                landscapeCustom = landscapeColumns > LibraryPreferences.AUTO_COLUMNS,
                onResetPortrait = { onPortraitColumnsChange(LibraryPreferences.AUTO_COLUMNS) },
                onResetLandscape = { onLandscapeColumnsChange(LibraryPreferences.AUTO_COLUMNS) },
            )
        }
    }
}

/** The four views as tiles: icon over a word; the chosen one is white with an ink outline. */
@Composable
private fun ViewTiles(selected: LibraryViewMode, onSelect: (LibraryViewMode) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val checked = mode == selected
            val stack = YomuTheme.stack
            val animate = yomuAnimationsEnabled()
            val container by animateColorAsState(
                targetValue = if (checked) stack.sheet else stack.raised,
                animationSpec = if (animate) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
                label = "viewTile",
            )
            val outline by animateColorAsState(
                targetValue = if (checked) stack.ink else Color.Transparent,
                animationSpec = if (animate) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
                label = "viewTileOutline",
            )
            val content = if (checked) stack.ink else stack.muted
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 76.dp)
                    .clip(shape)
                    .drawBehind { drawRect(container) }
                    .border(1.5.dp, outline, shape)
                    .selectable(
                        selected = checked,
                        role = Role.RadioButton,
                        onClick = {
                            if (!checked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(mode)
                        },
                    )
                    .padding(vertical = YomuTheme.space.sm, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(mode.icon, contentDescription = null, tint = content)
                Text(
                    text = mode.label,
                    style = StackType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    // "Comfortable" in a quarter of a phone's width, at a larger system text size,
                    // clipped: it shrinks to fit instead.
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = StackType.Caption.fontSize, stepSize = 0.5.sp),
                )
            }
        }
    }
}

/** One orientation's columns: − value +, starting from the automatic count. */
@Composable
private fun ColumnsStepper(
    label: String,
    columns: Int,
    autoColumns: Int,
    enabled: Boolean,
    divider: Boolean,
    onColumnsChange: (Int) -> Unit,
) {
    val stack = YomuTheme.stack
    val auto = columns <= LibraryPreferences.AUTO_COLUMNS
    val current = if (auto) {
        autoColumns.coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
    } else {
        columns.coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
    }
    val valueText = if (auto) "Automatic" else "$current"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind {
                if (divider) {
                    val rule = 1.dp.toPx()
                    drawRect(stack.rule, topLeft = Offset(0f, size.height - rule), size = size.copy(height = rule))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) stack.ink else stack.muted,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            icon = StackIcons.Remove,
            label = "Fewer $label columns".lowercase().replaceFirstChar { it.uppercase() },
            enabled = enabled && current > LibraryPreferences.MIN_COLUMNS,
            onClick = { onColumnsChange(current - 1) },
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) stack.ink else stack.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 76.dp)
                .semantics { stateDescription = "$label columns: $valueText" },
        )
        StepButton(
            icon = StackIcons.Add,
            label = "More $label columns".lowercase().replaceFirstChar { it.uppercase() },
            enabled = enabled && current < LibraryPreferences.MAX_COLUMNS,
            onClick = { onColumnsChange(current + 1) },
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    IconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) stack.ink else stack.faint.copy(alpha = 0.5f),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(stack.raised)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ColumnsFootnote(
    gridView: Boolean,
    portraitCustom: Boolean,
    landscapeCustom: Boolean,
    onResetPortrait: () -> Unit,
    onResetLandscape: () -> Unit,
) {
    val stack = YomuTheme.stack
    val resetLabel = when {
        !gridView -> null
        portraitCustom && landscapeCustom -> "Reset columns"
        portraitCustom -> "Reset portrait"
        landscapeCustom -> "Reset landscape"
        else -> null
    }
    val text = buildAnnotatedString {
        append(if (gridView) "Automatic fits columns to your screen and text size." else "Columns apply to the grid views.")
        if (resetLabel != null) {
            append(" ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "reset",
                    styles = TextLinkStyles(SpanStyle(color = stack.ink, fontWeight = FontWeight.SemiBold)),
                ) {
                    if (portraitCustom) onResetPortrait()
                    if (landscapeCustom) onResetLandscape()
                },
            ) { append(resetLabel) }
        }
    }
    Text(
        text = text,
        style = StackType.Caption,
        color = stack.muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

private val LibraryViewMode.icon: ImageVector
    get() = when (this) {
        LibraryViewMode.ComfortableGrid -> StackIcons.GridView
        LibraryViewMode.CompactGrid -> StackIcons.Apps
        LibraryViewMode.CoverOnlyGrid -> StackIcons.PhotoLibrary
        LibraryViewMode.List -> StackIcons.ViewList
    }
