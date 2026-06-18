package com.itexpert120.yomu.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.itexpert120.yomu.core.designsystem.YomuCircleIconButton
import com.itexpert120.yomu.core.designsystem.YomuOptionSheet
import com.itexpert120.yomu.core.designsystem.YomuPillFilter
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
internal fun LibraryHeader(
    bookCount: Int,
    searchActive: Boolean,
    searchQuery: String,
    sortMode: SortMode,
    groupMode: GroupMode,
    viewMode: LibraryViewMode,
    themePreference: ThemePreference,
    showSortSheet: Boolean,
    showGroupSheet: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onSortSheetToggle: () -> Unit,
    onGroupSheetToggle: () -> Unit,
    onDisplaySheetToggle: () -> Unit,
    onImport: () -> Unit,
    onThemeToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    elevated: Boolean,
) {
    // Lift the header onto its own plane once the grid scrolls beneath it; the
    // shadow defines the seam instead of fading the content under it.
    val elevation by animateDpAsState(
        targetValue = if (elevated) 4.dp else 0.dp,
        label = "headerElevation",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation)
            .background(YomuTheme.colors.appBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display,
                )
                Text(
                    text = "$bookCount books",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YomuCircleIconButton(
                    onClick = onImport,
                    icon = Icons.Rounded.Add,
                    contentDescription = "Import EPUB",
                )
                YomuCircleIconButton(
                    onClick = onThemeToggle,
                    icon = themePreference.themeIcon(),
                    contentDescription = "Toggle theme",
                )
                YomuCircleIconButton(
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                )
            }
        }

        if (searchActive) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchToggle,
            )
        } else {
            SearchHint(onClick = onSearchToggle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YomuPillFilter(
                label = "Sort",
                value = sortMode.label,
                onClick = onSortSheetToggle,
            )
            YomuPillFilter(
                label = "Group",
                value = groupMode.label,
                onClick = onGroupSheetToggle,
            )
            YomuPillFilter(
                label = "View",
                value = viewMode.label,
                onClick = onDisplaySheetToggle,
            )
        }
    }

    YomuOptionSheet(
        visible = showSortSheet,
        onDismiss = onSortSheetToggle,
        title = "Sort by",
        options = SortMode.entries,
        selectedOption = sortMode,
        onSelect = onSortModeChange,
        label = { it.label },
    )

    YomuOptionSheet(
        visible = showGroupSheet,
        onDismiss = onGroupSheetToggle,
        title = "Group by",
        options = GroupMode.entries,
        selectedOption = groupMode,
        onSelect = onGroupModeChange,
        label = { it.label },
    )
}

/** Icon reflects the active theme choice so the quick toggle communicates current state. */
private fun ThemePreference.themeIcon(): ImageVector = when (this) {
    ThemePreference.System -> Icons.Rounded.BrightnessAuto
    ThemePreference.Light -> Icons.Rounded.LightMode
    ThemePreference.Dark -> Icons.Rounded.DarkMode
}

@Composable
private fun SearchHint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Search books, authors",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = YomuTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .border(1.dp, YomuTheme.colors.accent, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(vertical = 10.dp),
            textStyle = YomuTheme.type.body.copy(color = YomuTheme.colors.textPrimary),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search books, authors",
                            color = YomuTheme.colors.textMuted,
                            style = YomuTheme.type.body,
                        )
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(YomuTheme.colors.surfaceRaised)
                .clickable {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        keyboardController?.hide()
                        onClose()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = YomuTheme.colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
