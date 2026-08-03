package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode

/** All library arrangement controls in one draggable sheet. */
@Composable
internal fun LibraryOptionsSheet(
    visible: Boolean,
    sortMode: SortMode,
    groupMode: GroupMode,
    viewMode: LibraryViewMode,
    columns: Int,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Arrange library",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.title,
            )

            SheetSection(label = "Sort") {
                val modes = SortMode.entries
                YomuSegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(sortMode),
                    onSelected = { onSortModeChange(modes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SheetSection(label = "Group") {
                val modes = GroupMode.entries
                YomuSegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(groupMode),
                    onSelected = { onGroupModeChange(modes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SheetSection(label = "View") {
                val modes = LibraryViewMode.entries
                YomuSegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(viewMode),
                    onSelected = { onViewModeChange(modes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Animate in/out so switching to List doesn't snap the sheet height.
            AnimatedVisibility(
                visible = viewMode == LibraryViewMode.Grid,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SheetSection(label = "Columns") {
                    // "Auto" sizes columns to the screen width; the numbers force a fixed count.
                    val values = listOf(LibraryPreferences.AUTO_COLUMNS) +
                        (LibraryPreferences.MIN_COLUMNS..LibraryPreferences.MAX_COLUMNS)
                    YomuSegmentedControl(
                        options = values.map { if (it == LibraryPreferences.AUTO_COLUMNS) "Auto" else it.toString() },
                        selectedIndex = values.indexOf(columns).coerceAtLeast(0),
                        onSelected = { onColumnsChange(values[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            FilledTonalButton(
                onClick = dismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SheetSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        content()
    }
}
