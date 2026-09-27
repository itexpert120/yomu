@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import kotlin.math.roundToInt

/** Arrange controls use a centered tablet dialog and retain a bottom sheet on phones. */
@Composable
internal fun LibraryOptionsSheet(
    visible: Boolean,
    sortMode: SortMode,
    groupMode: GroupMode,
    viewMode: LibraryViewMode,
    portraitColumns: Int,
    landscapeColumns: Int,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onPortraitColumnsChange: (Int) -> Unit,
    onLandscapeColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        wideMaxWidth = 460.dp,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Arrange library",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )

            ArrangementChoiceGroup(
                title = "Sort by",
                options = SortMode.entries,
                selected = sortMode,
                label = { it.label },
                onSelected = onSortModeChange,
            )
            ArrangementChoiceGroup(
                title = "Group by",
                options = GroupMode.entries,
                selected = groupMode,
                label = { it.label },
                onSelected = onGroupModeChange,
            )
            ArrangementChoiceGroup(
                title = "Library view",
                options = LibraryViewMode.entries,
                selected = viewMode,
                label = { it.label },
                onSelected = onViewModeChange,
            )
            GridColumnsControl(
                title = "Portrait columns",
                columns = portraitColumns,
                enabled = viewMode != LibraryViewMode.List,
                onColumnsChange = onPortraitColumnsChange,
            )
            GridColumnsControl(
                title = "Landscape columns",
                columns = landscapeColumns,
                enabled = viewMode != LibraryViewMode.List,
                onColumnsChange = onLandscapeColumnsChange,
            )

            Button(
                onClick = dismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GridColumnsControl(
    title: String,
    columns: Int,
    enabled: Boolean,
    onColumnsChange: (Int) -> Unit,
) {
    val value = if (columns <= LibraryPreferences.AUTO_COLUMNS) {
        LibraryPreferences.AUTO_COLUMNS
    } else {
        columns.coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
    }
    val sliderValue = if (value == LibraryPreferences.AUTO_COLUMNS) {
        0f
    } else {
        (value - LibraryPreferences.MIN_COLUMNS + 1).toFloat()
    }
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val labelColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabledColor
    val valueColor = if (enabled) MaterialTheme.colorScheme.primary else disabledColor
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (value == LibraryPreferences.AUTO_COLUMNS) {
                    "Automatic"
                } else {
                    "$value columns"
                },
                color = valueColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { position ->
                val index = position.roundToInt()
                onColumnsChange(
                    if (index == 0) {
                        LibraryPreferences.AUTO_COLUMNS
                    } else {
                        index + LibraryPreferences.MIN_COLUMNS - 1
                    },
                )
            },
            valueRange = 0f..(
                LibraryPreferences.MAX_COLUMNS - LibraryPreferences.MIN_COLUMNS + 1
                ).toFloat(),
            steps = LibraryPreferences.MAX_COLUMNS - LibraryPreferences.MIN_COLUMNS,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Automatic",
                color = labelColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "7 columns",
                color = labelColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun <T> ArrangementChoiceGroup(
    modifier: Modifier = Modifier,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        YomuConnectedChoiceGroup(
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelected,
        )
    }
}
