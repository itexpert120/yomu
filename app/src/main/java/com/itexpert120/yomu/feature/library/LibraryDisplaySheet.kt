@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode

/** Native Material 3 arrangement sheet; choices apply immediately and the sheet stays simple. */
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
    if (!visible) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Arrange library",
                style = MaterialTheme.typography.headlineSmall,
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
                title = "View",
                options = LibraryViewMode.entries,
                selected = viewMode,
                label = { it.label },
                onSelected = onViewModeChange,
            )
            if (viewMode == LibraryViewMode.Grid) {
                val values = listOf(LibraryPreferences.AUTO_COLUMNS) +
                    (LibraryPreferences.MIN_COLUMNS..LibraryPreferences.MAX_COLUMNS).toList()
                ArrangementChoiceGroup(
                    title = "Grid columns",
                    options = values,
                    selected = columns,
                    label = { if (it == LibraryPreferences.AUTO_COLUMNS) "Automatic" else it.toString() },
                    onSelected = onColumnsChange,
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun <T> ColumnScope.ArrangementChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Surface(
            modifier = Modifier.selectableGroup(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                options.forEachIndexed { index, option ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelected(option) },
                                role = Role.RadioButton,
                            ),
                        headlineContent = { Text(label(option)) },
                        trailingContent = {
                            RadioButton(
                                selected = option == selected,
                                onClick = null,
                            )
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    if (index < options.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
