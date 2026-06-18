package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode

/** Display options (view mode + grid columns) as a draggable slide-up sheet. */
@Composable
internal fun LibraryDisplaySheet(
    visible: Boolean,
    viewMode: LibraryViewMode,
    columns: Int,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Display", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.title)

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(YomuTheme.radius.md))
                    .background(YomuTheme.colors.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Done", color = YomuTheme.colors.textSecondary, style = YomuTheme.type.body)
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
