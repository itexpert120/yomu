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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import kotlinx.coroutines.launch

/**
 * Display options for the library grid: view mode and (grid-only) column count. Uses the same
 * hide-then-dismiss animation as [com.itexpert120.yomu.core.designsystem.YomuOptionSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryDisplaySheet(
    visible: Boolean,
    viewMode: LibraryViewMode,
    columns: Int,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun animateDismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YomuTheme.colors.panel,
        contentColor = YomuTheme.colors.textPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
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
                    val options = (LibraryPreferences.MIN_COLUMNS..LibraryPreferences.MAX_COLUMNS).toList()
                    YomuSegmentedControl(
                        options = options.map { it.toString() },
                        selectedIndex = options.indexOf(columns).coerceAtLeast(0),
                        onSelected = { onColumnsChange(options[it]) },
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
                        onClick = { animateDismiss() },
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
